package com.example.aitoy.feature.asr.domain

enum class RecognitionState {
    Idle,
    Streaming,
    Finalizing,
    Completed,
    ModelMissing,
    Error
}
