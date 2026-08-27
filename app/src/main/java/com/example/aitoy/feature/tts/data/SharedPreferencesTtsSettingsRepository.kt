package com.example.aitoy.feature.tts.data

import android.content.Context
import androidx.core.content.edit
import com.example.aitoy.feature.tts.domain.TtsSettings
import com.example.aitoy.feature.tts.domain.TtsSettingsRepository
import com.example.aitoy.feature.tts.domain.TtsVoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedPreferencesTtsSettingsRepository(
    context: Context
) : TtsSettingsRepository {
    private val sharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val _settings = MutableStateFlow(loadSettings())

    override val settings: StateFlow<TtsSettings> = _settings.asStateFlow()

    override fun setAutoSpeakEnabled(enabled: Boolean) {
        val updated = _settings.value.copy(isAutoSpeakEnabled = enabled)
        _settings.value = updated
        sharedPreferences.edit {
            putBoolean(KEY_AUTO_SPEAK_ENABLED, enabled)
        }
    }

    override fun selectVoice(voice: TtsVoice) {
        val updated = _settings.value.copy(selectedVoice = voice)
        _settings.value = updated
        sharedPreferences.edit {
            putString(KEY_SELECTED_VOICE, voice.name)
        }
    }

    private fun loadSettings(): TtsSettings {
        val rawVoice = sharedPreferences.getString(KEY_SELECTED_VOICE, null)
        val voice = rawVoice?.let {
            runCatching { TtsVoice.valueOf(it) }.getOrNull()
        } ?: TtsSettings.DEFAULT_VOICE

        return TtsSettings(
            isAutoSpeakEnabled = sharedPreferences.getBoolean(
                KEY_AUTO_SPEAK_ENABLED,
                TtsSettings.DEFAULT_AUTO_SPEAK_ENABLED
            ),
            selectedVoice = voice
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "tts_settings"
        const val KEY_AUTO_SPEAK_ENABLED = "auto_speak_enabled"
        const val KEY_SELECTED_VOICE = "selected_voice"
    }
}
