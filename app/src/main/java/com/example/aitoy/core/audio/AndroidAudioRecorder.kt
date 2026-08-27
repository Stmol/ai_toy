package com.example.aitoy.core.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.aitoy.feature.microphone.domain.AudioCaptureError
import com.example.aitoy.feature.microphone.domain.PcmAudioFrame
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AndroidAudioRecorder {
    private val _recorderState = MutableStateFlow(AudioRecorderState.Idle)
    private val _audioLevel = MutableStateFlow(0f)
    private val _audioFrames = MutableSharedFlow<PcmAudioFrame>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val _errors = MutableSharedFlow<AudioCaptureError>(extraBufferCapacity = 1)
    private val recorderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal val recorderState: StateFlow<AudioRecorderState> = _recorderState.asStateFlow()
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()
    val audioFrames: SharedFlow<PcmAudioFrame> = _audioFrames.asSharedFlow()
    val errors: SharedFlow<AudioCaptureError> = _errors.asSharedFlow()

    private var audioRecord: AudioRecord? = null
    private var readJob: Job? = null
    private var smoothedLevel = 0f

    @Synchronized
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (_recorderState.value == AudioRecorderState.Listening) {
            return true
        }

        releaseResources(updateState = false)

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        if (minBufferSize <= 0) {
            publishError(AudioCaptureError.InitFailed)
            return false
        }

        val frameSizeInSamples = SAMPLE_RATE_HZ / 10
        val frameSizeInBytes = frameSizeInSamples * PCM_16_BIT_BYTES
        val bufferSizeInBytes = max(minBufferSize, frameSizeInBytes * 2)

        val recorder = try {
            AudioRecord(
                AUDIO_SOURCE,
                SAMPLE_RATE_HZ,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSizeInBytes
            )
        } catch (_: IllegalArgumentException) {
            publishError(AudioCaptureError.InitFailed)
            return false
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            publishError(AudioCaptureError.InitFailed)
            return false
        }

        val started = runCatching {
            recorder.startRecording()
        }.isSuccess && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING

        if (!started) {
            recorder.release()
            publishError(AudioCaptureError.StartFailed)
            return false
        }

        audioRecord = recorder
        smoothedLevel = 0f
        _audioLevel.value = 0f
        _recorderState.value = AudioRecorderState.Listening

        val readBuffer = ShortArray(bufferSizeInBytes / PCM_16_BIT_BYTES)
        readJob = recorderScope.launch {
            readLoop(recorder = recorder, buffer = readBuffer)
        }
        return true
    }

    @Synchronized
    fun stop() {
        releaseResources(updateState = true)
    }

    private suspend fun readLoop(recorder: AudioRecord, buffer: ShortArray) {
        try {
            while (currentCoroutineContext().isActive) {
                val samplesRead = recorder.read(
                    buffer,
                    0,
                    buffer.size,
                    AudioRecord.READ_BLOCKING
                )

                if (!currentCoroutineContext().isActive ||
                    _recorderState.value != AudioRecorderState.Listening
                ) {
                    break
                }

                if (samplesRead <= 0) {
                    publishError(AudioCaptureError.ReadFailed)
                    break
                }

                _audioFrames.tryEmit(
                    PcmAudioFrame(
                        samples = buffer.copyOf(samplesRead),
                        sampleRateHz = SAMPLE_RATE_HZ,
                        channelCount = CHANNEL_COUNT
                    )
                )

                val rawLevel = calculateMeterLevel(buffer = buffer, samplesRead = samplesRead)
                smoothedLevel = smooth(previous = smoothedLevel, next = rawLevel)
                _audioLevel.value = smoothedLevel
            }
        } catch (_: CancellationException) {
            // Normal stop path.
        }
    }

    private fun calculateMeterLevel(buffer: ShortArray, samplesRead: Int): Float {
        if (samplesRead <= 0) {
            return 0f
        }

        var sum = 0.0
        var peak = 0
        for (index in 0 until samplesRead) {
            val absoluteSample = abs(buffer[index].toInt()).coerceAtMost(Short.MAX_VALUE.toInt())
            peak = max(peak, absoluteSample)
            val normalizedSample = absoluteSample.toDouble() / Short.MAX_VALUE.toDouble()
            sum += normalizedSample * normalizedSample
        }

        val rms = sqrt(sum / samplesRead).toFloat()
        val peakLevel = peak.toFloat() / Short.MAX_VALUE.toFloat()
        val combinedLevel = max(rms, peakLevel * PEAK_WEIGHT)

        return mapToMeterScale(combinedLevel)
    }

    private fun smooth(previous: Float, next: Float): Float {
        if (previous == 0f) {
            return next
        }

        val smoothingFactor = if (next > previous) ATTACK_SMOOTHING_FACTOR else RELEASE_SMOOTHING_FACTOR
        return (previous * smoothingFactor + next * (1f - smoothingFactor)).coerceIn(0f, 1f)
    }

    private fun mapToMeterScale(linearLevel: Float): Float {
        val safeLevel = linearLevel.coerceAtLeast(MIN_LINEAR_LEVEL)
        val db = 20f * log10(safeLevel)
        return ((db - MIN_DB_LEVEL) / (MAX_DB_LEVEL - MIN_DB_LEVEL)).coerceIn(0f, 1f)
    }

    @Synchronized
    private fun releaseResources(updateState: Boolean) {
        readJob?.cancel()
        readJob = null

        audioRecord?.let { recorder ->
            runCatching {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            }
            recorder.release()
        }
        audioRecord = null
        smoothedLevel = 0f
        _audioLevel.value = 0f

        if (updateState) {
            _recorderState.value = AudioRecorderState.Idle
        }
    }

    private fun publishError(error: AudioCaptureError) {
        releaseResources(updateState = false)
        _recorderState.value = AudioRecorderState.Error
        _errors.tryEmit(error)
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val PCM_16_BIT_BYTES = 2
        const val ATTACK_SMOOTHING_FACTOR = 0.25f
        const val RELEASE_SMOOTHING_FACTOR = 0.85f
        const val PEAK_WEIGHT = 0.8f
        const val MIN_LINEAR_LEVEL = 0.0001f
        const val MIN_DB_LEVEL = -55f
        const val MAX_DB_LEVEL = -8f

        const val CHANNEL_COUNT = 1
        const val AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
}
