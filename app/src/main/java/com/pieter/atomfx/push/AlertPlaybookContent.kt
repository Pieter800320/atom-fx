package com.pieter.atomfx.push

/**
 * Part 3 of the "living handbook" pass (Pieter, 2026-09-04) — extending the pattern proven on
 * `archetype_change`/`regime_flip` (see `RegimePlaybookContent.kt`/`TechnicalRegimePlaybookContent.kt`)
 * to the remaining state-transition alerts: `structure_event`, `volatility_spike`, `tf_alignment`,
 * `conviction_extreme`, and `gold_signal`. `level_alert` and `potential_state` deliberately excluded
 * (Pieter's own call) — `level_alert` is a user-defined price trigger with no backend regime/state
 * to unpack, and `potential_state`'s own mechanism is already fully covered by the existing
 * "Potential" and "The Six-Factor Engine" Library entries.
 *
 * One shared, simpler shape this time, not a bespoke class per alert (the Regime Playbook's
 * richer shape — confirmingAxes/confidenceNote/biasMechanism/historicalNote — was built for a
 * 10-code system with live per-firing evidence assembly; none of these five alerts have that
 * shape, so forcing it here would mean empty/unused fields everywhere). Every entry is grounded
 * directly in the frozen/EXTEND source that actually fires it — `scanner/extend/state_alerts.py`,
 * `scanner/scan_h1.py`'s gold-signal block, `scanner/extend/conviction.py` — read in full before
 * writing, not paraphrased from the Library's own (mechanism-only) entries. Where an alert has a
 * genuinely different story depending on which way it fired (BOS vs CHoCH, gold bull vs bear,
 * crowded-long vs crowded-short), it's keyed by that split, same as Risk-On/Risk-Off are two
 * separate entries in the Technical Regime Playbook rather than one entry with a direction flag.
 * `volatility_spike` and `tf_alignment` have no such split — a spike or an alignment means the
 * same thing regardless of which way it's pointing — so those are single entries.
 */
data class AlertPlaybookEntry(
    val key: String,
    val title: String,
    val coreStory: String,
    val mechanism: String,
    val relationToOtherSystems: String,
    val falsificationChecklist: List<String>,
)

