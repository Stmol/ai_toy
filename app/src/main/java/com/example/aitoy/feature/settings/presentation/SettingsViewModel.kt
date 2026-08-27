package com.example.aitoy.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aitoy.feature.asr.data.ModelFileStatus
import com.example.aitoy.feature.asr.domain.RecognitionState
import com.example.aitoy.feature.asr.domain.SpeechRecognitionController
import com.example.aitoy.feature.asr.domain.VadAutoStopSettings
import com.example.aitoy.feature.tts.data.TtsModelLocator
import com.example.aitoy.feature.tts.domain.TtsController
import com.example.aitoy.feature.tts.domain.TtsError
import com.example.aitoy.feature.tts.domain.TtsPlaybackState
import com.example.aitoy.feature.tts.domain.TtsSettingsRepository
import com.example.aitoy.feature.tts.domain.TtsVoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val speechRecognitionController: SpeechRecognitionController,
    private val ttsSettingsRepository: TtsSettingsRepository,
    private val ttsController: TtsController,
    private val ttsModelLocator: TtsModelLocator
) : ViewModel() {
    private val draftVadSettings = MutableStateFlow(speechRecognitionController.vadSettings.value)
    private val message = MutableStateFlow<String?>(null)
    private val ttsMessage = MutableStateFlow<String?>(null)
    private val vadState = combine(
        speechRecognitionController.vadSettings,
        draftVadSettings,
        speechRecognitionController.recognitionState,
        message
    ) { appliedSettings, draftSettings, recognitionState, message ->
        VadSettingsState(
            appliedSettings = appliedSettings,
            draftSettings = draftSettings,
            recognitionState = recognitionState,
            message = message
        )
    }
    private val ttsState = combine(
        ttsSettingsRepository.settings,
        ttsController.playbackState,
        ttsMessage
    ) { ttsSettings, ttsPlaybackState, ttsMessage ->
        TtsSettingsState(
            settings = ttsSettings,
            playbackState = ttsPlaybackState,
            message = ttsMessage
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        vadState,
        ttsState
    ) { vadState, ttsState ->
        val sanitizedDraft = vadState.draftSettings.coerceInSupportedRange()
        val inspection = runCatching { ttsModelLocator.inspectModelDirectory() }.getOrNull()
        val statusMessage = ttsState.message ?: when {
            inspection == null || inspection.fileStatus == ModelFileStatus.Missing ->
                "TTS voice pack is missing. Download it on the Models tab."
            ttsState.playbackState is TtsPlaybackState.Preparing ->
                "Preparing ${ttsState.playbackState.voice.label()} voice."
            ttsState.playbackState is TtsPlaybackState.Speaking ->
                "Speaking with ${ttsState.playbackState.voice.label()} voice."
            inspection.fileStatus == ModelFileStatus.Partial ->
                "TTS voice pack is incomplete. Download the full Piper pack again."
            else ->
                "Ready. Selected voice: ${ttsState.settings.selectedVoice.label()}."
        }
        SettingsUiState(
            appliedVadSettings = vadState.appliedSettings,
            draftVadSettings = sanitizedDraft,
            recognitionState = vadState.recognitionState,
            message = vadState.message,
            canSaveVadSettings = vadState.recognitionState != RecognitionState.Streaming &&
                vadState.recognitionState != RecognitionState.Finalizing,
            hasUnsavedVadSettings = sanitizedDraft != vadState.appliedSettings,
            ttsSettings = ttsState.settings,
            ttsPlaybackState = ttsState.playbackState,
            ttsStatusMessage = statusMessage,
            canChangeTtsSettings = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = SettingsUiState()
    )

    init {
        viewModelScope.launch {
            ttsController.errors.collect { error ->
                ttsMessage.value = error.toUserMessage()
            }
        }
    }

    fun onThresholdChanged(value: Float) {
        updateDraft { copy(threshold = value) }
    }

    fun onMinSpeechDurationChanged(value: Float) {
        updateDraft { copy(minSpeechDurationSeconds = value) }
    }

    fun onMinSilenceDurationChanged(value: Float) {
        updateDraft { copy(minSilenceDurationSeconds = value) }
    }

    fun onMaxSpeechDurationChanged(value: Float) {
        updateDraft { copy(maxSpeechDurationSeconds = value) }
    }

    fun onResetVadSettingsClick() {
        draftVadSettings.value = VadAutoStopSettings()
        message.value = null
    }

    fun onSaveVadSettingsClick() {
        val saved = speechRecognitionController.updateVadSettings(
            draftVadSettings.value.coerceInSupportedRange()
        )
        message.value = if (saved) {
            "VAD settings saved. Runtime was reloaded when active."
        } else {
            "Stop recording or dictation before saving VAD settings."
        }
    }

    private fun updateDraft(update: VadAutoStopSettings.() -> VadAutoStopSettings) {
        draftVadSettings.value = draftVadSettings.value.update().coerceInSupportedRange()
        message.value = null
    }

    fun onAutoSpeakEnabledChanged(enabled: Boolean) {
        ttsSettingsRepository.setAutoSpeakEnabled(enabled)
        ttsMessage.value = null
    }

    fun onSelectedVoiceChanged(voice: TtsVoice) {
        if (ttsController.playbackState.value is TtsPlaybackState.Speaking) {
            ttsController.stop()
        }
        ttsSettingsRepository.selectVoice(voice)
        ttsMessage.value = null
    }

    private fun TtsVoice.label(): String {
        return when (this) {
            TtsVoice.DmitriMedium -> "Dmitri"
            TtsVoice.IrinaMedium -> "Irina"
        }
    }

    private fun TtsError.toUserMessage(): String {
        return when (this) {
            TtsError.ModelMissing -> "TTS voice pack is missing."
            is TtsError.VoiceMissing -> "${voice.label()} voice files are missing."
            is TtsError.InitializationFailed -> "TTS failed to initialize: ${details ?: "unknown error"}"
            is TtsError.SynthesisFailed -> "TTS synthesis failed: ${details ?: "unknown error"}"
            is TtsError.PlaybackFailed -> "TTS playback failed: ${details ?: "unknown error"}"
            TtsError.EmptyText -> "Nothing to speak."
        }
    }

    class Factory(
        private val speechRecognitionController: SpeechRecognitionController,
        private val ttsSettingsRepository: TtsSettingsRepository,
        private val ttsController: TtsController,
        private val ttsModelLocator: TtsModelLocator
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return SettingsViewModel(
                speechRecognitionController = speechRecognitionController,
                ttsSettingsRepository = ttsSettingsRepository,
                ttsController = ttsController,
                ttsModelLocator = ttsModelLocator
            ) as T
        }
    }

    private data class VadSettingsState(
        val appliedSettings: VadAutoStopSettings,
        val draftSettings: VadAutoStopSettings,
        val recognitionState: RecognitionState,
        val message: String?
    )

    private data class TtsSettingsState(
        val settings: com.example.aitoy.feature.tts.domain.TtsSettings,
        val playbackState: TtsPlaybackState,
        val message: String?
    )
}
