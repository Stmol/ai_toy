package com.example.aitoy.feature.llm.domain

data class LlmInferenceSettings(
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val temperature: Double = DEFAULT_TEMPERATURE,
    val topK: Int = DEFAULT_TOP_K,
    val topP: Double = DEFAULT_TOP_P,
    val seed: Int = DEFAULT_SEED,
    val maxTokens: Int = DEFAULT_MAX_TOKENS
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT = "Отвечай кратко, по-русски, 1-3 короткими фразами."
        const val DEFAULT_TEMPERATURE = 0.8
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_TOP_P = 0.95
        const val DEFAULT_SEED = 1
        const val DEFAULT_MAX_TOKENS = 96
    }
}
