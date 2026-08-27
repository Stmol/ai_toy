package com.example.aitoy.feature.llm.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aitoy.R
import com.example.aitoy.feature.asr.domain.AsrModelState
import com.example.aitoy.feature.llm.domain.LlmBackend
import com.example.aitoy.feature.llm.domain.LlmGenerationState
import com.example.aitoy.feature.llm.domain.LlmHistoryEntry
import com.example.aitoy.feature.llm.domain.LlmModelState
import com.example.aitoy.ui.components.ErrorMessageBlock
import com.example.aitoy.ui.components.LoadingMessageBlock
import com.example.aitoy.ui.theme.YasinTheme
import java.util.Locale

@Composable
fun LlmRoute(
    viewModel: LlmViewModel,
    hasMicrophonePermission: () -> Boolean,
    onRequestMicrophonePermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (hasMicrophonePermission()) {
            viewModel.onVoicePromptPermissionResult(granted = true, permanentlyDenied = false)
        }
    }

    LlmScreen(
        uiState = uiState.value,
        onPromptChanged = viewModel::onPromptChanged,
        onClearPromptClick = viewModel::onClearPromptClick,
        onBackendSelected = viewModel::onBackendSelected,
        onGenerateClick = viewModel::onGenerateClick,
        onSpeakResponseClick = {
            if (uiState.value.canStopSpeaking) {
                viewModel.onStopSpeakingClick()
            } else {
                viewModel.onSpeakResponseClick()
            }
        },
        onVoicePromptClick = {
            if (uiState.value.canStopVoicePrompt) {
                viewModel.onVoicePromptStopClick()
            } else if (hasMicrophonePermission()) {
                viewModel.onVoicePromptPermissionResult(granted = true, permanentlyDenied = false)
                viewModel.onVoicePromptStartClick()
            } else {
                onRequestMicrophonePermission()
            }
        },
        onSystemPromptChanged = viewModel::onSystemPromptChanged,
        onClearSystemPromptClick = viewModel::onClearSystemPromptClick,
        onTemperatureChanged = viewModel::onTemperatureChanged,
        onTopKChanged = viewModel::onTopKChanged,
        onTopPChanged = viewModel::onTopPChanged,
        onSeedChanged = viewModel::onSeedChanged,
        onMaxTokensChanged = viewModel::onMaxTokensChanged,
        onApplySettingsClick = viewModel::onApplySettingsClick,
        onResetSettingsClick = viewModel::onResetSettingsClick,
        modifier = modifier
    )
}

