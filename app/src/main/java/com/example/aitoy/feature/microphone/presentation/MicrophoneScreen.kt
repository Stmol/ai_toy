package com.example.aitoy.feature.microphone.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aitoy.R
import com.example.aitoy.feature.asr.domain.AsrModelState
import com.example.aitoy.feature.asr.domain.RecognitionState
import com.example.aitoy.feature.microphone.domain.CaptureState
import com.example.aitoy.feature.microphone.domain.PermissionState
import com.example.aitoy.feature.microphone.domain.VoiceTranscriptHistoryEntry
import com.example.aitoy.ui.components.ErrorMessageBlock
import com.example.aitoy.ui.components.LoadingMessageBlock
import com.example.aitoy.ui.theme.YasinTheme
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MicrophoneRoute(
    viewModel: MicrophoneViewModel,
    hasMicrophonePermission: () -> Boolean,
    onOpenEyesDemoClick: () -> Unit,
    onRequestMicrophonePermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (hasMicrophonePermission()) {
            viewModel.onPermissionResult(granted = true, permanentlyDenied = false)
        }
    }

    MicrophoneScreen(
        uiState = uiState.value,
        onStartClick = {
            if (hasMicrophonePermission()) {
                viewModel.onPermissionResult(granted = true, permanentlyDenied = false)
                viewModel.onStartClick()
            } else {
                onRequestMicrophonePermission()
            }
        },
        onStopClick = viewModel::onStopClick,
        onOpenEyesDemoClick = onOpenEyesDemoClick,
        onCloseTranscriptSheet = viewModel::onCloseTranscriptSheet,
        onHistoryEntryClick = viewModel::onHistoryEntryClick,
        onCloseHistoryEntrySheet = viewModel::onCloseHistoryEntrySheet,
        onDeleteHistoryEntry = viewModel::onDeleteHistoryEntry,
        modifier = modifier
    )
}

