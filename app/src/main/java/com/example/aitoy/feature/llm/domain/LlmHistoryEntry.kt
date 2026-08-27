package com.example.aitoy.feature.llm.domain

data class LlmHistoryEntry(
    val promptText: String,
    val backend: LlmBackend,
    val durationMs: Long
)
