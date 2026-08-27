package com.example.aitoy.feature.asr.data

import android.content.Context
import androidx.core.content.edit
import com.example.aitoy.feature.asr.domain.VadAutoStopSettings
import com.example.aitoy.feature.asr.domain.VadSettingsRepository

class SharedPreferencesVadSettingsRepository(
    context: Context
) : VadSettingsRepository {
    private val sharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun loadSettings(): VadAutoStopSettings {
        return VadAutoStopSettings(
            threshold = sharedPreferences.getFloat(
                KEY_THRESHOLD,
                VadAutoStopSettings.DEFAULT_THRESHOLD
            ),
            minSpeechDurationSeconds = sharedPreferences.getFloat(
                KEY_MIN_SPEECH_DURATION,
                VadAutoStopSettings.DEFAULT_MIN_SPEECH_DURATION_SECONDS
            ),
            minSilenceDurationSeconds = sharedPreferences.getFloat(
                KEY_MIN_SILENCE_DURATION,
                VadAutoStopSettings.DEFAULT_MIN_SILENCE_DURATION_SECONDS
            ),
            maxSpeechDurationSeconds = sharedPreferences.getFloat(
                KEY_MAX_SPEECH_DURATION,
                VadAutoStopSettings.DEFAULT_MAX_SPEECH_DURATION_SECONDS
            )
        ).coerceInSupportedRange()
    }

    override fun saveSettings(settings: VadAutoStopSettings) {
        val sanitized = settings.coerceInSupportedRange()
        sharedPreferences.edit {
            putFloat(KEY_THRESHOLD, sanitized.threshold)
            putFloat(KEY_MIN_SPEECH_DURATION, sanitized.minSpeechDurationSeconds)
            putFloat(KEY_MIN_SILENCE_DURATION, sanitized.minSilenceDurationSeconds)
            putFloat(KEY_MAX_SPEECH_DURATION, sanitized.maxSpeechDurationSeconds)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "vad_settings"
        const val KEY_THRESHOLD = "threshold"
        const val KEY_MIN_SPEECH_DURATION = "min_speech_duration"
        const val KEY_MIN_SILENCE_DURATION = "min_silence_duration"
        const val KEY_MAX_SPEECH_DURATION = "max_speech_duration"
    }
}
