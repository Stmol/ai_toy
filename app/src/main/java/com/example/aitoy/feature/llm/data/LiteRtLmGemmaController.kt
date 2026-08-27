package com.example.aitoy.feature.llm.data

import android.os.SystemClock
import android.util.Log
import com.example.aitoy.feature.llm.domain.LlmBackend
import com.example.aitoy.feature.llm.domain.LlmController
import com.example.aitoy.feature.llm.domain.LlmGenerationCompletion
import com.example.aitoy.feature.llm.domain.LlmError
import com.example.aitoy.feature.llm.domain.LlmGenerationState
import com.example.aitoy.feature.llm.domain.LlmHistoryEntry
import com.example.aitoy.feature.llm.domain.LlmInferenceSettings
import com.example.aitoy.feature.llm.domain.LlmModelState
import com.example.aitoy.feature.llm.domain.LlmSettingsRepository
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LiteRtLmJniException
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class LiteRtLmGemmaController(
    private val modelLocator: LlmModelLocator,
    private val settingsRepository: LlmSettingsRepository
) : LlmController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val runtimeLock = Any()

    private val _modelState = MutableStateFlow(initialModelState())
    private val _generationState = MutableStateFlow(LlmGenerationState.Idle)
    private val _responseText = MutableStateFlow("")
    private val _selectedBackend = MutableStateFlow(settingsRepository.loadBackend())
    private val _activeBackend = MutableStateFlow<LlmBackend?>(null)
    private val _lastLoadDurationMs = MutableStateFlow<Long?>(null)
    private val _lastGenerationDurationMs = MutableStateFlow<Long?>(null)
    private val _settings = MutableStateFlow(settingsRepository.loadInferenceSettings())
    private val _historyEntries = MutableStateFlow<List<LlmHistoryEntry>>(emptyList())
    private val _errors = MutableSharedFlow<LlmError>(extraBufferCapacity = 1)
    private val _generationCompletions =
        MutableSharedFlow<LlmGenerationCompletion>(extraBufferCapacity = 8)
    private val generationRequestCounter = java.util.concurrent.atomic.AtomicLong(0L)

    override val modelState: StateFlow<LlmModelState> = _modelState.asStateFlow()
    override val generationState: StateFlow<LlmGenerationState> = _generationState.asStateFlow()
    override val responseText: StateFlow<String> = _responseText.asStateFlow()
    override val selectedBackend: StateFlow<LlmBackend> = _selectedBackend.asStateFlow()
    override val activeBackend: StateFlow<LlmBackend?> = _activeBackend.asStateFlow()
    override val lastLoadDurationMs: StateFlow<Long?> = _lastLoadDurationMs.asStateFlow()
    override val lastGenerationDurationMs: StateFlow<Long?> = _lastGenerationDurationMs.asStateFlow()
    override val settings: StateFlow<LlmInferenceSettings> = _settings.asStateFlow()
    override val historyEntries: StateFlow<List<LlmHistoryEntry>> = _historyEntries.asStateFlow()
    override val errors: Flow<LlmError> = _errors.asSharedFlow()
    override val generationCompletions: Flow<LlmGenerationCompletion> =
        _generationCompletions.asSharedFlow()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var loadJob: Job? = null
    private var generationJob: Job? = null

    override fun expectedModelFilePath(): String = modelLocator.expectedModelFilePath()

    override fun loadModel() {
        if (_modelState.value == LlmModelState.Loaded || _modelState.value == LlmModelState.Loading) {
            return
        }

        val modelFile = modelLocator.resolveModelFile()
        if (modelFile == null) {
            _modelState.value = LlmModelState.Missing
            _errors.tryEmit(LlmError.ModelMissing)
            return
        }

        _modelState.value = LlmModelState.Loading
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                val backend = _selectedBackend.value
                val loadStartedAt = SystemClock.elapsedRealtime()
                val settings = _settings.value
                val loadedEngine = runCatching {
                    createEngine(
                        modelFile = modelFile,
                        backend = backend,
                        settings = settings
                    )
                }.getOrElse { throwable ->
                    handleModelLoadFailure(throwable)
                    return@launch
                }

                val loadedConversation = runCatching {
                    createConversation(loadedEngine, settings)
                }.getOrElse { throwable ->
                    Log.e(TAG, "LiteRT-LM conversation creation failed", throwable)
                    safeCloseEngine(loadedEngine)
                    handleModelLoadFailure(throwable)
                    return@launch
                }

                synchronized(runtimeLock) {
                    safeCloseConversation(conversation)
                    conversation = loadedConversation
                    safeCloseEngine(engine)
                    engine = loadedEngine
                }

                _activeBackend.value = backend
                _lastLoadDurationMs.value = SystemClock.elapsedRealtime() - loadStartedAt
                _modelState.value = LlmModelState.Loaded
                if (_generationState.value == LlmGenerationState.Error) {
                    _generationState.value = LlmGenerationState.Idle
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    throw throwable
                }
                handleModelLoadFailure(throwable)
            }
        }
    }

    override fun applySettings(settings: LlmInferenceSettings) {
        val previousSettings = _settings.value
        if (previousSettings == settings) {
            return
        }

        _settings.value = settings
        settingsRepository.saveInferenceSettings(settings)

        if (_modelState.value != LlmModelState.Loaded ||
            _generationState.value == LlmGenerationState.Generating ||
            _modelState.value == LlmModelState.Loading
        ) {
            return
        }

        scope.launch {
            reloadModelForCurrentSettings()
        }
    }

    override fun resetSettings() {
        applySettings(LlmInferenceSettings())
    }

    override fun unloadModel() {
        if (_generationState.value == LlmGenerationState.Generating) {
            return
        }

        generationJob?.cancel()
        generationJob = null
        loadJob?.cancel()
        loadJob = null

        releaseRuntime()

        _responseText.value = ""
        _generationState.value = LlmGenerationState.Idle
        _modelState.value = initialModelState()
        _activeBackend.value = null
    }

    override fun generate(prompt: String) {
        if (prompt.isBlank()) {
            _generationState.value = LlmGenerationState.Error
            _errors.tryEmit(LlmError.EmptyPrompt)
            return
        }

        val requestId = generationRequestCounter.incrementAndGet()

        if (_modelState.value != LlmModelState.Loaded) {
            if (modelLocator.resolveModelFile() == null) {
                _modelState.value = LlmModelState.Missing
                _errors.tryEmit(LlmError.ModelMissing)
            } else {
                _generationState.value = LlmGenerationState.Error
                _errors.tryEmit(LlmError.LoadFailed())
            }
            return
        }

        val activeConversation = synchronized(runtimeLock) { conversation }
        if (activeConversation == null) {
            _modelState.value = LlmModelState.Error
            _generationState.value = LlmGenerationState.Error
            _errors.tryEmit(LlmError.LoadFailed())
            return
        }

        generationJob?.cancel()
        generationJob = scope.launch {
            try {
                _generationState.value = LlmGenerationState.Generating
                _responseText.value = ""
                val generationStartedAt = SystemClock.elapsedRealtime()

                val response = runCatching {
                    activeConversation.sendMessage(prompt.trim(), emptyMap())
                }.getOrElse {
                    Log.e(TAG, "LiteRT-LM generation failed", it)
                    _generationState.value = LlmGenerationState.Error
                    _errors.tryEmit(LlmError.GenerationFailed(it.message ?: it.javaClass.simpleName))
                    return@launch
                }

                _responseText.value = extractText(response).trim()
                val durationMs = SystemClock.elapsedRealtime() - generationStartedAt
                _lastGenerationDurationMs.value = durationMs
                _historyEntries.value = listOf(
                    LlmHistoryEntry(
                        promptText = prompt.toHistoryPrompt(),
                        backend = _activeBackend.value ?: _selectedBackend.value,
                        durationMs = durationMs
                    )
                ) + _historyEntries.value
                _generationState.value = LlmGenerationState.Completed
                _generationCompletions.tryEmit(
                    LlmGenerationCompletion(
                        requestId = requestId,
                        promptText = prompt.trim(),
                        responseText = _responseText.value
                    )
                )
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    throw throwable
                }
                Log.e(TAG, "LiteRT-LM generation crashed", throwable)
                _generationState.value = LlmGenerationState.Error
                _errors.tryEmit(LlmError.GenerationFailed(throwable.message ?: throwable.javaClass.simpleName))
            }
        }
    }

    override fun selectBackend(backend: LlmBackend) {
        if (_selectedBackend.value == backend) {
            return
        }
        _selectedBackend.value = backend
        settingsRepository.saveBackend(backend)

        if (_modelState.value == LlmModelState.Loading ||
            _generationState.value == LlmGenerationState.Generating
        ) {
            return
        }

        if (_modelState.value == LlmModelState.Loaded) {
            unloadModel()
            loadModel()
        }
    }

    override fun release() {
        generationJob?.cancel()
        generationJob = null
        loadJob?.cancel()
        loadJob = null
        releaseRuntime()
        _responseText.value = ""
        _generationState.value = LlmGenerationState.Idle
        _modelState.value = initialModelState()
        _activeBackend.value = null
    }

    private fun extractText(message: com.google.ai.edge.litertlm.Message): String {
        return message.contents.contents
            .joinToString(separator = "") { content ->
                when (content) {
                    is com.google.ai.edge.litertlm.Content.Text -> content.text
                    else -> ""
                }
            }
            .ifBlank { message.toString() }
    }

    private fun reloadModelForCurrentSettings() {
        val modelFile = modelLocator.resolveModelFile()
        if (modelFile == null) {
            _modelState.value = LlmModelState.Missing
            _errors.tryEmit(LlmError.ModelMissing)
            return
        }

        val backend = _activeBackend.value ?: _selectedBackend.value
        val settings = _settings.value
        _modelState.value = LlmModelState.Loading
        releaseRuntime()

        val loadStartedAt = SystemClock.elapsedRealtime()
        val loadedEngine = runCatching {
            createEngine(
                modelFile = modelFile,
                backend = backend,
                settings = settings
            )
        }.getOrElse { throwable ->
            handleModelLoadFailure(throwable)
            return
        }

        val loadedConversation = runCatching {
            createConversation(loadedEngine, settings)
        }.getOrElse { throwable ->
            Log.e(TAG, "LiteRT-LM conversation recreation failed", throwable)
            safeCloseEngine(loadedEngine)
            handleModelLoadFailure(throwable)
            return
        }

        synchronized(runtimeLock) {
            safeCloseConversation(conversation)
            conversation = loadedConversation
            safeCloseEngine(engine)
            engine = loadedEngine
        }

        _activeBackend.value = backend
        _lastLoadDurationMs.value = SystemClock.elapsedRealtime() - loadStartedAt
        _modelState.value = LlmModelState.Loaded
        if (_generationState.value == LlmGenerationState.Error) {
            _generationState.value = LlmGenerationState.Idle
        }
    }

    private fun createEngine(
        modelFile: File,
        backend: LlmBackend,
        settings: LlmInferenceSettings
    ): Engine {
        return Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = backend.toLiteRtBackend(),
                cacheDir = cacheDirFor(modelFile)
            )
        ).also { it.initialize() }
    }

    private fun createConversation(
        activeEngine: Engine,
        settings: LlmInferenceSettings
    ): Conversation {
        val systemInstruction = settings.systemPrompt
            .trim()
            .takeIf(String::isNotBlank)
            ?.let(Contents::of)
            ?: Contents.of(emptyList())

        return activeEngine.createConversation(
            ConversationConfig(
                systemInstruction,
                emptyList(),
                emptyList(),
                samplerConfig = SamplerConfig(
                    settings.topK,
                    settings.topP,
                    settings.temperature,
                    settings.seed
                )
            )
        )
    }

    private fun handleModelLoadFailure(throwable: Throwable) {
        Log.e(TAG, "LiteRT-LM model load failed", throwable)
        releaseRuntime()
        _activeBackend.value = null
        _lastLoadDurationMs.value = null
        _modelState.value = modelErrorState(throwable)
        _errors.tryEmit(modelErrorType(throwable))
    }

    private fun releaseRuntime() {
        synchronized(runtimeLock) {
            safeCloseConversation(conversation)
            conversation = null
            safeCloseEngine(engine)
            engine = null
        }
    }

    private fun safeCloseConversation(activeConversation: Conversation?) {
        runCatching {
            activeConversation?.close()
        }.onFailure {
            Log.w(TAG, "Failed to close LiteRT-LM conversation", it)
        }
    }

    private fun safeCloseEngine(activeEngine: Engine?) {
        runCatching {
            activeEngine?.close()
        }.onFailure {
            Log.w(TAG, "Failed to close LiteRT-LM engine", it)
        }
    }

    private fun cacheDirFor(modelFile: File): String {
        val cacheDir = modelFile.parentFile?.resolve(CACHE_DIR_NAME)
            ?: return modelFile.absolutePath
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return cacheDir.absolutePath
    }

    private fun String.toHistoryPrompt(): String {
        return trim().replace("\\s+".toRegex(), " ")
    }

    private fun initialModelState(): LlmModelState {
        return if (modelLocator.resolveModelFile() != null) {
            LlmModelState.Unloaded
        } else {
            LlmModelState.Missing
        }
    }

    private fun modelErrorState(throwable: Throwable): LlmModelState {
        return if (throwable is LiteRtLmJniException && modelLocator.resolveModelFile() == null) {
            LlmModelState.Missing
        } else {
            LlmModelState.Error
        }
    }

    private fun modelErrorType(throwable: Throwable): LlmError {
        return if (throwable is LiteRtLmJniException && modelLocator.resolveModelFile() == null) {
            LlmError.ModelMissing
        } else {
            LlmError.LoadFailed(throwable.message ?: throwable.javaClass.simpleName)
        }
    }

    private fun LlmBackend.toLiteRtBackend(): Backend {
        return when (this) {
            LlmBackend.CPU -> Backend.CPU()
            LlmBackend.GPU -> Backend.GPU()
        }
    }

    private companion object {
        const val TAG = "LiteRtLmGemma"
        const val CACHE_DIR_NAME = "litertlm-cache"
    }
}
