package com.example.aitoy.feature.microphone.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AudioCaptureController {
    fun start(): Boolean
    fun stop()

    val captureState: StateFlow<CaptureState>
    val audioLevel: StateFlow<Float>
    val audioFrames: Flow<PcmAudioFrame>
    val errors: Flow<AudioCaptureError>
}
