package com.pieter.atomfx.ui.wheel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pieter.atomfx.data.SignalsRepository
import com.pieter.atomfx.data.SignalsResult
import com.pieter.atomfx.data.UserPreferences
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.domain.WheelMapper
import kotlinx.coroutines.delay
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

/**
 * Owns the fetch → map pipeline; the screen only ever reads [screenState]. Also runs the
 * foreground refresh loop from Functional Spec §9's "Data source → refresh cadence" setting —
 * while the app (and this ViewModel) is alive, it refetches every [UserPreferences.state]
 * `refreshMinutes`. This is foreground-only, not a background `WorkManager` job: gold-signal/
 * level-alert delivery already doesn't depend on it (that's push, Architecture §7) — this loop
 * only keeps the open app's own view of `signals.json` current.
 */
class WheelViewModel(
    private val repository: SignalsRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _screenState = MutableStateFlow<WheelScreenState>(WheelScreenState.Loading)
    val screenState: StateFlow<WheelScreenState> = _screenState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            while (true) {
                delay(userPreferences.state.value.refreshMinutes * 60_000L)
                refresh()
            }
        }
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