// ── Structure — BOS / CHoCH (`structure_event`) ─────────────────────────────────────────────────
// Grounded in `scanner/extend/structure_expose.py`'s swing-pivot logic (same source the existing
// "Structure — BOS / CHoCH" Library entry already documents mechanically) — this goes past the
// mechanism into what a fresh break actually implies and where it can mislead.
val STRUCTURE_EVENT_PLAYBOOK: Map<String, AlertPlaybookEntry> = listOf(
    AlertPlaybookEntry(
        key = "BOS",
        title = "Break of Structure",
        coreStory = "Price just closed beyond the last swing point in the direction the trend was already moving — the market doing exactly what a trend is supposed to do: make a new higher high (uptrend) or lower low (downtrend). This is confirmation, not discovery — the trend already existed before this alert fired; a BOS is the market re-affirming it, most often the moment a pullback ends and the prior direction resumes. That's why it's usually read as a continuation/pullback-entry signal, not a fresh idea on its own.",
        mechanism = "Swing pivots are found first (a bar strictly higher/lower than several bars on both sides — pure price geometry, no indicator), the trend read from the last two swings, then the latest close classified against the most recent swing in that trend's own direction. The strength score (0–1) that rides alongside every BOS measures how far price broke past that swing relative to ATR — a BOS that clears the old swing by a wide margin is a cleaner, more decisive break than one that barely ticks past it.",
        relationToOtherSystems = "Feeds the frozen technical score directly: a BOS scales it up to +30%. It's also one of the six Setup Rank/Continuation Score inputs (TF alignment), so a fresh BOS often shows up alongside a rising Potential level or Continuation Score for the same pair — not a coincidence, the same underlying price action is driving both.",
        falsificationChecklist = listOf(
            "How large was the strength score? A BOS with strength near 0 barely cleared the old swing — treat it as provisional until the next bar or two confirm the break is holding, not retesting back below it.",
            "Does the wheel's own Momentum or ADX wing agree there's real conviction behind this move, or is price grinding just past the level on thin participation?",
            "Is this pair's H4 regime supportive of the same direction? A BOS against the prevailing regime is a weaker continuation signal than one aligned with it — check the wheel's hub.",
        ),
    ),
    AlertPlaybookEntry(
        key = "CHoCH",
        title = "Change of Character",
        coreStory = "Price just closed beyond the last swing point in the OPPOSITE direction from the established trend — the market breaking a pattern it had been respecting. This is a warning, not a confirmed reversal: it says the trend that was in place is now in genuine question, not that a new trend in the other direction has started. The name is deliberate — \"change of character\", not \"reversal\" — because plenty of CHoChs resolve into a larger range or a deeper pullback rather than a full trend flip.",
        mechanism = "Same swing-pivot/trend-read mechanism as BOS — the only difference is which direction the break runs relative to the established trend. A CHoCH scales the frozen technical score down (up to −60%), a much larger penalty than a BOS's own +30% bonus — the engine treats a character change as materially more informative than a continuation, on the reasonable logic that trends are the default state and a genuine break in one deserves more weight than routine confirmation.",
        relationToOtherSystems = "The Entry factor (Six-Factor Engine) explicitly blocks on a CHoCH — a pair can't reach Tradeable/A+ Potential while its H4 structure reads CHoCH, regardless of how well everything else lines up. That's the single clearest signal for how seriously the engine treats this event.",
        falsificationChecklist = listOf(
            "Is this the FIRST CHoCH after a long-established trend, or one of several back-to-back — a market that's already chopping? A CHoCH inside a trend that was already weakening (falling ADX, cooling Momentum) is more likely a genuine turn than one in an otherwise strong, one-directional trend.",
            "Did positions in the OLD direction get closed/tightened yet, or is this still fresh? A CHoCH's own guidance text exists specifically because open positions in the direction now in question are the actual risk here.",
            "Check the strength score the same way as a BOS — a CHoCH that barely cleared the old swing is a shallower warning than one that broke it decisively.",
        ),
    ),
).associateBy { it.key }

// ── Volatility Spike ─────────────────────────────────────────────────────────────────────────
// Single entry — grounded in `scanner/score.py::compute_atr_percentile` (ATR's own percentile
// rank against its recent range) and `state_alerts.py::_volatility_spike_alerts` (fires once,
// edge-triggered, when a pair's ATR percentile newly crosses 90).
val VOLATILITY_SPIKE_PLAYBOOK = AlertPlaybookEntry(
    key = "volatility_spike",
    title = "Volatility Expanding",
    coreStory = "This pair's current ATR just crossed into the top 10% of its own recent range — not a price level, a change in how MUCH price is moving, in either direction. Volatility expansion has no direction of its own baked in (this alert never carries a bull/bear flag) — it says the range of outcomes just widened, not which outcome is more likely. That's exactly why it needs a different response than a directional alert: it's a risk-sizing signal first, an opportunity signal second.",
    mechanism = "ATR percentile ranks the CURRENT bar's own ATR reading against a rolling window of its own recent history (0 = the most compressed reading in that window, 100 = the most extended) — a relative, self-referential measure, not an absolute volatility level, so it's comparable across pairs with very different natural ranges (a quiet pair like EURCHF and a naturally volatile one like GBPJPY both use the same 0–100 scale against their OWN history). The alert fires once, the scan it first crosses 90 — it won't fire again the next hour just because volatility is still elevated, only on the next fresh expansion after it's cooled back down.",
    relationToOtherSystems = "ATR Percentile is also half of Continuation Score's own \"Entry Position\" component (alongside Reset Score) and feeds the Entry factor's own gate — so a volatility spike often coincides with a shift in Potential/Continuation for the same pair, not just a standalone volatility event. A compressed ATR percentile (the opposite extreme, near 0) is itself informative too — it's the classic pre-breakout setup, low volatility that tends to resolve into expansion — but that state doesn't get its own push alert, only the spike itself does.",
    falsificationChecklist = listOf(
        "Did this expansion coincide with a scheduled catalyst (check the Calendar tab) or come from nowhere? A spike around a known release is more likely to mean-revert once the news is digested; an unexplained spike deserves more caution, not less.",
        "Is price actually breaking out directionally (check Structure for a fresh BOS/CHoCH on this same pair), or just chopping wider without going anywhere? Expanding range and directional movement aren't the same thing.",
        "Was this pair already extended (high Reset Score) before the spike, or was it near equilibrium? A volatility spike on top of an already-stretched pair compounds the risk of chasing; one from a fresh equilibrium is a cleaner setup either way it resolves.",
    ),
)

