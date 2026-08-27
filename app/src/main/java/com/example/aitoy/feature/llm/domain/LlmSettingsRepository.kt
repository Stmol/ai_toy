package com.example.aitoy.feature.llm.domain

interface LlmSettingsRepository {
    fun loadInferenceSettings(): LlmInferenceSettings
    fun saveInferenceSettings(settings: LlmInferenceSettings)
    fun loadBackend(): LlmBackend
    fun saveBackend(backend: LlmBackend)
}
