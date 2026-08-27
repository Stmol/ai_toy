package com.example.aitoy.feature.eyesdemo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aitoy.feature.eyesdemo.domain.EyesDemoState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EyesDemoViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(EyesDemoUiState())
    private val _closeRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var autoResetJob: Job? = null

    val uiState: StateFlow<EyesDemoUiState> = _uiState.asStateFlow()
    val closeRequests = _closeRequests.asSharedFlow()

    fun onStateSelected(state: EyesDemoState) {
        autoResetJob?.cancel()
        _uiState.update { current ->
            current.copy(selectedState = state)
        }
        if (state == EyesDemoState.Idle) {
            return
        }
        autoResetJob = viewModelScope.launch {
            delay(state.displayDurationMillis())
            _uiState.update { current ->
                if (current.selectedState == state) {
                    current.copy(selectedState = EyesDemoState.Idle)
                } else {
                    current
                }
            }
        }
    }

    fun onControlsVisibilityToggled() {
        _uiState.update { current ->
            current.copy(isControlsVisible = !current.isControlsVisible)
        }
    }

    fun onCloseRequested() {
        _closeRequests.tryEmit(Unit)
    }

    override fun onCleared() {
        autoResetJob?.cancel()
        super.onCleared()
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(EyesDemoViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return EyesDemoViewModel() as T
        }
    }
}

private fun EyesDemoState.displayDurationMillis(): Long {
    return when (this) {
        EyesDemoState.Idle -> 0L
        EyesDemoState.Blink -> 420L
        EyesDemoState.LookLeft,
        EyesDemoState.LookRight -> 900L
        EyesDemoState.Listening,
        EyesDemoState.Thinking,
        EyesDemoState.Speaking -> 1_200L
        EyesDemoState.Happy,
        EyesDemoState.Sad,
        EyesDemoState.Excited -> 1_450L
    }
}
