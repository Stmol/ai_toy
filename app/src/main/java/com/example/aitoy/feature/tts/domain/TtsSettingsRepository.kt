package com.example.aitoy.feature.tts.domain

import kotlinx.coroutines.flow.StateFlow

interface TtsSettingsRepository {
    val settings: StateFlow<TtsSettings>

    fun setAutoSpeakEnabled(enabled: Boolean)
    fun selectVoice(voice: TtsVoice)
}
