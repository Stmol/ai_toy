package com.example.aitoy.feature.tts.domain

sealed interface TtsPlaybackState {
    data object Idle : TtsPlaybackState
    data class Preparing(
        val voice: TtsVoice
    ) : TtsPlaybackState
    data class Speaking(
        val voice: TtsVoice,
        val text: String
    ) : TtsPlaybackState
}
