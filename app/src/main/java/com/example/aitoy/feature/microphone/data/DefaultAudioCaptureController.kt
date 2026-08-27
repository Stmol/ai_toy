package com.example.aitoy.feature.microphone.data

import com.example.aitoy.core.audio.AndroidAudioRecorder
import com.example.aitoy.core.audio.AudioRecorderState
import com.example.aitoy.feature.microphone.domain.AudioCaptureController
import com.example.aitoy.feature.microphone.domain.AudioCaptureError
import com.example.aitoy.feature.microphone.domain.CaptureState
import com.example.aitoy.feature.microphone.domain.PcmAudioFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DefaultAudioCaptureController(
    private val audioRecorder: AndroidAudioRecorder
) : AudioCaptureController {
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _captureState = MutableStateFlow(CaptureState.Idle)

    override val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()
    override val audioLevel: StateFlow<Float> = audioRecorder.audioLevel
    override val audioFrames: Flow<PcmAudioFrame> = audioRecorder.audioFrames
    override val errors: Flow<AudioCaptureError> = audioRecorder.errors

    init {
        controllerScope.launch {
            audioRecorder.recorderState.collect { recorderState ->
                _captureState.value = recorderState.toCaptureState()
            }
        }
    }

    override fun start(): Boolean {
        return audioRecorder.start()
    }

    override fun stop() {
        audioRecorder.stop()
    }

    private fun AudioRecorderState.toCaptureState(): CaptureState {
        return when (this) {
            AudioRecorderState.Idle -> CaptureState.Idle
            AudioRecorderState.Listening -> CaptureState.Listening
            AudioRecorderState.Error -> CaptureState.Error
        }
    }
}
