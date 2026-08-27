package com.example.aitoy.feature.models.presentation

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aitoy.R
import com.example.aitoy.ui.theme.YasinTheme
import java.io.File

@Composable
fun ModelsRoute(
    viewModel: ModelsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    ModelsScreen(
        uiState = uiState.value,
        onDownloadClick = viewModel::onDownloadClick,
        onCancelDownloadClick = viewModel::onCancelDownloadClick,
        onDeleteClick = viewModel::onDeleteClick,
        modifier = modifier
    )
}

@Composable
fun ModelsScreen(
    uiState: ModelsUiState,
    onDownloadClick: (ManagedModelType) -> Unit,
    onCancelDownloadClick: (ManagedModelType) -> Unit,
    onDeleteClick: (ManagedModelType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.models_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        ModelCard(
            state = uiState.asrCard,
            onDownloadClick = onDownloadClick,
            onCancelDownloadClick = onCancelDownloadClick,
            onDeleteClick = onDeleteClick
        )

        ModelCard(
            state = uiState.llmCard,
            onDownloadClick = onDownloadClick,
            onCancelDownloadClick = onCancelDownloadClick,
            onDeleteClick = onDeleteClick
        )

        ModelCard(
            state = uiState.vadCard,
            onDownloadClick = onDownloadClick,
            onCancelDownloadClick = onCancelDownloadClick,
            onDeleteClick = onDeleteClick
        )

        ModelCard(
            state = uiState.ttsCard,
            onDownloadClick = onDownloadClick,
            onCancelDownloadClick = onCancelDownloadClick,
            onDeleteClick = onDeleteClick
        )
    }
}

