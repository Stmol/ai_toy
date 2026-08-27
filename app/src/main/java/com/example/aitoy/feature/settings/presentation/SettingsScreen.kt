package com.example.aitoy.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aitoy.R
import com.example.aitoy.feature.asr.domain.VadAutoStopSettings
import com.example.aitoy.feature.tts.domain.TtsVoice
import java.util.Locale

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        uiState = uiState.value,
        onThresholdChanged = viewModel::onThresholdChanged,
        onMinSpeechDurationChanged = viewModel::onMinSpeechDurationChanged,
        onMinSilenceDurationChanged = viewModel::onMinSilenceDurationChanged,
        onMaxSpeechDurationChanged = viewModel::onMaxSpeechDurationChanged,
        onSaveVadSettingsClick = viewModel::onSaveVadSettingsClick,
        onResetVadSettingsClick = viewModel::onResetVadSettingsClick,
        onAutoSpeakEnabledChanged = viewModel::onAutoSpeakEnabledChanged,
        onSelectedVoiceChanged = viewModel::onSelectedVoiceChanged,
        modifier = modifier
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onThresholdChanged: (Float) -> Unit,
    onMinSpeechDurationChanged: (Float) -> Unit,
    onMinSilenceDurationChanged: (Float) -> Unit,
    onMaxSpeechDurationChanged: (Float) -> Unit,
    onSaveVadSettingsClick: () -> Unit,
    onResetVadSettingsClick: () -> Unit,
    onAutoSpeakEnabledChanged: (Boolean) -> Unit,
    onSelectedVoiceChanged: (TtsVoice) -> Unit,
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
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        VadSettingsCard(
            uiState = uiState,
            onThresholdChanged = onThresholdChanged,
            onMinSpeechDurationChanged = onMinSpeechDurationChanged,
            onMinSilenceDurationChanged = onMinSilenceDurationChanged,
            onMaxSpeechDurationChanged = onMaxSpeechDurationChanged,
            onSaveVadSettingsClick = onSaveVadSettingsClick,
            onResetVadSettingsClick = onResetVadSettingsClick,
            modifier = Modifier.fillMaxWidth()
        )
        TtsSettingsCard(
            uiState = uiState,
            onAutoSpeakEnabledChanged = onAutoSpeakEnabledChanged,
            onSelectedVoiceChanged = onSelectedVoiceChanged,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun VadSettingsCard(
    uiState: SettingsUiState,
    onThresholdChanged: (Float) -> Unit,
    onMinSpeechDurationChanged: (Float) -> Unit,
    onMinSilenceDurationChanged: (Float) -> Unit,
    onMaxSpeechDurationChanged: (Float) -> Unit,
    onSaveVadSettingsClick: () -> Unit,
    onResetVadSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_vad_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.settings_vad_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            VadSettingSlider(
                label = stringResource(R.string.settings_vad_threshold),
                value = uiState.draftVadSettings.threshold,
                valueText = formatDecimal(uiState.draftVadSettings.threshold),
                valueRange = VadAutoStopSettings.THRESHOLD_MIN..VadAutoStopSettings.THRESHOLD_MAX,
                onValueChange = onThresholdChanged
            )
            VadSettingSlider(
                label = stringResource(R.string.settings_vad_min_speech),
                value = uiState.draftVadSettings.minSpeechDurationSeconds,
                valueText = formatSeconds(uiState.draftVadSettings.minSpeechDurationSeconds),
                valueRange = VadAutoStopSettings.MIN_SPEECH_DURATION_MIN..
                    VadAutoStopSettings.MIN_SPEECH_DURATION_MAX,
                onValueChange = onMinSpeechDurationChanged
            )
            VadSettingSlider(
                label = stringResource(R.string.settings_vad_min_silence),
                value = uiState.draftVadSettings.minSilenceDurationSeconds,
                valueText = formatSeconds(uiState.draftVadSettings.minSilenceDurationSeconds),
                valueRange = VadAutoStopSettings.MIN_SILENCE_DURATION_MIN..
                    VadAutoStopSettings.MIN_SILENCE_DURATION_MAX,
                onValueChange = onMinSilenceDurationChanged
            )
            VadSettingSlider(
                label = stringResource(R.string.settings_vad_max_speech),
                value = uiState.draftVadSettings.maxSpeechDurationSeconds,
                valueText = formatSeconds(uiState.draftVadSettings.maxSpeechDurationSeconds),
                valueRange = VadAutoStopSettings.MAX_SPEECH_DURATION_MIN..
                    VadAutoStopSettings.MAX_SPEECH_DURATION_MAX,
                onValueChange = onMaxSpeechDurationChanged
            )
            if (uiState.message != null) {
                Text(
                    text = uiState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onResetVadSettingsClick,
                    enabled = uiState.canSaveVadSettings,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.settings_reset_defaults))
                }
                Button(
                    onClick = onSaveVadSettingsClick,
                    enabled = uiState.canSaveVadSettings && uiState.hasUnsavedVadSettings,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.settings_save))
                }
            }
        }
    }
}

@Composable
private fun TtsSettingsCard(
    uiState: SettingsUiState,
    onAutoSpeakEnabledChanged: (Boolean) -> Unit,
    onSelectedVoiceChanged: (TtsVoice) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_tts_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.settings_tts_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_tts_auto_speak),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = if (uiState.ttsSettings.isAutoSpeakEnabled) {
                            stringResource(R.string.settings_tts_auto_speak_enabled)
                        } else {
                            stringResource(R.string.settings_tts_auto_speak_disabled)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.ttsSettings.isAutoSpeakEnabled,
                    onCheckedChange = onAutoSpeakEnabledChanged,
                    enabled = uiState.canChangeTtsSettings
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VoiceSelectionButton(
                    voice = TtsVoice.DmitriMedium,
                    selectedVoice = uiState.ttsSettings.selectedVoice,
                    enabled = uiState.canChangeTtsSettings,
                    onSelectedVoiceChanged = onSelectedVoiceChanged,
                    modifier = Modifier.weight(1f)
                )
                VoiceSelectionButton(
                    voice = TtsVoice.IrinaMedium,
                    selectedVoice = uiState.ttsSettings.selectedVoice,
                    enabled = uiState.canChangeTtsSettings,
                    onSelectedVoiceChanged = onSelectedVoiceChanged,
                    modifier = Modifier.weight(1f)
                )
            }
            uiState.ttsStatusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VoiceSelectionButton(
    voice: TtsVoice,
    selectedVoice: TtsVoice,
    enabled: Boolean,
    onSelectedVoiceChanged: (TtsVoice) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = when (voice) {
        TtsVoice.DmitriMedium -> "Dmitri"
        TtsVoice.IrinaMedium -> "Irina"
    }

    if (voice == selectedVoice) {
        Button(
            onClick = { onSelectedVoiceChanged(voice) },
            enabled = enabled,
            modifier = modifier
        ) {
            Text(text = label)
        }
    } else {
        OutlinedButton(
            onClick = { onSelectedVoiceChanged(voice) },
            enabled = enabled,
            modifier = modifier
        ) {
            Text(text = label)
        }
    }
}

@Composable
private fun VadSettingSlider(
    label: String,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

private fun formatDecimal(value: Float): String {
    return String.format(Locale.US, "%.2f", value)
}

private fun formatSeconds(value: Float): String {
    return String.format(Locale.US, "%.2fs", value)
}
