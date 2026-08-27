package com.example.aitoy.feature.asr.domain

data class VadAutoStopSettings(
    val threshold: Float = DEFAULT_THRESHOLD,
    val minSpeechDurationSeconds: Float = DEFAULT_MIN_SPEECH_DURATION_SECONDS,
    val minSilenceDurationSeconds: Float = DEFAULT_MIN_SILENCE_DURATION_SECONDS,
    val maxSpeechDurationSeconds: Float = DEFAULT_MAX_SPEECH_DURATION_SECONDS
) {
    fun coerceInSupportedRange(): VadAutoStopSettings {
        return copy(
            threshold = threshold.coerceIn(THRESHOLD_MIN, THRESHOLD_MAX),
            minSpeechDurationSeconds = minSpeechDurationSeconds.coerceIn(
                MIN_SPEECH_DURATION_MIN,
                MIN_SPEECH_DURATION_MAX
            ),
            minSilenceDurationSeconds = minSilenceDurationSeconds.coerceIn(
                MIN_SILENCE_DURATION_MIN,
                MIN_SILENCE_DURATION_MAX
            ),
            maxSpeechDurationSeconds = maxSpeechDurationSeconds.coerceIn(
                MAX_SPEECH_DURATION_MIN,
                MAX_SPEECH_DURATION_MAX
            )
        )
    }

    companion object {
        const val DEFAULT_THRESHOLD = 0.5f
        const val DEFAULT_MIN_SPEECH_DURATION_SECONDS = 0.25f
        const val DEFAULT_MIN_SILENCE_DURATION_SECONDS = 0.8f
        const val DEFAULT_MAX_SPEECH_DURATION_SECONDS = 30f

        const val THRESHOLD_MIN = 0.1f
        const val THRESHOLD_MAX = 0.95f
        const val MIN_SPEECH_DURATION_MIN = 0.05f
        const val MIN_SPEECH_DURATION_MAX = 2.0f
        const val MIN_SILENCE_DURATION_MIN = 0.2f
        const val MIN_SILENCE_DURATION_MAX = 3.0f
        const val MAX_SPEECH_DURATION_MIN = 5f
        const val MAX_SPEECH_DURATION_MAX = 60f
    }
}

interface VadSettingsRepository {
    fun loadSettings(): VadAutoStopSettings
    fun saveSettings(settings: VadAutoStopSettings)
}