// ── Timeframe Alignment ──────────────────────────────────────────────────────────────────────
// Single entry — grounded in `state_alerts.py::_pills_aligned`/`_tf_alignment_alerts` (D1, H4
// and H1 pills all reading bull_strong or all reading bear_strong at once) and the 5-State
// Technical Score the pills themselves come from.
val TF_ALIGNMENT_PLAYBOOK = AlertPlaybookEntry(
    key = "tf_alignment",
    title = "Timeframe Alignment",
    coreStory = "All three timeframes — D1, H4 and H1 — just independently reached the same extreme read (Strong Buy or Strong Sell) at once. Each timeframe's own 5-State Score is computed from a completely separate slice of price history (D1 bars, H4 bars, H1 bars each have their own trend/momentum/RSI read), so three of them agreeing isn't circular — it's three genuinely different measurements of the same underlying move, all pointing the same way at the same moment. That convergence is what multi-timeframe confluence actually means in practice, not just a phrase.",
    mechanism = "`_pills_aligned` checks whether the D1/H4/H1 pill set collapses to a single value, and only counts it if that value is the EXTREME state specifically (bull_strong/bear_strong) — a plain \"bull\" agreement across all three doesn't fire this alert, only the strongest tier does. Edge-triggered the same as every state alert: it fires the scan alignment is newly reached, not every hour it continues to hold.",
    relationToOtherSystems = "This is the same three-square read the Pair sheet's own alignment strip already shows (SB/B/N/S/SS, one per timeframe) — the alert fires exactly when all three squares turn the same extreme colour at once. It doesn't feed Potential or Setup Rank directly (those use Continuation Score's own, separately-weighted TF-alignment component, not this specific pill-collapse check), so a tf_alignment firing and a Potential-level change are related in spirit but not the same trigger.",
    falsificationChecklist = listOf(
        "How LONG has each timeframe individually been at its extreme? If D1 has read Strong Buy for weeks and H1 just caught up this hour, that's a very different situation from all three turning together — check each pill's own recent history if you can, not just today's snapshot.",
        "Is this alignment happening on a pair that's already extended (high Reset Score, high ATR percentile)? Three-timeframe agreement can just as easily mark a move's exhaustion point as its start — this alert says the readings agree, not where in the move you're catching it.",
        "Does the H4 or Macro regime support the same direction? Alignment across three technical timeframes is a strong price signal on its own, but it's still a purely technical read — cross-check the wheel's hub the same way the Structure entries above recommend.",
    ),
)

