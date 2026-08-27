package com.example.aitoy.feature.models.data

import java.io.File
import java.io.IOException

class InstalledModelDeleteRepository {
    fun deleteDirectory(path: String) {
        deletePaths(listOf(path)) { file ->
            if (file.exists()) {
                file.deleteRecursively()
            }
            !file.exists()
        }
    }

    fun deleteFile(path: String) {
        deletePaths(listOf(path)) { file ->
            if (file.exists()) {
                file.delete()
            }
            !file.exists()
        }
    }

    fun deletePaths(
        paths: List<String>,
        deleteAction: (File) -> Boolean
    ) {
        val undeletedPaths = buildList {
            paths.distinct().filter { it.isNotBlank() }.forEach { path ->
                val file = File(path)
                runCatching {
                    deleteAction(file)
                }.getOrElse { false }
                    .takeIf { !it }
                    ?.let { add(path) }
            }
        }

        if (undeletedPaths.isNotEmpty()) {
            throw IOException("Could not delete model files from: ${undeletedPaths.joinToString()}")
        }
    }
}
