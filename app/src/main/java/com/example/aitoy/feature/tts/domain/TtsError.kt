package com.example.aitoy.feature.tts.domain

sealed interface TtsError {
    data object ModelMissing : TtsError
    data class VoiceMissing(
        val voice: TtsVoice
    ) : TtsError
    data class InitializationFailed(
        val details: String? = null
    ) : TtsError
    data class SynthesisFailed(
        val details: String? = null
    ) : TtsError
    data class PlaybackFailed(
        val details: String? = null
    ) : TtsError
    data object EmptyText : TtsError
}
