package com.example.aitoy.feature.settings.presentation

import com.example.aitoy.feature.asr.domain.RecognitionState
import com.example.aitoy.feature.asr.domain.VadAutoStopSettings
import com.example.aitoy.feature.tts.domain.TtsPlaybackState
import com.example.aitoy.feature.tts.domain.TtsSettings
import com.example.aitoy.feature.tts.domain.TtsVoice

data class SettingsUiState(
    val appliedVadSettings: VadAutoStopSettings = VadAutoStopSettings(),
    val draftVadSettings: VadAutoStopSettings = VadAutoStopSettings(),
    val recognitionState: RecognitionState = RecognitionState.Idle,
    val message: String? = null,
    val canSaveVadSettings: Boolean = true,
    val hasUnsavedVadSettings: Boolean = false,
    val ttsSettings: TtsSettings = TtsSettings(),
    val ttsPlaybackState: TtsPlaybackState = TtsPlaybackState.Idle,
    val ttsStatusMessage: String? = null,
    val canChangeTtsSettings: Boolean = true
)
