package com.example.aitoy.feature.llm.presentation

import com.example.aitoy.feature.asr.domain.AsrModelState
import com.example.aitoy.feature.asr.domain.RecognitionState
import com.example.aitoy.feature.llm.domain.LlmGenerationState
import com.example.aitoy.feature.llm.domain.LlmBackend
import com.example.aitoy.feature.llm.domain.LlmHistoryEntry
import com.example.aitoy.feature.llm.domain.LlmInferenceSettings
import com.example.aitoy.feature.llm.domain.LlmModelState
import com.example.aitoy.feature.microphone.domain.CaptureState
import com.example.aitoy.feature.tts.domain.TtsPlaybackState
import com.example.aitoy.feature.tts.domain.TtsVoice

data class LlmUiState(
    val modelState: LlmModelState = LlmModelState.Unloaded,
    val modelName: String? = null,
    val modelPath: String = "",
    val selectedBackend: LlmBackend = LlmBackend.CPU,
    val activeBackend: LlmBackend? = null,
    val lastLoadDurationMs: Long? = null,
    val lastGenerationDurationMs: Long? = null,
    val historyEntries: List<LlmHistoryEntry> = emptyList(),
    val promptText: String = "",
    val responseText: String = "",
    val generationState: LlmGenerationState = LlmGenerationState.Idle,
    val errorMessage: String? = null,
    val appliedSettings: LlmInferenceSettings = LlmInferenceSettings(),
    val systemPromptDraft: String = "",
    val temperatureText: String = "0.8",
    val topKText: String = "40",
    val topPText: String = "0.95",
    val seedText: String = "1",
    val maxTokensText: String = "512",
    val voicePromptModelState: AsrModelState = AsrModelState.Unloaded,
    val voicePromptCaptureState: CaptureState = CaptureState.Idle,
    val voicePromptRecognitionState: RecognitionState = RecognitionState.Idle,
    val voicePromptErrorMessage: String? = null,
    val isVoicePromptListening: Boolean = false,
    val isVoicePromptTranscribing: Boolean = false,
    val canStartVoicePrompt: Boolean = false,
    val canStopVoicePrompt: Boolean = false,
    val canEditPrompt: Boolean = true,
    val canGenerate: Boolean = false,
    val canChangeBackend: Boolean = true,
    val canApplySettings: Boolean = false,
    val canResetSettings: Boolean = false,
    val ttsPlaybackState: TtsPlaybackState = TtsPlaybackState.Idle,
    val ttsErrorMessage: String? = null,
    val selectedTtsVoice: TtsVoice = TtsVoice.DmitriMedium,
    val isSpeakingResponse: Boolean = false,
    val canSpeakResponse: Boolean = false,
    val canStopSpeaking: Boolean = false,
    val isBackendReloading: Boolean = false,
    val isBusy: Boolean = false
)