// ── Conviction Extreme ───────────────────────────────────────────────────────────────────────
// Grounded directly in `scanner/extend/conviction.py` (`_score_cot_position`/`_score_csm_extreme`
// in particular) — a genuinely non-obvious mechanism worth getting right: two of the six inputs
// feeding this score are ALREADY contrarian-weighted before this alert's own ±80 threshold ever
// applies, so a headline reading of, say, +85 is not simply "everyone is crowded long, therefore
// risky" — see the coreStory below for why that naive reading is usually backwards.
val CONVICTION_EXTREME_PLAYBOOK: Map<String, AlertPlaybookEntry> = listOf(
    AlertPlaybookEntry(
        key = "bull",
        title = "Conviction Extreme — Bullish",
        coreStory = "This currency's Conviction score just crossed +80 — but two of the six inputs that built that score (CFTC positioning and the CSM-extreme read) are ALREADY contrarian by construction: deeply crowded long positioning scores NEGATIVE, not positive, and an already-high CSM scores negative too. So a genuinely extreme POSITIVE reading usually means the opposite of the naive \"everyone's crowded long\" read — it more often means this currency ISN'T crowded (avoiding that penalty) while open-interest momentum, structural/tactical fund alignment, extension and breadth are all independently confirming bullish at the same time. Reaching ±80 out of a ±100 scale needs nearly every one of the six components pulling the same way at once — a genuinely rare, broad alignment, not a single lopsided input.",
        mechanism = "Six weighted components (see the \"Conviction (Positioning)\" Library entry for the full list) sum to a raw score, normalised to −100..+100, then EWMA-smoothed week to week. This alert is purely a threshold crossing on that already-computed weekly number — nothing new is calculated here, it just watches for the edge into ≥80 that wasn't there last week.",
        relationToOtherSystems = "This is the live version of the exact check the Regime Playbook's own Regime E (Liquidity shock) and Regime J (Crowded carry unwind) entries point back to — both explicitly name Conviction data as the tool for spotting the positioning extreme that a purely price-based regime read can't see on its own. If a Conviction Extreme alert and a Regime E/J read show up close together, that's the two systems corroborating the same underlying risk from different data.",
        falsificationChecklist = listOf(
            "Which components actually drove this, if you can tell from the Currency Detail sheet's own breakdown — a score built mostly from COT/CSM avoiding their own contrarian penalties reads differently than one where open-interest momentum or extension is doing the heavy lifting.",
            "Is CFTC data stale? It publishes Fridays covering the prior Tuesday — a fast-moving week can leave the positioning component several days behind the market's current reality even though the score itself just crossed the line.",
            "Does this coincide with a genuinely strong technical picture (high Momentum, high ADX, low Reset Score) for the same currency's pairs, or is price actually looking tired here? An extreme Conviction score describes structural/positioning support, not entry timing on its own.",
        ),
    ),
    AlertPlaybookEntry(
        key = "bear",
        title = "Conviction Extreme — Bearish",
        coreStory = "The mirror image of the bullish case, with the same non-obvious mechanism: crossing below −80 doesn't straightforwardly mean \"everyone is crowded short, so it's due to bounce\" — deeply crowded SHORT positioning actually scores POSITIVE (contrarian-bullish) in this engine, so an extreme NEGATIVE reading more often means the opposite of naive crowding logic — genuinely bearish momentum, open interest, extension and breadth all lining up together, with positioning itself either not a factor or actively working against the bearish case (and still not enough to prevent the extreme reading).",
        mechanism = "Identical mechanism to the bullish entry, mirrored — the same six-component blend, the same EWMA smoothing, the same pure threshold-crossing trigger, just watching the other tail of the same −100..+100 scale.",
        relationToOtherSystems = "Same cross-reference as the bullish case — Regime E and Regime J in the Regime Playbook both point here as the tool for catching a positioning extreme a price-only regime read would miss.",
        falsificationChecklist = listOf(
            "Same three checks as the bullish entry — which components actually drove it, how stale is the CFTC data behind it, and does the technical picture (Momentum/ADX/Reset Score) for this currency's pairs actually support the same bearish read right now.",
            "Is this currency's own weakness broad-based (showing up across most of its pairs) or concentrated in just one or two? A currency-level Conviction extreme driven by one dominant pair's own move is a thinner signal than one where the whole basket agrees.",
            "Has this reading held for one week or several? A first crossing into extreme is a different situation from one that's persisted — the alert only fires on the transition in, not on it remaining true.",
        ),
    ),
).associateBy { it.key }

