package com.example.aitoy.feature.models.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aitoy.feature.asr.data.AsrModelInspection
import com.example.aitoy.feature.asr.data.AsrModelLocator
import com.example.aitoy.feature.asr.data.ModelFileStatus
import com.example.aitoy.feature.asr.data.VadModelInspection
import com.example.aitoy.feature.asr.data.VadModelLocator
import com.example.aitoy.feature.asr.domain.AsrModelState
import com.example.aitoy.feature.asr.domain.RecognitionState
import com.example.aitoy.feature.asr.domain.SpeechRecognitionController
import com.example.aitoy.feature.llm.data.LlmModelInspection
import com.example.aitoy.feature.llm.data.LlmModelLocator
import com.example.aitoy.feature.llm.domain.LlmController
import com.example.aitoy.feature.llm.domain.LlmGenerationState
import com.example.aitoy.feature.llm.domain.LlmModelState
import com.example.aitoy.feature.models.data.ModelDownloadCatalog
import com.example.aitoy.feature.models.data.ModelDownloadEvent
import com.example.aitoy.feature.models.data.ModelDownloadRepository
import com.example.aitoy.feature.models.data.RemoteModelSpec
import com.example.aitoy.feature.models.data.InstalledModelDeleteRepository
import com.example.aitoy.feature.tts.data.TtsModelInspection
import com.example.aitoy.feature.tts.data.TtsModelLocator
import com.example.aitoy.feature.tts.domain.TtsController
import com.example.aitoy.feature.tts.domain.TtsPlaybackState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ModelsViewModel(
    private val speechRecognitionController: SpeechRecognitionController,
    private val asrModelLocator: AsrModelLocator,
    private val vadModelLocator: VadModelLocator,
    private val llmController: LlmController,
    private val llmModelLocator: LlmModelLocator,
    private val ttsController: TtsController,
    private val ttsModelLocator: TtsModelLocator,
    private val modelDownloadRepository: ModelDownloadRepository,
    private val installedModelDeleteRepository: InstalledModelDeleteRepository,
    private val requestStartupRetry: () -> Unit
) : ViewModel() {
    private val refreshToken = MutableStateFlow(0)
    private val downloadStates = MutableStateFlow(
        ManagedModelType.entries.associateWith { DownloadUiState() }
    )
    private val actionMessages = MutableStateFlow(
        ManagedModelType.entries.associateWith { null as String? }
    )
    private val activeDownloadJobs = mutableMapOf<ManagedModelType, Job>()
    private val asrRuntimeState = combine(
        speechRecognitionController.modelState,
        speechRecognitionController.vadModelState,
        speechRecognitionController.recognitionState
    ) { runtimeModelState, vadRuntimeModelState, recognitionState ->
        AsrRuntimeModelsState(
            asrModelState = runtimeModelState,
            vadModelState = vadRuntimeModelState,
            recognitionState = recognitionState
        )
    }
    private val llmRuntimeState = combine(
        llmController.modelState,
        llmController.generationState
    ) { llmModelState, llmGenerationState ->
        LlmRuntimeModelsState(
            llmModelState = llmModelState,
            llmGenerationState = llmGenerationState
        )
    }
    private val runtimeState = combine(
        asrRuntimeState,
        llmRuntimeState,
        ttsController.playbackState
    ) { asrRuntimeState, llmRuntimeState, ttsPlaybackState ->
        RuntimeModelsState(
            asrModelState = asrRuntimeState.asrModelState,
            vadModelState = asrRuntimeState.vadModelState,
            recognitionState = asrRuntimeState.recognitionState,
            llmModelState = llmRuntimeState.llmModelState,
            llmGenerationState = llmRuntimeState.llmGenerationState,
            ttsPlaybackState = ttsPlaybackState
        )
    }

    val uiState: StateFlow<ModelsUiState> = combine(
        runtimeState,
        refreshToken,
        downloadStates,
        actionMessages
    ) { runtimeState, _, downloadStates, actionMessages ->
        val asrInspection = runCatching { asrModelLocator.inspectModelDirectory() }
            .getOrElse { fallbackAsrInspection() }
        val vadInspection = runCatching { vadModelLocator.inspectModelFile() }
            .getOrElse { fallbackVadInspection() }
        val llmInspection = runCatching { llmModelLocator.inspectModelFile() }
            .getOrElse { fallbackLlmInspection() }
        val ttsInspection = runCatching { ttsModelLocator.inspectModelDirectory() }
            .getOrElse { fallbackTtsInspection() }

        ModelsUiState(
            asrCard = buildAsrCardState(
                runtimeModelState = runtimeState.asrModelState,
                vadRuntimeModelState = runtimeState.vadModelState,
                recognitionState = runtimeState.recognitionState,
                inspection = asrInspection,
                vadInspection = vadInspection,
                downloadState = downloadStates.getValue(ManagedModelType.Asr),
                actionMessage = actionMessages[ManagedModelType.Asr]
            ),
            llmCard = buildLlmCardState(
                runtimeModelState = runtimeState.llmModelState,
                generationState = runtimeState.llmGenerationState,
                inspection = llmInspection,
                downloadState = downloadStates.getValue(ManagedModelType.Llm),
                actionMessage = actionMessages[ManagedModelType.Llm]
            ),
            vadCard = buildVadCardState(
                vadRuntimeModelState = runtimeState.vadModelState,
                recognitionState = runtimeState.recognitionState,
                inspection = vadInspection,
                downloadState = downloadStates.getValue(ManagedModelType.Vad),
                actionMessage = actionMessages[ManagedModelType.Vad]
            ),
            ttsCard = buildTtsCardState(
                inspection = ttsInspection,
                playbackState = runtimeState.ttsPlaybackState,
                downloadState = downloadStates.getValue(ManagedModelType.Tts),
                actionMessage = actionMessages[ManagedModelType.Tts]
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = ModelsUiState()
    )

    fun onDownloadClick(type: ManagedModelType) {
        val spec = remoteModelSpec(type) ?: return
        if (activeDownloadJobs[type]?.isActive == true) {
            return
        }

        setDownloadState(type, DownloadUiState())
        clearActionMessage(type)

        val job = viewModelScope.launch {
            modelDownloadRepository.downloadModel(spec).collect { event ->
                when (event) {
                    ModelDownloadEvent.Idle -> {
                        setDownloadState(type, DownloadUiState())
                    }
                    is ModelDownloadEvent.Preparing -> {
                        setDownloadState(
                            type,
                            DownloadUiState(
                                isDownloading = true,
                                totalBytes = event.totalBytes,
                                downloadMessage = "Preparing download...",
                                canCancelDownload = true
                            )
                        )
                    }
                    is ModelDownloadEvent.Downloading -> {
                        setDownloadState(
                            type,
                            DownloadUiState(
                                isDownloading = true,
                                downloadedBytes = event.downloadedBytes,
                                totalBytes = event.totalBytes,
                                progressPercent = event.progressPercent,
                                canCancelDownload = true
                            )
                        )
                    }
                    ModelDownloadEvent.Installing -> {
                        val previousState = downloadStates.value.getValue(type)
                        setDownloadState(
                            type,
                            DownloadUiState(
                                isDownloading = true,
                                downloadedBytes = previousState.totalBytes,
                                totalBytes = previousState.totalBytes,
                                progressPercent = 100,
                                downloadMessage = "Installing downloaded files...",
                                canCancelDownload = false
                            )
                        )
                    }
                    ModelDownloadEvent.Cancelling -> {
                        setDownloadState(
                            type,
                            downloadStates.value.getValue(type).copy(
                                isDownloading = true,
                                downloadMessage = "Cancelling download...",
                                canCancelDownload = false
                            )
                        )
                    }
                    ModelDownloadEvent.Completed -> {
                        setDownloadState(type, DownloadUiState())
                        refresh()
                        requestStartupRetry()
                    }
                    is ModelDownloadEvent.Failed -> {
                        setDownloadState(
                            type,
                            DownloadUiState(
                                downloadMessage = event.userMessage
                            )
                        )
                        refresh()
                    }
                    ModelDownloadEvent.Cancelled -> {
                        setDownloadState(type, DownloadUiState())
                        refresh()
                    }
                }
            }
        }

        activeDownloadJobs[type] = job
        job.invokeOnCompletion {
            activeDownloadJobs.remove(type)
        }
    }

    fun onCancelDownloadClick(type: ManagedModelType) {
        val spec = remoteModelSpec(type) ?: return
        val currentState = downloadStates.value.getValue(type)

        if (!currentState.isDownloading) {
            return
        }

        setDownloadState(
            type,
            currentState.copy(
                isDownloading = true,
                downloadMessage = "Cancelling download...",
                canCancelDownload = false
            )
        )
        modelDownloadRepository.cancelDownload(spec.id)
    }

    fun onDeleteClick(type: ManagedModelType) {
        when (type) {
            ManagedModelType.Asr -> performAction(
                type = type,
                fallbackMessage = "Failed to delete ASR model.",
                action = {
                    clearActionMessage(type)
                    speechRecognitionController.unloadModel()
                    ensureAsrRuntimeReleased()
                    val inspection = asrModelLocator.inspectModelDirectory()
                    val deletePath = inspection.activePath ?: inspection.expectedPath
                    installedModelDeleteRepository.deleteDirectory(deletePath)
                    refresh()
                    requestStartupRetry()
                }
            )
            ManagedModelType.Llm -> performAction(
                type = type,
                fallbackMessage = "Failed to delete LLM model.",
                action = {
                    clearActionMessage(type)
                    llmController.unloadModel()
                    ensureLlmRuntimeReleased()
                    val inspection = llmModelLocator.inspectModelFile()
                    val deletePath = inspection.activePath ?: inspection.expectedPath
                    installedModelDeleteRepository.deletePaths(
                        listOf(
                            deletePath,
                            llmCacheDirFor(deletePath)
                        )
                    ) { file ->
                        if (file.exists()) {
                            if (file.isDirectory) {
                                file.deleteRecursively()
                            } else {
                                file.delete()
                            }
                        }
                        !file.exists()
                    }
                    refresh()
                    requestStartupRetry()
                }
            )
            ManagedModelType.Vad -> performAction(
                type = type,
                fallbackMessage = "Failed to delete VAD model.",
                action = {
                    clearActionMessage(type)
                    speechRecognitionController.unloadModel()
                    ensureVadRuntimeReleased()
                    val inspection = vadModelLocator.inspectModelFile()
                    val deletePath = inspection.activePath ?: inspection.expectedPath
                    installedModelDeleteRepository.deleteFile(deletePath)
                    refresh()
                    requestStartupRetry()
                }
            )
            ManagedModelType.Tts -> performAction(
                type = type,
                fallbackMessage = "Failed to delete TTS voice pack.",
                action = {
                    clearActionMessage(type)
                    ttsController.release()
                    val inspection = ttsModelLocator.inspectModelDirectory()
                    val deletePath = inspection.activePath ?: inspection.expectedPath
                    installedModelDeleteRepository.deleteDirectory(deletePath)
                    refresh()
                }
            )
        }
    }

    private fun buildAsrCardState(
        runtimeModelState: AsrModelState,
        vadRuntimeModelState: AsrModelState,
        recognitionState: RecognitionState,
        inspection: AsrModelInspection,
        vadInspection: VadModelInspection,
        downloadState: DownloadUiState,
        actionMessage: String?
    ): ModelCardState {
        val status = mapStatus(runtimeModelState, inspection.fileStatus)
        val controls = controlsAvailability(runtimeModelState, recognitionState)
        val exists = inspection.fileStatus == ModelFileStatus.Complete ||
            inspection.fileStatus == ModelFileStatus.Partial
        val vadExists = vadInspection.fileStatus == ModelFileStatus.Complete
        val isRuntimeLocked = runtimeModelState == AsrModelState.Loaded ||
            runtimeModelState == AsrModelState.Loading

        return ModelCardState(
            type = ManagedModelType.Asr,
            status = status,
            expectedPath = inspection.expectedPath,
            activePath = inspection.activePath,
            exists = exists,
            message = actionMessage ?: asrStatusMessage(
                status = status,
                inspection = inspection,
                vadExists = vadExists,
                vadRuntimeModelState = vadRuntimeModelState
            ),
            isDownloadAvailable = !downloadState.isDownloading &&
                !isRuntimeLocked &&
                inspection.fileStatus != ModelFileStatus.Complete,
            isDownloading = downloadState.isDownloading,
            downloadedBytes = downloadState.downloadedBytes,
            totalBytes = downloadState.totalBytes,
            downloadProgressPercent = downloadState.progressPercent,
            downloadMessage = downloadState.downloadMessage,
            canCancelDownload = downloadState.canCancelDownload,
            canDelete = exists && !downloadState.isDownloading && controls.canMutate,
            canLoad = false,
            canUnload = false,
            isBusy = controls.isBusy || downloadState.isDownloading
        )
    }

    private fun buildVadCardState(
        vadRuntimeModelState: AsrModelState,
        recognitionState: RecognitionState,
        inspection: VadModelInspection,
        downloadState: DownloadUiState,
        actionMessage: String?
    ): ModelCardState {
        val exists = inspection.fileStatus == ModelFileStatus.Complete
        val status = vadStatus(
            exists = exists,
            vadRuntimeModelState = vadRuntimeModelState
        )
        val canMutate = !downloadState.isDownloading &&
            vadRuntimeModelState != AsrModelState.Loading &&
            recognitionState != RecognitionState.Streaming &&
            recognitionState != RecognitionState.Finalizing

        return ModelCardState(
            type = ManagedModelType.Vad,
            status = status,
            expectedPath = inspection.expectedPath,
            activePath = inspection.activePath,
            exists = exists,
            message = actionMessage ?: vadStatusMessage(status),
            isDownloadAvailable = canMutate && !exists,
            isDownloading = downloadState.isDownloading,
            downloadedBytes = downloadState.downloadedBytes,
            totalBytes = downloadState.totalBytes,
            downloadProgressPercent = downloadState.progressPercent,
            downloadMessage = downloadState.downloadMessage,
            canCancelDownload = downloadState.canCancelDownload,
            canDelete = exists && canMutate,
            canLoad = false,
            canUnload = false,
            isBusy = downloadState.isDownloading || vadRuntimeModelState == AsrModelState.Loading
        )
    }

    private fun vadStatus(
        exists: Boolean,
        vadRuntimeModelState: AsrModelState
    ): ManagedModelStatus {
        if (!exists) {
            return ManagedModelStatus.MissingFiles
        }

        return when (vadRuntimeModelState) {
            AsrModelState.Loading -> ManagedModelStatus.Loading
            AsrModelState.Loaded -> ManagedModelStatus.Loaded
            AsrModelState.Error -> ManagedModelStatus.Error
            AsrModelState.Unloaded,
            AsrModelState.Missing -> ManagedModelStatus.Unloaded
        }
    }

    private fun buildLlmCardState(
        runtimeModelState: LlmModelState,
        generationState: LlmGenerationState,
        inspection: LlmModelInspection,
        downloadState: DownloadUiState,
        actionMessage: String?
    ): ModelCardState {
        val isRuntimeBusy = runtimeModelState == LlmModelState.Loading ||
            generationState == LlmGenerationState.Generating
        val canMutate = !isRuntimeBusy && !downloadState.isDownloading
        val status = when (runtimeModelState) {
            LlmModelState.Unloaded -> ManagedModelStatus.Unloaded
            LlmModelState.Loading -> ManagedModelStatus.Loading
            LlmModelState.Loaded -> ManagedModelStatus.Loaded
            LlmModelState.Missing -> ManagedModelStatus.MissingFiles
            LlmModelState.Error -> ManagedModelStatus.Error
        }

        return ModelCardState(
            type = ManagedModelType.Llm,
            status = status,
            expectedPath = inspection.expectedPath,
            activePath = inspection.activePath,
            exists = inspection.exists,
            message = actionMessage ?: llmStatusMessage(status, inspection),
            isDownloadAvailable = !downloadState.isDownloading &&
                runtimeModelState != LlmModelState.Loaded &&
                runtimeModelState != LlmModelState.Loading &&
                !inspection.exists,
            isDownloading = downloadState.isDownloading,
            downloadedBytes = downloadState.downloadedBytes,
            totalBytes = downloadState.totalBytes,
            downloadProgressPercent = downloadState.progressPercent,
            downloadMessage = downloadState.downloadMessage,
            canCancelDownload = downloadState.canCancelDownload,
            canDelete = inspection.exists && canMutate,
            canLoad = false,
            canUnload = false,
            isBusy = isRuntimeBusy || downloadState.isDownloading
        )
    }

    private fun buildTtsCardState(
        inspection: TtsModelInspection,
        playbackState: TtsPlaybackState,
        downloadState: DownloadUiState,
        actionMessage: String?
    ): ModelCardState {
        val exists = inspection.fileStatus == ModelFileStatus.Complete ||
            inspection.fileStatus == ModelFileStatus.Partial
        val isSpeaking = playbackState is TtsPlaybackState.Preparing ||
            playbackState is TtsPlaybackState.Speaking
        val status = when (inspection.fileStatus) {
            ModelFileStatus.Complete -> if (isSpeaking) {
                ManagedModelStatus.Loading
            } else {
                ManagedModelStatus.Loaded
            }
            ModelFileStatus.Partial -> ManagedModelStatus.UnsupportedFiles
            ModelFileStatus.Missing -> ManagedModelStatus.MissingFiles
            ModelFileStatus.Unsupported -> ManagedModelStatus.UnsupportedFiles
        }

        return ModelCardState(
            type = ManagedModelType.Tts,
            status = status,
            expectedPath = inspection.expectedPath,
            activePath = inspection.activePath,
            exists = exists,
            message = actionMessage ?: ttsStatusMessage(
                inspection = inspection,
                playbackState = playbackState
            ),
            isDownloadAvailable = !downloadState.isDownloading &&
                inspection.fileStatus != ModelFileStatus.Complete,
            isDownloading = downloadState.isDownloading,
            downloadedBytes = downloadState.downloadedBytes,
            totalBytes = downloadState.totalBytes,
            downloadProgressPercent = downloadState.progressPercent,
            downloadMessage = downloadState.downloadMessage,
            canCancelDownload = downloadState.canCancelDownload,
            canDelete = exists && !downloadState.isDownloading && !isSpeaking,
            canLoad = false,
            canUnload = false,
            isBusy = downloadState.isDownloading || isSpeaking
        )
    }

    private fun mapStatus(
        runtimeModelState: AsrModelState,
        fileStatus: ModelFileStatus
    ): ManagedModelStatus {
        return when (runtimeModelState) {
            AsrModelState.Loading -> ManagedModelStatus.Loading
            AsrModelState.Loaded -> ManagedModelStatus.Loaded
            AsrModelState.Error -> ManagedModelStatus.Error
            AsrModelState.Unloaded,
            AsrModelState.Missing -> when (fileStatus) {
                ModelFileStatus.Complete -> ManagedModelStatus.Unloaded
                ModelFileStatus.Partial,
                ModelFileStatus.Unsupported -> ManagedModelStatus.UnsupportedFiles
                ModelFileStatus.Missing -> ManagedModelStatus.MissingFiles
            }
        }
    }

    private fun controlsAvailability(
        runtimeModelState: AsrModelState,
        recognitionState: RecognitionState
    ): ControlsAvailability {
        val isBusy = runtimeModelState == AsrModelState.Loading
        val isStreaming = recognitionState == RecognitionState.Streaming ||
            recognitionState == RecognitionState.Finalizing
        val canMutate = !isBusy && !isStreaming

        return ControlsAvailability(
            canLoad = canMutate && runtimeModelState != AsrModelState.Loaded,
            canUnload = canMutate && runtimeModelState == AsrModelState.Loaded,
            canMutate = canMutate,
            isBusy = isBusy
        )
    }

    private fun asrStatusMessage(
        status: ManagedModelStatus,
        inspection: AsrModelInspection,
        vadExists: Boolean,
        vadRuntimeModelState: AsrModelState
    ): String? {
        if (!vadExists) {
            return "VAD file is missing. ASR listening is disabled until VAD is downloaded."
        }
        if (vadRuntimeModelState == AsrModelState.Error) {
            return "VAD file is unsupported or failed to load. Delete it and download the supported Silero VAD again."
        }

        return when (status) {
            ManagedModelStatus.MissingFiles ->
                "ASR files are missing. Download the model on the Models tab."
            ManagedModelStatus.UnsupportedFiles -> {
                val missing = inspection.missingFiles.joinToString(", ")
                "ASR directory contains unsupported partial set. Missing: $missing"
            }
            ManagedModelStatus.Error ->
                "ASR runtime initialization failed. You can retry."
            else -> null
        }
    }

    private fun vadStatusMessage(status: ManagedModelStatus): String? {
        return when (status) {
            ManagedModelStatus.MissingFiles ->
                "VAD file is missing. ASR listening is disabled until VAD is downloaded."
            ManagedModelStatus.Error ->
                "VAD file is unsupported or failed to load. Delete it and download the supported Silero VAD again."
            ManagedModelStatus.Unloaded ->
                "VAD file is available and will be used when ASR runtime is loaded."
            ManagedModelStatus.Loading,
            ManagedModelStatus.Loaded,
            ManagedModelStatus.UnsupportedFiles -> null
        }
    }

    private fun llmStatusMessage(
        status: ManagedModelStatus,
        inspection: LlmModelInspection
    ): String? {
        val modelPath = inspection.activePath ?: inspection.expectedPath
        return when (status) {
            ManagedModelStatus.MissingFiles ->
                "LLM file is missing. Expected: $modelPath"
            ManagedModelStatus.Error ->
                "LLM runtime initialization failed. You can retry."
            else -> null
        }
    }

    private fun ttsStatusMessage(
        inspection: TtsModelInspection,
        playbackState: TtsPlaybackState
    ): String? {
        if (playbackState is TtsPlaybackState.Preparing) {
            return "Preparing ${playbackState.voice.label()} voice."
        }

        if (playbackState is TtsPlaybackState.Speaking) {
            return "Speaking with ${playbackState.voice.label()} voice."
        }

        return when (inspection.fileStatus) {
            ModelFileStatus.Complete ->
                "Voice pack is ready. Select Dmitri or Irina in Settings."
            ModelFileStatus.Partial -> {
                val available = inspection.availableVoices.joinToString { it.label() }
                    .ifBlank { "none" }
                val missing = inspection.missingVoices.joinToString { it.label() }
                    .ifBlank { "none" }
                "Voice pack is incomplete. Available: $available. Missing: $missing."
            }
            ModelFileStatus.Missing ->
                "TTS voice pack is missing. Download it on the Models tab."
            ModelFileStatus.Unsupported ->
                "TTS voice pack is unsupported."
        }
    }

    private fun performAction(
        type: ManagedModelType,
        fallbackMessage: String,
        action: () -> Unit
    ) {
        runCatching(action).onFailure { throwable ->
            setActionMessage(type, fallbackMessage.withDetails(throwable))
            refresh()
        }
    }

    private fun remoteModelSpec(type: ManagedModelType): RemoteModelSpec? {
        return when (type) {
            ManagedModelType.Asr -> ModelDownloadCatalog.asrGigaAmV3Rnnt
            ManagedModelType.Llm -> ModelDownloadCatalog.llmGemma4E2BIt
            ManagedModelType.Vad -> ModelDownloadCatalog.vadSilero
            ManagedModelType.Tts -> ModelDownloadCatalog.ttsPiperRussianVoicePack
        }
    }

    private fun ensureAsrRuntimeReleased() {
        if (speechRecognitionController.modelState.value == AsrModelState.Loaded ||
            speechRecognitionController.modelState.value == AsrModelState.Loading ||
            speechRecognitionController.recognitionState.value == RecognitionState.Streaming ||
            speechRecognitionController.recognitionState.value == RecognitionState.Finalizing
        ) {
            throw IllegalStateException("ASR runtime is still busy.")
        }
    }

    private fun ensureLlmRuntimeReleased() {
        if (llmController.modelState.value == LlmModelState.Loaded ||
            llmController.modelState.value == LlmModelState.Loading ||
            llmController.generationState.value == LlmGenerationState.Generating
        ) {
            throw IllegalStateException("LLM runtime is still busy.")
        }
    }

    private fun ensureVadRuntimeReleased() {
        if (speechRecognitionController.modelState.value == AsrModelState.Loaded ||
            speechRecognitionController.modelState.value == AsrModelState.Loading ||
            speechRecognitionController.vadModelState.value == AsrModelState.Loading ||
            speechRecognitionController.recognitionState.value == RecognitionState.Streaming ||
            speechRecognitionController.recognitionState.value == RecognitionState.Finalizing
        ) {
            throw IllegalStateException("VAD runtime is still busy.")
        }
    }

    private fun llmCacheDirFor(modelPath: String): String {
        return java.io.File(modelPath).parentFile?.resolve("litertlm-cache")?.absolutePath.orEmpty()
    }

    private fun setDownloadState(
        type: ManagedModelType,
        state: DownloadUiState
    ) {
        downloadStates.value = downloadStates.value.toMutableMap().apply {
            this[type] = state
        }
    }

    private fun setActionMessage(type: ManagedModelType, message: String?) {
        actionMessages.value = actionMessages.value.toMutableMap().apply {
            this[type] = message
        }
    }

    private fun clearActionMessage(type: ManagedModelType) {
        setActionMessage(type, null)
    }

    private fun String.withDetails(throwable: Throwable): String {
        val details = throwable.message?.takeIf { it.isNotBlank() }
            ?: throwable.javaClass.simpleName
        return "$this $details"
    }

    private fun refresh() {
        refreshToken.value += 1
    }

    private fun fallbackAsrInspection(): AsrModelInspection {
        return AsrModelInspection(
            fileStatus = ModelFileStatus.Missing,
            expectedPath = asrModelLocator.expectedModelDirectoryPath(),
            activePath = null,
            candidatePaths = emptyList(),
            presentFiles = emptyList(),
            missingFiles = emptyList(),
            preferredExternalPath = null
        )
    }

    private fun fallbackVadInspection(): VadModelInspection {
        return VadModelInspection(
            fileStatus = ModelFileStatus.Missing,
            expectedPath = vadModelLocator.expectedModelFilePath(),
            activePath = null,
            candidatePaths = emptyList(),
            preferredExternalPath = null
        )
    }

    private fun fallbackLlmInspection(): LlmModelInspection {
        return LlmModelInspection(
            exists = false,
            expectedPath = llmModelLocator.expectedModelFilePath(),
            activePath = null,
            candidatePaths = emptyList(),
            preferredExternalPath = null
        )
    }

    private fun fallbackTtsInspection(): TtsModelInspection {
        return TtsModelInspection(
            fileStatus = ModelFileStatus.Missing,
            expectedPath = ttsModelLocator.expectedModelDirectoryPath(),
            activePath = null,
            candidatePaths = emptyList(),
            availableVoices = emptySet(),
            missingVoices = emptySet(),
            preferredExternalPath = null
        )
    }

    private data class DownloadUiState(
        val isDownloading: Boolean = false,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val progressPercent: Int? = null,
        val downloadMessage: String? = null,
        val canCancelDownload: Boolean = false
    )

    private data class ControlsAvailability(
        val canLoad: Boolean,
        val canUnload: Boolean,
        val canMutate: Boolean,
        val isBusy: Boolean
    )

    private data class RuntimeModelsState(
        val asrModelState: AsrModelState,
        val vadModelState: AsrModelState,
        val recognitionState: RecognitionState,
        val llmModelState: LlmModelState,
        val llmGenerationState: LlmGenerationState,
        val ttsPlaybackState: TtsPlaybackState
    )

    private data class AsrRuntimeModelsState(
        val asrModelState: AsrModelState,
        val vadModelState: AsrModelState,
        val recognitionState: RecognitionState
    )

    private data class LlmRuntimeModelsState(
        val llmModelState: LlmModelState,
        val llmGenerationState: LlmGenerationState
    )

    private fun com.example.aitoy.feature.tts.domain.TtsVoice.label(): String {
        return when (this) {
            com.example.aitoy.feature.tts.domain.TtsVoice.DmitriMedium -> "Dmitri"
            com.example.aitoy.feature.tts.domain.TtsVoice.IrinaMedium -> "Irina"
        }
    }

    class Factory(
        private val speechRecognitionController: SpeechRecognitionController,
        private val asrModelLocator: AsrModelLocator,
        private val vadModelLocator: VadModelLocator,
        private val llmController: LlmController,
        private val llmModelLocator: LlmModelLocator,
        private val ttsController: TtsController,
        private val ttsModelLocator: TtsModelLocator,
        private val modelDownloadRepository: ModelDownloadRepository,
        private val installedModelDeleteRepository: InstalledModelDeleteRepository,
        private val requestStartupRetry: () -> Unit
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ModelsViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return ModelsViewModel(
                speechRecognitionController = speechRecognitionController,
                asrModelLocator = asrModelLocator,
                vadModelLocator = vadModelLocator,
                llmController = llmController,
                llmModelLocator = llmModelLocator,
                ttsController = ttsController,
                ttsModelLocator = ttsModelLocator,
                modelDownloadRepository = modelDownloadRepository,
                installedModelDeleteRepository = installedModelDeleteRepository,
                requestStartupRetry = requestStartupRetry
            ) as T
        }
    }
}
