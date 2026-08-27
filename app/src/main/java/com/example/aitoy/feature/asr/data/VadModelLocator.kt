package com.example.aitoy.feature.asr.data

import android.content.Context
import java.io.File

class VadModelLocator(
    private val context: Context
) {
    fun expectedModelFilePath(): String {
        return candidateModelFiles().first().file.absolutePath
    }

    fun resolveModelFile(): File? {
        return candidateModelFiles().firstOrNull { it.file.exists() && it.file.isFile }?.file
    }

    fun inspectModelFile(): VadModelInspection {
        val candidates = candidateModelFiles()
        val completeCandidate = candidates.firstOrNull { it.file.exists() && it.file.isFile }

        if (completeCandidate != null) {
            return VadModelInspection(
                fileStatus = ModelFileStatus.Complete,
                expectedPath = expectedModelFilePath(),
                activePath = completeCandidate.file.absolutePath,
                candidatePaths = candidates.map { it.file.absolutePath },
                preferredExternalPath = preferredExternalPath(candidates)
            )
        }

        return VadModelInspection(
            fileStatus = ModelFileStatus.Missing,
            expectedPath = expectedModelFilePath(),
            activePath = null,
            candidatePaths = candidates.map { it.file.absolutePath },
            preferredExternalPath = preferredExternalPath(candidates)
        )
    }

    private fun candidateModelFiles(): List<VadModelCandidate> {
        val externalFilesDir = context.getExternalFilesDir(null)

        return buildList {
            add(
                VadModelCandidate(
                    file = File(context.filesDir, RELATIVE_MODEL_FILE_PATH),
                    storage = ModelStorage.Internal
                )
            )
            if (externalFilesDir != null) {
                add(
                    VadModelCandidate(
                        file = File(externalFilesDir, RELATIVE_MODEL_FILE_PATH),
                        storage = ModelStorage.External
                    )
                )
            }
        }.distinctBy { it.file.absolutePath }
    }

    private fun preferredExternalPath(candidates: List<VadModelCandidate>): String? {
        return candidates.firstOrNull { it.storage == ModelStorage.External }?.file?.absolutePath
    }

    private companion object {
        const val RELATIVE_MODEL_FILE_PATH = "models/vad/silero_vad.onnx"
    }
}

data class VadModelCandidate(
    val file: File,
    val storage: ModelStorage
)

data class VadModelInspection(
    val fileStatus: ModelFileStatus,
    val expectedPath: String,
    val activePath: String?,
    val candidatePaths: List<String>,
    val preferredExternalPath: String?
)
