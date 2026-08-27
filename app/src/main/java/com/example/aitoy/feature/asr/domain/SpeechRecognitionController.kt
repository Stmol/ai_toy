package com.example.aitoy.feature.asr.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SpeechRecognitionController {
    fun loadModel()
    fun unloadModel()
    fun startSession()
    fun stopSession()
    fun cancelSession()
    fun loadVadModel()
    fun updateVadSettings(settings: VadAutoStopSettings): Boolean
    fun release()
    fun expectedModelDirectoryPath(): String

    val modelState: StateFlow<AsrModelState>
    val vadModelState: StateFlow<AsrModelState>
    val vadSettings: StateFlow<VadAutoStopSettings>
    val recognitionState: StateFlow<RecognitionState>
    val partialText: StateFlow<String>
    val finalText: StateFlow<String?>
    val errors: Flow<SpeechRecognitionError>
    val sessionEvents: Flow<SpeechRecognitionSessionEvent>
}

sealed interface SpeechRecognitionSessionEvent {
    data object AutoStopRequested : SpeechRecognitionSessionEvent
}
