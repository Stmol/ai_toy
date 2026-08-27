package com.example.aitoy.feature.asr.data

import android.util.Log
import com.example.aitoy.feature.asr.domain.AsrModelState
import com.example.aitoy.feature.asr.domain.VadAutoStopSettings
import com.example.aitoy.feature.asr.domain.VadSettingsRepository
import com.example.aitoy.feature.microphone.domain.PcmAudioFrame
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SherpaOnnxVadAutoStopDetector(
    private val vadModelLocator: VadModelLocator,
    private val settingsRepository: VadSettingsRepository
) {
    private val _modelState = MutableStateFlow(initialModelState())
    private val _settings = MutableStateFlow(settingsRepository.loadSettings())
    private val engineLock = Any()
    private var vad: Vad? = null
    private var hasDetectedSpeech = false

    val modelState: StateFlow<AsrModelState> = _modelState.asStateFlow()
    val settings: StateFlow<VadAutoStopSettings> = _settings.asStateFlow()

    fun loadModel() {
        if (_modelState.value == AsrModelState.Loaded ||
            _modelState.value == AsrModelState.Loading
        ) {
            return
        }

        val modelFile = vadModelLocator.resolveModelFile()
        if (modelFile == null) {
            releaseModel()
            _modelState.value = AsrModelState.Missing
            return
        }

        _modelState.value = AsrModelState.Loading

        if (!modelFile.isSupportedOnnxModel()) {
            releaseModel()
            _modelState.value = AsrModelState.Error
            return
        }

        synchronized(engineLock) {
            if (vad != null) {
                _modelState.value = AsrModelState.Loaded
                return
            }

            val createdVad = createVad(modelFile)

            vad = createdVad
            _modelState.value = if (createdVad != null) {
                AsrModelState.Loaded
            } else {
                AsrModelState.Error
            }
        }
    }

    fun prepareForSession(): Boolean {
        if (_modelState.value != AsrModelState.Loaded) {
            loadModel()
        }

        val modelFile = vadModelLocator.resolveModelFile()
        if (_modelState.value != AsrModelState.Loaded || modelFile == null) {
            return false
        }

        if (!modelFile.isSupportedOnnxModel()) {
            releaseModel()
            _modelState.value = AsrModelState.Error
            return false
        }

        synchronized(engineLock) {
            releaseLocked()
            val createdVad = createVad(modelFile)
            vad = createdVad
            _modelState.value = if (createdVad != null) {
                AsrModelState.Loaded
            } else {
                AsrModelState.Error
            }
            return createdVad != null
        }
    }

    fun releaseModel() {
        synchronized(engineLock) {
            releaseLocked()
            _modelState.value = initialModelState()
        }
    }

    fun updateSettings(settings: VadAutoStopSettings): Boolean {
        val sanitized = settings.coerceInSupportedRange()
        settingsRepository.saveSettings(sanitized)
        _settings.value = sanitized

        if (_modelState.value != AsrModelState.Loaded) {
            return true
        }

        synchronized(engineLock) {
            releaseLocked()
            _modelState.value = initialModelState()
        }
        loadModel()
        return _modelState.value == AsrModelState.Loaded
    }

    fun reset() {
        synchronized(engineLock) {
            hasDetectedSpeech = false
            vad?.reset()
        }
    }

    fun shouldAutoStop(frame: PcmAudioFrame): Boolean {
        if (frame.sampleRateHz != SAMPLE_RATE_HZ ||
            frame.channelCount != CHANNEL_COUNT ||
            frame.samples.isEmpty()
        ) {
            return false
        }

        val normalized = frame.toNormalizedFloatArray()
        synchronized(engineLock) {
            val activeVad = vad ?: return false
            activeVad.acceptWaveform(normalized)
            if (activeVad.isSpeechDetected()) {
                hasDetectedSpeech = true
            }

            var hasCompletedSpeechSegment = false
            while (!activeVad.empty()) {
                activeVad.pop()
                hasCompletedSpeechSegment = true
            }

            if (hasCompletedSpeechSegment) {
                hasDetectedSpeech = true
                return true
            }

            return false
        }
    }

    fun release() {
        synchronized(engineLock) {
            releaseLocked()
            _modelState.value = initialModelState()
        }
    }

    private fun releaseLocked() {
        runCatching {
            vad?.release()
        }.onFailure { throwable ->
            Log.w(TAG, "Failed to release Silero VAD", throwable)
        }
        vad = null
        hasDetectedSpeech = false
    }

    private fun createVad(modelFile: File): Vad? {
        val activeSettings = settings.value
        return runCatching {
            Vad(
                assetManager = null,
                config = VadModelConfig(
                    sileroVadModelConfig = SileroVadModelConfig(
                        model = modelFile.absolutePath,
                        threshold = activeSettings.threshold,
                        minSilenceDuration = activeSettings.minSilenceDurationSeconds,
                        minSpeechDuration = activeSettings.minSpeechDurationSeconds,
                        windowSize = WINDOW_SIZE,
                        maxSpeechDuration = activeSettings.maxSpeechDurationSeconds
                    ),
                    sampleRate = SAMPLE_RATE_HZ,
                    numThreads = NUM_THREADS,
                    provider = PROVIDER,
                    debug = false
                )
            )
        }.getOrElse { throwable ->
            Log.w(TAG, "Failed to load Silero VAD. Auto-stop is disabled.", throwable)
            null
        }
    }

    private fun initialModelState(): AsrModelState {
        return if (vadModelLocator.resolveModelFile() != null) {
            AsrModelState.Unloaded
        } else {
            AsrModelState.Missing
        }
    }

    private fun PcmAudioFrame.toNormalizedFloatArray(): FloatArray {
        return FloatArray(samples.size) { index ->
            (samples[index] / SHORT_NORMALIZATION).coerceIn(-1f, 1f)
        }
    }

    private fun File.isSupportedOnnxModel(): Boolean {
        val irVersion = readOnnxIrVersion()
        if (irVersion == null) {
            Log.w(TAG, "Failed to read Silero VAD ONNX IR version from $absolutePath")
            return false
        }
        if (irVersion > MAX_SUPPORTED_ONNX_IR_VERSION) {
            Log.w(
                TAG,
                "Unsupported Silero VAD ONNX IR version $irVersion. " +
                    "Bundled sherpa-onnx supports up to $MAX_SUPPORTED_ONNX_IR_VERSION."
            )
            return false
        }
        return true
    }

    private fun File.readOnnxIrVersion(): Long? {
        return runCatching {
            val buffer = ByteArray(ONNX_HEADER_SCAN_BYTES)
            val count = inputStream().use { inputStream ->
                inputStream.read(buffer)
            }
            if (count < 0) {
                null
            } else {
                parseOnnxIrVersion(buffer.copyOf(count))
            }
        }.getOrNull()
    }

    private fun parseOnnxIrVersion(bytes: ByteArray): Long? {
        var offset = 0
        while (offset < bytes.size) {
            val tag = readVarint(bytes, offset) ?: return null
            offset = tag.nextOffset
            val fieldNumber = (tag.value shr PROTOBUF_FIELD_NUMBER_SHIFT).toInt()
            val wireType = (tag.value and PROTOBUF_WIRE_TYPE_MASK).toInt()

            if (fieldNumber == ONNX_IR_VERSION_FIELD_NUMBER && wireType == PROTOBUF_VARINT_WIRE_TYPE) {
                return readVarint(bytes, offset)?.value
            }

            offset = skipProtobufValue(bytes, offset, wireType) ?: return null
        }
        return null
    }

    private fun readVarint(bytes: ByteArray, startOffset: Int): VarintResult? {
        var result = 0L
        var shift = 0
        var offset = startOffset
        while (offset < bytes.size && shift < Long.SIZE_BITS) {
            val byte = bytes[offset].toInt() and BYTE_MASK
            result = result or ((byte and VARINT_VALUE_MASK).toLong() shl shift)
            offset += 1
            if ((byte and VARINT_CONTINUATION_MASK) == 0) {
                return VarintResult(result, offset)
            }
            shift += VARINT_SHIFT
        }
        return null
    }

    private fun skipProtobufValue(bytes: ByteArray, offset: Int, wireType: Int): Int? {
        return when (wireType) {
            PROTOBUF_VARINT_WIRE_TYPE -> readVarint(bytes, offset)?.nextOffset
            PROTOBUF_FIXED64_WIRE_TYPE -> (offset + FIXED64_SIZE_BYTES).takeIf { it <= bytes.size }
            PROTOBUF_LENGTH_DELIMITED_WIRE_TYPE -> {
                val length = readVarint(bytes, offset) ?: return null
                val nextOffset = length.nextOffset + length.value
                nextOffset.toInt().takeIf { nextOffset <= bytes.size }
            }
            PROTOBUF_FIXED32_WIRE_TYPE -> (offset + FIXED32_SIZE_BYTES).takeIf { it <= bytes.size }
            else -> null
        }
    }

    private data class VarintResult(
        val value: Long,
        val nextOffset: Int
    )

    private companion object {
        const val TAG = "SherpaOnnxVad"
        const val SAMPLE_RATE_HZ = 16_000
        const val CHANNEL_COUNT = 1
        const val SHORT_NORMALIZATION = 32768f
        const val WINDOW_SIZE = 512
        const val NUM_THREADS = 1
        const val PROVIDER = "cpu"
        const val MAX_SUPPORTED_ONNX_IR_VERSION = 9L
        const val ONNX_IR_VERSION_FIELD_NUMBER = 1
        const val ONNX_HEADER_SCAN_BYTES = 4096
        const val PROTOBUF_FIELD_NUMBER_SHIFT = 3
        const val PROTOBUF_WIRE_TYPE_MASK = 0x7L
        const val PROTOBUF_VARINT_WIRE_TYPE = 0
        const val PROTOBUF_FIXED64_WIRE_TYPE = 1
        const val PROTOBUF_LENGTH_DELIMITED_WIRE_TYPE = 2
        const val PROTOBUF_FIXED32_WIRE_TYPE = 5
        const val BYTE_MASK = 0xFF
        const val VARINT_VALUE_MASK = 0x7F
        const val VARINT_CONTINUATION_MASK = 0x80
        const val VARINT_SHIFT = 7
        const val FIXED64_SIZE_BYTES = 8
        const val FIXED32_SIZE_BYTES = 4
    }
}
