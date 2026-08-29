package com.pieter.atomfx.ui.wheel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pieter.atomfx.data.SignalsRepository
import com.pieter.atomfx.data.SignalsResult
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.domain.WheelMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Freshness { FRESH, STALE }

sealed interface WheelScreenState {
    data object Loading : WheelScreenState

    /** [signals] is the raw fetch, kept alongside the wheel's own trimmed [state] so the
     * factor/regime sheets (Phase 4) can read fields the wheel itself never needed. */
    data class Loaded(val state: WheelUiState, val signals: Signals, val freshness: Freshness) : WheelScreenState
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
                is SignalsResult.Fresh ->
                    WheelScreenState.Loaded(WheelMapper.map(result.signals), result.signals, Freshness.FRESH)
                is SignalsResult.Stale ->
                    WheelScreenState.Loaded(WheelMapper.map(result.signals), result.signals, Freshness.STALE)
                SignalsResult.Unavailable -> WheelScreenState.Unavailable
            }
        }
    }
}
