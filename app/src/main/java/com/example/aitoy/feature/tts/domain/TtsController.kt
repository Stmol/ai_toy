package com.example.aitoy.feature.tts.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface TtsController {
    suspend fun prepare(voice: TtsVoice)
    suspend fun speak(text: String, voice: TtsVoice)
    fun stop()
    fun release()

    val playbackState: StateFlow<TtsPlaybackState>
    val errors: Flow<TtsError>
}
