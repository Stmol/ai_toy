package com.example.aitoy.feature.eyesdemo.presentation

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.Loop
import app.rive.runtime.kotlin.renderers.PointerEvents
import app.rive.runtime.kotlin.core.Alignment as RiveAlignment
import com.example.aitoy.R
import com.example.aitoy.feature.eyesdemo.domain.EyesDemoState

@Composable
fun EyesDemoRoute(
    viewModel: EyesDemoViewModel,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.closeRequests.collect {
            onDismissRequest()
        }
    }

    EyesDemoScreen(
        uiState = uiState.value,
        onStateSelected = viewModel::onStateSelected,
        onToggleControlsClick = viewModel::onControlsVisibilityToggled,
        onCloseClick = viewModel::onCloseRequested,
        modifier = modifier
    )
}

@Composable
fun EyesDemoScreen(
    uiState: EyesDemoUiState,
    onStateSelected: (EyesDemoState) -> Unit,
    onToggleControlsClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onCloseClick)

    val visualSpec = uiState.selectedState.visualSpec()
    val viewport = uiState.selectedState.viewport()
    val animatedScale by animateFloatAsState(
        targetValue = viewport.zoom,
        animationSpec = tween(durationMillis = 280),
        label = "eyes-demo-viewport-scale"
    )
    val animatedShiftX by animateFloatAsState(
        targetValue = viewport.shiftXFraction,
        animationSpec = tween(durationMillis = 280),
        label = "eyes-demo-viewport-shift-x"
    )
    val animatedShiftY by animateFloatAsState(
        targetValue = viewport.shiftYFraction,
        animationSpec = tween(durationMillis = 280),
        label = "eyes-demo-viewport-shift-y"
    )
    var riveView by remember { mutableStateOf<RiveAnimationView?>(null) }

    LaunchedEffect(riveView, uiState.selectedState) {
        val currentView = riveView ?: return@LaunchedEffect
        currentView.post {
            applyStateToRiveView(
                view = currentView,
                state = uiState.selectedState
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = visualSpec.backgroundColors,
                    radius = 1_700f
                )
            )
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                SmallHeaderChip(
                    text = stringResource(R.string.eyes_demo_title),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                )

                OutlinedButton(
                    onClick = onCloseClick,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                ) {
                    Text(text = stringResource(R.string.eyes_demo_close))
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.88f)
                        .aspectRatio(2.28f),
                    shape = RoundedCornerShape(40.dp),
                    color = Color.Black.copy(alpha = 0.10f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(40.dp))
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        visualSpec.glowColor.copy(alpha = 0.22f),
                                        Color.Transparent
                                    ),
                                    radius = 880f
                                )
                            )
                    ) {
                        AndroidView(
                            factory = { context ->
                                createEyesRiveView(
                                    context = context,
                                    uiState = uiState
                                ).also { view ->
                                    riveView = view
                                }
                            },
                            update = { view ->
                                riveView = view
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = animatedScale
                                    scaleY = animatedScale
                                    translationX = size.width * animatedShiftX
                                    translationY = size.height * animatedShiftY
                                }
                        )
                    }
                }

                SmallHeaderChip(
                    text = uiState.selectedState.label(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            if (uiState.isControlsVisible) {
                ControlsRail(
                    selectedState = uiState.selectedState,
                    onStateSelected = onStateSelected,
                    onToggleControlsClick = onToggleControlsClick
                )
            } else {
                CompactControlsToggle(
                    onClick = onToggleControlsClick,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }
}

@Composable
private fun ControlsRail(
    selectedState: EyesDemoState,
    onStateSelected: (EyesDemoState) -> Unit,
    onToggleControlsClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(128.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(28.dp),
        color = Color.Black.copy(alpha = 0.18f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.eyes_demo_title),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = onToggleControlsClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.eyes_demo_hide_controls))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EyesDemoState.entries.forEach { state ->
                    val isSelected = state == selectedState
                    if (isSelected) {
                        FilledTonalButton(
                            onClick = { onStateSelected(state) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.label(),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onStateSelected(state) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.label(),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactControlsToggle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.18f)
    ) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.padding(8.dp)
        ) {
            Text(text = stringResource(R.string.eyes_demo_show_controls))
        }
    }
}

@Composable
private fun SmallHeaderChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.18f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

private fun createEyesRiveView(
    context: Context,
    uiState: EyesDemoUiState
): RiveAnimationView {
    return RiveAnimationView.Builder(context)
        .setResource(R.raw.eyes_demo_blink_and_track)
        .setArtboardName(uiState.artboardName)
        .setStateMachineName(uiState.stateMachineName)
        .setAutoplay(true)
        .setFit(Fit.CONTAIN)
        .setAlignment(RiveAlignment.CENTER)
        .setLoop(Loop.LOOP)
        .setTouchPassThrough(false)
        .setMultiTouchEnabled(false)
        .build()
}

private fun applyStateToRiveView(
    view: RiveAnimationView,
    state: EyesDemoState
) {
    if (view.width <= 0 || view.height <= 0) {
        return
    }

    val point = state.pointerTarget()
    val pointerX = view.width * point.first
    val pointerY = view.height * point.second

    view.controller.pointerEvent(
        PointerEvents.POINTER_MOVE,
        RiveAnimationView.SINGLE_TOUCH_ID,
        pointerX,
        pointerY
    )

    if (state == EyesDemoState.Blink) {
        view.fireState(STATE_MACHINE_NAME, BLINK_TRIGGER_NAME)
    }
}

private fun EyesDemoState.visualSpec(): EyesDemoVisualSpec {
    return when (this) {
        EyesDemoState.Idle -> EyesDemoVisualSpec(
            backgroundColors = listOf(Color(0xFF071120), Color(0xFF173760)),
            glowColor = Color(0xFF90D7FF)
        )
        EyesDemoState.Blink -> EyesDemoVisualSpec(
            backgroundColors = listOf(Color(0xFF071120), Color(0xFF173760)),
            glowColor = Color(0xFFBFE6FF)
        )
        EyesDemoState.LookLeft,
        EyesDemoState.LookRight -> EyesDemoVisualSpec(
            backgroundColors = listOf(Color(0xFF0B1320), Color(0xFF1E426A)),
            glowColor = Color(0xFF8FD6FF)
        )
        EyesDemoState.Listening -> EyesDemoVisualSpec(
            backgroundColors = listOf(Color(0xFF07161B), Color(0xFF175A5D)),
            glowColor = Color(0xFF89FFF4)
        )
        EyesDemoState.Thinking -> EyesDemoVisualSpec(
            backgroundColors = listOf(Color(0xFF0F1520), Color(0xFF415270)),
            glowColor = Color(0xFFC2D1FF)
        )
        EyesDemoState.Speaking -> EyesDemoVisualSpec(
            backgroundColors = listOf(Color(0xFF1B1208), Color(0xFF845528)),
            glowColor = Color(0xFFFFC280)
        )
        EyesDemoState.Happy -> EyesDemoVisualSpec(
            backgroundColors = listOf(Color(0xFF2A1504), Color(0xFFF09528)),
            glowColor = Color(0xFFFFD269)
        )
        EyesDemoState.Sad -> EyesDemoVisualSpec(
            backgroundColors = listOf(Color(0xFF081220), Color(0xFF2B4D72)),
            glowColor = Color(0xFF98C0FF)
        )
        EyesDemoState.Excited -> EyesDemoVisualSpec(
            backgroundColors = listOf(Color(0xFF2B1307), Color(0xFFF35F29)),
            glowColor = Color(0xFFFFB56B)
        )
    }
}

private fun EyesDemoState.viewport(): EyesViewportSpec {
    return when (this) {
        EyesDemoState.Idle -> EyesViewportSpec(zoom = 2.24f, shiftXFraction = 0.56f, shiftYFraction = 0.56f)
        EyesDemoState.Blink -> EyesViewportSpec(zoom = 2.24f, shiftXFraction = 0.56f, shiftYFraction = 0.56f)
        EyesDemoState.LookLeft -> EyesViewportSpec(zoom = 2.24f, shiftXFraction = 0.56f, shiftYFraction = 0.56f)
        EyesDemoState.LookRight -> EyesViewportSpec(zoom = 2.24f, shiftXFraction = 0.56f, shiftYFraction = 0.56f)
        EyesDemoState.Listening -> EyesViewportSpec(zoom = 2.24f, shiftXFraction = -0.56f, shiftYFraction = 0.56f)
        EyesDemoState.Thinking -> EyesViewportSpec(zoom = 2.24f, shiftXFraction = 0.56f, shiftYFraction = -0.56f)
        EyesDemoState.Speaking -> EyesViewportSpec(zoom = 2.24f, shiftXFraction = -0.56f, shiftYFraction = -0.56f)
        EyesDemoState.Happy -> EyesViewportSpec(zoom = 2.28f, shiftXFraction = -0.56f, shiftYFraction = 0.56f)
        EyesDemoState.Sad -> EyesViewportSpec(zoom = 2.28f, shiftXFraction = 0.56f, shiftYFraction = -0.56f)
        EyesDemoState.Excited -> EyesViewportSpec(zoom = 2.30f, shiftXFraction = -0.56f, shiftYFraction = -0.56f)
    }
}

private fun EyesDemoState.pointerTarget(): Pair<Float, Float> {
    return when (this) {
        EyesDemoState.Idle,
        EyesDemoState.Blink -> 0.25f to 0.25f
        EyesDemoState.LookLeft -> 0.10f to 0.25f
        EyesDemoState.LookRight -> 0.40f to 0.25f
        EyesDemoState.Listening -> 0.75f to 0.22f
        EyesDemoState.Thinking -> 0.24f to 0.74f
        EyesDemoState.Speaking -> 0.76f to 0.76f
        EyesDemoState.Happy -> 0.72f to 0.18f
        EyesDemoState.Sad -> 0.18f to 0.82f
        EyesDemoState.Excited -> 0.78f to 0.70f
    }
}

private fun EyesDemoState.label(): String {
    return when (this) {
        EyesDemoState.Idle -> "Idle"
        EyesDemoState.Blink -> "Blink"
        EyesDemoState.LookLeft -> "Left"
        EyesDemoState.LookRight -> "Right"
        EyesDemoState.Listening -> "Listen"
        EyesDemoState.Thinking -> "Think"
        EyesDemoState.Speaking -> "Speak"
        EyesDemoState.Happy -> "Happy"
        EyesDemoState.Sad -> "Sad"
        EyesDemoState.Excited -> "Excited"
    }
}

private data class EyesDemoVisualSpec(
    val backgroundColors: List<Color>,
    val glowColor: Color
)

private data class EyesViewportSpec(
    val zoom: Float,
    val shiftXFraction: Float,
    val shiftYFraction: Float
)

private const val STATE_MACHINE_NAME = "State Machine 1"
private const val BLINK_TRIGGER_NAME = "Trigger 1"
