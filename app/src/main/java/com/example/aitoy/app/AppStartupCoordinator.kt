package com.example.aitoy.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aitoy.feature.asr.data.AsrModelLocator
import com.example.aitoy.feature.asr.data.ModelFileStatus
import com.example.aitoy.feature.asr.data.VadModelLocator
import com.example.aitoy.feature.asr.domain.AsrModelState
import com.example.aitoy.feature.asr.domain.SpeechRecognitionController
import com.example.aitoy.feature.llm.data.LlmModelLocator
import com.example.aitoy.feature.llm.domain.LlmController
import com.example.aitoy.feature.llm.domain.LlmModelState
import com.example.aitoy.feature.models.presentation.ManagedModelType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AppStartupCoordinator(
    private val speechRecognitionController: SpeechRecognitionController,
    private val asrModelLocator: AsrModelLocator,
    private val vadModelLocator: VadModelLocator,
    private val llmController: LlmController,
    private val llmModelLocator: LlmModelLocator
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _uiState = MutableStateFlow<AppStartupUiState>(
        AppStartupUiState.Loading("Загрузка моделей...")
    )
    private var bootstrapJob: Job? = null

    val uiState: StateFlow<AppStartupUiState> = _uiState.asStateFlow()

    init {
        requestBootstrap()
    }

    fun requestBootstrap() {
        bootstrapJob?.cancel()
        bootstrapJob = scope.launch {
            bootstrap()
        }
    }

    private suspend fun bootstrap() {
        _uiState.value = AppStartupUiState.Loading("Загрузка моделей...")

        val missingModels = inspectMissingModels()
        if (missingModels.isNotEmpty()) {
            _uiState.value = AppStartupUiState.BlockedMissingModels(
                missing = missingModels,
                message = missingModels.toBlockedMessage()
            )
            return
        }

        speechRecognitionController.loadVadModel()
        speechRecognitionController.loadModel()
        llmController.loadModel()

        combine(
            speechRecognitionController.modelState,
            speechRecognitionController.vadModelState,
            llmController.modelState
        ) { asrState, vadState, llmState ->
            RuntimeModelStates(
                asrState = asrState,
                vadState = vadState,
                llmState = llmState
            )
        }.collect { states ->
            when {
                states.asrState == AsrModelState.Loaded &&
                    states.vadState == AsrModelState.Loaded &&
                    states.llmState == LlmModelState.Loaded -> {
                    _uiState.value = AppStartupUiState.Ready
                    bootstrapJob?.cancel()
                }

                states.asrState == AsrModelState.Missing ||
                    states.vadState == AsrModelState.Missing ||
                    states.llmState == LlmModelState.Missing -> {
                    val nowMissing = inspectMissingModels()
                    _uiState.value = AppStartupUiState.BlockedMissingModels(
                        missing = nowMissing,
                        message = nowMissing.toBlockedMessage()
                    )
                    bootstrapJob?.cancel()
                }

                states.asrState == AsrModelState.Error -> {
                    _uiState.value = AppStartupUiState.BlockedLoadError(
                        model = ManagedModelType.Asr,
                        message = "Приложение не может продолжить работу: не удалось загрузить ASR model."
                    )
                    bootstrapJob?.cancel()
                }

                states.vadState == AsrModelState.Error -> {
                    _uiState.value = AppStartupUiState.BlockedLoadError(
                        model = ManagedModelType.Vad,
                        message = "Приложение не может продолжить работу: не удалось загрузить VAD model."
                    )
                    bootstrapJob?.cancel()
                }

                states.llmState == LlmModelState.Error -> {
                    _uiState.value = AppStartupUiState.BlockedLoadError(
                        model = ManagedModelType.Llm,
                        message = "Приложение не может продолжить работу: не удалось загрузить LLM model."
                    )
                    bootstrapJob?.cancel()
                }

                else -> {
                    _uiState.value = AppStartupUiState.Loading("Загрузка моделей...")
                }
            }
        }
    }

    private fun inspectMissingModels(): List<ManagedModelType> {
        return buildList {
            val asrInspection = runCatching { asrModelLocator.inspectModelDirectory() }.getOrNull()
            if (asrInspection?.fileStatus != ModelFileStatus.Complete) {
                add(ManagedModelType.Asr)
            }

            val vadInspection = runCatching { vadModelLocator.inspectModelFile() }.getOrNull()
            if (vadInspection?.fileStatus != ModelFileStatus.Complete) {
                add(ManagedModelType.Vad)
            }

            val llmInspection = runCatching { llmModelLocator.inspectModelFile() }.getOrNull()
            if (llmInspection?.exists != true) {
                add(ManagedModelType.Llm)
            }
        }
    }

    private fun List<ManagedModelType>.toBlockedMessage(): String {
        val names = joinToString(", ") { it.displayName() }
        return "Приложение не может продолжить работу без: $names. Скачайте модель на вкладке Models."
    }

    private fun ManagedModelType.displayName(): String {
        return when (this) {
            ManagedModelType.Asr -> "ASR model"
            ManagedModelType.Llm -> "LLM model"
            ManagedModelType.Tts -> "TTS voice pack"
            ManagedModelType.Vad -> "VAD model"
        }
    }

    private data class RuntimeModelStates(
        val asrState: AsrModelState,
        val vadState: AsrModelState,
        val llmState: LlmModelState
    )
}

sealed interface AppStartupUiState {
    data class Loading(val message: String) : AppStartupUiState
    data object Ready : AppStartupUiState
    data class BlockedMissingModels(
        val missing: List<ManagedModelType>,
        val message: String
    ) : AppStartupUiState
    data class BlockedLoadError(
        val model: ManagedModelType,
        val message: String
    ) : AppStartupUiState
}

class AppStartupViewModel(
    private val coordinator: AppStartupCoordinator
) : ViewModel() {
    val uiState: StateFlow<AppStartupUiState> = coordinator.uiState

    class Factory(
        private val coordinator: AppStartupCoordinator
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AppStartupViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return AppStartupViewModel(coordinator) as T
        }
    }
}
