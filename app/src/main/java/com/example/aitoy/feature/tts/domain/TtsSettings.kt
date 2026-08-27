package com.example.aitoy.feature.tts.domain

data class TtsSettings(
    val isAutoSpeakEnabled: Boolean = DEFAULT_AUTO_SPEAK_ENABLED,
    val selectedVoice: TtsVoice = DEFAULT_VOICE
) {
    companion object {
        const val DEFAULT_AUTO_SPEAK_ENABLED = true
        val DEFAULT_VOICE = TtsVoice.DmitriMedium
    }
}