@Composable
fun LlmScreen(
    uiState: LlmUiState,
    onPromptChanged: (String) -> Unit,
    onClearPromptClick: () -> Unit,
    onBackendSelected: (LlmBackend) -> Unit,
    onGenerateClick: () -> Unit,
    onSpeakResponseClick: () -> Unit,
    onVoicePromptClick: () -> Unit,
    onSystemPromptChanged: (String) -> Unit,
    onClearSystemPromptClick: () -> Unit,
    onTemperatureChanged: (String) -> Unit,
    onTopKChanged: (String) -> Unit,
    onTopPChanged: (String) -> Unit,
    onSeedChanged: (String) -> Unit,
    onMaxTokensChanged: (String) -> Unit,
    onApplySettingsClick: () -> Unit,
    onResetSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val isModelReady = uiState.modelState == LlmModelState.Loaded
    var isHistorySheetVisible by rememberSaveable { mutableStateOf(false) }
    var selectedSection by rememberSaveable { mutableStateOf(LlmSection.Chat) }

    LaunchedEffect(isModelReady, selectedSection) {
        if (!isModelReady && selectedSection == LlmSection.Settings) {
            selectedSection = LlmSection.Chat
        }
    }

    fun submitPrompt() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onGenerateClick()
    }

    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.llm_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        LlmSectionSelector(
            selectedSection = selectedSection,
            isSettingsEnabled = isModelReady,
            onSectionSelected = { selectedSection = it },
            modifier = Modifier.fillMaxWidth()
        )

        if (selectedSection == LlmSection.Chat) {
            if (!isModelReady) {
                BlockedLlmCard(
                    uiState = uiState,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                PromptComposerCard(
                    uiState = uiState,
                    onPromptChanged = onPromptChanged,
                    onClearPromptClick = onClearPromptClick,
                    onGenerateClick = ::submitPrompt,
                    onVoicePromptClick = onVoicePromptClick,
                    modifier = Modifier.fillMaxWidth()
                )
                HistoryActionLine(
                    count = uiState.historyEntries.size,
                    onClick = { isHistorySheetVisible = true }
                )
                ResponseCard(
                    uiState = uiState,
                    onSpeakResponseClick = onSpeakResponseClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            BackendSelectorCard(
                selectedBackend = uiState.selectedBackend,
                enabled = uiState.canChangeBackend,
                onBackendSelected = onBackendSelected,
                modifier = Modifier.fillMaxWidth()
            )
            SettingsCard(
                uiState = uiState,
                onSystemPromptChanged = onSystemPromptChanged,
                onClearSystemPromptClick = onClearSystemPromptClick,
                onTemperatureChanged = onTemperatureChanged,
                onTopKChanged = onTopKChanged,
                onTopPChanged = onTopPChanged,
                onSeedChanged = onSeedChanged,
                onMaxTokensChanged = onMaxTokensChanged,
                onApplySettingsClick = onApplySettingsClick,
                onResetSettingsClick = onResetSettingsClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (isHistorySheetVisible) {
        LlmHistoryBottomSheet(
            historyEntries = uiState.historyEntries,
            onDismissRequest = { isHistorySheetVisible = false }
        )
    }
}

@Composable
private fun LlmSectionSelector(
    selectedSection: LlmSection,
    isSettingsEnabled: Boolean,
    onSectionSelected: (LlmSection) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionButton(
                title = stringResource(R.string.llm_section_chat),
                selected = selectedSection == LlmSection.Chat,
                enabled = true,
                onClick = { onSectionSelected(LlmSection.Chat) },
                modifier = Modifier.weight(1f)
            )
            SectionButton(
                title = stringResource(R.string.llm_section_settings),
                selected = selectedSection == LlmSection.Settings,
                enabled = isSettingsEnabled,
                onClick = { onSectionSelected(LlmSection.Settings) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SectionButton(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
        ) {
            Text(text = title)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
        ) {
            Text(text = title)
        }
    }
}

@Composable
private fun BackendSelectorCard(
    selectedBackend: LlmBackend,
    enabled: Boolean,
    onBackendSelected: (LlmBackend) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.llm_selected_backend_label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BackendButton(
                    backend = LlmBackend.CPU,
                    selectedBackend = selectedBackend,
                    enabled = enabled,
                    onBackendSelected = onBackendSelected,
                    modifier = Modifier.weight(1f)
                )
                BackendButton(
                    backend = LlmBackend.GPU,
                    selectedBackend = selectedBackend,
                    enabled = enabled,
                    onBackendSelected = onBackendSelected,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BackendButton(
    backend: LlmBackend,
    selectedBackend: LlmBackend,
    enabled: Boolean,
    onBackendSelected: (LlmBackend) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = backend == selectedBackend
    if (isSelected) {
        Button(
            onClick = { onBackendSelected(backend) },
            enabled = enabled,
            modifier = modifier
        ) {
            Text(text = backend.name)
        }
    } else {
        OutlinedButton(
            onClick = { onBackendSelected(backend) },
            enabled = enabled,
            modifier = modifier
        ) {
            Text(text = backend.name)
        }
    }
}

@Composable
private fun SettingsCard(
    uiState: LlmUiState,
    onSystemPromptChanged: (String) -> Unit,
    onClearSystemPromptClick: () -> Unit,
    onTemperatureChanged: (String) -> Unit,
    onTopKChanged: (String) -> Unit,
    onTopPChanged: (String) -> Unit,
    onSeedChanged: (String) -> Unit,
    onMaxTokensChanged: (String) -> Unit,
    onApplySettingsClick: () -> Unit,
    onResetSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val temperatureValue = uiState.temperatureText.toFloatOrNull()
        ?.coerceIn(0f, 2f)
        ?: uiState.appliedSettings.temperature.toFloat().coerceIn(0f, 2f)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.llm_settings_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.llm_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = uiState.systemPromptDraft,
                onValueChange = onSystemPromptChanged,
                label = { Text(text = stringResource(R.string.llm_setting_system_prompt)) },
                placeholder = { Text(text = stringResource(R.string.llm_setting_system_prompt_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                shape = RoundedCornerShape(18.dp)
            )

            OutlinedButton(
                onClick = onClearSystemPromptClick,
                enabled = uiState.systemPromptDraft.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.llm_setting_system_prompt_clear))
            }

            TemperatureSliderField(
                value = temperatureValue,
                onValueChange = { value ->
                    onTemperatureChanged(String.format(Locale.US, "%.2f", value))
                },
                modifier = Modifier.fillMaxWidth()
            )

            SettingsNumberField(
                value = uiState.topKText,
                label = stringResource(R.string.llm_setting_top_k),
                onValueChange = onTopKChanged,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth()
            )

            SettingsNumberField(
                value = uiState.topPText,
                label = stringResource(R.string.llm_setting_top_p),
                onValueChange = onTopPChanged,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth()
            )

            SettingsNumberField(
                value = uiState.seedText,
                label = stringResource(R.string.llm_setting_seed),
                onValueChange = onSeedChanged,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth()
            )

            SettingsNumberField(
                value = uiState.maxTokensText,
                onValueChange = onMaxTokensChanged,
                label = stringResource(R.string.llm_setting_max_tokens),
                supportingText = {
                    Text(text = stringResource(R.string.llm_setting_max_tokens_hint))
                },
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onResetSettingsClick,
                    enabled = uiState.canResetSettings,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.llm_settings_reset))
                }
                Button(
                    onClick = onApplySettingsClick,
                    enabled = uiState.canApplySettings,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.llm_settings_apply))
                }
            }
        }
    }
}

