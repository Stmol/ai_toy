package com.example.aitoy.feature.asr.data

import android.util.Log
import com.example.aitoy.feature.asr.domain.AsrModelState
import com.example.aitoy.feature.asr.domain.RecognitionState
import com.example.aitoy.feature.asr.domain.SpeechRecognitionController
import com.example.aitoy.feature.asr.domain.SpeechRecognitionError
import com.example.aitoy.feature.asr.domain.SpeechRecognitionSessionEvent
import com.example.aitoy.feature.asr.domain.VadAutoStopSettings
import com.example.aitoy.feature.microphone.domain.AudioCaptureController
import com.example.aitoy.feature.microphone.domain.PcmAudioFrame
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SherpaOnnxSpeechRecognitionController(
    private val audioCaptureController: AudioCaptureController,
    private val recognizerFactory: SherpaOnnxRecognizerFactory,
    private val vadAutoStopDetector: SherpaOnnxVadAutoStopDetector
) : SpeechRecognitionController {
    private val _modelState = MutableStateFlow(initialModelState())
    private val _recognitionState = MutableStateFlow(RecognitionState.Idle)
    private val _partialText = MutableStateFlow("")
    private val _finalText = MutableStateFlow<String?>(null)
    private val _errors = MutableSharedFlow<SpeechRecognitionError>(extraBufferCapacity = 1)
    private val _sessionEvents = MutableSharedFlow<SpeechRecognitionSessionEvent>(
        extraBufferCapacity = 1
    )

    override val modelState: StateFlow<AsrModelState> = _modelState.asStateFlow()
    override val vadModelState: StateFlow<AsrModelState> = vadAutoStopDetector.modelState
    override val vadSettings: StateFlow<VadAutoStopSettings> = vadAutoStopDetector.settings
    override val recognitionState: StateFlow<RecognitionState> = _recognitionState.asStateFlow()
    override val partialText: StateFlow<String> = _partialText.asStateFlow()
    override val finalText: StateFlow<String?> = _finalText.asStateFlow()
    override val errors: Flow<SpeechRecognitionError> = _errors.asSharedFlow()
    override val sessionEvents: Flow<SpeechRecognitionSessionEvent> = _sessionEvents.asSharedFlow()

    private val recognitionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collectFramesJob: Job? = null
    private var recognizerInitialization: Deferred<Result<OfflineRecognizer>>? = null

    private val engineLock = Any()
    private val audioBufferLock = Any()
    private var recognizer: OfflineRecognizer? = null
    private val bufferedFrames = mutableListOf<ShortArray>()
    private var bufferedSampleCount = 0
    private var autoStopRequested = false

    override fun expectedModelDirectoryPath(): String {
        return recognizerFactory.expectedModelDirectoryPath()
    }

    override fun loadModel() {
        if (_modelState.value == AsrModelState.Loaded ||
            _modelState.value == AsrModelState.Loading
        ) {
            return
        }

        if (!recognizerFactory.hasModelFiles()) {
            _modelState.value = AsrModelState.Missing
            _recognitionState.value = RecognitionState.ModelMissing
            _errors.tryEmit(SpeechRecognitionError.ModelMissing)
            return
        }

        _modelState.value = AsrModelState.Loading
        vadAutoStopDetector.loadModel()
        if (handleVadUnavailable()) {
            return
        }

        recognizerInitialization?.cancel()
        recognizerInitialization = recognitionScope.async {
            recognizerFactory.createRecognizer()
        }

        recognitionScope.launch {
            try {
                val createdRecognizer = recognizerInitialization?.await()?.getOrElse { throwable ->
                    recognizerInitialization = null
                    handleModelLoadFailure(throwable)
                    return@launch
                } ?: return@launch

                recognizerInitialization = null

                synchronized(engineLock) {
                    safeReleaseRecognizer(recognizer)
                    recognizer = createdRecognizer
                }

                _modelState.value = AsrModelState.Loaded
                if (_recognitionState.value == RecognitionState.ModelMissing ||
                    _recognitionState.value == RecognitionState.Error
                ) {
                    _recognitionState.value = RecognitionState.Idle
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    throw throwable
                }
                recognizerInitialization = null
                handleModelLoadFailure(throwable)
            }
        }
    }

    override fun unloadModel() {
        if (_recognitionState.value == RecognitionState.Streaming ||
            _recognitionState.value == RecognitionState.Finalizing
        ) {
            return
        }

        recognizerInitialization?.cancel()
        recognizerInitialization = null
        synchronized(engineLock) {
            safeReleaseRecognizer(recognizer)
            recognizer = null
        }
        vadAutoStopDetector.release()
        clearBufferedAudio()
        clearTranscripts()
        _modelState.value = initialModelState()
    }

    override fun loadVadModel() {
        vadAutoStopDetector.loadModel()
    }

    override fun updateVadSettings(settings: VadAutoStopSettings): Boolean {
        if (_recognitionState.value == RecognitionState.Streaming ||
            _recognitionState.value == RecognitionState.Finalizing
        ) {
            return false
        }

        return vadAutoStopDetector.updateSettings(settings)
    }

    override fun startSession() {
        if (_recognitionState.value == RecognitionState.Streaming ||
            _recognitionState.value == RecognitionState.Finalizing
        ) {
            return
        }

        if (!recognizerFactory.hasModelFiles()) {
            _modelState.value = AsrModelState.Missing
            _recognitionState.value = RecognitionState.ModelMissing
            _errors.tryEmit(SpeechRecognitionError.ModelMissing)
            return
        }

        if (vadAutoStopDetector.modelState.value != AsrModelState.Loaded) {
            vadAutoStopDetector.loadModel()
        }

        if (handleVadUnavailable()) {
            return
        }

        if (_modelState.value != AsrModelState.Loaded) {
            _errors.tryEmit(SpeechRecognitionError.ModelNotLoaded)
            return
        }

        val loadedRecognizer = synchronized(engineLock) { recognizer }
        if (loadedRecognizer == null) {
            _recognitionState.value = RecognitionState.Error
            _errors.tryEmit(SpeechRecognitionError.RecognizerInitFailed)
            return
        }

        collectFramesJob?.cancel()
        clearBufferedAudio()
        clearTranscripts()
        autoStopRequested = false
        if (!vadAutoStopDetector.prepareForSession()) {
            handleVadUnavailable()
            return
        }
        _recognitionState.value = RecognitionState.Streaming

        collectFramesJob = recognitionScope.launch {
            audioCaptureController.audioFrames.collect { frame ->
                if (_recognitionState.value != RecognitionState.Streaming) {
                    return@collect
                }

                bufferFrame(frame)
                maybeRequestAutoStop(frame)
            }
        }
    }

    override fun stopSession() {
        if (_recognitionState.value != RecognitionState.Streaming) {
            return
        }

        val activeCollectFramesJob = collectFramesJob
        collectFramesJob = null
        _recognitionState.value = RecognitionState.Finalizing

        recognitionScope.launch {
            activeCollectFramesJob?.cancelAndJoin()
            vadAutoStopDetector.reset()

            val final = runCatching {
                decodeBufferedAudio()
            }.getOrElse {
                _recognitionState.value = RecognitionState.Error
                _errors.tryEmit(SpeechRecognitionError.RecognitionFailed)
                return@launch
            }

            if (final.isBlank()) {
                _recognitionState.value = RecognitionState.Error
                _errors.tryEmit(SpeechRecognitionError.NoSpeechDetected)
                return@launch
            }

            _partialText.value = final
            _finalText.value = final
            _recognitionState.value = RecognitionState.Completed
        }
    }

    override fun cancelSession() {
        if (_recognitionState.value != RecognitionState.Streaming &&
            _recognitionState.value != RecognitionState.Finalizing &&
            _recognitionState.value != RecognitionState.Error
        ) {
            return
        }

        val activeCollectFramesJob = collectFramesJob
        collectFramesJob = null

        recognitionScope.launch {
            activeCollectFramesJob?.cancelAndJoin()
            vadAutoStopDetector.reset()
            clearBufferedAudio()
            clearTranscripts()
            _recognitionState.value = RecognitionState.Idle
        }
    }

    override fun release() {
        collectFramesJob?.cancel()
        collectFramesJob = null
        recognizerInitialization?.cancel()
        recognizerInitialization = null

        synchronized(engineLock) {
            safeReleaseRecognizer(recognizer)
            recognizer = null
        }
        vadAutoStopDetector.release()

        clearBufferedAudio()
        clearTranscripts()
        _modelState.value = initialModelState()
        _recognitionState.value = RecognitionState.Idle
    }

    private fun bufferFrame(frame: PcmAudioFrame) {
        if (frame.channelCount != CHANNEL_COUNT || frame.samples.isEmpty()) {
            return
        }

        synchronized(audioBufferLock) {
            bufferedFrames += frame.samples.copyOf()
            bufferedSampleCount += frame.samples.size
        }
    }

    private fun maybeRequestAutoStop(frame: PcmAudioFrame) {
        if (autoStopRequested || _recognitionState.value != RecognitionState.Streaming) {
            return
        }

        if (vadAutoStopDetector.shouldAutoStop(frame)) {
            autoStopRequested = true
            _sessionEvents.tryEmit(SpeechRecognitionSessionEvent.AutoStopRequested)
        }
    }

    private fun decodeBufferedAudio(): String {
        val currentRecognizer = synchronized(engineLock) { recognizer }
            ?: throw IllegalStateException("Recognizer is not available.")
        val audioSnapshot = snapshotAudio()
        if (audioSnapshot.isEmpty()) {
            return ""
        }

        val stream = currentRecognizer.createStream()
        return try {
            stream.acceptWaveform(audioSnapshot, SAMPLE_RATE_HZ)
            currentRecognizer.decode(stream)
            currentRecognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    private fun snapshotAudio(): FloatArray {
        synchronized(audioBufferLock) {
            val normalized = FloatArray(bufferedSampleCount + DECODE_TAIL_PADDING_SAMPLES)
            var destinationIndex = 0
            bufferedFrames.forEach { frame ->
                frame.forEach { sample ->
                    normalized[destinationIndex] =
                        (sample / SHORT_NORMALIZATION).coerceIn(-1f, 1f)
                    destinationIndex += 1
                }
            }
            while (destinationIndex < normalized.size) {
                normalized[destinationIndex] = 0f
                destinationIndex += 1
            }
            return normalized
        }
    }

    private fun clearBufferedAudio() {
        synchronized(audioBufferLock) {
            bufferedFrames.clear()
            bufferedSampleCount = 0
        }
    }

    private fun clearTranscripts() {
        _partialText.value = ""
        _finalText.value = null
    }

    private fun handleModelLoadFailure(throwable: Throwable) {
        Log.e(TAG, "Sherpa recognizer load failed", throwable)
        synchronized(engineLock) {
            safeReleaseRecognizer(recognizer)
            recognizer = null
        }

        if (throwable is ModelFilesMissingException) {
            _modelState.value = AsrModelState.Missing
            _recognitionState.value = RecognitionState.ModelMissing
            _errors.tryEmit(SpeechRecognitionError.ModelMissing)
            return
        }

        _modelState.value = AsrModelState.Error
        _recognitionState.value = RecognitionState.Error
        _errors.tryEmit(SpeechRecognitionError.RecognizerInitFailed)
    }

    private fun handleVadUnavailable(): Boolean {
        val vadState = vadAutoStopDetector.modelState.value
        if (vadState == AsrModelState.Loaded) {
            return false
        }

        if (vadState == AsrModelState.Error) {
            _modelState.value = AsrModelState.Error
            _recognitionState.value = RecognitionState.Error
            _errors.tryEmit(SpeechRecognitionError.RecognizerInitFailed)
            return true
        }

        _modelState.value = AsrModelState.Missing
        _recognitionState.value = RecognitionState.ModelMissing
        _errors.tryEmit(SpeechRecognitionError.ModelMissing)
        return true
    }

    private fun safeReleaseRecognizer(activeRecognizer: OfflineRecognizer?) {
        runCatching {
            activeRecognizer?.release()
        }.onFailure {
            Log.w(TAG, "Failed to release Sherpa recognizer", it)
        }
    }

    private fun initialModelState(): AsrModelState {
        return if (recognizerFactory.hasModelFiles() &&
            vadAutoStopDetector.modelState.value != AsrModelState.Missing
        ) {
            AsrModelState.Unloaded
        } else {
            AsrModelState.Missing
        }
    }

    private companion object {
        const val TAG = "SherpaOnnxAsr"
        const val CHANNEL_COUNT = 1
        const val SAMPLE_RATE_HZ = 16_000
        const val SHORT_NORMALIZATION = 32768f
        const val DECODE_TAIL_PADDING_SAMPLES = SAMPLE_RATE_HZ / 2
    }
}
