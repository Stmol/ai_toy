package com.example.aitoy.feature.asr.data

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

class AsrModelLocator(
    private val context: Context
) {
    fun expectedModelDirectoryPath(): String {
        return candidateModelDirectories().first().directory.absolutePath
    }

    fun resolveModelFiles(): AsrModelFiles? {
        candidateModelDirectories().forEach { candidate ->
            val modelDir = candidate.directory
            val tokens = File(modelDir, "tokens.txt")
            val encoder = File(modelDir, "encoder.onnx")
            val decoder = File(modelDir, "decoder.onnx")
            val joiner = File(modelDir, "joiner.onnx")

            Log.d(
                TAG,
                "Checking model dir=${modelDir.absolutePath} " +
                    "tokens=${tokens.exists()} encoder=${encoder.exists()} " +
                    "decoder=${decoder.exists()} joiner=${joiner.exists()}"
            )

            if (tokens.exists() &&
                encoder.exists() &&
                decoder.exists() &&
                joiner.exists()
            ) {
                Log.d(TAG, "Resolved model dir=${modelDir.absolutePath}")
                return AsrModelFiles(
                    tokens = tokens,
                    encoder = encoder,
                    decoder = decoder,
                    joiner = joiner
                )
            }
        }

        return null
    }

    fun candidateModelDirectories(): List<ModelDirectoryCandidate> {
        val packageName = context.packageName
        val externalFilesDir = context.getExternalFilesDir(null)

        return buildList {
            add(
                ModelDirectoryCandidate(
                    directory = File(context.filesDir, RELATIVE_MODEL_DIR),
                    storage = ModelStorage.Internal
                )
            )
            if (externalFilesDir != null) {
                add(
                    ModelDirectoryCandidate(
                        directory = File(externalFilesDir, RELATIVE_MODEL_DIR),
                        storage = ModelStorage.External
                    )
                )
            }
            add(
                ModelDirectoryCandidate(
                    directory = File(externalAndroidDataFilesDir(packageName), RELATIVE_MODEL_DIR),
                    storage = ModelStorage.External
                )
            )
            if (packageName != LEGACY_PACKAGE_NAME) {
                add(
                    ModelDirectoryCandidate(
                        directory = File(
                            externalAndroidDataFilesDir(LEGACY_PACKAGE_NAME),
                            RELATIVE_MODEL_DIR
                        ),
                        storage = ModelStorage.External
                    )
                )
            }
        }.distinctBy { it.directory.absolutePath }
    }

    fun inspectModelDirectory(): AsrModelInspection {
        val candidates = candidateModelDirectories()
        var partialCandidate: ModelDirectoryCandidate? = null
        var partialPresentFiles: List<String> = emptyList()

        candidates.forEach { candidate ->
            val presentFiles = REQUIRED_FILE_NAMES.filter { fileName ->
                File(candidate.directory, fileName).isFile
            }
            if (presentFiles.size == REQUIRED_FILE_NAMES.size) {
                return AsrModelInspection(
                    fileStatus = ModelFileStatus.Complete,
                    expectedPath = expectedModelDirectoryPath(),
                    activePath = candidate.directory.absolutePath,
                    candidatePaths = candidates.map { it.directory.absolutePath },
                    presentFiles = REQUIRED_FILE_NAMES,
                    missingFiles = emptyList(),
                    preferredExternalPath = preferredExternalPath(candidates)
                )
            }

            if (presentFiles.isNotEmpty() && partialCandidate == null) {
                partialCandidate = candidate
                partialPresentFiles = presentFiles
            }
        }

        if (partialCandidate != null) {
            val presentSet = partialPresentFiles.toSet()
            return AsrModelInspection(
                fileStatus = ModelFileStatus.Partial,
                expectedPath = expectedModelDirectoryPath(),
                activePath = partialCandidate.directory.absolutePath,
                candidatePaths = candidates.map { it.directory.absolutePath },
                presentFiles = partialPresentFiles,
                missingFiles = REQUIRED_FILE_NAMES.filterNot { it in presentSet },
                preferredExternalPath = preferredExternalPath(candidates)
            )
        }

        return AsrModelInspection(
            fileStatus = ModelFileStatus.Missing,
            expectedPath = expectedModelDirectoryPath(),
            activePath = null,
            candidatePaths = candidates.map { it.directory.absolutePath },
            presentFiles = emptyList(),
            missingFiles = REQUIRED_FILE_NAMES,
            preferredExternalPath = preferredExternalPath(candidates)
        )
    }

    private fun preferredExternalPath(candidates: List<ModelDirectoryCandidate>): String? {
        val externalCandidate = candidates.firstOrNull { it.storage == ModelStorage.External }
            ?: return null
        return externalCandidate.directory.absolutePath
    }

    private fun externalAndroidDataFilesDir(packageName: String): File {
        return File(Environment.getExternalStorageDirectory(), "Android/data/$packageName/files")
    }

    companion object {
        private const val TAG = "AsrModelLocator"
        private const val RELATIVE_MODEL_DIR = "models/asr/gigaam_v3_rnnt"
        private const val LEGACY_PACKAGE_NAME = "com.example.aitoy"
        private val REQUIRED_FILE_NAMES = listOf(
            "tokens.txt",
            "encoder.onnx",
            "decoder.onnx",
            "joiner.onnx"
        )
    }
}

data class ModelDirectoryCandidate(
    val directory: File,
    val storage: ModelStorage
)

enum class ModelStorage {
    Internal,
    External
}

data class AsrModelInspection(
    val fileStatus: ModelFileStatus,
    val expectedPath: String,
    val activePath: String?,
    val candidatePaths: List<String>,
    val presentFiles: List<String>,
    val missingFiles: List<String>,
    val preferredExternalPath: String?
)

enum class ModelFileStatus {
    Complete,
    Partial,
    Missing,
    Unsupported
}

data class AsrModelFiles(
    val tokens: File,
    val encoder: File,
    val decoder: File,
    val joiner: File
)