@Composable
private fun TemperatureSliderField(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.llm_setting_temperature),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = String.format(Locale.US, "%.2f", value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..2f
        )
    }
}

@Composable
private fun SettingsNumberField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    supportingText: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        supportingText = supportingText
    )
}

@Composable
private fun PromptComposerCard(
    uiState: LlmUiState,
    onPromptChanged: (String) -> Unit,
    onClearPromptClick: () -> Unit,
    onGenerateClick: () -> Unit,
    onVoicePromptClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = uiState.promptText,
                onValueChange = { value ->
                    val sanitizedValue = value.replace("\n", "")
                    if (sanitizedValue != uiState.promptText) {
                        onPromptChanged(sanitizedValue)
                    }
                    if (value.contains('\n') && uiState.canGenerate) {
                        onGenerateClick()
                    }
                },
                label = { Text(text = stringResource(R.string.llm_prompt_label)) },
                placeholder = { Text(text = stringResource(R.string.llm_prompt_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                enabled = uiState.canEditPrompt,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (uiState.canGenerate) {
                            onGenerateClick()
                        }
                    }
                )
            )
            VoicePromptActionLine(
                uiState = uiState,
                onClick = onVoicePromptClick
            )
            VoicePromptStatusHint(uiState = uiState)
            uiState.voicePromptErrorMessage?.let { message ->
                ErrorMessageBlock(message = message)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onClearPromptClick,
                    enabled = uiState.canEditPrompt && uiState.promptText.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.llm_clear_prompt))
                }
                Button(
                    onClick = onGenerateClick,
                    enabled = uiState.canGenerate,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.llm_generate))
                }
            }
        }
    }
}

@Composable
private fun VoicePromptStatusHint(uiState: LlmUiState) {
    val messageRes = when {
        uiState.canStartVoicePrompt || uiState.canStopVoicePrompt -> null
        uiState.voicePromptModelState == AsrModelState.Loading -> R.string.asr_model_loading_message
        uiState.voicePromptModelState != AsrModelState.Loaded -> R.string.asr_model_required_message
        else -> null
    }

    messageRes?.let { resId ->
        Text(
            text = stringResource(resId),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VoicePromptActionLine(
    uiState: LlmUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEnabled = uiState.canStartVoicePrompt || uiState.canStopVoicePrompt
    val label = when {
        uiState.isVoicePromptListening -> R.string.llm_voice_prompt_stop
        uiState.isVoicePromptTranscribing -> R.string.llm_voice_prompt_transcribing
        else -> R.string.llm_voice_prompt_start
    }

    if (uiState.isVoicePromptListening) {
        Button(
            onClick = onClick,
            enabled = isEnabled,
            modifier = modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(label))
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = isEnabled,
            modifier = modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(label))
        }
    }
}

@Composable
private fun ResponseCard(
    uiState: LlmUiState,
    onSpeakResponseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.isBusy) {
                val progressLabelRes = if (uiState.generationState == LlmGenerationState.Generating) {
                    R.string.llm_generation_generating
                } else {
                    R.string.llm_model_loading_progress
                }
                LoadingMessageBlock(message = stringResource(progressLabelRes))
            }

            uiState.errorMessage?.let { message ->
                ErrorMessageBlock(message = message)
            }

            uiState.ttsErrorMessage?.let { message ->
                ErrorMessageBlock(message = message)
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = uiState.responseText.ifBlank {
                        stringResource(R.string.llm_response_empty)
                    },
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (uiState.isSpeakingResponse) {
                Button(
                    onClick = onSpeakResponseClick,
                    enabled = uiState.canStopSpeaking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.llm_stop_speaking))
                }
            } else {
                OutlinedButton(
                    onClick = onSpeakResponseClick,
                    enabled = uiState.canSpeakResponse,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.llm_speak_response))
                }
            }
        }
    }
}

