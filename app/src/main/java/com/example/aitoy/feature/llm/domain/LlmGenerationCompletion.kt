package com.example.aitoy.feature.llm.domain

data class LlmGenerationCompletion(
    val requestId: Long,
    val promptText: String,
    val responseText: String
)
