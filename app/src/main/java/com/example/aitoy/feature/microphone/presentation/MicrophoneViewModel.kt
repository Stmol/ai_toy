package com.example.aitoy.feature.microphone.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aitoy.feature.asr.data.AsrModelLocator
import com.example.aitoy.feature.asr.domain.AsrModelState
import com.example.aitoy.feature.asr.domain.PushToTalkAsrSessionCoordinator
import com.example.aitoy.feature.asr.domain.PushToTalkAsrSessionOwner
import com.example.aitoy.feature.asr.domain.PushToTalkAsrStartResult
import com.example.aitoy.feature.asr.domain.RecognitionState
import com.example.aitoy.feature.asr.domain.SpeechRecognitionController
import com.example.aitoy.feature.asr.domain.SpeechRecognitionError
import com.example.aitoy.feature.asr.domain.SpeechRecognitionSessionEvent
import com.example.aitoy.feature.microphone.domain.AudioCaptureController
import com.example.aitoy.feature.microphone.domain.AudioCaptureError
import com.example.aitoy.feature.microphone.domain.CaptureState
import com.example.aitoy.feature.microphone.domain.PermissionState
import com.example.aitoy.feature.microphone.domain.VoiceTranscriptHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MicrophoneViewModel(
    private val audioCaptureController: AudioCaptureController,
    private val speechRecognitionController: SpeechRecognitionController,
    private val pushToTalkAsrSessionCoordinator: PushToTalkAsrSessionCoordinator,
    private val asrModelLocator: AsrModelLocator,
    private val voiceTranscriptHistoryRepository: VoiceTranscriptHistoryRepository
) : ViewModel() {
    private data class RuntimeState(
        val permissionState: PermissionState,
        val modelState: AsrModelState,
        val modelName: String?,
        val captureState: CaptureState,
        val recognitionState: RecognitionState,
        val audioLevel: Float,
        val partialText: String,
        val finalText: String?,
        val isTranscriptSheetVisible: Boolean,
        val isRecordingSessionActive: Boolean,
        val historyEntries: List<com.example.aitoy.feature.microphone.domain.VoiceTranscriptHistoryEntry>,
        val selectedHistoryEntryId: String?
    )

    private val permissionState = MutableStateFlow(PermissionState.Unknown)
    private val microphoneErrorMessage = MutableStateFlow<String?>(null)
    private val recognitionErrorMessage = MutableStateFlow<String?>(null)
    private val isTranscriptSheetVisible = MutableStateFlow(false)
    private val selectedHistoryEntryId = MutableStateFlow<String?>(null)

    private val runtimeState: StateFlow<RuntimeState> = combine(
        combine(
            permissionState,
            speechRecognitionController.modelState,
            audioCaptureController.captureState,
            speechRecognitionController.recognitionState,
            audioCaptureController.audioLevel
        ) { permissionState, modelState, captureState, recognitionState, audioLevel ->
            BaseRuntimeState(
                permissionState = permissionState,
                modelState = modelState,
                modelName = resolveModelName(modelState),
                captureState = captureState,
                recognitionState = recognitionState,
                audioLevel = audioLevel
            )
        },
        combine(
            speechRecognitionController.partialText,
            speechRecognitionController.finalText
        ) { partialText, finalText ->
            TranscriptState(
                partialText = partialText,
                finalText = finalText
            )
        },
        combine(
            isTranscriptSheetVisible,
            pushToTalkAsrSessionCoordinator.activeOwner
        ) { isTranscriptSheetVisible, activeOwner ->
            RecordingSessionSnapshot(
                isTranscriptSheetVisible = isTranscriptSheetVisible,
                isRecordingSessionActive = activeOwner == PushToTalkAsrSessionOwner.Recording
            )
        },
        voiceTranscriptHistoryRepository.historyEntries,
        selectedHistoryEntryId
    ) { baseState,
        transcriptState,
        recordingSession,
        historyEntries,
        selectedHistoryEntryId ->
        RuntimeState(
            permissionState = baseState.permissionState,
            modelState = baseState.modelState,
            modelName = baseState.modelName,
            captureState = baseState.captureState,
            recognitionState = baseState.recognitionState,
            audioLevel = baseState.audioLevel,
            partialText = transcriptState.partialText,
            finalText = transcriptState.finalText,
            isTranscriptSheetVisible = recordingSession.isTranscriptSheetVisible,
            isRecordingSessionActive = recordingSession.isRecordingSessionActive,
            historyEntries = historyEntries,
            selectedHistoryEntryId = selectedHistoryEntryId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = RuntimeState(
            permissionState = PermissionState.Unknown,
            modelState = AsrModelState.Unloaded,
            modelName = null,
            captureState = CaptureState.Idle,
            recognitionState = RecognitionState.Idle,
            audioLevel = 0f,
            partialText = "",
            finalText = null,
            isTranscriptSheetVisible = false,
            isRecordingSessionActive = false,
            historyEntries = emptyList(),
            selectedHistoryEntryId = null
        )
    )

    private data class BaseRuntimeState(
        val permissionState: PermissionState,
        val modelState: AsrModelState,
        val modelName: String?,
        val captureState: CaptureState,
        val recognitionState: RecognitionState,
        val audioLevel: Float
    )

    private data class TranscriptState(
        val partialText: String,
        val finalText: String?
    )

    private data class RecordingSessionSnapshot(
        val isTranscriptSheetVisible: Boolean,
        val isRecordingSessionActive: Boolean
    )

    val uiState: StateFlow<MicrophoneUiState> = combine(
        runtimeState,
        microphoneErrorMessage,
        recognitionErrorMessage
    ) { runtimeState, microphoneErrorMessage, recognitionErrorMessage ->
        val isRecordingSessionActive = runtimeState.isRecordingSessionActive
        val hasRecoverableVoiceSession = !isRecordingSessionActive &&
            (runtimeState.captureState == CaptureState.Listening ||
                runtimeState.recognitionState == RecognitionState.Streaming ||
                runtimeState.recognitionState == RecognitionState.Finalizing)
        val canControlCurrentSession = isRecordingSessionActive || hasRecoverableVoiceSession
        val isStreamingActive = isRecordingSessionActive &&
            (runtimeState.recognitionState == RecognitionState.Streaming ||
                runtimeState.recognitionState == RecognitionState.Finalizing)
        val isBusy = runtimeState.modelState == AsrModelState.Loading ||
            (isRecordingSessionActive &&
                runtimeState.recognitionState == RecognitionState.Finalizing)
        val showStartButton = runtimeState.modelState == AsrModelState.Loaded
        val selectedHistoryEntry = runtimeState.historyEntries.firstOrNull {
            it.id == runtimeState.selectedHistoryEntryId
        }
        val canShowCurrentTranscript = isRecordingSessionActive ||
            runtimeState.isTranscriptSheetVisible

        MicrophoneUiState(
            permissionState = runtimeState.permissionState,
            modelState = runtimeState.modelState,
            modelName = runtimeState.modelName,
            captureState = if (canControlCurrentSession) {
                runtimeState.captureState
            } else {
                CaptureState.Idle
            },
            recognitionState = if (canControlCurrentSession) {
                runtimeState.recognitionState
            } else {
                RecognitionState.Idle
            },
            audioLevel = if (isRecordingSessionActive) runtimeState.audioLevel else 0f,
            partialText = if (canShowCurrentTranscript) runtimeState.partialText else "",
            finalText = if (canShowCurrentTranscript) runtimeState.finalText else null,
            isTranscriptSheetVisible = runtimeState.isTranscriptSheetVisible,
            historyEntries = runtimeState.historyEntries,
            selectedHistoryEntry = selectedHistoryEntry,
            isStreamingActive = isStreamingActive,
            showStartButton = showStartButton,
            errorMessage = microphoneErrorMessage,
            recognitionErrorMessage = recognitionErrorMessage,
            isBusy = isBusy,
            canStart = showStartButton &&
                !isBusy &&
                runtimeState.captureState != CaptureState.Listening &&
                runtimeState.recognitionState != RecognitionState.Streaming &&
                runtimeState.recognitionState != RecognitionState.Finalizing &&
                runtimeState.permissionState != PermissionState.PermanentlyDenied,
            canStop = canControlCurrentSession &&
                (runtimeState.captureState == CaptureState.Listening ||
                    runtimeState.recognitionState == RecognitionState.Streaming ||
                    runtimeState.recognitionState == RecognitionState.Finalizing),
            canCloseTranscriptSheet = !isStreamingActive
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = MicrophoneUiState()
    )

    init {
        var persistedCompletedTranscript: String? = null

        viewModelScope.launch {
            audioCaptureController.errors.collect { error ->
                if (!pushToTalkAsrSessionCoordinator.isOwner(PushToTalkAsrSessionOwner.Recording)) {
                    return@collect
                }

                microphoneErrorMessage.value = error.toUserMessage()
                pushToTalkAsrSessionCoordinator.cancel(PushToTalkAsrSessionOwner.Recording)
                isTranscriptSheetVisible.value = false
            }
        }

        viewModelScope.launch {
            speechRecognitionController.errors.collect { error ->
                if (!pushToTalkAsrSessionCoordinator.isOwner(PushToTalkAsrSessionOwner.Recording)) {
                    when (error) {
                        SpeechRecognitionError.ModelMissing,
                        SpeechRecognitionError.ModelNotLoaded,
                        SpeechRecognitionError.RecognizerInitFailed -> {
                            recognitionErrorMessage.value = error.toUserMessage()
                        }

                        SpeechRecognitionError.RecognitionFailed,
                        SpeechRecognitionError.NoSpeechDetected -> Unit
                    }
                    return@collect
                }

                recognitionErrorMessage.value = error.toUserMessage()
                pushToTalkAsrSessionCoordinator.cancel(PushToTalkAsrSessionOwner.Recording)
            }
        }

        viewModelScope.launch {
            speechRecognitionController.sessionEvents.collect { event ->
                if (!pushToTalkAsrSessionCoordinator.isOwner(PushToTalkAsrSessionOwner.Recording)) {
                    return@collect
                }

                when (event) {
                    SpeechRecognitionSessionEvent.AutoStopRequested ->
                        pushToTalkAsrSessionCoordinator.autoStopCurrentSession()
                }
            }
        }

        viewModelScope.launch {
            speechRecognitionController.modelState.collect { modelState ->
                if (modelState == AsrModelState.Loaded) {
                    recognitionErrorMessage.value = null
                }
            }
        }

        viewModelScope.launch {
            speechRecognitionController.recognitionState.collect { recognitionState ->
                if (recognitionState != RecognitionState.Completed) {
                    persistedCompletedTranscript = null
                    return@collect
                }

                val transcriptToPersist = speechRecognitionController.finalText.value?.trim().orEmpty()
                if (!pushToTalkAsrSessionCoordinator.isOwner(PushToTalkAsrSessionOwner.Recording)) {
                    return@collect
                }
                if (transcriptToPersist.isBlank() ||
                    transcriptToPersist == persistedCompletedTranscript
                ) {
                    pushToTalkAsrSessionCoordinator.finish(PushToTalkAsrSessionOwner.Recording)
                    return@collect
                }

                persistedCompletedTranscript = transcriptToPersist
                voiceTranscriptHistoryRepository.addTranscript(transcriptToPersist)
                pushToTalkAsrSessionCoordinator.finish(PushToTalkAsrSessionOwner.Recording)
            }
        }
    }

    fun onStartClick() {
        if (permissionState.value != PermissionState.Granted) {
            microphoneErrorMessage.value = AudioCaptureError.PermissionMissing.toUserMessage()
            return
        }

        if (speechRecognitionController.modelState.value != AsrModelState.Loaded) {
            recognitionErrorMessage.value = "Model is not loaded. Try loading it again."
            return
        }

        microphoneErrorMessage.value = null
        recognitionErrorMessage.value = null
        isTranscriptSheetVisible.value = true
        selectedHistoryEntryId.value = null

        when (pushToTalkAsrSessionCoordinator.start(PushToTalkAsrSessionOwner.Recording)) {
            PushToTalkAsrStartResult.Started -> Unit
            PushToTalkAsrStartResult.Busy -> {
                microphoneErrorMessage.value = "Another voice session is already active."
                isTranscriptSheetVisible.value = false
            }
            PushToTalkAsrStartResult.Recovering -> {
                microphoneErrorMessage.value =
                    "Previous voice session is still shutting down. Try again."
                isTranscriptSheetVisible.value = false
            }
            PushToTalkAsrStartResult.ModelNotLoaded -> {
                recognitionErrorMessage.value = SpeechRecognitionError.ModelNotLoaded.toUserMessage()
                isTranscriptSheetVisible.value = false
            }
            PushToTalkAsrStartResult.StartFailed -> {
                recognitionErrorMessage.value = SpeechRecognitionError.RecognitionFailed.toUserMessage()
                isTranscriptSheetVisible.value = false
            }
        }
    }

    fun onStopClick() {
        if (!uiState.value.canStop) {
            return
        }

        pushToTalkAsrSessionCoordinator.stop(PushToTalkAsrSessionOwner.Recording)
    }

    fun onCloseTranscriptSheet() {
        if (!pushToTalkAsrSessionCoordinator.isOwner(PushToTalkAsrSessionOwner.Recording)) {
            isTranscriptSheetVisible.value = false
            return
        }

        when (speechRecognitionController.recognitionState.value) {
            RecognitionState.Streaming -> {
                pushToTalkAsrSessionCoordinator.stop(PushToTalkAsrSessionOwner.Recording)
            }
            RecognitionState.Finalizing -> Unit
            else -> {
                pushToTalkAsrSessionCoordinator.finish(PushToTalkAsrSessionOwner.Recording)
                isTranscriptSheetVisible.value = false
            }
        }
    }

    fun onHistoryEntryClick(entryId: String) {
        isTranscriptSheetVisible.value = false
        selectedHistoryEntryId.value = entryId
    }

    fun onCloseHistoryEntrySheet() {
        selectedHistoryEntryId.value = null
    }

    fun onDeleteHistoryEntry(entryId: String) {
        viewModelScope.launch {
            if (selectedHistoryEntryId.value == entryId) {
                selectedHistoryEntryId.value = null
            }
            voiceTranscriptHistoryRepository.deleteTranscript(entryId)
        }
    }

    fun onPermissionResult(granted: Boolean, permanentlyDenied: Boolean) {
        permissionState.value = when {
            granted -> PermissionState.Granted
            permanentlyDenied -> PermissionState.PermanentlyDenied
            else -> PermissionState.Denied
        }

        microphoneErrorMessage.value = when (permissionState.value) {
            PermissionState.Granted -> null
            PermissionState.Denied -> "Microphone permission denied."
            PermissionState.PermanentlyDenied ->
                "Open app settings to grant microphone access."
            PermissionState.Unknown -> microphoneErrorMessage.value
        }
    }

    override fun onCleared() {
        audioCaptureController.stop()
        pushToTalkAsrSessionCoordinator.cancel(PushToTalkAsrSessionOwner.Recording)
        speechRecognitionController.release()
        super.onCleared()
    }

    private fun resolveModelName(modelState: AsrModelState): String? {
        if (modelState != AsrModelState.Loaded) {
            return null
        }

        return runCatching {
            asrModelLocator.inspectModelDirectory().activePath
        }.getOrNull()
            ?.let(::extractModelName)
    }

    private fun extractModelName(path: String): String {
        val trimmedPath = path.trimEnd('/')
        val lastSeparatorIndex = trimmedPath.lastIndexOf('/')
        return if (lastSeparatorIndex >= 0 && lastSeparatorIndex < trimmedPath.lastIndex) {
            trimmedPath.substring(lastSeparatorIndex + 1)
        } else {
            trimmedPath
        }
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

    class Factory(
        private val audioCaptureController: AudioCaptureController,
        private val speechRecognitionController: SpeechRecognitionController,
        private val pushToTalkAsrSessionCoordinator: PushToTalkAsrSessionCoordinator,
        private val asrModelLocator: AsrModelLocator,
        private val voiceTranscriptHistoryRepository: VoiceTranscriptHistoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MicrophoneViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return MicrophoneViewModel(
                audioCaptureController = audioCaptureController,
                speechRecognitionController = speechRecognitionController,
                pushToTalkAsrSessionCoordinator = pushToTalkAsrSessionCoordinator,
                asrModelLocator = asrModelLocator,
                voiceTranscriptHistoryRepository = voiceTranscriptHistoryRepository
            ) as T
        }
    }
}
