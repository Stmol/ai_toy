package com.example.aitoy.feature.microphone.presentation

import com.example.aitoy.feature.asr.domain.AsrModelState
import com.example.aitoy.feature.asr.domain.RecognitionState
import com.example.aitoy.feature.microphone.domain.CaptureState
import com.example.aitoy.feature.microphone.domain.PermissionState
import com.example.aitoy.feature.microphone.domain.VoiceTranscriptHistoryEntry

data class MicrophoneUiState(
    val permissionState: PermissionState = PermissionState.Unknown,
    val modelState: AsrModelState = AsrModelState.Unloaded,
    val modelName: String? = null,
    val captureState: CaptureState = CaptureState.Idle,
    val recognitionState: RecognitionState = RecognitionState.Idle,
    val audioLevel: Float = 0f,
    val partialText: String = "",
    val finalText: String? = null,
    val isTranscriptSheetVisible: Boolean = false,
    val historyEntries: List<VoiceTranscriptHistoryEntry> = emptyList(),
    val selectedHistoryEntry: VoiceTranscriptHistoryEntry? = null,
    val isStreamingActive: Boolean = false,
    val showStartButton: Boolean = false,
    val errorMessage: String? = null,
    val recognitionErrorMessage: String? = null,
    val isBusy: Boolean = false,
    val canStart: Boolean = true,
    val canStop: Boolean = false,
    val canCloseTranscriptSheet: Boolean = false
)
