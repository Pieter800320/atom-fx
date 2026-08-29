package com.pieter.atomfx.data

import com.pieter.atomfx.data.model.Signals

/** Architecture §8.4: never blank, never invented — a fetch either lands fresh, falls back to a stale cache, or there's nothing at all. */
sealed interface SignalsResult {
    data class Fresh(val signals: Signals) : SignalsResult
    data class Stale(val signals: Signals) : SignalsResult
    data object Unavailable : SignalsResult
}
