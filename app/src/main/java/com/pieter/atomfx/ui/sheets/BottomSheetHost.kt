package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.wheel.Factor
import com.pieter.atomfx.ui.wheel.WheelUiState
import com.pieter.atomfx.ui.wheel.topPair

/** Design §13.1 routing: tap a ring → factor sheet, tap the nucleus → regime sheet, tap a node → pair sheet. */
sealed interface SheetTarget {
    data object Nucleus : SheetTarget
    data class Ring(val factor: Factor) : SheetTarget
    data class Node(val pair: String) : SheetTarget

    /** Design §12's right edge panel — summoned from the header, not a ring/node/nucleus tap. */
    data object Calendar : SheetTarget

    /** Design §12's left edge panel — summoned from the header (Phase 7). */
    data object Recommendation : SheetTarget

    /** Design §16 — long-press a node opens its 3-TF close-price chart (Phase 9). */
    data class Chart(val pair: String) : SheetTarget

    /** A single currency's CSM detail (Phase 9) — distinct from the market-wide Flow ring sheet. */
    data class Currency(val code: String) : SheetTarget

    /** Wheel v2: tap an outer cross-asset wedge → the full list, that asset pinned on top. */
    data class CrossAsset(val id: String) : SheetTarget
}

/**
 * Wraps Material3's `ModalBottomSheet` (half-expanded ~48% with the wheel visible behind,
 * expanded ~92%, swipe/scrim to dismiss — Design §13, minus the "collapsed peek" detent that
 * has no anchor point until Phase 5) and routes to the sheet Design §14 specifies for each tap
 * target. Momentum/Structure/Entry rings default to the wheel's current top pair, since that
 * content is inherently pair-shaped rather than market-wide (see `PairSheet`'s doc comment).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetHost(
    target: SheetTarget,
    wheelState: WheelUiState,
    signals: Signals,
    colors: AtomColors,
    onDismiss: () -> Unit,
    onNavigate: (SheetTarget) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
    ) {
        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            when (target) {
                SheetTarget.Nucleus -> RegimeSheet(signals, colors)
                is SheetTarget.Ring -> when (target.factor) {
                    Factor.REGIME -> RegimeSheet(signals, colors)
                    Factor.FLOW -> CurrencyFlowSheet(signals, colors, onCurrencyClick = { onNavigate(SheetTarget.Currency(it)) })
                    Factor.BREADTH -> BreadthSheet(signals, colors)
                    Factor.MOMENTUM -> PairSheet(wheelState.topPair(), wheelState.nodes, signals, colors, initialTab = 1)
                    Factor.STRUCTURE -> PairSheet(wheelState.topPair(), wheelState.nodes, signals, colors, initialTab = 2)
                    Factor.ENTRY -> PairSheet(wheelState.topPair(), wheelState.nodes, signals, colors, initialTab = 3)
                }

                is SheetTarget.Node -> {
                    val node = wheelState.nodes.firstOrNull { it.pair == target.pair }
                    if (node != null) PairSheet(node, wheelState.nodes, signals, colors)
                }

                SheetTarget.Calendar -> CalendarSheet(signals, colors)
                SheetTarget.Recommendation -> RecommendationSheet(signals, colors)
                is SheetTarget.Chart -> ChartSheet(target.pair, signals, colors)
                is SheetTarget.Currency -> CurrencyDetailSheet(target.code, signals, colors)
                is SheetTarget.CrossAsset -> CrossAssetSheet(target.id, signals, colors)
            }
        }
    }
}
