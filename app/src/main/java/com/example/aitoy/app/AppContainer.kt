package com.example.aitoy.app

import android.content.Context
import com.example.aitoy.core.audio.AndroidAudioRecorder
import com.example.aitoy.feature.asr.data.AsrModelLocator
import com.example.aitoy.feature.asr.data.SherpaOnnxRecognizerFactory
import com.example.aitoy.feature.asr.data.SherpaOnnxSpeechRecognitionController
import com.example.aitoy.feature.asr.data.SherpaOnnxVadAutoStopDetector
import com.example.aitoy.feature.asr.data.SharedPreferencesVadSettingsRepository
import com.example.aitoy.feature.asr.data.VadModelLocator
import com.example.aitoy.feature.asr.domain.PushToTalkAsrSessionCoordinator
import com.example.aitoy.feature.llm.data.LiteRtLmGemmaController
import com.example.aitoy.feature.llm.data.LlmModelLocator
import com.example.aitoy.feature.llm.data.SharedPreferencesLlmSettingsRepository
import com.example.aitoy.feature.eyesdemo.presentation.EyesDemoViewModel
import com.example.aitoy.feature.models.data.OkHttpModelDownloadRepository
import com.example.aitoy.feature.models.data.InstalledModelDeleteRepository
import com.example.aitoy.feature.llm.presentation.LlmViewModel
import com.example.aitoy.feature.microphone.data.DefaultAudioCaptureController
import com.example.aitoy.feature.microphone.data.SharedPreferencesVoiceTranscriptHistoryRepository
import com.example.aitoy.feature.microphone.presentation.MicrophoneViewModel
import com.example.aitoy.feature.models.presentation.ModelsViewModel
import com.example.aitoy.feature.settings.presentation.SettingsViewModel
import com.example.aitoy.feature.tts.data.SharedPreferencesTtsSettingsRepository
import com.example.aitoy.feature.tts.data.SherpaPiperTtsController
import com.example.aitoy.feature.tts.data.TtsModelLocator
import okhttp3.OkHttpClient

class AppContainer(
    appContext: Context
) {
    private val applicationContext = appContext.applicationContext
    private val asrModelLocator = AsrModelLocator(context = applicationContext)
    private val vadModelLocator = VadModelLocator(context = applicationContext)
    private val llmModelLocator = LlmModelLocator(context = applicationContext)
    private val ttsModelLocator = TtsModelLocator(context = applicationContext)
    private val llmSettingsRepository = SharedPreferencesLlmSettingsRepository(
        context = applicationContext
    )
    private val ttsSettingsRepository = SharedPreferencesTtsSettingsRepository(
        context = applicationContext
    )
    private val vadSettingsRepository = SharedPreferencesVadSettingsRepository(
        context = applicationContext
    )
    private val audioCaptureController = DefaultAudioCaptureController(
        audioRecorder = AndroidAudioRecorder()
    )
    private val voiceTranscriptHistoryRepository = SharedPreferencesVoiceTranscriptHistoryRepository(
        context = applicationContext
    )
    private val recognizerFactory = SherpaOnnxRecognizerFactory(
        modelLocator = asrModelLocator
    )
    private val vadAutoStopDetector = SherpaOnnxVadAutoStopDetector(
        vadModelLocator = vadModelLocator,
        settingsRepository = vadSettingsRepository
    )
    private val speechRecognitionController = SherpaOnnxSpeechRecognitionController(
        audioCaptureController = audioCaptureController,
        recognizerFactory = recognizerFactory,
        vadAutoStopDetector = vadAutoStopDetector
    )
    private val ttsController = SherpaPiperTtsController(
        context = applicationContext,
        modelLocator = ttsModelLocator
    )
    private val pushToTalkAsrSessionCoordinator = PushToTalkAsrSessionCoordinator(
        audioCaptureController = audioCaptureController,
        speechRecognitionController = speechRecognitionController,
        stopActivePlayback = ttsController::stop
    )
    private val llmController = LiteRtLmGemmaController(
        modelLocator = llmModelLocator,
        settingsRepository = llmSettingsRepository
    )
    private val modelDownloadRepository = OkHttpModelDownloadRepository(
        context = applicationContext,
        httpClient = OkHttpClient()
    )
    private val installedModelDeleteRepository = InstalledModelDeleteRepository()
    private val appStartupCoordinator = AppStartupCoordinator(
        speechRecognitionController = speechRecognitionController,
        asrModelLocator = asrModelLocator,
        vadModelLocator = vadModelLocator,
        llmController = llmController,
        llmModelLocator = llmModelLocator
    )

    fun createAppStartupViewModelFactory(): AppStartupViewModel.Factory {
        return AppStartupViewModel.Factory(
            coordinator = appStartupCoordinator
        )
    }

    fun createMicrophoneViewModelFactory(): MicrophoneViewModel.Factory {
        return MicrophoneViewModel.Factory(
            audioCaptureController = audioCaptureController,
            speechRecognitionController = speechRecognitionController,
            pushToTalkAsrSessionCoordinator = pushToTalkAsrSessionCoordinator,
            asrModelLocator = asrModelLocator,
            voiceTranscriptHistoryRepository = voiceTranscriptHistoryRepository
        )
    }

    fun createModelsViewModelFactory(): ModelsViewModel.Factory {
        return ModelsViewModel.Factory(
            speechRecognitionController = speechRecognitionController,
            asrModelLocator = asrModelLocator,
            vadModelLocator = vadModelLocator,
            llmController = llmController,
            llmModelLocator = llmModelLocator,
            ttsController = ttsController,
            ttsModelLocator = ttsModelLocator,
            modelDownloadRepository = modelDownloadRepository,
            installedModelDeleteRepository = installedModelDeleteRepository,
            requestStartupRetry = appStartupCoordinator::requestBootstrap
        )
    }

    fun createLlmViewModelFactory(): LlmViewModel.Factory {
        return LlmViewModel.Factory(
            llmController = llmController,
            llmModelLocator = llmModelLocator,
            audioCaptureController = audioCaptureController,
            speechRecognitionController = speechRecognitionController,
            pushToTalkAsrSessionCoordinator = pushToTalkAsrSessionCoordinator,
            ttsController = ttsController,
            ttsSettingsRepository = ttsSettingsRepository
        )
    }

    fun createSettingsViewModelFactory(): SettingsViewModel.Factory {
        return SettingsViewModel.Factory(
            speechRecognitionController = speechRecognitionController,
            ttsSettingsRepository = ttsSettingsRepository,
            ttsController = ttsController,
            ttsModelLocator = ttsModelLocator
        )
    }

    fun createEyesDemoViewModelFactory(): EyesDemoViewModel.Factory {
        return EyesDemoViewModel.Factory()
    }
}
