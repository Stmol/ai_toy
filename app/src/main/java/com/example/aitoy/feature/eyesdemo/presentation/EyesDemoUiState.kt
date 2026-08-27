package com.example.aitoy.feature.eyesdemo.presentation

import com.example.aitoy.feature.eyesdemo.domain.EyesDemoState

data class EyesDemoUiState(
    val selectedState: EyesDemoState = EyesDemoState.Idle,
    val isControlsVisible: Boolean = true,
    val assetTitle: String = "Eyes Animation – Blink and Track",
    val assetAuthor: String = "Tom_acco",
    val artboardName: String = "Master",
    val stateMachineName: String = "State Machine 1"
)