@Composable
private fun ModelCard(
    state: ModelCardState,
    onDownloadClick: (ManagedModelType) -> Unit,
    onCancelDownloadClick: (ManagedModelType) -> Unit,
    onDeleteClick: (ManagedModelType) -> Unit
) {
    val context = LocalContext.current
    val progress = state.downloadProgressPercent?.div(100f)
    val primaryAction = primaryActionFor(
        state = state,
        onDownloadClick = onDownloadClick,
        onCancelDownloadClick = onCancelDownloadClick
    )
    val secondaryActions = secondaryActionsFor(
        state = state,
        primaryAction = primaryAction,
        onDownloadClick = onDownloadClick,
        onCancelDownloadClick = onCancelDownloadClick,
        onDeleteClick = onDeleteClick
    )
    val statusLabel = stringResource(state.status.toLabelRes())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(state.type.titleRes()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                StatusPill(status = state.status, label = statusLabel)
            }

            CompactInfoRow(
                label = stringResource(R.string.models_detected_name_label),
                value = detectedModelName(state)
            )
            CompactInfoRow(
                label = stringResource(R.string.models_runtime_name_label),
                value = runtimeModelName(state)
            )
            PathBlock(path = directoryPathForDisplay(state.activePath ?: state.expectedPath))

            if (state.isDownloading) {
                DownloadProgressBlock(
                    progress = progress,
                    text = state.downloadMessage ?: downloadProgressText(state, context)
                )
            }

            val messages = buildList {
                if (!state.downloadMessage.isNullOrBlank() && !state.isDownloading) {
                    add(state.downloadMessage)
                }
                if (!state.message.isNullOrBlank() &&
                    state.message != state.downloadMessage
                ) {
                    add(state.message)
                }
            }
            messages.forEach { message ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            primaryAction?.let { action ->
                ModelActionButton(
                    action = action,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (secondaryActions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    secondaryActions.forEach { action ->
                        ModelActionButton(
                            action = action,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    status: ManagedModelStatus,
    label: String
) {
    val colors = when (status) {
        ManagedModelStatus.Loaded -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer
        ManagedModelStatus.Loading -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
        ManagedModelStatus.MissingFiles,
        ManagedModelStatus.UnsupportedFiles,
        ManagedModelStatus.Error -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer
        ManagedModelStatus.Unloaded -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = colors.first
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = colors.second,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CompactInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.38f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.62f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PathBlock(path: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.models_path_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = path,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DownloadProgressBlock(
    progress: Float?,
    text: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun ModelActionButton(
    action: ModelActionSpec,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    when (action.style) {
        ModelActionStyle.Primary -> {
            Button(
                onClick = action.onClick,
                enabled = action.enabled,
                modifier = modifier
            ) {
                Text(text = stringResource(action.labelRes))
            }
        }
        ModelActionStyle.Tonal -> {
            FilledTonalButton(
                onClick = action.onClick,
                enabled = action.enabled,
                modifier = modifier,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isDarkTheme) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    },
                    contentColor = if (isDarkTheme) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                Text(text = stringResource(action.labelRes))
            }
        }
        ModelActionStyle.Outline -> {
            OutlinedButton(
                onClick = action.onClick,
                enabled = action.enabled,
                modifier = modifier,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.25.dp,
                    color = if (isDarkTheme) {
                        MaterialTheme.colorScheme.outline
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
                    }
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isDarkTheme) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                Text(text = stringResource(action.labelRes))
            }
        }
        ModelActionStyle.Danger -> {
            Button(
                onClick = action.onClick,
                enabled = action.enabled,
                modifier = modifier,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDarkTheme) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    contentColor = if (isDarkTheme) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onError
                    }
                )
            ) {
                Text(text = stringResource(action.labelRes))
            }
        }
    }
}

@Composable
private fun runtimeModelName(state: ModelCardState): String {
    return when (state.type) {
        ManagedModelType.Asr -> {
            if (state.status == ManagedModelStatus.Loaded) {
                state.activePath?.let(::modelNameFromPath)
                    ?: stringResource(R.string.models_runtime_not_loaded)
            } else {
                stringResource(R.string.models_runtime_not_loaded)
            }
        }
        ManagedModelType.Llm -> {
            if (state.status == ManagedModelStatus.Loaded) {
                state.activePath?.let(::modelNameFromPath)
                    ?: stringResource(R.string.models_runtime_not_loaded)
            } else {
                stringResource(R.string.models_runtime_not_loaded)
            }
        }
        ManagedModelType.Vad -> stringResource(R.string.models_runtime_managed_by_asr)
        ManagedModelType.Tts -> stringResource(R.string.models_runtime_managed_by_settings)
    }
}

@Composable
private fun detectedModelName(state: ModelCardState): String {
    val path = state.activePath
    return if (path.isNullOrBlank()) {
        stringResource(R.string.models_name_unavailable)
    } else {
        modelNameFromPath(path)
    }
}

private fun modelNameFromPath(path: String): String {
    val file = File(path)
    return if (file.extension.isNotEmpty()) {
        file.name
    } else {
        file.name.ifBlank { path }
    }
}

private fun directoryPathForDisplay(expectedPath: String): String {
    val pathFile = File(expectedPath)
    return if (pathFile.extension.isNotEmpty()) {
        pathFile.parent ?: expectedPath
    } else {
        expectedPath
    }
}

@Composable
private fun downloadProgressText(
    state: ModelCardState,
    context: android.content.Context
): String {
    val totalBytes = state.totalBytes
    val progressPercent = state.downloadProgressPercent

    if (totalBytes <= 0L || progressPercent == null) {
        return stringResource(R.string.models_download_preparing)
    }

    val downloaded = Formatter.formatShortFileSize(context, state.downloadedBytes)
    val total = Formatter.formatShortFileSize(context, totalBytes)
    return stringResource(
        R.string.models_download_in_progress,
        downloaded,
        total,
        progressPercent
    )
}

private fun ManagedModelStatus.toLabelRes(): Int {
    return when (this) {
        ManagedModelStatus.Unloaded -> R.string.models_status_unloaded
        ManagedModelStatus.Loading -> R.string.models_status_loading
        ManagedModelStatus.Loaded -> R.string.models_status_loaded
        ManagedModelStatus.MissingFiles -> R.string.models_status_missing
        ManagedModelStatus.UnsupportedFiles -> R.string.models_status_unsupported
        ManagedModelStatus.Error -> R.string.models_status_error
    }
}

private fun ManagedModelType.titleRes(): Int {
    return when (this) {
        ManagedModelType.Asr -> R.string.asr_model_title
        ManagedModelType.Llm -> R.string.llm_model_title
        ManagedModelType.Vad -> R.string.vad_model_title
        ManagedModelType.Tts -> R.string.tts_model_title
    }
}

private enum class ModelActionId {
    Download,
    CancelDownload,
    Delete
}

private enum class ModelActionStyle {
    Primary,
    Tonal,
    Outline,
    Danger
}

private data class ModelActionSpec(
    val id: ModelActionId,
    val labelRes: Int,
    val enabled: Boolean,
    val style: ModelActionStyle,
    val onClick: () -> Unit
)

private fun primaryActionFor(
    state: ModelCardState,
    onDownloadClick: (ManagedModelType) -> Unit,
    onCancelDownloadClick: (ManagedModelType) -> Unit
): ModelActionSpec? {
    return when {
        state.isDownloading -> ModelActionSpec(
            id = ModelActionId.CancelDownload,
            labelRes = R.string.models_action_cancel_download,
            enabled = state.canCancelDownload,
            style = ModelActionStyle.Danger,
            onClick = { onCancelDownloadClick(state.type) }
        )
        state.isDownloadAvailable -> ModelActionSpec(
            id = ModelActionId.Download,
            labelRes = R.string.models_action_download,
            enabled = !state.isBusy,
            style = ModelActionStyle.Primary,
            onClick = { onDownloadClick(state.type) }
        )
        else -> null
    }
}

private fun secondaryActionsFor(
    state: ModelCardState,
    primaryAction: ModelActionSpec?,
    onDownloadClick: (ManagedModelType) -> Unit,
    onCancelDownloadClick: (ManagedModelType) -> Unit,
    onDeleteClick: (ManagedModelType) -> Unit
): List<ModelActionSpec> {
    return buildList {
        if (showSecondaryDownloadAction(state, primaryAction)) {
            add(
                ModelActionSpec(
                    id = ModelActionId.Download,
                    labelRes = R.string.models_action_download,
                    enabled = !state.isBusy,
                    style = ModelActionStyle.Outline,
                    onClick = { onDownloadClick(state.type) }
                )
            )
        }
        if (state.canCancelDownload && primaryAction?.id != ModelActionId.CancelDownload) {
            add(
                ModelActionSpec(
                    id = ModelActionId.CancelDownload,
                    labelRes = R.string.models_action_cancel_download,
                    enabled = true,
                    style = ModelActionStyle.Outline,
                    onClick = { onCancelDownloadClick(state.type) }
                )
            )
        }
        if (state.canDelete) {
            add(
                ModelActionSpec(
                    id = ModelActionId.Delete,
                    labelRes = R.string.models_action_delete,
                    enabled = !state.isBusy,
                    style = ModelActionStyle.Outline,
                    onClick = { onDeleteClick(state.type) }
                )
            )
        }
    }
}

private fun showSecondaryDownloadAction(
    state: ModelCardState,
    primaryAction: ModelActionSpec?
): Boolean {
    return (state.isDownloadAvailable || state.isDownloading) &&
        primaryAction?.id != ModelActionId.Download &&
        primaryAction?.id != ModelActionId.CancelDownload
}

@Preview(showBackground = true)
@Composable
private fun ModelsScreenPreview() {
    YasinTheme {
        ModelsScreen(
            uiState = ModelsUiState(
                asrCard = ModelCardState(
                    type = ManagedModelType.Asr,
                    status = ManagedModelStatus.UnsupportedFiles,
                    expectedPath = "models/asr/gigaam_v3_rnnt",
                    activePath = "models/asr/gigaam_v3_rnnt",
                    exists = true,
                    message = "ASR directory contains unsupported partial set.",
                    isDownloadAvailable = true,
                    canDelete = true,
                    canLoad = false,
                    canUnload = false
                ),
                llmCard = ModelCardState(
                    type = ManagedModelType.Llm,
                    status = ManagedModelStatus.Loaded,
                    expectedPath = "models/llm/gemma-4-e2b-it/gemma-4-E2B-it.litertlm",
                    activePath = "models/llm/gemma-4-e2b-it/gemma-4-E2B-it.litertlm",
                    exists = true,
                    canLoad = false,
                    canDelete = true,
                    canUnload = true
                ),
                vadCard = ModelCardState(
                    type = ManagedModelType.Vad,
                    status = ManagedModelStatus.Unloaded,
                    expectedPath = "models/vad/silero_vad.onnx",
                    exists = false,
                    isDownloading = true,
                    downloadedBytes = 50L * 1024L * 1024L,
                    totalBytes = 225L * 1024L * 1024L,
                    downloadProgressPercent = 22,
                    canCancelDownload = true,
                    canDelete = false,
                    canLoad = false,
                    canUnload = false
                ),
                ttsCard = ModelCardState(
                    type = ManagedModelType.Tts,
                    status = ManagedModelStatus.Loaded,
                    expectedPath = "models/tts/piper-voices",
                    activePath = "models/tts/piper-voices",
                    exists = true,
                    message = "Voice pack is ready. Select Dmitri or Irina in Settings.",
                    canDelete = true,
                    canLoad = false,
                    canUnload = false
                )
            ),
            onDownloadClick = {},
            onCancelDownloadClick = {},
            onDeleteClick = {},
        )
    }
}
