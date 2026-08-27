package com.example.aitoy.feature.models.presentation

data class ModelsUiState(
    val asrCard: ModelCardState = ModelCardState(type = ManagedModelType.Asr),
    val llmCard: ModelCardState = ModelCardState(type = ManagedModelType.Llm),
    val vadCard: ModelCardState = ModelCardState(type = ManagedModelType.Vad),
    val ttsCard: ModelCardState = ModelCardState(type = ManagedModelType.Tts)
)

data class ModelCardState(
    val type: ManagedModelType,
    val status: ManagedModelStatus = ManagedModelStatus.Unloaded,
    val expectedPath: String = "",
    val activePath: String? = null,
    val exists: Boolean = false,
    val message: String? = null,
    val isDownloadAvailable: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadProgressPercent: Int? = null,
    val downloadMessage: String? = null,
    val canCancelDownload: Boolean = false,
    val canDelete: Boolean = false,
    val canLoad: Boolean = true,
    val canUnload: Boolean = false,
    val isBusy: Boolean = false
)

enum class ManagedModelType {
    Asr,
    Llm,
    Vad,
    Tts
}

enum class ManagedModelStatus {
    Unloaded,
    Loading,
    Loaded,
    MissingFiles,
    UnsupportedFiles,
    Error
}
