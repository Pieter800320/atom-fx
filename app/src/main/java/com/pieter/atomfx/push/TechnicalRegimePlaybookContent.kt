package com.pieter.atomfx.push

/**
 * The Technical Regime Playbook — part 2 of the "living handbook" pass (Pieter, 2026-09-04),
 * extending the pattern proven on `archetype_change` (see RegimePlaybookContent.kt) to
 * `regime_flip`: the H4 structural regime shown at the wheel's hub.
 *
 * This is a genuinely different system from the Macro Archetype, not a rename of it — grounded
 * directly in `scanner/regime.py::classify_regime` (frozen, read in full before writing this):
 * three votes cast from CSM/pill data alone (safe-haven CSM divergence, USD-proxy CSM
 * divergence, risk-basket pill count), plus a ranging override that can force the whole
 * question moot before the votes are even tallied. No `evidence`/`conflicts` arrays exist on
 * this data shape (`RegimeBlock` is just `regime, confidence, score, stable`) — so unlike the
 * Macro Archetype, there is no live-evidence fragment to assemble per firing; the content
 * below is deliberately entry-level static prose, same as archetype_change's Notification
 * History path already is when no evidence snapshot exists.
 *
 * The single most important thing this content exists to prevent: reading "Confidence" or
 * "Score" here the same way the Macro Archetype's badges read. They are computed by unrelated
 * formulas and mean different things — see [TechnicalRegimeEntry.confidenceNote] and
 * [TechnicalRegimeEntry.scoreNote] on every entry.
 */
data class TechnicalRegimeEntry(
    val regime: String,
    val coreStory: String,
    val votesExplanation: String,
    val confidenceNote: String,
    val scoreNote: String,
    val relationToOtherSystems: String,
    val falsificationChecklist: List<String>,
)