// ── Gold Signal ──────────────────────────────────────────────────────────────────────────────
// Grounded in `scanner/scan_h1.py`'s gold-signal block — the actual gate is exactly "Risk-On" or
// "Risk-Off" on the Technical Regime (not Mixed, not Ranging) on BOTH H4 and H1, confirming gold's
// own direction — already documented from the regime side in the Technical Regime Playbook's own
// `relationToOtherSystems` fields; this is the gold-side counterpart.
val GOLD_SIGNAL_PLAYBOOK: Map<String, AlertPlaybookEntry> = listOf(
    AlertPlaybookEntry(
        key = "bull",
        title = "Gold Signal — Bullish",
        coreStory = "Gold is rising AND the Technical Regime independently reads Risk-On on both H4 and H1 at once — the alert only fires when price and regime agree, not on gold's own move in isolation. Gold's relationship to risk sentiment isn't fixed the way a textbook might imply, though: gold can rally on genuine risk-on demand for growth-sensitive assets generally, but it can also rally as a diversification/inflation hedge even in calmer conditions that don't obviously look risk-on — this alert only fires the specific case where gold's move and a Risk-On price regime are moving together, not gold strength generally.",
        mechanism = "Direction is set from gold's own `macro_assets` delta (up/down) crossed against the H4 Technical Regime; H1 must independently confirm the same Risk-On read before the alert is eligible to fire at all, and H4 confidence must be Medium or High — a Low-confidence H4 Risk-On read with gold rising does NOT fire this alert, even though the direction technically matches.",
        relationToOtherSystems = "Hard-gated by the Technical Regime specifically (not the Macro Archetype) — see that Playbook's own Risk-On entry for exactly what the underlying three-vote mechanism is and isn't measuring. A Gold Signal firing alongside a Macro Archetype of A (Growth-positive risk-on) is two independent systems corroborating the same story from different data; firing while the Macro Archetype reads something else entirely is worth noticing, not dismissing.",
        falsificationChecklist = listOf(
            "Check the Gold Overlay field (Macro tab) — is gold's move reading as \"defensive\" or \"diversification\"? A Risk-On regime with a gold rally reading defensive rather than diversification is an unusual combination worth a second look before trusting the alignment.",
            "Is H4 confidence Medium or High for a real reason (multiple votes agreeing), or just barely over the two-of-three line? The Technical Regime Playbook's own falsification checklist for Risk-On applies directly here.",
            "How large was gold's actual move? The alert's own direction check only needs `up`/`down`, not a minimum size — a marginal gold tick alongside a Risk-On regime is a thinner alignment than a genuine, sized move.",
        ),
    ),
    AlertPlaybookEntry(
        key = "bear",
        title = "Gold Signal — Bearish",
        coreStory = "Gold is falling AND the Technical Regime independently reads Risk-Off on both H4 and H1 — the mirror case of the bullish signal. The same caveat applies in reverse: gold falling in a Risk-Off regime is the classic \"USD/liquidity demand overwhelms gold's own haven bid\" case (see the Regime Playbook's Regime D/E entries for exactly this mechanism on the FX side) — it isn't the only way gold can fall, just the specific case this alert is built to catch.",
        mechanism = "Identical mechanism to the bullish signal, mirrored — gold's own `down` delta crossed against a Risk-Off Technical Regime, H1 independently confirming the same Risk-Off read, H4 confidence Medium or High required.",
        relationToOtherSystems = "Same Technical Regime gate as the bullish case. Worth cross-checking against WHICH kind of risk-off is live on the Macro tab (Regime D: Recession shock, E: Liquidity shock, and I: European energy shock can all read \"Risk-Off\" on the Technical Regime first) — gold's own behaviour can differ meaningfully across those, particularly Regime E where gold itself can be sold for pure liquidity/margin reasons even though it's nominally the \"safe-haven\" asset.",
        falsificationChecklist = listOf(
            "Is this a genuine risk-off move, or the specific liquidity-shock case (Regime E) where gold itself gets sold for cash regardless of its haven status? The Gold Overlay field and the size/speed of the underlying VIX move are the tells the Regime Playbook's own Regime E entry names.",
            "Same confidence check as the bullish case — is H4 Medium/High for a real multi-vote reason, or barely past the threshold?",
            "Does USD strength (check the CSM strip) corroborate the same story, or is gold moving on its own? A Risk-Off regime with USD not actually confirming is a thinner version of the classic mechanism this alert assumes.",
        ),
    ),
).associateBy { it.key }
