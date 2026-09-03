package com.pieter.atomfx.ui.sheets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.pieter.atomfx.data.model.Signals
import com.pieter.atomfx.ui.theme.AtomColors
import com.pieter.atomfx.ui.wheel.Factor
import com.pieter.atomfx.ui.wheel.WheelUiState
import com.pieter.atomfx.ui.wheel.topPair

// A sheet never covers more than this fraction of the screen — enough room that the scrim above
// it still reads as "there's a screen behind this," same reasoning Material's own bottom-sheet
// guidance gives for not going edge-to-edge; content past this scrolls inside the sheet instead.
private const val MAX_SHEET_HEIGHT_FRACTION = 0.8f

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
 * Wraps Material3's `ModalBottomSheet` and routes to the sheet Design §14 specifies for each tap
 * target. Momentum/Structure/Entry rings default to the wheel's current top pair, since that
 * content is inherently pair-shaped rather than market-wide (see `PairSheet`'s doc comment).
 *
 * Superseded 2026-09-03 (Pieter, direct in-session ask) — Design §13's original three-detent
 * model (collapsed peek, half ~48% with the wheel visible behind, expanded ~92%) is dropped for a
 * simpler one: `skipPartiallyExpanded = true` means every sheet rises straight to fit its content
 * (no manual drag through a half-stop first, and no separate "collapsed" stop either), capped at
 * `MAX_SHEET_HEIGHT_FRACTION` (80%) of the screen so a long sheet never edge-to-edge covers it and
 * always scrolls internally past that cap instead of clipping — the real bug that prompted this:
 * the Pair sheet's last WHY card was silently cut off with no way to reach it, since the old setup
 * had no scroll container at all and could get stuck at the half detent. `skipPartiallyExpanded`
 * also fixes swipe-to-dismiss needing two flicks — with no half-stop to land on, one drag-down
 * from expanded dismisses directly.
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * MAX_SHEET_HEIGHT_FRACTION).dp
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
    ) {
        Box(
            modifier = Modifier
                .heightIn(max = maxSheetHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            when (target) {
                SheetTarget.Nucleus -> RegimeSheet(signals, colors)
                is SheetTarget.Ring -> when (target.factor) {
                    Factor.REGIME -> RegimeSheet(signals, colors)
                    Factor.FLOW -> CurrencyFlowSheet(signals, colors, onCurrencyClick = { onNavigate(SheetTarget.Currency(it)) })
                    Factor.BREADTH -> BreadthSheet(signals, colors)
                    // PairSheet's tabs collapsed to [Overview, Breakdown] (2026-09-03) — Momentum/
                    // Structure/Entry all now live in the single Breakdown tab (index 1).
                    Factor.MOMENTUM -> PairSheet(wheelState.topPair(), wheelState.nodes, signals, colors, initialTab = 1)
                    Factor.STRUCTURE -> PairSheet(wheelState.topPair(), wheelState.nodes, signals, colors, initialTab = 1)
                    Factor.ENTRY -> PairSheet(wheelState.topPair(), wheelState.nodes, signals, colors, initialTab = 1)
                }

                is SheetTarget.Node -> {
                    val node = wheelState.nodes.firstOrNull { it.pair == target.pair }
                    if (node != null) PairSheet(node, wheelState.nodes, signals, colors)
                }

                SheetTarget.Calendar -> CalendarSheet(signals, colors)
                SheetTarget.Recommendation -> RecommendationSheet(signals, colors)
                is SheetTarget.Chart -> ChartSheet(target.pair, signals, colors)
                is SheetTarget.Currency -> CurrencyDetailSheet(target.code, signals, colors, onPairClick = { onNavigate(SheetTarget.Node(it)) })
                is SheetTarget.CrossAsset -> CrossAssetSheet(target.id, signals, colors)
            }
        }
    }
}
