package com.example.aitoy.feature.asr.data

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import java.io.BufferedInputStream
import java.io.File

class SherpaOnnxRecognizerFactory(
    private val modelLocator: AsrModelLocator
) {
    fun expectedModelDirectoryPath(): String = modelLocator.expectedModelDirectoryPath()

    fun hasModelFiles(): Boolean = modelLocator.resolveModelFiles() != null

    fun createRecognizer(): Result<OfflineRecognizer> {
        val files = modelLocator.resolveModelFiles()
            ?: return Result.failure(ModelFilesMissingException(expectedModelDirectoryPath()))
        val validationError = validateModelFiles(files)
        if (validationError != null) {
            return Result.failure(validationError)
        }

        return runCatching {
            val transducerConfig = OfflineTransducerModelConfig(
                encoder = files.encoder.absolutePath,
                decoder = files.decoder.absolutePath,
                joiner = files.joiner.absolutePath
            )

            val modelConfig = OfflineModelConfig(
                transducer = transducerConfig,
                numThreads = NUM_THREADS,
                debug = false,
                provider = PROVIDER,
                modelType = MODEL_TYPE,
                tokens = files.tokens.absolutePath,
                modelingUnit = MODELING_UNIT,
                bpeVocab = ""
            )

            val recognizerConfig = OfflineRecognizerConfig(
                featConfig = FeatureConfig(
                    sampleRate = SAMPLE_RATE_HZ,
                    featureDim = FEATURE_DIM
                ),
                modelConfig = modelConfig,
                decodingMethod = DECODING_METHOD,
                maxActivePaths = MAX_ACTIVE_PATHS
            )

            OfflineRecognizer(
                null,
                recognizerConfig
            )
        }
    }

    private fun validateModelFiles(files: AsrModelFiles): Throwable? {
        if (!files.encoder.containsAsciiToken(REQUIRED_ENCODER_METADATA_KEY)) {
            return InvalidAsrModelException(
                "encoder.onnx is missing required metadata key: $REQUIRED_ENCODER_METADATA_KEY"
            )
        }

        return null
    }

    private fun File.containsAsciiToken(token: String): Boolean {
        val needle = token.toByteArray(Charsets.US_ASCII)
        if (needle.isEmpty() || !isFile) {
            return false
        }

        BufferedInputStream(inputStream()).use { input ->
            val buffer = ByteArray(BUFFER_SIZE_BYTES)
            var carry = ByteArray(0)

            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead <= 0) {
                    break
                }

                val window = ByteArray(carry.size + bytesRead)
                System.arraycopy(carry, 0, window, 0, carry.size)
                System.arraycopy(buffer, 0, window, carry.size, bytesRead)

                if (window.indexOf(needle) >= 0) {
                    return true
                }

                val carrySize = minOf(needle.size - 1, window.size)
                carry = if (carrySize > 0) {
                    window.copyOfRange(window.size - carrySize, window.size)
                } else {
                    ByteArray(0)
                }
            }
        }

        return false
    }

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        if (needle.isEmpty() || needle.size > size) {
            return -1
        }

        for (startIndex in 0..(size - needle.size)) {
            var matched = true
            for (offset in needle.indices) {
                if (this[startIndex + offset] != needle[offset]) {
                    matched = false
                    break
                }
            }
            if (matched) {
                return startIndex
            }
        }

        return -1
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val FEATURE_DIM = 80
        const val NUM_THREADS = 2
        const val PROVIDER = "cpu"
        const val MODEL_TYPE = "nemo_transducer"
        const val MODELING_UNIT = "cjkchar"
        const val DECODING_METHOD = "greedy_search"
        const val MAX_ACTIVE_PATHS = 4
        const val REQUIRED_ENCODER_METADATA_KEY = "vocab_size"
        const val BUFFER_SIZE_BYTES = 64 * 1024
    }
}

class ModelFilesMissingException(
    val path: String
) : IllegalStateException("ASR model files are missing in: $path")

class InvalidAsrModelException(
    message: String
) : IllegalStateException(message)
