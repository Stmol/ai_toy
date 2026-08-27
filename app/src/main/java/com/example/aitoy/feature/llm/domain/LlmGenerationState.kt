package com.example.aitoy.feature.llm.domain

enum class LlmGenerationState {
    Idle,
    Generating,
    Completed,
    Error
}
