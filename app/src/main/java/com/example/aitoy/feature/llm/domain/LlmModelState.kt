package com.example.aitoy.feature.llm.domain

enum class LlmModelState {
    Unloaded,
    Loading,
    Loaded,
    Missing,
    Error
}
