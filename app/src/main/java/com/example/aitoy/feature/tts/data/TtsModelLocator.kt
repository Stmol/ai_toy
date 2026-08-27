package com.example.aitoy.feature.tts.data

import android.content.Context
import android.os.Environment
import com.example.aitoy.feature.asr.data.ModelFileStatus
import com.example.aitoy.feature.tts.domain.TtsVoice
import java.io.File

class TtsModelLocator(
    private val context: Context
) {
    fun expectedModelDirectoryPath(): String {
        return candidateModelDirectories().first().directory.absolutePath
    }

    fun resolveVoiceFiles(voice: TtsVoice): TtsVoiceFiles? {
        candidateModelDirectories().forEach { candidate ->
            val voiceFiles = voiceFilesIn(candidate.directory, voice)
            if (voiceFiles.isComplete()) {
                return voiceFiles
            }
        }

        return null
    }

    fun inspectModelDirectory(): TtsModelInspection {
        val candidates = candidateModelDirectories()
        val resolvedDirectory = candidates.firstOrNull { candidate ->
            TtsVoice.entries.all { voice ->
                voiceFilesIn(candidate.directory, voice).isComplete()
            }
        }?.directory

        if (resolvedDirectory != null) {
            return TtsModelInspection(
                fileStatus = ModelFileStatus.Complete,
                expectedPath = expectedModelDirectoryPath(),
                activePath = resolvedDirectory.absolutePath,
                candidatePaths = candidates.map { it.directory.absolutePath },
                availableVoices = TtsVoice.entries.toSet(),
                missingVoices = emptySet(),
                preferredExternalPath = preferredExternalPath(candidates)
            )
        }

        val availableVoices = buildSet {
            TtsVoice.entries.forEach { voice ->
                if (candidates.any { candidate -> voiceFilesIn(candidate.directory, voice).isComplete() }) {
                    add(voice)
                }
            }
        }

        return TtsModelInspection(
            fileStatus = if (availableVoices.isEmpty()) {
                ModelFileStatus.Missing
            } else {
                ModelFileStatus.Partial
            },
            expectedPath = expectedModelDirectoryPath(),
            activePath = candidates.firstOrNull { candidate ->
                availableVoices.any { voice -> voiceFilesIn(candidate.directory, voice).isComplete() }
            }?.directory?.absolutePath,
            candidatePaths = candidates.map { it.directory.absolutePath },
            availableVoices = availableVoices,
            missingVoices = TtsVoice.entries.toSet() - availableVoices,
            preferredExternalPath = preferredExternalPath(candidates)
        )
    }

    private fun candidateModelDirectories(): List<TtsModelDirectoryCandidate> {
        val packageName = context.packageName
        val externalFilesDir = context.getExternalFilesDir(null)

        return buildList {
            add(
                TtsModelDirectoryCandidate(
                    directory = File(context.filesDir, RELATIVE_MODEL_DIR),
                    storage = TtsModelStorage.Internal
                )
            )
            if (externalFilesDir != null) {
                add(
                    TtsModelDirectoryCandidate(
                        directory = File(externalFilesDir, RELATIVE_MODEL_DIR),
                        storage = TtsModelStorage.External
                    )
                )
            }
            add(
                TtsModelDirectoryCandidate(
                    directory = File(externalAndroidDataFilesDir(packageName), RELATIVE_MODEL_DIR),
                    storage = TtsModelStorage.External
                )
            )
            if (packageName != LEGACY_PACKAGE_NAME) {
                add(
                    TtsModelDirectoryCandidate(
                        directory = File(
                            externalAndroidDataFilesDir(LEGACY_PACKAGE_NAME),
                            RELATIVE_MODEL_DIR
                        ),
                        storage = TtsModelStorage.External
                    )
                )
            }
        }.distinctBy { it.directory.absolutePath }
    }

    private fun voiceFilesIn(rootDirectory: File, voice: TtsVoice): TtsVoiceFiles {
        val voiceDirectory = File(rootDirectory, voice.directoryName)
        return TtsVoiceFiles(
            model = File(voiceDirectory, voice.modelFileName),
            tokens = File(voiceDirectory, "tokens.txt"),
            dataDir = File(voiceDirectory, "espeak-ng-data")
        )
    }

    private fun preferredExternalPath(candidates: List<TtsModelDirectoryCandidate>): String? {
        return candidates.firstOrNull { it.storage == TtsModelStorage.External }
            ?.directory
            ?.absolutePath
    }

    private fun externalAndroidDataFilesDir(packageName: String): File {
        return File(Environment.getExternalStorageDirectory(), "Android/data/$packageName/files")
    }

    private companion object {
        private const val RELATIVE_MODEL_DIR = "models/tts/piper-voices"
        private const val LEGACY_PACKAGE_NAME = "com.example.aitoy"
    }
}

data class TtsModelInspection(
    val fileStatus: ModelFileStatus,
    val expectedPath: String,
    val activePath: String?,
    val candidatePaths: List<String>,
    val availableVoices: Set<TtsVoice>,
    val missingVoices: Set<TtsVoice>,
    val preferredExternalPath: String?
)

data class TtsModelDirectoryCandidate(
    val directory: File,
    val storage: TtsModelStorage
)

enum class TtsModelStorage {
    Internal,
    External
}

data class TtsVoiceFiles(
    val model: File,
    val tokens: File,
    val dataDir: File
) {
    fun isComplete(): Boolean {
        return model.isFile &&
            tokens.isFile &&
            dataDir.exists() &&
            dataDir.isDirectory
    }
}

private val TtsVoice.directoryName: String
    get() = when (this) {
        TtsVoice.DmitriMedium -> "vits-piper-ru_RU-dmitri-medium"
        TtsVoice.IrinaMedium -> "vits-piper-ru_RU-irina-medium"
    }

private val TtsVoice.modelFileName: String
    get() = when (this) {
        TtsVoice.DmitriMedium -> "ru_RU-dmitri-medium.onnx"
        TtsVoice.IrinaMedium -> "ru_RU-irina-medium.onnx"
    }