@Composable
fun MicrophoneScreen(
    uiState: MicrophoneUiState,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onOpenEyesDemoClick: () -> Unit,
    onCloseTranscriptSheet: () -> Unit,
    onHistoryEntryClick: (String) -> Unit,
    onCloseHistoryEntrySheet: () -> Unit,
    onDeleteHistoryEntry: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val transcriptText = uiState.finalText
        ?.takeIf { it.isNotBlank() }
        ?: uiState.partialText
    val isModelReady = uiState.modelState == AsrModelState.Loaded

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(18.dp))
        }

        item {
            Text(
                text = stringResource(R.string.microphone_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
        }

        item {
            OutlinedButton(
                onClick = onOpenEyesDemoClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.open_eyes_demo))
            }
        }

        item {
            if (!isModelReady) {
                BlockedMicrophoneCard(
                    uiState = uiState,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                RecordingControlCard(
                    uiState = uiState,
                    onStartClick = onStartClick,
                    onStopClick = onStopClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.saved_transcripts_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
        }

        if (uiState.historyEntries.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.saved_transcripts_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(
                items = uiState.historyEntries,
                key = VoiceTranscriptHistoryEntry::id
            ) { entry ->
                SwipeRevealTranscriptHistoryCard(
                    entry = entry,
                    onClick = { onHistoryEntryClick(entry.id) },
                    onDeleteClick = { onDeleteHistoryEntry(entry.id) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (uiState.isTranscriptSheetVisible) {
        LiveTranscriptBottomSheet(
            transcriptText = transcriptText,
            recognitionState = uiState.recognitionState,
            audioLevel = uiState.audioLevel,
            canStop = uiState.canStop,
            canClose = uiState.canCloseTranscriptSheet,
            onStopClick = onStopClick,
            onCloseClick = onCloseTranscriptSheet
        )
    }

    uiState.selectedHistoryEntry?.let { selectedEntry ->
        SavedTranscriptBottomSheet(
            entry = selectedEntry,
            onCloseClick = onCloseHistoryEntrySheet
        )
    }
}

@Composable
private fun BlockedMicrophoneCard(
    uiState: MicrophoneUiState,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(
                    if (uiState.modelState == AsrModelState.Loading) {
                        R.string.asr_model_loading_message
                    } else {
                        R.string.asr_model_required_message
                    }
                )
            )
            if (uiState.modelState == AsrModelState.Loading) {
                LoadingMessageBlock(message = stringResource(R.string.model_loading_hint))
            }
            if (uiState.recognitionErrorMessage != null) {
                ErrorMessageBlock(
                    message = uiState.recognitionErrorMessage
                )
            }
        }
    }
}

@Composable
private fun RecordingControlCard(
    uiState: MicrophoneUiState,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.audio_level_label_with_value,
                    formatAudioLevel(uiState.audioLevel)
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            AudioLevelBar(
                progress = uiState.audioLevel.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth()
            )
            if (uiState.isBusy) {
                LoadingMessageBlock(message = stringResource(R.string.recognition_finalizing_hint))
            }
            if (uiState.permissionState == PermissionState.PermanentlyDenied) {
                ErrorMessageBlock(message = stringResource(R.string.permanently_denied_hint))
            }
            if (uiState.errorMessage != null) {
                ErrorMessageBlock(message = uiState.errorMessage)
            }
            if (uiState.recognitionErrorMessage != null) {
                ErrorMessageBlock(message = uiState.recognitionErrorMessage)
            }
            if (uiState.showStartButton) {
                if (uiState.isStreamingActive) {
                    Button(
                        onClick = onStopClick,
                        enabled = uiState.canStop,
                        modifier = Modifier.fillMaxWidth(),
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
                        Text(text = stringResource(R.string.stop_listening))
                    }
                } else {
                    Button(
                        onClick = onStartClick,
                        enabled = uiState.canStart,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.start_listening))
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioLevelBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp
) {
    val boundedProgress = progress.coerceIn(0f, 1f)
    val shape = MaterialTheme.shapes.small
    val isDarkTheme = isSystemInDarkTheme()

    Box(
        modifier = modifier
            .border(
                width = 1.dp,
                color = if (isDarkTheme) {
                    MaterialTheme.colorScheme.outlineVariant
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                },
                shape = shape
            )
            .clip(shape)
            .background(
                if (isDarkTheme) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
        )
        if (boundedProgress > 0f) {
            Box(
                modifier = Modifier
                .fillMaxWidth(boundedProgress)
                .height(height)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun SwipeRevealTranscriptHistoryCard(
    entry: VoiceTranscriptHistoryEntry,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deleteButtonWidth = 108.dp
    val revealPadding = 8.dp
    val revealWidth = deleteButtonWidth + (revealPadding * 2)
    val revealWidthPx = with(LocalDensity.current) {
        revealWidth.toPx()
    }
    var dragOffsetPx by remember(entry.id) { mutableFloatStateOf(0f) }
    val animatedOffsetPx by animateFloatAsState(
        targetValue = dragOffsetPx,
        label = "transcriptHistoryOffset"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(108.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(width = revealWidth, height = 108.dp)
                .fillMaxHeight()
                .padding(revealPadding)
        ) {
            Button(
                onClick = onDeleteClick,
                modifier = Modifier
                    .fillMaxHeight()
                    .size(width = deleteButtonWidth, height = 92.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(text = stringResource(R.string.saved_transcript_delete))
            }
        }

        Card(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(animatedOffsetPx.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        dragOffsetPx = (dragOffsetPx + delta).coerceIn(-revealWidthPx, 0f)
                    },
                    onDragStopped = {
                        dragOffsetPx = if (dragOffsetPx <= -revealWidthPx * 0.45f) {
                            -revealWidthPx
                        } else {
                            0f
                        }
                    }
                )
                .clickable {
                    if (dragOffsetPx < 0f) {
                        dragOffsetPx = 0f
                    } else {
                        onClick()
                    }
                },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 4.dp
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveTranscriptBottomSheet(
    transcriptText: String,
    recognitionState: RecognitionState,
    audioLevel: Float,
    canStop: Boolean,
    canClose: Boolean,
    onStopClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { targetValue ->
            if (targetValue == SheetValue.Hidden) {
                onCloseClick()
                false
            } else {
                true
            }
        }
    )

    ModalBottomSheet(
        modifier = Modifier.fillMaxSize(),
        sheetState = sheetState,
        onDismissRequest = onCloseClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.live_transcript_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (recognitionState == RecognitionState.Streaming) {
                Text(
                    text = stringResource(
                        R.string.audio_level_label_with_value,
                        formatAudioLevel(audioLevel)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                AudioLevelBar(
                    progress = audioLevel,
                    modifier = Modifier.fillMaxWidth(),
                    height = 12.dp
                )
            } else {
                Text(
                    text = stringResource(recognitionState.toLabelRes()),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = transcriptText.ifBlank { stringResource(R.string.live_transcript_empty) },
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            )
            Spacer(modifier = Modifier.height(24.dp))

            when {
                recognitionState == RecognitionState.Streaming -> {
                    Button(
                        onClick = onStopClick,
                        enabled = canStop,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.stop_listening))
                    }
                }

                recognitionState == RecognitionState.Finalizing -> {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.recognition_finalizing_hint))
                    }
                }

                else -> {
                    Button(
                        onClick = onCloseClick,
                        enabled = canClose,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.close_recognized_text))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedTranscriptBottomSheet(
    entry: VoiceTranscriptHistoryEntry,
    onCloseClick: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        modifier = Modifier.fillMaxSize(),
        sheetState = sheetState,
        onDismissRequest = onCloseClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.saved_transcript_sheet_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = entry.text,
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onCloseClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.close_recognized_text))
            }
        }
    }
}

private fun RecognitionState.toLabelRes(): Int {
    return when (this) {
        RecognitionState.Idle -> R.string.recognition_idle
        RecognitionState.Streaming -> R.string.recognition_streaming
        RecognitionState.Finalizing -> R.string.recognition_finalizing
        RecognitionState.Completed -> R.string.recognition_completed
        RecognitionState.ModelMissing -> R.string.recognition_model_missing
        RecognitionState.Error -> R.string.recognition_error
    }
}

private fun formatAudioLevel(audioLevel: Float): String {
    return String.format(Locale.US, "%.0f", audioLevel.coerceIn(0f, 1f) * 100f)
}

@Preview(showBackground = true)
@Composable
private fun MicrophoneScreenPreview() {
    YasinTheme {
        MicrophoneScreen(
            uiState = MicrophoneUiState(
                permissionState = PermissionState.Granted,
                modelState = AsrModelState.Loaded,
                modelName = "gigaam_v3_rnnt",
                captureState = CaptureState.Listening,
                recognitionState = RecognitionState.Streaming,
                audioLevel = 0.42f,
                partialText = "Privet mir eto test",
                finalText = null,
                isTranscriptSheetVisible = true,
                historyEntries = listOf(
                    VoiceTranscriptHistoryEntry(
                        id = "1",
                        text = "Pervoe zapisannoe soobshchenie s bolee dlinnym tekstom dlya proverki obrezki na tri stroki v spiske.",
                        createdAtEpochMillis = 1L
                    ),
                    VoiceTranscriptHistoryEntry(
                        id = "2",
                        text = "Vtoroe soobshchenie",
                        createdAtEpochMillis = 2L
                    )
                ),
                isStreamingActive = true,
                showStartButton = true,
                errorMessage = null,
                recognitionErrorMessage = null,
                isBusy = false,
                canStart = false,
                canStop = true,
                canCloseTranscriptSheet = false
            ),
            onStartClick = {},
            onStopClick = {},
            onOpenEyesDemoClick = {},
            onCloseTranscriptSheet = {},
            onHistoryEntryClick = {},
            onCloseHistoryEntrySheet = {},
            onDeleteHistoryEntry = {}
        )
    }
}
