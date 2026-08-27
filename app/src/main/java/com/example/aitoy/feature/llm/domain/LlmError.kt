package com.example.aitoy.feature.llm.domain

sealed interface LlmError {
    data object ModelMissing : LlmError
    data class LoadFailed(val details: String? = null) : LlmError
    data class SettingsApplyFailed(val details: String? = null) : LlmError
    data class GenerationFailed(val details: String? = null) : LlmError
    data object EmptyPrompt : LlmError
}
