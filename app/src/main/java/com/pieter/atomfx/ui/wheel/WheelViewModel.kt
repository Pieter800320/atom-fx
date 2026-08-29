package com.pieter.atomfx.ui.wheel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pieter.atomfx.data.SignalsRepository
import com.pieter.atomfx.data.SignalsResult
import com.pieter.atomfx.domain.WheelMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Freshness { FRESH, STALE }

sealed interface WheelScreenState {
    data object Loading : WheelScreenState
    data class Loaded(val state: WheelUiState, val freshness: Freshness) : WheelScreenState
    data object Unavailable : WheelScreenState
}

/** Owns the fetch → map pipeline; the screen only ever reads [screenState]. */
class WheelViewModel(private val repository: SignalsRepository) : ViewModel() {

    private val _screenState = MutableStateFlow<WheelScreenState>(WheelScreenState.Loading)
    val screenState: StateFlow<WheelScreenState> = _screenState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _screenState.value = when (val result = repository.fetch()) {
                is SignalsResult.Fresh -> WheelScreenState.Loaded(WheelMapper.map(result.signals), Freshness.FRESH)
                is SignalsResult.Stale -> WheelScreenState.Loaded(WheelMapper.map(result.signals), Freshness.STALE)
                SignalsResult.Unavailable -> WheelScreenState.Unavailable
            }
        }
    }
}
