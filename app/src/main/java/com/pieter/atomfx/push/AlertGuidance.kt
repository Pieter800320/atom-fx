package com.pieter.atomfx.push

/**
 * Short, static "what to consider" copy per push `type` — distinct register from the in-app
 * Library's prose (Library answers "what is this / how does it work"; this answers "what
 * would I typically do about it"). Written once per alert type, not a live AI narration per
 * notification — same reasoning as the Library itself: free, instant, deterministic, and
 * consistent every time the same alert type fires.
 *
 * [libraryEntryId] is the matching `LibraryEntry.id` (see `LibraryContent.kt`) the
 * Notification History screen's "Learn more" link opens, pre-scrolled — the full mechanism
 * writeup lives there once, not duplicated here.
 */
data class AlertGuidance(val text: String, val libraryEntryId: String)

val ALERT_GUIDANCE: Map<String, AlertGuidance> = mapOf(
    "gold_signal" to AlertGuidance(
        "Connects gold's move to the market regime — check the ranked top setups on Home before acting; this flags conditions, it isn't a standalone entry.",
        "gold-signal",
    ),
    "level_alert" to AlertGuidance(
        "Price reached a level you set. Confirm the setup still holds on the Pair sheet before entering.",
        "level-ema-alerts",
    ),
    "potential_state" to AlertGuidance(
        "This pair cleared every factor in the Six-Factor gate. Open the Pair sheet's WHY checklist to see what's supporting it.",
        "potential",
    ),
    "structure_event" to AlertGuidance(
        "A BOS usually confirms the existing trend (a pullback entry opportunity); a CHoCH is a reversal warning — tighten risk on positions in the old direction. Check which one fired above.",
        "structure-events",
    ),
    "regime_flip" to AlertGuidance(
        "The market backdrop just changed — re-evaluate open positions; the Six-Factor gate will reshuffle which pairs qualify.",
        "h4-regime",
    ),
    "archetype_change" to AlertGuidance(
        "The macro narrative shifted, favouring a different currency set. Check the Macro tab for the new strong/weak split.",
        "macro-archetype",
    ),
    "volatility_spike" to AlertGuidance(
        "Volatility is expanding fast — consider wider stops; breakouts in either direction are more likely than usual.",
        "atr-percentile",
    ),
    "tf_alignment" to AlertGuidance(
        "All three timeframes agree — often high-conviction, but still confirm entry timing and risk on the Pair sheet.",
        "five-state-score",
    ),
    "conviction_extreme" to AlertGuidance(
        "Positioning looks crowded — a contrarian caution flag, not a standalone entry signal.",
        "conviction",
    ),
)
