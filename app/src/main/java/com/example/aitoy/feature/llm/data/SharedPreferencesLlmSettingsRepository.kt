package com.example.aitoy.feature.llm.data

import android.content.Context
import androidx.core.content.edit
import com.example.aitoy.feature.llm.domain.LlmBackend
import com.example.aitoy.feature.llm.domain.LlmInferenceSettings
import com.example.aitoy.feature.llm.domain.LlmSettingsRepository

class SharedPreferencesLlmSettingsRepository(
    context: Context
) : LlmSettingsRepository {
    private val sharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun loadInferenceSettings(): LlmInferenceSettings {
        return LlmInferenceSettings(
            systemPrompt = sharedPreferences.getString(
                KEY_SYSTEM_PROMPT,
                LlmInferenceSettings.DEFAULT_SYSTEM_PROMPT
            ) ?: LlmInferenceSettings.DEFAULT_SYSTEM_PROMPT,
            temperature = sharedPreferences.getFloat(
                KEY_TEMPERATURE,
                LlmInferenceSettings.DEFAULT_TEMPERATURE.toFloat()
            ).toDouble().coerceIn(TEMPERATURE_MIN, TEMPERATURE_MAX),
            topK = sharedPreferences.getInt(
                KEY_TOP_K,
                LlmInferenceSettings.DEFAULT_TOP_K
            ).coerceIn(TOP_K_MIN, TOP_K_MAX),
            topP = sharedPreferences.getFloat(
                KEY_TOP_P,
                LlmInferenceSettings.DEFAULT_TOP_P.toFloat()
            ).toDouble().coerceIn(TOP_P_MIN, TOP_P_MAX),
            seed = sharedPreferences.getInt(
                KEY_SEED,
                LlmInferenceSettings.DEFAULT_SEED
            ).coerceAtLeast(SEED_MIN),
            maxTokens = sharedPreferences.getInt(
                KEY_MAX_TOKENS,
                LlmInferenceSettings.DEFAULT_MAX_TOKENS
            ).coerceIn(MAX_TOKENS_MIN, MAX_TOKENS_MAX)
        )
    }

    override fun saveInferenceSettings(settings: LlmInferenceSettings) {
        sharedPreferences.edit {
            putString(KEY_SYSTEM_PROMPT, settings.systemPrompt)
            putFloat(KEY_TEMPERATURE, settings.temperature.toFloat())
            putInt(KEY_TOP_K, settings.topK)
            putFloat(KEY_TOP_P, settings.topP.toFloat())
            putInt(KEY_SEED, settings.seed)
            putInt(KEY_MAX_TOKENS, settings.maxTokens)
        }
    }

    override fun loadBackend(): LlmBackend {
        val rawValue = sharedPreferences.getString(KEY_BACKEND, null)
            ?: return LlmBackend.CPU

        return runCatching {
            LlmBackend.valueOf(rawValue)
        }.getOrDefault(LlmBackend.CPU)
    }

    override fun saveBackend(backend: LlmBackend) {
        sharedPreferences.edit {
            putString(KEY_BACKEND, backend.name)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "llm_settings"
        const val KEY_SYSTEM_PROMPT = "system_prompt"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_TOP_K = "top_k"
        const val KEY_TOP_P = "top_p"
        const val KEY_SEED = "seed"
        const val KEY_MAX_TOKENS = "max_tokens"
        const val KEY_BACKEND = "backend"

        const val TEMPERATURE_MIN = 0.0
        const val TEMPERATURE_MAX = 2.0
        const val TOP_K_MIN = 1
        const val TOP_K_MAX = 1_000
        const val TOP_P_MIN = 0.0
        const val TOP_P_MAX = 1.0
        const val SEED_MIN = 0
        const val MAX_TOKENS_MIN = 1
        const val MAX_TOKENS_MAX = 8_192
    }
}
