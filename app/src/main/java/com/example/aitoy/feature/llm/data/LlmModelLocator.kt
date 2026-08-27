package com.example.aitoy.feature.llm.data

import android.content.Context
import android.os.Environment
import java.io.File

class LlmModelLocator(
    private val context: Context
) {
    fun expectedModelFilePath(): String {
        return candidateModelFiles().first().file.absolutePath
    }

    fun resolveModelFile(): File? {
        return candidateModelFiles()
            .firstOrNull { it.file.isFile }
            ?.file
    }

    fun inspectModelFile(): LlmModelInspection {
        val candidates = candidateModelFiles()
        val resolvedFile = resolveModelFile()

        return if (resolvedFile != null) {
            LlmModelInspection(
                exists = true,
                expectedPath = expectedModelFilePath(),
                activePath = resolvedFile.absolutePath,
                candidatePaths = candidates.map { it.file.absolutePath },
                preferredExternalPath = preferredExternalPath(candidates)
            )
        } else {
            LlmModelInspection(
                exists = false,
                expectedPath = expectedModelFilePath(),
                activePath = null,
                candidatePaths = candidates.map { it.file.absolutePath },
                preferredExternalPath = preferredExternalPath(candidates)
            )
        }
    }

    private fun candidateModelFiles(): List<LlmModelFileCandidate> {
        val packageName = context.packageName
        val externalFilesDir = context.getExternalFilesDir(null)

        return buildList {
            add(
                LlmModelFileCandidate(
                    file = File(context.filesDir, RELATIVE_MODEL_FILE),
                    storage = LlmModelStorage.Internal
                )
            )
            if (externalFilesDir != null) {
                add(
                    LlmModelFileCandidate(
                        file = File(externalFilesDir, RELATIVE_MODEL_FILE),
                        storage = LlmModelStorage.External
                    )
                )
            }
            add(
                LlmModelFileCandidate(
                    file = File(externalAndroidDataFilesDir(packageName), RELATIVE_MODEL_FILE),
                    storage = LlmModelStorage.External
                )
            )
            if (packageName != LEGACY_PACKAGE_NAME) {
                add(
                    LlmModelFileCandidate(
                        file = File(
                            externalAndroidDataFilesDir(LEGACY_PACKAGE_NAME),
                            RELATIVE_MODEL_FILE
                        ),
                        storage = LlmModelStorage.External
                    )
                )
            }
        }.distinctBy { it.file.absolutePath }
    }

    private fun preferredExternalPath(
        candidates: List<LlmModelFileCandidate>
    ): String? {
        return candidates.firstOrNull { it.storage == LlmModelStorage.External }
            ?.file
            ?.absolutePath
    }

    private fun externalAndroidDataFilesDir(packageName: String): File {
        return File(Environment.getExternalStorageDirectory(), "Android/data/$packageName/files")
    }

    companion object {
        private const val RELATIVE_MODEL_FILE =
            "models/llm/gemma-4-e2b-it/gemma-4-E2B-it.litertlm"
        private const val LEGACY_PACKAGE_NAME = "com.example.aitoy"
    }
}

data class LlmModelInspection(
    val exists: Boolean,
    val expectedPath: String,
    val activePath: String?,
    val candidatePaths: List<String>,
    val preferredExternalPath: String?
)

data class LlmModelFileCandidate(
    val file: File,
    val storage: LlmModelStorage
)

enum class LlmModelStorage {
    Internal,
    External
}