val TECHNICAL_REGIME_PLAYBOOK: Map<String, TechnicalRegimeEntry> = listOf(
    TechnicalRegimeEntry(
        regime = "Risk-On",
        coreStory = "Two or more of three price-based checks agree that risk appetite is genuinely broad-based right now: safe-havens (JPY/CHF) are being sold relative to commodity/risk currencies (AUD/NZD/CAD) on CSM, the dollar is being sold relative to the other majors on CSM, or a clear majority of six risk pairs (AUDUSD, NZDUSD, GBPUSD, EURUSD, AUDJPY, NZDJPY) have bullish pills at H4. This is a purely technical read — it doesn't know or care WHY risk appetite is up, only that price itself is confirming it from more than one angle at once.",
        votesExplanation = "Vote 1 (safe-haven CSM): JPY+CHF average vs AUD+NZD+CAD average — needs a 15-point CSM gap the risk side's way to count as risk-on. Vote 2 (USD CSM): USD vs EUR/GBP/AUD/NZD average — needs a 20-point gap the non-USD side's way. Vote 3 (risk-basket pills): needs at least 2 more bullish than bearish pills across the six risk pairs at H4. Two of these three agreeing is enough to call the regime; the third can be dissenting or flat.",
        confidenceNote = "High means all three votes agree; Medium means two of three. This is NOT the same meaning as the Macro Archetype's confidence badge, which counts independent macro evidence axes (Risk/Rates/USD/Commodity/Safe-haven) — this one counts three views of essentially the same risk-currency price action (two CSM divergences plus one pill count), so even a High reading here is corroboration within one data source (price), not across independent markets the way the archetype's High is.",
        scoreNote = "The score's own docstring says \"0-10, higher = more Risk-Off\" — read literally that's only true for a Risk-Off regime. For a Risk-On regime specifically, the formula actually runs the opposite way: it climbs toward 10 as the Risk-On vote gets MORE unanimous and the safe-haven/risk-currency CSM gap widens, not less. Treat the score here as \"how convincingly Risk-On\", not as a position on a bipolar risk dial — a Risk-On score of 9 is a stronger reading than a Risk-On score of 4, full stop.",
        relationToOtherSystems = "Fully independent of the Macro Archetype (which reads macro_assets cross-asset data — SPX/VIX/yields/DXY/commodities/gold, not CSM or pills) — they're computed from different inputs and can, and do, disagree. Because this one is pure price/CSM, it typically moves first; the Macro Archetype often catches up a scan or two later once the cross-asset data confirms the same story. Gold Signal's own confirmation logic requires exactly \"Risk-On\" here (not Mixed, not Ranging) on H4 AND H1 before a bullish gold call is allowed to fire — so this regime is a hard gate on that notification, not just related colour.",
        falsificationChecklist = listOf(
            "Which two votes actually agreed? A win on Vote 3 (pill count) alone plus one CSM vote is thinner than a win built on both CSM votes agreeing — the pill count can flip on a single pair pulling back.",
            "Does the Macro Archetype agree? If the cross-asset picture (Macro tab) is showing something closer to Regime B (US rate dominance) or Regime F (Inflation shock) at the same time, this technical Risk-On read may be a shorter-lived price move, not a durable backdrop.",
            "Was the ranging override even in play? Check how many of the 12 pairs actually have a directional pill right now — a Risk-On call sitting on top of mostly-neutral price action is a thinner signal than one where most pairs are actively trending.",
        ),
    ),
    TechnicalRegimeEntry(
        regime = "Risk-Off",
        coreStory = "The mirror image of Risk-On: two or more of the same three checks now agree the other way — safe-havens are being bought relative to commodity currencies, USD is being bought relative to the other majors, or a clear majority of the six risk pairs have bearish pills at H4. Same caveat as Risk-On: this is price/CSM only, it doesn't distinguish a genuine growth scare from a liquidity event from a simple rate-driven USD bid — for that distinction, this is exactly what the Macro Archetype's Regime D/E split exists for.",
        votesExplanation = "Same three votes as Risk-On, run the other direction: safe-haven CSM 15+ points above the risk-currency average, USD CSM 20+ points above the non-USD average, or 2+ more bearish than bullish pills across the six risk pairs at H4.",
        confidenceNote = "High = all three votes agree, Medium = two of three — again, three lenses on one data source (CSM + pills), not three independent markets. Don't read this High the way the Macro Archetype's High reads (3+ of five genuinely independent cross-asset axes).",
        scoreNote = "For a Risk-Off regime the score does climb toward 10 as Risk-Off gets stronger — this is the one direction where the docstring's \"higher = more Risk-Off\" claim actually holds, because it's specifically the Risk-Off branch of the formula. It still isn't a dial that reads Risk-On on one end and Risk-Off on the other, though — Risk-On's own score climbs on its own separate scale (see the Risk-On entry).",
        relationToOtherSystems = "Independent of the Macro Archetype, same as Risk-On — but a Risk-Off read here is compatible with several different Macro Archetype regimes that mean very different things for positioning (D: Recession shock, E: Liquidity shock, I: European energy shock all show up as \"Risk-Off\" here first). Check the Macro tab before assuming which kind of risk-off this is. Gold Signal requires exactly \"Risk-Off\" here (H4 AND H1) before a bearish gold call fires.",
        falsificationChecklist = listOf(
            "Is this a genuine broad move, or one CSM vote dragged by a single currency? A JPY-specific safe-haven bid (carry unwind) can trip Vote 1 without the USD vote agreeing at all — check whether Vote 2 is also confirming before treating this as a broad risk-off.",
            "Does this coincide with a VIX spike large enough to suggest Regime E (Liquidity shock) on the Macro tab, rather than an ordinary Regime D read? The technical regime alone can't tell you which — it only says \"risk-off\", not \"how violent\".",
            "Is H1 confirming the same direction as H4, or diverging? Gold Signal specifically requires both to agree — a H4-only Risk-Off read is a weaker, more provisional signal than one both timeframes share.",
        ),
    ),
    TechnicalRegimeEntry(
        regime = "Mixed",
        coreStory = "None of the three votes reached the 2-of-3 majority either side needs — this is the residual case, not a positive read of \"market's confused\". It covers two genuinely different situations the label itself doesn't distinguish: a real split (one vote says risk-on, one says risk-off, one is flat), or simple non-confirmation (all three votes individually land in the flat/mixed zone without any of them crossing their own threshold). Both produce the same \"Mixed\" label and the same forced Low confidence.",
        votesExplanation = "Whenever risk-off votes and risk-on votes are both below 2 (and the ranging override hasn't already fired), the regime defaults to Mixed — by construction, not by any positive test for \"genuinely conflicted\".",
        confidenceNote = "Always Low, and the score is always fixed at exactly 5.0 — this isn't a measured reading, it's the formula's default when neither side reaches a majority. Don't read \"5.0\" as \"halfway between Risk-On and Risk-Off\" — it's simply the unset value.",
        scoreNote = "Fixed at 5.0 by construction, always — carries no information about how close either side came to a majority.",
        relationToOtherSystems = "A Mixed technical read can still coexist with a confident Macro Archetype call — they're computed from entirely different inputs, so a clean cross-asset story (Macro tab) can be sitting on top of genuinely split near-term price action, or vice versa. Gold Signal never confirms on a Mixed regime — it requires exactly Risk-On or Risk-Off on both H4 and H1.",
        falsificationChecklist = listOf(
            "Which vote(s), if any, actually leaned one way without reaching the threshold? A 1-1-1 genuine split reads very differently from three votes all sitting near-flat — check the D1/H1 squares in the sheet to see if a shorter or longer timeframe already resolved the same question.",
            "Is this Mixed read new, or has it held for several scans? A regime_flip alert only fires on the transition INTO Mixed — if it's persisted, that itself says something (indecision, not just a one-scan blip).",
            "Does the Macro Archetype have a confident read right now? If it does, that's a genuine second opinion worth weighing more heavily while the technical picture is unresolved.",
        ),
    ),
    TechnicalRegimeEntry(
        regime = "Ranging",
        coreStory = "This is not a vote outcome at all — it's an override that runs BEFORE the three votes are even tallied. If fewer than 40% of all 12 pairs (not just the six risk pairs) have any directional pill at H4, the classifier forces \"Ranging\" outright and never gets to ask what the safe-haven/USD/risk-basket votes would have said. Ranging and Mixed are easy to treat as synonyms for \"unclear\" but they're structurally different diagnoses: Mixed means the votes disagreed; Ranging means there wasn't enough directional data across the whole pair set to even run the vote.",
        votesExplanation = "A single check, run first: directional pill count across all 12 pairs at H4, divided by 12. Below 40% forces Ranging and skips the vote tally entirely — the three votes described in the Risk-On/Risk-Off entries never execute this scan.",
        confidenceNote = "Always Low, and the score is always fixed at exactly 5.0, same as Mixed — but for a different reason: it isn't a failed vote, it's a bypassed one. A Ranging read tells you nothing about which way sentiment would lean once price starts trending again.",
        scoreNote = "Fixed at 5.0 by construction, always — no information content, same caveat as Mixed.",
        relationToOtherSystems = "Independent of the Macro Archetype as always, but worth noting specifically here: a broad lack of directional conviction in price doesn't mean the cross-asset/macro backdrop is quiet too — the Macro tab can still show a confident archetype call while most individual pairs sit range-bound. Gold Signal never confirms on Ranging.",
        falsificationChecklist = listOf(
            "Which pairs are actually neutral, and which handful are still trending? A Ranging read driven by 5 pairs sitting exactly at the 40% line is a much thinner call than one where almost every pair is flat.",
            "Is this Ranging read about to flip based on a single pair's pill changing? Since the override is a hard threshold (40%, not a smoothed measure), it can be more sensitive to one or two pairs crossing the line than the label suggests.",
            "Check the wheel directly for which pairs are actually neutral right now — this entry can only describe the mechanism, not which specific pairs are driving today's reading.",
        ),
    ),
).associateBy { it.regime }
