package com.example.aitoy.app

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aitoy.R
import com.example.aitoy.feature.eyesdemo.presentation.EyesDemoRoute
import com.example.aitoy.feature.eyesdemo.presentation.EyesDemoViewModel
import com.example.aitoy.feature.llm.presentation.LlmRoute
import com.example.aitoy.feature.llm.presentation.LlmViewModel
import com.example.aitoy.feature.microphone.presentation.MicrophoneRoute
import com.example.aitoy.feature.microphone.presentation.MicrophoneViewModel
import com.example.aitoy.feature.models.presentation.ModelsRoute
import com.example.aitoy.feature.models.presentation.ModelsViewModel
import com.example.aitoy.feature.settings.presentation.SettingsRoute
import com.example.aitoy.feature.settings.presentation.SettingsViewModel
import com.example.aitoy.ui.theme.YasinTheme

class MainActivity : ComponentActivity() {
    private val appStartupViewModel: AppStartupViewModel by viewModels {
        val app = application as YasinApp
        app.appContainer.createAppStartupViewModelFactory()
    }
    private val microphoneViewModel: MicrophoneViewModel by viewModels {
        val app = application as YasinApp
        app.appContainer.createMicrophoneViewModelFactory()
    }
    private val modelsViewModel: ModelsViewModel by viewModels {
        val app = application as YasinApp
        app.appContainer.createModelsViewModelFactory()
    }
    private val llmViewModel: LlmViewModel by viewModels {
        val app = application as YasinApp
        app.appContainer.createLlmViewModelFactory()
    }
    private val settingsViewModel: SettingsViewModel by viewModels {
        val app = application as YasinApp
        app.appContainer.createSettingsViewModelFactory()
    }
    private val eyesDemoViewModel: EyesDemoViewModel by viewModels {
        val app = application as YasinApp
        app.appContainer.createEyesDemoViewModelFactory()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val activity = this@MainActivity
            var pendingMicrophonePermissionTarget by remember {
                mutableStateOf<MicrophonePermissionTarget?>(null)
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                val permanentlyDenied = !granted &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.RECORD_AUDIO
                    )
                when (pendingMicrophonePermissionTarget) {
                    MicrophonePermissionTarget.Recording -> {
                        microphoneViewModel.onPermissionResult(
                            granted = granted,
                            permanentlyDenied = permanentlyDenied
                        )
                        if (granted) {
                            microphoneViewModel.onStartClick()
                        }
                    }

                    MicrophonePermissionTarget.Llm -> {
                        llmViewModel.onVoicePromptPermissionResult(
                            granted = granted,
                            permanentlyDenied = permanentlyDenied
                        )
                        if (granted) {
                            llmViewModel.onVoicePromptStartClick()
                        }
                    }

                    null -> {
                        microphoneViewModel.onPermissionResult(
                            granted = granted,
                            permanentlyDenied = permanentlyDenied
                        )
                        llmViewModel.onVoicePromptPermissionResult(
                            granted = granted,
                            permanentlyDenied = permanentlyDenied
                        )
                    }
                }
                pendingMicrophonePermissionTarget = null
            }

            YasinTheme {
                val startupUiState by appStartupViewModel.uiState.collectAsStateWithLifecycle()
                var selectedTab by remember { mutableStateOf(MainTab.Main) }
                var isEyesDemoVisible by rememberSaveable { mutableStateOf(false) }
                val isStartupBlocked = startupUiState is AppStartupUiState.BlockedMissingModels ||
                    startupUiState is AppStartupUiState.BlockedLoadError
                val closeEyesDemo = {
                    isEyesDemoVisible = false
                    exitLandscapeDemoMode()
                }

                LaunchedEffect(startupUiState) {
                    if (isStartupBlocked) {
                        selectedTab = MainTab.Models
                    }
                }

                LaunchedEffect(selectedTab, startupUiState) {
                    if (selectedTab == MainTab.Llm && startupUiState == AppStartupUiState.Ready) {
                        llmViewModel.onLlmTabSelected()
                    }
                }

                if (startupUiState is AppStartupUiState.Loading) {
                    StartupLoadingScreen(
                        message = (startupUiState as AppStartupUiState.Loading).message
                    )
                    return@YasinTheme
                }

                LaunchedEffect(isEyesDemoVisible) {
                    if (isEyesDemoVisible) {
                        enterLandscapeDemoMode()
                    } else {
                        exitLandscapeDemoMode()
                    }
                }

                if (isEyesDemoVisible) {
                    EyesDemoRoute(
                        viewModel = eyesDemoViewModel,
                        onDismissRequest = closeEyesDemo,
                        modifier = Modifier.fillMaxSize()
                    )
                    return@YasinTheme
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            MainTab.entries.forEach { tab ->
                                val isSelected = tab == selectedTab
                                val enabled = !isStartupBlocked || tab == MainTab.Models
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = {
                                        if (enabled) {
                                            selectedTab = tab
                                        }
                                    },
                                    enabled = enabled,
                                    icon = {
                                        Text(
                                            text = if (isSelected) "●" else "○"
                                        )
                                    },
                                    label = {
                                        Text(text = stringResource(tab.labelRes))
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    when (selectedTab) {
                        MainTab.Main -> {
                            MicrophoneRoute(
                                viewModel = microphoneViewModel,
                                hasMicrophonePermission = {
                                    hasMicrophonePermission()
                                },
                                onOpenEyesDemoClick = {
                                    isEyesDemoVisible = true
                                },
                                onRequestMicrophonePermission = {
                                    pendingMicrophonePermissionTarget =
                                        MicrophonePermissionTarget.Recording
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }

                        MainTab.Llm -> {
                            LlmRoute(
                                viewModel = llmViewModel,
                                hasMicrophonePermission = {
                                    hasMicrophonePermission()
                                },
                                onRequestMicrophonePermission = {
                                    pendingMicrophonePermissionTarget =
                                        MicrophonePermissionTarget.Llm
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }

                        MainTab.Models -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                startupUiState.blockingMessage()?.let { message ->
                                    StartupBlockedBanner(
                                        message = message,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 12.dp)
                                    )
                                }
                                ModelsRoute(
                                    viewModel = modelsViewModel,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        MainTab.Settings -> {
                            SettingsRoute(
                                viewModel = settingsViewModel,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        microphoneViewModel.onStopClick()
        llmViewModel.onVoicePromptStopClick()
        llmViewModel.onStopPlayback()
        super.onStop()
    }

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun enterLandscapeDemoMode() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    private fun exitLandscapeDemoMode() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

@Composable
private fun StartupLoadingScreen(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(44.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun StartupBlockedBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

private fun AppStartupUiState.blockingMessage(): String? {
    return when (this) {
        is AppStartupUiState.BlockedMissingModels -> message
        is AppStartupUiState.BlockedLoadError -> message
        AppStartupUiState.Ready,
        is AppStartupUiState.Loading -> null
    }
}

private enum class MainTab(
    val labelRes: Int
) {
    Main(R.string.main_tab_main),
    Llm(R.string.main_tab_llm),
    Models(R.string.main_tab_models),
    Settings(R.string.main_tab_settings)
}

private enum class MicrophonePermissionTarget {
    Recording,
    Llm
}
