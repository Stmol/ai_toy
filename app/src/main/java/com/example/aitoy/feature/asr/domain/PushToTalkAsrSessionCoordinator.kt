package com.example.aitoy.feature.asr.domain

import com.example.aitoy.feature.microphone.domain.AudioCaptureController
import com.example.aitoy.feature.microphone.domain.CaptureState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PushToTalkAsrSessionCoordinator(
    private val audioCaptureController: AudioCaptureController,
    private val speechRecognitionController: SpeechRecognitionController,
    private val stopActivePlayback: () -> Unit = {}
) {
    private val _activeOwner = MutableStateFlow<PushToTalkAsrSessionOwner?>(null)
    val activeOwner: StateFlow<PushToTalkAsrSessionOwner?> = _activeOwner.asStateFlow()

    fun start(owner: PushToTalkAsrSessionOwner): PushToTalkAsrStartResult {
        if (_activeOwner.value != null) {
            return PushToTalkAsrStartResult.Busy
        }

        if (recoverIfStale()) {
            return PushToTalkAsrStartResult.Recovering
        }

        if (speechRecognitionController.modelState.value != AsrModelState.Loaded) {
            return PushToTalkAsrStartResult.ModelNotLoaded
        }

        stopActivePlayback()
        _activeOwner.value = owner
        speechRecognitionController.startSession()
        if (speechRecognitionController.recognitionState.value != RecognitionState.Streaming) {
            _activeOwner.value = null
            return PushToTalkAsrStartResult.StartFailed
        }

        if (!audioCaptureController.start()) {
            speechRecognitionController.cancelSession()
            _activeOwner.value = null
            return PushToTalkAsrStartResult.StartFailed
        }
        return PushToTalkAsrStartResult.Started
    }

    fun stop(owner: PushToTalkAsrSessionOwner) {
        if (!isOwner(owner) && !hasStaleActiveSession()) {
            return
        }

        if (audioCaptureController.captureState.value == CaptureState.Listening) {
            audioCaptureController.stop()
        }

        when (speechRecognitionController.recognitionState.value) {
            RecognitionState.Streaming -> {
                speechRecognitionController.stopSession()
                if (speechRecognitionController.recognitionState.value != RecognitionState.Finalizing) {
                    finish(owner)
                }
            }
            RecognitionState.Finalizing -> {
                speechRecognitionController.cancelSession()
                finish(owner)
            }
            else -> {
                finish(owner)
            }
        }
    }

    fun autoStopCurrentSession() {
        val owner = _activeOwner.value ?: return
        stop(owner)
    }

    fun cancel(owner: PushToTalkAsrSessionOwner) {
        if (!isOwner(owner)) {
            return
        }

        if (audioCaptureController.captureState.value == CaptureState.Listening) {
            audioCaptureController.stop()
        }
        speechRecognitionController.cancelSession()
        finish(owner)
    }

    fun finish(owner: PushToTalkAsrSessionOwner) {
        if (isOwner(owner)) {
            _activeOwner.value = null
        }
    }

    fun isOwner(owner: PushToTalkAsrSessionOwner): Boolean {
        return _activeOwner.value == owner
    }

    fun isBusy(owner: PushToTalkAsrSessionOwner): Boolean {
        return isOwner(owner) &&
            (audioCaptureController.captureState.value == CaptureState.Listening ||
                speechRecognitionController.recognitionState.value == RecognitionState.Streaming ||
                speechRecognitionController.recognitionState.value == RecognitionState.Finalizing)
    }

    private fun recoverIfStale(): Boolean {
        if (!hasStaleActiveSession()) {
            return false
        }

        if (audioCaptureController.captureState.value == CaptureState.Listening) {
            audioCaptureController.stop()
        }
        speechRecognitionController.cancelSession()
        return true
    }

    private fun hasStaleActiveSession(): Boolean {
        return _activeOwner.value == null &&
            (audioCaptureController.captureState.value == CaptureState.Listening ||
                speechRecognitionController.recognitionState.value == RecognitionState.Streaming ||
                speechRecognitionController.recognitionState.value == RecognitionState.Finalizing)
    }
}

enum class PushToTalkAsrSessionOwner {
    Recording,
    LlmVoicePrompt
}

sealed interface PushToTalkAsrStartResult {
    data object Started : PushToTalkAsrStartResult
    data object Busy : PushToTalkAsrStartResult
    data object Recovering : PushToTalkAsrStartResult
    data object ModelNotLoaded : PushToTalkAsrStartResult
    data object StartFailed : PushToTalkAsrStartResult
}