@Composable
private fun HistoryActionLine(
    count: Int,
    onClick: () -> Unit
) {
    val summary = if (count == 0) {
        stringResource(R.string.llm_history_empty_summary)
    } else {
        pluralStringResource(R.plurals.llm_history_summary, count, count)
    }
    val isDarkTheme = isSystemInDarkTheme()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (isDarkTheme) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isDarkTheme) {
                MaterialTheme.colorScheme.outlineVariant
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.llm_history_label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LlmHistoryBottomSheet(
    historyEntries: List<LlmHistoryEntry>,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.llm_history_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (historyEntries.isEmpty()) {
                    stringResource(R.string.llm_history_empty_summary)
                } else {
                    pluralStringResource(
                        R.plurals.llm_history_summary,
                        historyEntries.size,
                        historyEntries.size
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HistoryHeaderRow()

            if (historyEntries.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = stringResource(R.string.llm_history_sheet_empty),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    historyEntries.forEachIndexed { index, entry ->
                        HistoryRow(index = index + 1, entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.llm_history_column_number),
            modifier = Modifier.width(28.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.llm_history_column_prompt),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.llm_history_column_backend),
            modifier = Modifier.width(44.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End
        )
        Text(
            text = stringResource(R.string.llm_history_column_time),
            modifier = Modifier.width(52.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun HistoryRow(
    index: Int,
    entry: LlmHistoryEntry
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = index.toString(),
                modifier = Modifier
                    .width(28.dp)
                    .defaultMinSize(minHeight = 20.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = entry.promptText,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.backend.name,
                modifier = Modifier.width(44.dp),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End
            )
            Text(
                text = entry.durationMs.toHistoryDurationLabel(),
                modifier = Modifier.width(52.dp),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun BlockedLlmCard(
    uiState: LlmUiState,
    modifier: Modifier = Modifier
) {
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
                    if (uiState.modelState == LlmModelState.Loading) {
                        R.string.llm_model_loading_message
                    } else {
                        R.string.llm_model_required_message
                    }
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            if (uiState.modelState == LlmModelState.Loading) {
                LoadingMessageBlock(message = stringResource(R.string.llm_model_loading_progress))
            }
            uiState.errorMessage?.let { message ->
                ErrorMessageBlock(message = message)
            }
        }
    }
}

private fun Long.toHistoryDurationLabel(): String {
    return if (this < 1_000L) {
        "<1s"
    } else {
        "${this / 1_000L}s"
    }
}

@Preview(showBackground = true)
@Composable
private fun LlmScreenPreview() {
    YasinTheme {
        LlmScreen(
            uiState = LlmUiState(
                modelState = LlmModelState.Loaded,
                promptText = "Объясни, что такое on-device inference.",
                responseText = "On-device inference — это выполнение модели прямо на устройстве."
            ),
            onPromptChanged = {},
            onClearPromptClick = {},
            onBackendSelected = {},
            onGenerateClick = {},
            onSpeakResponseClick = {},
            onVoicePromptClick = {},
            onSystemPromptChanged = {},
            onClearSystemPromptClick = {},
            onTemperatureChanged = {},
            onTopKChanged = {},
            onTopPChanged = {},
            onSeedChanged = {},
            onMaxTokensChanged = {},
            onApplySettingsClick = {},
            onResetSettingsClick = {}
        )
    }
}

private enum class LlmSection {
    Chat,
    Settings
}
