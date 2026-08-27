package com.example.aitoy.feature.llm.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface LlmController {
    fun loadModel()
    fun unloadModel()
    fun generate(prompt: String)
    fun selectBackend(backend: LlmBackend)
    fun applySettings(settings: LlmInferenceSettings)
    fun resetSettings()
    fun release()
    fun expectedModelFilePath(): String

    val modelState: StateFlow<LlmModelState>
    val generationState: StateFlow<LlmGenerationState>
    val responseText: StateFlow<String>
    val selectedBackend: StateFlow<LlmBackend>
    val activeBackend: StateFlow<LlmBackend?>
    val lastLoadDurationMs: StateFlow<Long?>
    val lastGenerationDurationMs: StateFlow<Long?>
    val settings: StateFlow<LlmInferenceSettings>
    val historyEntries: StateFlow<List<LlmHistoryEntry>>
    val errors: Flow<LlmError>
    val generationCompletions: Flow<LlmGenerationCompletion>
}
