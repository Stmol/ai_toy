package com.example.aitoy.feature.models.data

import kotlinx.coroutines.flow.Flow

interface ModelDownloadRepository {
    fun downloadModel(spec: RemoteModelSpec): Flow<ModelDownloadEvent>

    fun cancelDownload(modelId: String)
}

sealed interface ModelDownloadEvent {
    data object Idle : ModelDownloadEvent

    data class Preparing(
        val totalBytes: Long
    ) : ModelDownloadEvent

    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val progressPercent: Int?
    ) : ModelDownloadEvent

    data object Installing : ModelDownloadEvent

    data object Cancelling : ModelDownloadEvent

    data object Completed : ModelDownloadEvent

    data class Failed(
        val userMessage: String
    ) : ModelDownloadEvent

    data object Cancelled : ModelDownloadEvent
}
