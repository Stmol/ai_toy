package com.example.aitoy.feature.llm.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aitoy.feature.asr.domain.AsrModelState
import com.example.aitoy.feature.asr.domain.PushToTalkAsrSessionCoordinator
import com.example.aitoy.feature.asr.domain.PushToTalkAsrSessionOwner
import com.example.aitoy.feature.asr.domain.PushToTalkAsrStartResult
import com.example.aitoy.feature.asr.domain.RecognitionState
import com.example.aitoy.feature.asr.domain.SpeechRecognitionController
import com.example.aitoy.feature.asr.domain.SpeechRecognitionError
import com.example.aitoy.feature.asr.domain.SpeechRecognitionSessionEvent
import com.example.aitoy.feature.llm.data.LlmModelLocator
import com.example.aitoy.feature.llm.domain.LlmBackend
import com.example.aitoy.feature.llm.domain.LlmController
import com.example.aitoy.feature.llm.domain.LlmError
import com.example.aitoy.feature.llm.domain.LlmGenerationCompletion
import com.example.aitoy.feature.llm.domain.LlmGenerationState
import com.example.aitoy.feature.llm.domain.LlmHistoryEntry
import com.example.aitoy.feature.llm.domain.LlmInferenceSettings
import com.example.aitoy.feature.llm.domain.LlmModelState
import com.example.aitoy.feature.microphone.domain.AudioCaptureController
import com.example.aitoy.feature.microphone.domain.AudioCaptureError
import com.example.aitoy.feature.microphone.domain.CaptureState
import com.example.aitoy.feature.microphone.domain.PermissionState
import com.example.aitoy.feature.tts.domain.TtsController
import com.example.aitoy.feature.tts.domain.TtsError
import com.example.aitoy.feature.tts.domain.TtsPlaybackState
import com.example.aitoy.feature.tts.domain.TtsSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class LlmViewModel(
    private val llmController: LlmController,
    private val llmModelLocator: LlmModelLocator,
    private val audioCaptureController: AudioCaptureController,
    private val speechRecognitionController: SpeechRecognitionController,
    private val pushToTalkAsrSessionCoordinator: PushToTalkAsrSessionCoordinator,
    private val ttsController: TtsController,
    private val ttsSettingsRepository: TtsSettingsRepository
) : ViewModel() {
    private val promptText = MutableStateFlow("")
    private val errorMessage = MutableStateFlow<String?>(null)
    private val backendReloadRequested = MutableStateFlow(false)
    private val voicePromptErrorMessage = MutableStateFlow<String?>(null)
    private val voicePromptPermissionState = MutableStateFlow(PermissionState.Unknown)
    private val ttsErrorMessage = MutableStateFlow<String?>(null)
    private val settingsDraft = MutableStateFlow(
        LlmSettingsDraft.from(llmController.settings.value)
    )
    private val runtimeState = combine(
        combine(
            llmController.selectedBackend,
            llmController.activeBackend,
            llmController.lastLoadDurationMs,
            llmController.lastGenerationDurationMs
        ) { selectedBackend, activeBackend, lastLoadDurationMs, lastGenerationDurationMs ->
            LlmRuntimeMetricsSnapshot(
                selectedBackend = selectedBackend,
                activeBackend = activeBackend,
                lastLoadDurationMs = lastLoadDurationMs,
                lastGenerationDurationMs = lastGenerationDurationMs
            )
        },
        combine(
            llmController.historyEntries,
            promptText
        ) { historyEntries, promptText ->
            LlmPromptSnapshot(
                historyEntries = historyEntries,
                promptText = promptText
            )
        }
    ) { metrics, prompt ->
        LlmRuntimeSnapshot(
            selectedBackend = metrics.selectedBackend,
            activeBackend = metrics.activeBackend,
            lastLoadDurationMs = metrics.lastLoadDurationMs,
            lastGenerationDurationMs = metrics.lastGenerationDurationMs,
            historyEntries = prompt.historyEntries,
            promptText = prompt.promptText
        )
    }
    private val voicePromptState = combine(
        combine(
            voicePromptPermissionState,
            speechRecognitionController.modelState,
            audioCaptureController.captureState,
            speechRecognitionController.recognitionState
        ) { permissionState, modelState, captureState, recognitionState ->
            VoicePromptRuntimeSnapshot(
                permissionState = permissionState,
                modelState = modelState,
                captureState = captureState,
                recognitionState = recognitionState
            )
        },
        combine(
            pushToTalkAsrSessionCoordinator.activeOwner,
            voicePromptErrorMessage
        ) { activeOwner, errorMessage ->
            VoicePromptSessionSnapshot(
                isSessionActive = activeOwner == PushToTalkAsrSessionOwner.LlmVoicePrompt,
                errorMessage = errorMessage
            )
        }
    ) { runtime, session ->
        VoicePromptSnapshot(
            permissionState = runtime.permissionState,
            modelState = runtime.modelState,
            captureState = runtime.captureState,
            recognitionState = runtime.recognitionState,
            isSessionActive = session.isSessionActive,
            errorMessage = session.errorMessage
        )
    }
    private val settingsState = combine(
        llmController.settings,
        settingsDraft
    ) { appliedSettings, draft ->
        LlmSettingsSnapshot(
            appliedSettings = appliedSettings,
            draft = draft
        )
    }
    private val uiInputState = combine(
        runtimeState,
        settingsState,
        voicePromptState,
        errorMessage
    ) { runtime, settings, voicePrompt, errorMessage ->
        LlmUiInputSnapshot(
            runtime = runtime,
            settings = settings,
            voicePrompt = voicePrompt,
            errorMessage = errorMessage
        )
    }
    private val generationStateSnapshot = combine(
        llmController.modelState,
        llmController.generationState,
        llmController.responseText,
        backendReloadRequested
    ) { modelState, generationState, responseText, backendReloadRequested ->
        LlmGenerationUiSnapshot(
            modelState = modelState,
            generationState = generationState,
            responseText = responseText,
            backendReloadRequested = backendReloadRequested
        )
    }
    private val ttsUiState = combine(
        ttsController.playbackState,
        ttsSettingsRepository.settings,
        ttsErrorMessage
    ) { playbackState, settings, errorMessage ->
        TtsUiSnapshot(
            playbackState = playbackState,
            settings = settings,
            errorMessage = errorMessage
        )
    }

    val uiState: StateFlow<LlmUiState> = combine(
        generationStateSnapshot,
        ttsUiState,
        uiInputState
    ) { generationSnapshot, ttsUiState, input ->
        val runtime = input.runtime
        val settings = input.settings
        val voicePrompt = input.voicePrompt
        val errorMessage = input.errorMessage
        val modelState = generationSnapshot.modelState
        val generationState = generationSnapshot.generationState
        val responseText = generationSnapshot.responseText
        val ttsPlaybackState = ttsUiState.playbackState
        val inspection = runCatching { llmModelLocator.inspectModelFile() }.getOrNull()
        val activePath = inspection?.activePath
        val expectedPath = inspection?.expectedPath ?: llmModelLocator.expectedModelFilePath()
        val modelPath = activePath ?: expectedPath
        val modelName = activePath?.let(::nameFromPath)

        val isBusy = modelState == LlmModelState.Loading ||
            generationState == LlmGenerationState.Generating
        val isBackendReloading =
            generationSnapshot.backendReloadRequested && modelState == LlmModelState.Loading

        val settingsError = settings.draft.toValidationError()
        val defaultDraft = LlmSettingsDraft.from(LlmInferenceSettings())
        val hasRecoverableVoicePrompt = !voicePrompt.isSessionActive &&
            (voicePrompt.captureState == CaptureState.Listening ||
                voicePrompt.recognitionState == RecognitionState.Streaming ||
                voicePrompt.recognitionState == RecognitionState.Finalizing)
        val isVoicePromptBusy = voicePrompt.isSessionActive &&
            (voicePrompt.captureState == CaptureState.Listening ||
                voicePrompt.recognitionState == RecognitionState.Streaming ||
                voicePrompt.recognitionState == RecognitionState.Finalizing)
        val canStopVoicePrompt = (voicePrompt.isSessionActive || hasRecoverableVoicePrompt) &&
            (voicePrompt.captureState == CaptureState.Listening ||
                voicePrompt.recognitionState == RecognitionState.Streaming ||
                voicePrompt.recognitionState == RecognitionState.Finalizing)
        val isVoicePromptListening = canStopVoicePrompt &&
            voicePrompt.captureState == CaptureState.Listening
        val isVoicePromptTranscribing = canStopVoicePrompt &&
            voicePrompt.recognitionState == RecognitionState.Finalizing
        val canUseVoicePrompt = modelState == LlmModelState.Loaded &&
            !isBusy &&
            !voicePrompt.isSessionActive &&
            voicePrompt.modelState == AsrModelState.Loaded &&
            voicePrompt.captureState != CaptureState.Listening &&
            voicePrompt.recognitionState != RecognitionState.Streaming &&
            voicePrompt.recognitionState != RecognitionState.Finalizing &&
            voicePrompt.permissionState != PermissionState.PermanentlyDenied
        val isSpeakingResponse = ttsPlaybackState is TtsPlaybackState.Preparing ||
            ttsPlaybackState is TtsPlaybackState.Speaking
        val canSpeakResponse = responseText.isNotBlank() && !isBusy && !isVoicePromptBusy
        val canStopSpeaking = isSpeakingResponse

        LlmUiState(
            modelState = modelState,
            modelName = modelName,
            modelPath = modelPath,
            selectedBackend = runtime.selectedBackend,
            activeBackend = runtime.activeBackend,
            lastLoadDurationMs = runtime.lastLoadDurationMs,
            lastGenerationDurationMs = runtime.lastGenerationDurationMs,
            historyEntries = runtime.historyEntries,
            promptText = runtime.promptText,
            responseText = responseText,
            generationState = generationState,
            errorMessage = errorMessage ?: settingsError,
            appliedSettings = settings.appliedSettings,
            systemPromptDraft = settings.draft.systemPrompt,
            temperatureText = settings.draft.temperature,
            topKText = settings.draft.topK,
            topPText = settings.draft.topP,
            seedText = settings.draft.seed,
            maxTokensText = settings.draft.maxTokens,
            voicePromptModelState = voicePrompt.modelState,
            voicePromptCaptureState = voicePrompt.captureState,
            voicePromptRecognitionState = voicePrompt.recognitionState,
            voicePromptErrorMessage = voicePrompt.errorMessage,
            isVoicePromptListening = isVoicePromptListening,
            isVoicePromptTranscribing = isVoicePromptTranscribing,
            canStartVoicePrompt = canUseVoicePrompt,
            canStopVoicePrompt = canStopVoicePrompt,
            canEditPrompt = !isVoicePromptBusy,
            canGenerate = modelState == LlmModelState.Loaded &&
                generationState != LlmGenerationState.Generating &&
                runtime.promptText.isNotBlank() &&
                !isVoicePromptBusy,
            canChangeBackend = !isBusy,
            canApplySettings = !isBusy &&
                !isVoicePromptBusy &&
                settingsError == null &&
                settings.draft != LlmSettingsDraft.from(settings.appliedSettings),
            canResetSettings = !isBusy &&
                !isVoicePromptBusy &&
                settings.draft != defaultDraft,
            ttsPlaybackState = ttsPlaybackState,
            ttsErrorMessage = ttsUiState.errorMessage,
            selectedTtsVoice = ttsUiState.settings.selectedVoice,
            isSpeakingResponse = isSpeakingResponse,
            canSpeakResponse = canSpeakResponse,
            canStopSpeaking = canStopSpeaking,
            isBackendReloading = isBackendReloading,
            isBusy = isBusy
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = LlmUiState(modelPath = llmModelLocator.expectedModelFilePath())
    )

    init {
        viewModelScope.launch {
            llmController.modelState.collect { modelState ->
                if (backendReloadRequested.value && modelState != LlmModelState.Loading) {
                    backendReloadRequested.value = false
                }
            }
        }
        viewModelScope.launch {
            llmController.errors.collect { error ->
                errorMessage.value = error.toUserMessage(llmModelLocator.expectedModelFilePath())
            }
        }
        viewModelScope.launch {
            llmController.generationCompletions.collect { completion ->
                handleGenerationCompleted(completion)
            }
        }
        viewModelScope.launch {
            ttsController.errors.collect { error ->
                ttsErrorMessage.value = error.toUserMessage()
            }
        }
        viewModelScope.launch {
            audioCaptureController.errors.collect { error ->
                if (!pushToTalkAsrSessionCoordinator.isOwner(PushToTalkAsrSessionOwner.LlmVoicePrompt)) {
                    return@collect
                }

                voicePromptErrorMessage.value = error.toUserMessage()
                pushToTalkAsrSessionCoordinator.cancel(PushToTalkAsrSessionOwner.LlmVoicePrompt)
            }
        }
        viewModelScope.launch {
            speechRecognitionController.errors.collect { error ->
                if (!pushToTalkAsrSessionCoordinator.isOwner(PushToTalkAsrSessionOwner.LlmVoicePrompt)) {
                    return@collect
                }

                voicePromptErrorMessage.value = error.toUserMessage()
                pushToTalkAsrSessionCoordinator.cancel(PushToTalkAsrSessionOwner.LlmVoicePrompt)
            }
        }
        viewModelScope.launch {
            speechRecognitionController.sessionEvents.collect { event ->
                if (!pushToTalkAsrSessionCoordinator.isOwner(PushToTalkAsrSessionOwner.LlmVoicePrompt)) {
                    return@collect
                }

                when (event) {
                    SpeechRecognitionSessionEvent.AutoStopRequested ->
                        pushToTalkAsrSessionCoordinator.autoStopCurrentSession()
                }
            }
        }
        viewModelScope.launch {
            speechRecognitionController.recognitionState.collect { recognitionState ->
                if (!pushToTalkAsrSessionCoordinator.isOwner(PushToTalkAsrSessionOwner.LlmVoicePrompt) ||
                    recognitionState != RecognitionState.Completed
                ) {
                    return@collect
                }

                val transcript = speechRecognitionController.finalText.value?.trim().orEmpty()
                if (transcript.isNotBlank()) {
                    promptText.value = transcript
                    errorMessage.value = null
                    voicePromptErrorMessage.value = null
                    llmController.generate(transcript)
                }
                pushToTalkAsrSessionCoordinator.finish(PushToTalkAsrSessionOwner.LlmVoicePrompt)
            }
        }
    }

    fun onPromptChanged(value: String) {
        if (isVoicePromptBusyNow()) {
            return
        }

        promptText.value = value
        if (errorMessage.value != null) {
            errorMessage.value = null
        }
    }

    fun onClearPromptClick() {
        if (isVoicePromptBusyNow()) {
            return
        }

        promptText.value = ""
        if (errorMessage.value != null) {
            errorMessage.value = null
        }
    }

    fun onLlmTabSelected() {
        // Model loading is owned by the app startup gate.
    }

    fun onBackendSelected(backend: LlmBackend) {
        if (errorMessage.value != null) {
            errorMessage.value = null
        }
        backendReloadRequested.value =
            llmController.modelState.value == LlmModelState.Loaded &&
                llmController.generationState.value != LlmGenerationState.Generating &&
                llmController.selectedBackend.value != backend
        llmController.selectBackend(backend)
    }

    fun onGenerateClick() {
        if (isVoicePromptBusyNow()) {
            return
        }

        ttsController.stop()
        ttsErrorMessage.value = null
        errorMessage.value = null
        llmController.generate(promptText.value)
    }

    fun onVoicePromptStartClick() {
        if (voicePromptPermissionState.value != PermissionState.Granted) {
            voicePromptErrorMessage.value = AudioCaptureError.PermissionMissing.toUserMessage()
            return
        }

        if (speechRecognitionController.modelState.value != AsrModelState.Loaded) {
            voicePromptErrorMessage.value =
                SpeechRecognitionError.ModelNotLoaded.toUserMessage()
            return
        }

        if (isVoicePromptBusyNow() ||
            llmController.generationState.value == LlmGenerationState.Generating
        ) {
            return
        }

        ttsController.stop()
        ttsErrorMessage.value = null
        errorMessage.value = null
        voicePromptErrorMessage.value = null

        when (pushToTalkAsrSessionCoordinator.start(PushToTalkAsrSessionOwner.LlmVoicePrompt)) {
            PushToTalkAsrStartResult.Started -> Unit
            PushToTalkAsrStartResult.Busy -> {
                voicePromptErrorMessage.value = "Another voice session is already active."
            }
            PushToTalkAsrStartResult.Recovering -> {
                voicePromptErrorMessage.value =
                    "Previous voice session is still shutting down. Try again."
            }
            PushToTalkAsrStartResult.ModelNotLoaded -> {
                voicePromptErrorMessage.value =
                    SpeechRecognitionError.ModelNotLoaded.toUserMessage()
            }
            PushToTalkAsrStartResult.StartFailed -> {
                voicePromptErrorMessage.value =
                    SpeechRecognitionError.RecognitionFailed.toUserMessage()
            }
        }
    }

    fun onVoicePromptStopClick() {
        if (!uiState.value.canStopVoicePrompt) {
            return
        }

        pushToTalkAsrSessionCoordinator.stop(PushToTalkAsrSessionOwner.LlmVoicePrompt)
    }

    fun onSpeakResponseClick() {
        if (!uiState.value.canSpeakResponse) {
            return
        }

        val responseText = uiState.value.responseText.trim()
        if (responseText.isBlank()) {
            return
        }

        ttsErrorMessage.value = null
        viewModelScope.launch {
            ttsController.speak(
                text = responseText,
                voice = ttsSettingsRepository.settings.value.selectedVoice
            )
        }
    }

    fun onStopSpeakingClick() {
        ttsController.stop()
    }

    fun onStopPlayback() {
        ttsController.stop()
    }

    fun onVoicePromptPermissionResult(granted: Boolean, permanentlyDenied: Boolean) {
        voicePromptPermissionState.value = when {
            granted -> PermissionState.Granted
            permanentlyDenied -> PermissionState.PermanentlyDenied
            else -> PermissionState.Denied
        }

        voicePromptErrorMessage.value = when (voicePromptPermissionState.value) {
            PermissionState.Granted -> null
            PermissionState.Denied -> "Microphone permission denied."
            PermissionState.PermanentlyDenied ->
                "Open app settings to grant microphone access."
            PermissionState.Unknown -> voicePromptErrorMessage.value
        }
    }

    fun onSystemPromptChanged(value: String) {
        settingsDraft.value = settingsDraft.value.copy(systemPrompt = value)
        if (errorMessage.value != null) {
            errorMessage.value = null
        }
    }

    fun onClearSystemPromptClick() {
        settingsDraft.value = settingsDraft.value.copy(systemPrompt = "")
        if (errorMessage.value != null) {
            errorMessage.value = null
        }
    }

    fun onTemperatureChanged(value: String) {
        settingsDraft.value = settingsDraft.value.copy(temperature = value)
        if (errorMessage.value != null) {
            errorMessage.value = null
        }
    }

    fun onTopKChanged(value: String) {
        settingsDraft.value = settingsDraft.value.copy(topK = value)
        if (errorMessage.value != null) {
            errorMessage.value = null
        }
    }

    fun onTopPChanged(value: String) {
        settingsDraft.value = settingsDraft.value.copy(topP = value)
        if (errorMessage.value != null) {
            errorMessage.value = null
        }
    }

    fun onSeedChanged(value: String) {
        settingsDraft.value = settingsDraft.value.copy(seed = value)
        if (errorMessage.value != null) {
            errorMessage.value = null
        }
    }

    fun onMaxTokensChanged(value: String) {
        settingsDraft.value = settingsDraft.value.copy(maxTokens = value)
        if (errorMessage.value != null) {
            errorMessage.value = null
        }
    }

    fun onApplySettingsClick() {
        val parsedSettings = settingsDraft.value.toInferenceSettingsOrNull()
        if (parsedSettings == null) {
            errorMessage.value = settingsDraft.value.toValidationError()
            return
        }

        errorMessage.value = null
        llmController.applySettings(parsedSettings)
    }

    fun onResetSettingsClick() {
        val defaultSettings = LlmInferenceSettings()
        settingsDraft.value = LlmSettingsDraft.from(defaultSettings)
        errorMessage.value = null
        llmController.resetSettings()
    }

    override fun onCleared() {
        pushToTalkAsrSessionCoordinator.cancel(PushToTalkAsrSessionOwner.LlmVoicePrompt)
        ttsController.release()
        llmController.release()
        super.onCleared()
    }

    private fun handleGenerationCompleted(
        completion: LlmGenerationCompletion
    ) {
        val settings = ttsSettingsRepository.settings.value
        if (!settings.isAutoSpeakEnabled || completion.responseText.isBlank()) {
            return
        }

        viewModelScope.launch {
            ttsController.speak(
                text = completion.responseText,
                voice = settings.selectedVoice
            )
        }
    }

    private fun nameFromPath(path: String): String {
        return File(path).name.ifBlank { path }
    }

    private data class LlmRuntimeSnapshot(
        val selectedBackend: LlmBackend,
        val activeBackend: LlmBackend?,
        val lastLoadDurationMs: Long?,
        val lastGenerationDurationMs: Long?,
        val historyEntries: List<LlmHistoryEntry>,
        val promptText: String
    )

    private data class LlmSettingsSnapshot(
        val appliedSettings: LlmInferenceSettings,
        val draft: LlmSettingsDraft
    )

    private data class LlmRuntimeMetricsSnapshot(
        val selectedBackend: LlmBackend,
        val activeBackend: LlmBackend?,
        val lastLoadDurationMs: Long?,
        val lastGenerationDurationMs: Long?
    )

    private data class LlmPromptSnapshot(
        val historyEntries: List<LlmHistoryEntry>,
        val promptText: String
    )

    private data class LlmUiInputSnapshot(
        val runtime: LlmRuntimeSnapshot,
        val settings: LlmSettingsSnapshot,
        val voicePrompt: VoicePromptSnapshot,
        val errorMessage: String?
    )

    private data class LlmGenerationUiSnapshot(
        val modelState: LlmModelState,
        val generationState: LlmGenerationState,
        val responseText: String,
        val backendReloadRequested: Boolean
    )

    private data class TtsUiSnapshot(
        val playbackState: TtsPlaybackState,
        val settings: com.example.aitoy.feature.tts.domain.TtsSettings,
        val errorMessage: String?
    )

    private data class VoicePromptRuntimeSnapshot(
        val permissionState: PermissionState,
        val modelState: AsrModelState,
        val captureState: CaptureState,
        val recognitionState: RecognitionState
    )

    private data class VoicePromptSessionSnapshot(
        val isSessionActive: Boolean,
        val errorMessage: String?
    )

    private data class VoicePromptSnapshot(
        val permissionState: PermissionState,
        val modelState: AsrModelState,
        val captureState: CaptureState,
        val recognitionState: RecognitionState,
        val isSessionActive: Boolean,
        val errorMessage: String?
    )

    private fun isVoicePromptBusyNow(): Boolean {
        return pushToTalkAsrSessionCoordinator.isBusy(PushToTalkAsrSessionOwner.LlmVoicePrompt)
    }

    private fun AudioCaptureError.toUserMessage(): String {
        return when (this) {
            AudioCaptureError.InitFailed -> "Failed to initialize microphone capture."
            AudioCaptureError.StartFailed -> "Failed to start microphone capture."
            AudioCaptureError.ReadFailed -> "Failed while reading microphone data."
            AudioCaptureError.PermissionMissing -> "Microphone permission is required."
        }
    }

    private fun SpeechRecognitionError.toUserMessage(): String {
        return when (this) {
            SpeechRecognitionError.ModelMissing ->
                "ASR model is missing. Expected path: ${speechRecognitionController.expectedModelDirectoryPath()}"

            SpeechRecognitionError.ModelNotLoaded ->
                "Model is not loaded. Try loading it again."

            SpeechRecognitionError.RecognizerInitFailed ->
                "ASR model failed to load. The model may be incompatible or corrupted."

            SpeechRecognitionError.RecognitionFailed ->
                "Speech recognition failed."

            SpeechRecognitionError.NoSpeechDetected ->
                "No speech detected."
        }
    }

    private fun LlmError.toUserMessage(expectedModelPath: String): String {
        return when (this) {
            LlmError.ModelMissing -> "LLM model is missing: $expectedModelPath"
            is LlmError.LoadFailed -> {
                val details = details?.takeIf { it.isNotBlank() }
                if (details != null) {
                    "LLM model failed to load: $details"
                } else {
                    "LLM model failed to load."
                }
            }
            is LlmError.SettingsApplyFailed -> {
                val details = details?.takeIf { it.isNotBlank() }
                if (details != null) {
                    "Failed to apply LLM settings: $details"
                } else {
                    "Failed to apply LLM settings."
                }
            }
            is LlmError.GenerationFailed -> {
                val details = details?.takeIf { it.isNotBlank() }
                if (details != null) {
                    "Gemma failed to generate a response: $details"
                } else {
                    "Gemma failed to generate a response."
                }
            }
            LlmError.EmptyPrompt -> "Enter a prompt before generation."
        }
    }

    private fun TtsError.toUserMessage(): String {
        return when (this) {
            TtsError.ModelMissing -> "TTS voice pack is missing."
            is TtsError.VoiceMissing -> "${voice.label()} voice files are missing."
            is TtsError.InitializationFailed ->
                "TTS failed to initialize: ${details ?: "unknown error"}"
            is TtsError.SynthesisFailed ->
                "TTS synthesis failed: ${details ?: "unknown error"}"
            is TtsError.PlaybackFailed ->
                "TTS playback failed: ${details ?: "unknown error"}"
            TtsError.EmptyText -> "Nothing to speak."
        }
    }

    private fun com.example.aitoy.feature.tts.domain.TtsVoice.label(): String {
        return when (this) {
            com.example.aitoy.feature.tts.domain.TtsVoice.DmitriMedium -> "Dmitri"
            com.example.aitoy.feature.tts.domain.TtsVoice.IrinaMedium -> "Irina"
        }
    }

    private fun LlmSettingsDraft.toInferenceSettingsOrNull(): LlmInferenceSettings? {
        val temperatureValue = temperature.toDoubleOrNull() ?: return null
        val topKValue = topK.toIntOrNull() ?: return null
        val topPValue = topP.toDoubleOrNull() ?: return null
        val seedValue = seed.toIntOrNull() ?: return null
        val maxTokensValue = maxTokens.toIntOrNull() ?: return null

        if (temperatureValue !in 0.0..2.0) return null
        if (topKValue !in 1..1_000) return null
        if (topPValue !in 0.0..1.0) return null
        if (seedValue < 0) return null
        if (maxTokensValue !in 1..8_192) return null

        return LlmInferenceSettings(
            systemPrompt = systemPrompt.trim(),
            temperature = temperatureValue,
            topK = topKValue,
            topP = topPValue,
            seed = seedValue,
            maxTokens = maxTokensValue
        )
    }

    private fun LlmSettingsDraft.toValidationError(): String? {
        if (temperature.toDoubleOrNull() == null) {
            return "Temperature must be a number between 0.0 and 2.0."
        }
        if (temperature.toDouble() !in 0.0..2.0) {
            return "Temperature must be between 0.0 and 2.0."
        }
        if (topK.toIntOrNull() == null) {
            return "Top-K must be an integer."
        }
        if (topK.toInt() !in 1..1_000) {
            return "Top-K must be between 1 and 1000."
        }
        if (topP.toDoubleOrNull() == null) {
            return "Top-P must be a number between 0.0 and 1.0."
        }
        if (topP.toDouble() !in 0.0..1.0) {
            return "Top-P must be between 0.0 and 1.0."
        }
        if (seed.toIntOrNull() == null) {
            return "Seed must be a non-negative integer."
        }
        if (seed.toInt() < 0) {
            return "Seed must be a non-negative integer."
        }
        if (maxTokens.toIntOrNull() == null) {
            return "Max tokens must be an integer."
        }
        if (maxTokens.toInt() !in 1..8_192) {
            return "Max tokens must be between 1 and 8192."
        }
        return null
    }

    private data class LlmSettingsDraft(
        val systemPrompt: String,
        val temperature: String,
        val topK: String,
        val topP: String,
        val seed: String,
        val maxTokens: String
    ) {
        companion object {
            fun from(settings: LlmInferenceSettings): LlmSettingsDraft {
                return LlmSettingsDraft(
                    systemPrompt = settings.systemPrompt,
                    temperature = settings.temperature.toString(),
                    topK = settings.topK.toString(),
                    topP = settings.topP.toString(),
                    seed = settings.seed.toString(),
                    maxTokens = settings.maxTokens.toString()
                )
            }
        }
    }

    class Factory(
        private val llmController: LlmController,
        private val llmModelLocator: LlmModelLocator,
        private val audioCaptureController: AudioCaptureController,
        private val speechRecognitionController: SpeechRecognitionController,
        private val pushToTalkAsrSessionCoordinator: PushToTalkAsrSessionCoordinator,
        private val ttsController: TtsController,
        private val ttsSettingsRepository: TtsSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LlmViewModel::class.java)) {
                return LlmViewModel(
                    llmController = llmController,
                    llmModelLocator = llmModelLocator,
                    audioCaptureController = audioCaptureController,
                    speechRecognitionController = speechRecognitionController,
                    pushToTalkAsrSessionCoordinator = pushToTalkAsrSessionCoordinator,
                    ttsController = ttsController,
                    ttsSettingsRepository = ttsSettingsRepository
                ) as T
            }
            error("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
