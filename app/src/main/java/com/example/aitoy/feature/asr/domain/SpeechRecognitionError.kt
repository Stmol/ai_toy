package com.example.aitoy.feature.asr.domain

enum class SpeechRecognitionError {
    ModelMissing,
    ModelNotLoaded,
    RecognizerInitFailed,
    RecognitionFailed,
    NoSpeechDetected
}
