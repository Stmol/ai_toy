package com.example.aitoy.feature.tts.data

import android.content.Context
import com.example.aitoy.feature.tts.domain.TtsController
import com.example.aitoy.feature.tts.domain.TtsError
import com.example.aitoy.feature.tts.domain.TtsPlaybackState
import com.example.aitoy.feature.tts.domain.TtsVoice
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SherpaPiperTtsController(
    context: Context,
    private val modelLocator: TtsModelLocator,
    private val playbackController: AudioTrackPlaybackController = AudioTrackPlaybackController()
) : TtsController {
    private val appContext = context.applicationContext
    private val runtimeMutex = Mutex()
    private val _playbackState = MutableStateFlow<TtsPlaybackState>(TtsPlaybackState.Idle)
    private val _errors = MutableSharedFlow<TtsError>(extraBufferCapacity = 4)

    override val playbackState: StateFlow<TtsPlaybackState> = _playbackState.asStateFlow()
    override val errors: Flow<TtsError> = _errors.asSharedFlow()

    private var currentVoice: TtsVoice? = null
    private var currentTts: OfflineTts? = null

    override suspend fun prepare(voice: TtsVoice) {
        runtimeMutex.withLock {
            if (currentVoice == voice && currentTts != null) {
                return
            }

            _playbackState.value = TtsPlaybackState.Preparing(voice)

            val voiceFiles = modelLocator.resolveVoiceFiles(voice)
            if (voiceFiles == null || !voiceFiles.isComplete()) {
                releaseRuntimeLocked()
                _playbackState.value = TtsPlaybackState.Idle
                _errors.tryEmit(TtsError.VoiceMissing(voice))
                return
            }

            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = voiceFiles.model.absolutePath,
                        lexicon = "",
                        tokens = voiceFiles.tokens.absolutePath,
                        dataDir = voiceFiles.dataDir.absolutePath,
                        dictDir = "",
                        noiseScale = 0.667f,
                        noiseScaleW = 0.8f,
                        lengthScale = 1.0f
                    ),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                ),
                ruleFsts = "",
                ruleFars = "",
                maxNumSentences = 1,
                silenceScale = 0.2f
            )

            val tts = runCatching {
                // We store Piper voices in app files, so sherpa-onnx must load them via newFromFile().
                OfflineTts(null, config)
            }.getOrElse { throwable ->
                releaseRuntimeLocked()
                _playbackState.value = TtsPlaybackState.Idle
                _errors.tryEmit(TtsError.InitializationFailed(throwable.message))
                return
            }

            releaseRuntimeLocked()
            currentVoice = voice
            currentTts = tts
            _playbackState.value = TtsPlaybackState.Idle
        }
    }

    override suspend fun speak(text: String, voice: TtsVoice) {
        val sanitizedText = text.trim()
        if (sanitizedText.isBlank()) {
            _errors.tryEmit(TtsError.EmptyText)
            return
        }

        stop()
        prepare(voice)

        val tts = runtimeMutex.withLock {
            currentTts
        }
        if (tts == null) {
            _errors.tryEmit(TtsError.ModelMissing)
            return
        }

        _playbackState.value = TtsPlaybackState.Preparing(voice)

        val audio = withContext(Dispatchers.Default) {
            runCatching {
                tts.generate(sanitizedText, 0, 1.0f)
            }
        }.getOrElse { throwable ->
            _playbackState.value = TtsPlaybackState.Idle
            _errors.tryEmit(TtsError.SynthesisFailed(throwable.message))
            return
        }

        runCatching {
            _playbackState.value = TtsPlaybackState.Speaking(voice, sanitizedText)
            playbackController.play(
                samples = audio.samples,
                sampleRate = audio.sampleRate
            ) {
                _playbackState.value = TtsPlaybackState.Idle
            }
        }.onFailure { throwable ->
            _playbackState.value = TtsPlaybackState.Idle
            _errors.tryEmit(TtsError.PlaybackFailed(throwable.message))
        }
    }

    override fun stop() {
        playbackController.stop()
        _playbackState.value = TtsPlaybackState.Idle
    }

    override fun release() {
        stop()
        runCatching {
            kotlinx.coroutines.runBlocking {
                runtimeMutex.withLock {
                    releaseRuntimeLocked()
                }
            }
        }
        playbackController.release()
    }

    private fun releaseRuntimeLocked() {
        currentTts?.release()
        currentTts = null
        currentVoice = null
    }
}
