package com.pieter.atomfx.ui.settings

/**
 * The Library — Pieter's own study material (2026-09-03 ask): "a full library that explains
 * every single element, calculation and item in the app," replacing the six-line ABOUT section.
 *
 * Every entry here is grounded directly in the frozen `scanner` package's own source (and the
 * `scanner.extend` additive layer) — read in full while writing this, not paraphrased from a doc. Numbers
 * (weights, thresholds) are quoted exactly as they appear in code as of 2026-09-03; if a frozen
 * file or an EXTEND config value ever changes, this file goes stale and needs a re-read, the same
 * way any other "expose, don't recompute" surface would.
 *
 * [howItWorks] describes the calculation in prose, not code — accurate, but for a reader who
 * wants to understand the mechanism, not re-implement it. [whyItMatters] is the actual "why do I
 * care" context — what a term means for reading the app day to day.
 */
data class LibraryEntry(
    val id: String,
    val term: String,
    val category: String,
    val summary: String,
    val howItWorks: String,
    val whyItMatters: String,
)

val LIBRARY_CATEGORIES = listOf(
    "The Wheel", "Momentum & Price Action", "Currency Strength",
    "Setup Quality", "Regime & Macro", "Cross-Asset & Correlation", "Alerts & Recommendation",
)

// Signals Roadmap §5 (2026-09-09) — Pieter's own reversal checklist, written out in full
// because he deliberately doesn't want it hardcoded into the bb_touch alert itself (see
// bb_touch.py's own doc comment): a touch alone fires the notification, but whether it's
// actually a good reversal candidate is his own judgment call, informed by this. Shared
// verbatim between this Library entry and the Watchlist card's own tap-to-reveal section
// (WatchlistScreen.kt) — one source of text, so the two surfaces can never drift apart.
const val BB_REVERSAL_HOW_TO_READ =
    "Check five things, in this order. (1) ADX — below ~20 means the market is ranging, " +
    "where a touch is more likely to mean-revert cleanly; above ~20 means it's trending, and " +
    "you should only trust a touch that's a pullback IN the trend's own direction, not a fade " +
    "against it. (2) The pair's own D1/H4/H1 pills — the classic pullback signature is the " +
    "higher timeframe still holding its trend (e.g. D1 bull/bull_strong) while the lower ones " +
    "have cooled or flipped (H4/H1 neutral or bear) — a temporary counter-move inside a trend, " +
    "not a real reversal. (3) Reset Score, read in the touch's implied direction (bull for a " +
    "lower-band touch, bear for an upper-band touch) — a low reading means price is genuinely " +
    "stretched from its own equilibrium, corroborating the touch independently. (4) Band-width " +
    "trend — a touch arriving on expanding bands is more likely a breakout candle than " +
    "exhaustion; a touch on narrow or converging bands is the better reversal candidate. " +
    "(5) Structure — a fresh CHoCH in the touch's own direction (Breakdown tab) is real, " +
    "independent confirmation that price action itself is turning."

const val BB_REVERSAL_WHY_IT_MATTERS =
    "A bare band touch is close to meaningless on its own — the bands answer \"is price high " +
    "or low relative to its recent history,\" not \"buy or sell here.\" The five checks above " +
    "are what separate a touch that's about to mean-revert from one that's just a pause before " +
    "the trend continues. None of them are hardcoded into the alert on purpose — the " +
    "thresholds that actually work are still being calibrated, so the notification stays broad " +
    "(any touch) and the judgment stays with you, informed by this checklist and whatever you " +
    "learn from the Watchlist over time."

val LIBRARY_ENTRIES: List<LibraryEntry> = listOf(
    LibraryEntry(
        id = "potential",
        term = "Overall (Continuation Score)",
        category = "The Wheel",
        summary = "0–100. The wheel's flagship/default reading — a continuous, ungated \"how good is this setup right now\" score.",
        howItWorks = "This is `cont`, the frozen Continuation Score (see \"Continuation Score\" under Setup Quality for the full weighted breakdown — timeframe alignment, entry position, CSM divergence, regime fit, session fit). Nothing new is computed for the wheel; the Overall wing is a straight read of the same number that already drives the app's own pair ranking (`rank.py`).",
        whyItMatters = "Simplification Rework, 2026-09-05 (Pieter's own call: \"drop the gate, use a composite score\") — this replaced the old sequential six-factor gate (Regime→Flow→Breadth→Momentum→Structure→Entry, a pair only \"levels up\" 0–6 by clearing each in strict order). That gate is retired app-wide: no more \"blocked at\" factor, no more pass/fail checklist. Overall answers the same question the old Level/Potential number tried to — \"how far along is this idea\" — with one continuous, already-proven number instead of a stepped count.",
    ),
    LibraryEntry(
        id = "level-state",
        term = "Setup Bands",
        category = "The Wheel",
        summary = "The Continuation Score split into four plain-language bands: LOW SETUP, DEVELOPING, STRONG SETUP, A+ SETUP.",
        howItWorks = "LOW SETUP is below 45 (the same qualifying line `rank.py` itself gates on), DEVELOPING is 45–64, STRONG SETUP is 65–84, A+ SETUP is 85 and above.",
        whyItMatters = "These four bands are what the pair sheet's own header word and the wheel's Overall wing colour language sort pairs into — the fastest read of \"is this worth a look\" on the whole app, now with no separate gate mechanism behind the label.",
    ),
    LibraryEntry(
        id = "wheel-four-wings",
        term = "The Wheel's 4 Wings",
        category = "The Wheel",
        summary = "The wheel's middle ring always shows the same 12 pairs — the 4 corner wings pick which value fills them: Overall, Trend, Momentum, or Volatility.",
        howItWorks = "Every wing's wedge fill is a magnitude of signal, never a raw value — a bigger wedge always means a stronger reading, on every wing, so the visual language stays the same regardless of which wing is active; colour is the separate channel that carries direction (except Volatility — see below). Every wing is also a FIXED timeframe (2026-09-06, Pieter's own settled call, after trying and rejecting a D1/H4/H1 toggle across the wheel) — Overall is the Continuation Score (see \"Overall\" above, inherently cross-timeframe). Momentum is fixed at D1 (0–100, 50 = neutral, not the D1/H4/H1-blended CMP). Trend (see \"ADX\" under Momentum & Price Action) is fixed at H4 — there is no D1 or H1 ADX anywhere in this system, so this isn't a preference, it's the only real number that exists. Volatility (see \"ATR Percentile\" under Setup Quality) is fixed at D1 too — it's computed from D1 candles by default (H4 only as a rare fallback when a pair has too little D1 history), meaning it reads the exact same candles Momentum does. Because Momentum's own reading is bipolar (0 and 100 are opposite extremes, not \"none\" and \"max\"), the wheel folds it around the neutral point (|momentum − 50|) before filling, then takes the square root of that fold so a real-but-modest 8-point deviation shows as a legible ~28% fill instead of a barely-visible 8%. Trend colours from the H4 pill specifically (not the pair's overall D1-derived direction) — ADX is an H4 number, and a pair can show real H4 trend strength while the D1 composite reads neutral; when that happens (ADX 25+, H4 pill neutral) the wedge reads watch-tinted rather than a misleadingly grey \"no signal.\" Volatility isn't bullish or bearish at all, so instead of a direction colour it uses the same 20–70 \"sane band\" the ATR Percentile entry already describes.",
        whyItMatters = "Simplification Rework, 2026-09-05 (Pieter's own call) — replaced the previous four wings (Potential/Momentum/ADX/Reset Score), which had grown into a toggle whose names didn't map to a mental model anyone could hold onto. Structure was deliberately left off the wheel entirely — it's event-based (a BOS or CHoCH either just happened or it didn't), not a magnitude like the other three, so stretching it into a wedge fill would have meant something different from every other wing. Structure still gets its own Overview row on the pair sheet and its own Structure alert — just not a wheel wing. 2026-09-06 — the fixed timeframes turn out to form a genuine consensus funnel, not an arbitrary mix: H4 Regime (the hub) sets the macro backdrop, H4 Trend confirms something persistent is actually happening at that same structural level (and directly shares its H4 timeframe with the H4 inputs — CSM divergence, regime fit, the ADX gate — that dominate Overall's own formula), D1 Momentum is the actual tradeable direction, and D1 Volatility judges entry timing on those exact same D1 candles. A pair where all four agree is a real, mathematically-grounded confluence, not four independent opinions that happen to align.",
    ),
    LibraryEntry(
        id = "mom1212",
        term = "MOM1212",
        category = "Momentum & Price Action",
        summary = "The core momentum oscillator, 0–100, one reading per timeframe (D1/H4/H1).",
        howItWorks = "Compares a 12-bar moving average to itself 12 bars earlier, normalised by 14-bar ATR, then squashed through a sigmoid (tanh) curve so it always lands between 0 and 100 with 50 as neutral. Each timeframe also carries its own delta — how much that reading has moved over a fixed lookback (5 bars on D1, 30 on H4, 120 on H1).",
        whyItMatters = "This is what \"momentum\" means everywhere in the app — the raw ingredient both CMP and the Momentum tab's bars are built from.",
    ),
    LibraryEntry(
        id = "cmp",
        term = "CMP (Composite Momentum Position)",
        category = "Momentum & Price Action",
        summary = "0–100. One number blending D1/H4/H1 momentum into a single composite read.",
        howItWorks = "Weighted 50% D1, 30% H4, 20% H1 — computed on the raw, pre-sigmoid momentum values and squashed once at the end, not averaged from three already-squashed numbers.",
        whyItMatters = "CMP is what Factor 4 (Momentum) actually gates on, and what the Momentum tab's CMP square shows.",
    ),
    LibraryEntry(
        id = "structure-events",
        term = "Structure — BOS / CHoCH",
        category = "Momentum & Price Action",
        summary = "Market-structure events built from genuine swing highs/lows, not an indicator.",
        howItWorks = "Finds swing pivots (a bar strictly higher/lower than several bars on both sides), reads the trend from the last two swings (higher highs + higher lows = bull, the reverse = bear), then classifies the latest close: breaking beyond the last swing in the trend's own direction is a BOS (continuation); breaking the opposite way is a CHoCH (a potential reversal warning). A strength score, 0–1, from how far price broke past that swing relative to ATR, then scales the pair's technical score up to +30% on a BOS or down to −60% on a CHoCH.",
        whyItMatters = "This is the one purely price-action-based signal in the whole engine — no oscillator, just where price actually broke. A fresh BOS or CHoCH also fires a Structure alert (see State-Transition Alerts) the moment it happens — tap the book icon on that alert's Notification History card (2026-09-04) to open its Structure Playbook, the deeper read on what a break actually implies and where it can mislead.",
    ),
    LibraryEntry(
        id = "adx",
        term = "ADX (Average Directional Index)",
        category = "Momentum & Price Action",
        summary = "0–100. Wilder's classic trend-strength indicator — how strongly a pair is trending, independent of which way.",
        howItWorks = "14-period directional movement: +DI and −DI track how much of each bar's range moved in the bullish vs bearish direction (ATR-normalised), DX is the normalised spread between them, and ADX is DX's own 14-period average. No neutral midpoint — 0 means no directional movement at all, higher means a stronger, more persistent trend either way (in practice rarely much above ~60–70). Computed once at H4 and reused everywhere ADX appears in the app (the wheel's own ADX wing included) — there's no separate D1/H1 reading. The 5-State Technical Score uses this same H4 ADX as a score multiplier, not a second calculation: below 15 the whole technical score zeroes out (no real trend to read), 15–20 scores at half weight, 20–25 at three-quarters, 25+ at full weight; Continuation Score gates on it too (capped at 45 below ADX 20).",
        whyItMatters = "ADX answers a different question than direction — CSM, pills, and Overall already cover that. It answers whether there's an actual trend worth trading, or just noise with a directional lean. Two pairs can share the same CSM-implied bias and read completely differently on ADX; that gap is exactly what the wheel's own Trend wing (renamed 2026-09-05 — same field, clearer name) and the pair sheet's Overview TREND row both surface. 2026-09-06 (Pieter's own catch) — a flat \"25+ = strong trend\" label was treating ADX 32 and ADX 88 as the same thing, which they aren't: the Overview row now reads 25–39 as trending, 40–59 as very strong, 60+ as extreme and worth treating with caution (rare, often an overextended/exhaustion-prone move, not simply \"more is better\"). Its colour also now comes from the H4 pill specifically, not the pair's overall D1-derived bias — ADX is an H4 number, so a pair can show real H4 trend strength while the D1 composite reads neutral; that mismatch gets its own watch-tinted flag rather than a misleadingly grey \"strong trend.\"",
    ),
    LibraryEntry(
        id = "five-state-score",
        term = "The 5-State Technical Score",
        category = "Momentum & Price Action",
        summary = "The underlying Strong Buy / Buy / Neutral / Sell / Strong Sell read behind every pill colour.",
        howItWorks = "Blends an EMA200 trend read, a 3-vote momentum group (EMA50 vs price, DMI, and MACD histogram direction), and a graduated RSI score, then scales the total by ADX strength — below ADX 15 it's zeroed out entirely, since there's no real trend to read. A structure multiplier (see BOS/CHoCH) and a same-direction-conflict penalty adjust it further before the five-state label is assigned by regime-specific thresholds.",
        whyItMatters = "Every pill you see — D1/H4/H1, bull, bull_strong, neutral, bear, bear_strong — comes from this one engine. It's the single source of truth for \"what does this timeframe think.\" The Pair sheet's 3-TF alignment strip (SB/B/N/S/SS, one square per timeframe) is this same read, compacted; three Strong Buys or three Strong Sells in a row also fires an Alignment alert — tap its book icon (2026-09-04) for the Alignment Playbook, on why three independently-computed timeframes agreeing is genuine confluence, not circular.",
    ),
    LibraryEntry(
        id = "csm",
        term = "CSM (Currency Strength Model)",
        category = "Currency Strength",
        summary = "0–100 per currency, per timeframe — how strong or weak each of the 8 currencies is right now.",
        howItWorks = "Every one of 16 fixed currency pairs contributes an ATR-normalised return to both its base currency (positive) and its quote currency (negative). Average each currency's contributions, then stretch the 8 currencies' averages so the weakest sits at 0 and the strongest at 100. D1 blends 14-bar D1 (70%) and H4 (30%) returns; H4 blends 5-bar H4 (80%) and 8-bar H1 (20%); H1 is pure 6-bar H1.",
        whyItMatters = "CSM is the foundation Currency Flow, Breadth, and the home screen's Currency Strength strip (below the wheel) are all built on.",
    ),
    LibraryEntry(
        id = "csm-delta",
        term = "CSM Delta",
        category = "Currency Strength",
        summary = "How much a currency's CSM has moved over a defined lookback — \"getting stronger\" vs \"strong.\"",
        howItWorks = "Recomputes CSM on a price history sliced back by a fixed offset — about a day for D1/H4, six hours for H1 — using the exact same CSM function, then subtracts: now minus then.",
        whyItMatters = "CSM tells you a level; Delta tells you a direction of travel — together they separate a currency that's strong-and-fading from one that's strong-and-accelerating. 2026-09-06 — the Currency Strength strip now has a Strength/Flow toggle (sharing the D1/H4/H1 row) that switches which one it plots: Strength is the familiar bottom-anchored level bars; Flow replaces the chart entirely with a diverging view off a zero-line — bars grow up for strengthening, down for weakening, sized by Delta. Two different chart shapes on purpose, not the same bars recoloured, so which one you're looking at is obvious without reading a label.",
    ),
    LibraryEntry(
        id = "currency-flow",
        term = "Currency Flow — Leader / Laggard",
        category = "Currency Strength",
        summary = "The fastest-moving currencies right now, by CSM Delta — not the strongest/weakest in absolute terms.",
        howItWorks = "Leader = the currency with the highest H4 CSM Delta; laggard = the lowest. Absolute leader/laggard is a separate, simpler read: whichever currency has the highest or lowest raw CSM level right now, regardless of how fast it's moving.",
        whyItMatters = "A currency can be an absolute laggard (weak) while still being the flow leader (getting less weak, fastest) — the two numbers answer different questions, and the app deliberately keeps them apart rather than blending them. 2026-09-06 — the dedicated Currency Flow sheet (a market-wide, sorted-by-strength list) was retired along with the old Summary cascade, its only entry point; the Currency Strength strip's own Flow view (tap Strength/Flow below the wheel) is where this now lives visually, per-currency rather than as a single named leader/laggard pair.",
    ),
    LibraryEntry(
        id = "breadth",
        term = "Breadth",
        category = "Currency Strength",
        summary = "The share of a currency's 16-pair relationships that agree with its net direction — proof a move isn't just one pair.",
        howItWorks = "Each currency appears in a different number of the 16 CSM pairs (USD in 7, AUD in 5, GBP and JPY in 4, EUR/CHF/CAD/NZD in 3). Breadth is how many of those relationships point the same way as the currency's own net direction, divided by how many it appears in at all — always compared as a percentage, never a raw count, because the totals differ per currency. 70% or above is \"strong,\" 50% or above is \"moderate,\" below that is \"weak.\"",
        whyItMatters = "This is the difference between \"EUR is strong\" (broad, real) and \"EUR is strong against JPY\" (one relationship — could be a JPY story, not a EUR story).",
    ),
    LibraryEntry(
        id = "setup-rank",
        term = "Setup Rank",
        category = "Setup Quality",
        summary = "0–10. A deterministic, frozen quality score for a pair's trade idea — independent of Potential.",
        howItWorks = "A weighted blend: Continuation 25%, CMP 20%, momentum delta 15%, D1 CSM divergence 20%, regime fit 10%, cross-asset tailwinds 10%. Pairs with a Continuation Score below 45 are excluded from ranking entirely.",
        whyItMatters = "2026-09-05 — no longer shown on the pair sheet directly (the old Entry tab, its only visible home, was retired along with the six-factor gate it fed). Still computed every scan and still feeds `potential.py`'s own Level/State internally; the pair sheet's headline number is now Overall (`cont`), a different score with a different job. 2026-09-06 — the Home screen's own \"Summary\" cascade, which used to read `potential.py`'s Level/State path for its TOP PAIR row, is retired too (see \"Recommendation Engine\"'s own entry). This same score resurfaced the same day, though, in a different spot: each Home Recommendation glyph's own panel shows \"SETUP SCORE\" — the exact `ranked.top[].score` this entry describes, just read per-pair instead of as a standalone row.",
    ),
    LibraryEntry(
        id = "continuation",
        term = "Continuation Score",
        category = "Setup Quality",
        summary = "0–100. How likely the current move is to keep going, once a direction has actually committed on D1 or H4.",
        howItWorks = "Direction first comes from the D1 pill; if D1 itself reads neutral, it falls back to H4's own pill instead of returning a flat 0 (2026-09-06 — a pair where H4/H1 are already aligned and structure's already confirming shouldn't score nothing just because D1 hasn't caught up yet; a genuine 0 still applies when neither D1 nor H4 has any bias at all). Once a direction exists, six weighted components apply: timeframe alignment across D1/H4/H1 (35%), entry position — a blend of Reset Score and ATR Percentile (23%), CSM divergence (16%), regime fit (13%), H4 structure confirmation — does the latest H4 BOS/CHoCH agree with the trade's direction (5%, 2026-09-06 — replaces the original Forex1212 \"rate differential\" slot, which this app never populated: no live rates feed, and a manually-typed-and-saved workflow wasn't worth building; H4 structure was already computed every scan and unused by this score, the natural fit), session fit (8%). Then two gates apply: capped at 45 if H4 ADX is below 20, and capped further on a counter-regime trade. CSM divergence is itself capped at the \"no reliable info\" level (2026-09-06) when H4's own CSM dispersion — see H4 Structural Regime for what that means — ranks in the bottom 20% of its last ~60 scans; CSM's 0–100 scale is min-max rescaled every scan, so a point-gap on a thin-dispersion day is more likely rescaling noise than a real signal, and no fixed floor on the gap itself could tell the two apart.",
        whyItMatters = "This is the single number Factor 6 (Entry) and Setup Rank both lean on hardest — the app's best answer to \"will this actually continue.\" 2026-09-06 fix alongside the component swap: the timeframe-alignment scoring had a gap where \"H4 alone disagrees while D1+H1 both support the trade\" fell through into the same worst score as a full three-way conflict — now scores the same as its mirror case (H4 agrees, H1 alone disagrees) instead.",
    ),
    LibraryEntry(
        id = "reset-score",
        term = "Reset Score",
        category = "Setup Quality",
        summary = "0–100. A directional mean-reversion oscillator — how \"stretched\" price is from its recent equilibrium.",
        howItWorks = "Blends an RSI-derived position, distance from the 20-period SMA, a momentum-divergence term, and a volatility z-score. Read directionally: for a long, oversold or below-mean scores low — that's a fresh, good entry; for a short, overbought or above-mean scores low.",
        whyItMatters = "A low Reset Score means price has pulled back to a sensible entry in your direction; a high one means you'd be chasing an already-extended move. 2026-09-05 — no longer shown on the pair sheet directly (its old Entry tab home was retired), but still computed every scan and still feeds `conviction.py`'s extension input (how many of a currency's pairs are overextended in its own direction) — nothing changed there, just no visible row of its own any more.",
    ),
    LibraryEntry(
        id = "atr-percentile",
        term = "ATR Percentile",
        category = "Setup Quality",
        summary = "0–100. Where today's volatility (ATR) ranks against the last 52 bars.",
        howItWorks = "A straight percentile rank — what share of the last 52 ATR readings sit below today's.",
        whyItMatters = "Too low, below roughly 20, means the market's too quiet to trust a breakout; too high means you might be entering into a volatility spike. 20–70 is the app's own \"sane\" entry band. Crossing 90 fires a Volatility alert (State-Transition Alerts) — tap its book icon (2026-09-04) for the Volatility Playbook, on why an expansion is a risk-sizing signal first, an opportunity signal second. 2026-09-05 — this is also the wheel's Volatility wing and the pair sheet's Overview VOLATILITY row (same field, same 20–70 band, just surfaced in two more places).",
    ),
    LibraryEntry(
        id = "h4-regime",
        term = "H4 Structural Regime",
        category = "Regime & Macro",
        summary = "Risk-On / Risk-Off / Mixed / Ranging — the app's read of the whole market's current mood.",
        howItWorks = "Four votes are cast: (1) safe-havens JPY/CHF vs risk currencies AUD/NZD/CAD by CSM, (2) USD vs the other majors by CSM, (3) how many of six risk pairs' pills are bullish vs bearish, (4) a ranging override that forces \"Ranging\" outright if fewer than 40% of all 12 pairs have any directional pill at all. Two or more votes agreeing on the same side wins; all three agreeing is High confidence, two is Medium. No majority is Mixed. Votes 1 and 2 are both CSM point-gap checks; CSM's 0–100 scale is min-max rescaled every single scan (whichever currency is strongest THIS scan is forced to exactly 100), so a gap can look identical on a dead-quiet day and a violently trending one. To catch that, each timeframe's raw pre-normalisation spread is tracked over its trailing ~60 scans (2026-09-06); when today's spread ranks in the bottom 20% of that history — thin enough that the gap is more likely rescaling noise than a real signal — votes 1 and 2 are both forced to Mixed rather than trusted. New: takes ~2.5 days of hourly scans after this ships to accumulate enough history to ever trigger; until then it's a no-op.",
        whyItMatters = "This is the regime shown at the wheel's hub, and it gates Factor 1 — nothing can pass Regime under a Mixed or Ranging backdrop. Tap the book icon on the Regime sheet (2026-09-04) to open its Regime Playbook — the deeper per-regime read of what the three votes and the score actually mean for whichever regime is live, and how this technical read relates to the separate Macro Archetype and to Gold Signal's own confirmation gate.",
    ),
    LibraryEntry(
        id = "macro-archetype",
        term = "Macro Archetype",
        category = "Regime & Macro",
        summary = "One of ten named macro regimes (A–J) from the FX macro handbook, matched against real cross-asset moves.",
        howItWorks = "Ten regimes each have a fixed strong/weak currency signature — e.g. Growth-positive risk-on favours AUD/NZD/CAD over JPY/CHF; Disinflationary easing favours AUD/NZD/EUR/GBP over USD. Five evidence axes (Risk, Rates, USD, Commodity, Safe-haven) are each read up/down/flat, and every archetype is scored by how many distinct axes support it — not how many correlated indicators happen to agree. Confidence: High at 3 or more distinct axes, Medium at 2, Low at 1 or fewer. Liquidity Shock is always capped at Low — its correlations are considered too unstable to trust. 2026-09-06 — the archetype PICK now reads the axes on a W1 basis (5 trading sessions), not 1-day: the handbook itself warns that a single day's level is the wrong thing to name a \"regime\" from (its own words: \"the path matters,\" \"VIX fell from 30 to 20 over several sessions\" is the informative read, not one day's number). The regime also won't flip on a marginal day any more — a new leader has to actually beat the standing regime's own axis count on today's numbers, not just edge it in an alphabetical tie-break; ties keep the standing regime. The one deliberate exception: a sudden VIX spike (Liquidity Shock / Oil Shock / Carry Unwind's shock check) still reads off today's 1-day move, not the 5-day window — a real shock (the handbook's own August 2024 carry-unwind case study) is a today event that a 5-day average would smooth away entirely.",
        whyItMatters = "This is the anti-double-counting discipline the whole Macro screen is built on: ten raw instruments collapse to five real, independent pieces of evidence. Now that the pick itself is W1-based and sticky, the archetype should actually behave like a regime — persisting for days, not flipping on ordinary daily noise — which is also what makes the `archetype_change` push notification worth reading when it does fire.",
    ),
    LibraryEntry(
        id = "evidence-axis",
        term = "Evidence Axis",
        category = "Regime & Macro",
        summary = "One of five buckets — Risk, Rates, USD, Commodity, Safe-haven — that groups correlated cross-asset signals so they're only counted once, now also tagged with whether TODAY's move is backing or fighting that axis's own trend.",
        howItWorks = "Risk = the net of SPX/VIX/Copper/BTC direction. Rates = US10Y and US3M direction. USD = DXY direction alone. Commodity = WTI or Copper direction. Safe-haven = Gold direction. 2026-09-06 — each axis on the Macro screen's Evidence list now carries a second read alongside its W1-based support flag: \"Today confirms this trend\" (today's 1-day move agrees with the axis's own 5-day direction), \"Today is fighting this trend\" (today's move runs the other way), or \"No fresh move today\" (today's move is below the daily noise threshold either way).",
        whyItMatters = "Without the axis grouping, a single risk-off move could look like four or five \"independent\" confirmations just because VIX, SPX, Copper and BTC all move together. The new confirm/diverge/quiet tag is what turns Evidence from a static checklist into an actual decision cue — the FX Macro Flow Handbook's own \"demand confirmation\" step (don't trade the story alone, check whether price action is actually confirming it) made concrete, per axis, every scan.",
    ),
    LibraryEntry(
        id = "gold-overlay",
        term = "Gold Overlay",
        category = "Regime & Macro",
        summary = "Defensive gold vs diversification gold — two different reasons gold can be rising.",
        howItWorks = "Only evaluated when gold is actually up. It reads as \"defensive\" if gold is rising alongside stress signals — VIX up, SPX down, or yields falling; otherwise it reads as \"diversification.\"",
        whyItMatters = "Gold up means something different in a panic than it does in a steady reflation trade — this overlay names which story you're actually in.",
    ),
    LibraryEntry(
        id = "usd-regime",
        term = "USD Regime",
        category = "Regime & Macro",
        summary = "Rate dominance / growth dominance / global risk-off / confidence shock — what a USD move actually means right now.",
        howItWorks = "If DXY isn't rising, it reads as growth dominance (when risk-on) or neutral. If DXY is rising: a VIX spike makes it a confidence shock; rising rates make it rate dominance; risk-off without a rates story makes it global risk-off; otherwise it defaults to rate dominance.",
        whyItMatters = "\"USD up\" is one of the most overloaded signals in FX — this classifies why before you read too much into the direction alone.",
    ),
    LibraryEntry(
        id = "cross-assets",
        term = "Cross-Asset Instruments",
        category = "Cross-Asset & Correlation",
        summary = "10 non-FX instruments the app watches for macro context: VIX, US10Y, US3M, the 10Y–3M curve, DXY, Gold, S&P 500, Copper, WTI, Bitcoin.",
        howItWorks = "Each gets a direction (up/down/flat) and a percentage or basis-point change over its window — the raw material every evidence axis and every archetype signature is built from. In the Cross-Assets sheet, an instrument whose Evidence Axis currently supports the regime gets \"confirms regime\" appended to its caption — a plain-text read of the same axis data, not a separate calculation.",
        whyItMatters = "No ring of their own on the wheel any more (Wheel v2 briefly gave them one; Pieter had it removed 2026-09-06 to keep the wheel to pairs only) — they're the entire backbone of the Macro screen and the Cross-Assets sheet instead. \"Confirms regime\" is the fastest way to see, instrument by instrument, which moves are actually backing the regime you're trading and which are just noise. 2026-09-06 — the Macro screen's own cross-asset list was restyled to look exactly like the Cross-Assets sheet's cards (direction-tinted fill, same impact/confirms caption, value+delta on the right) rather than a plain divided table; tapping a card opens that same sheet, pinned to the tapped asset — previously only reachable by tapping the wheel's own cross-asset wedge, which is what the removal above took away.",
    ),
    LibraryEntry(
        id = "correlation",
        term = "Correlation",
        category = "Cross-Asset & Correlation",
        summary = "How closely two pairs have moved together over the last ~8 trading days, from −1 to +1.",
        howItWorks = "Pearson correlation of H4 percentage returns over the last 50 H4 bars, for every pair against every other pair.",
        whyItMatters = "A pair at +0.85 correlation with one you already hold isn't a second independent idea — it's largely the same trade twice. The pair sheet's Correlation tab exists to catch that before you double up risk.",
    ),
    LibraryEntry(
        id = "gold-signal",
        term = "Gold Signal",
        category = "Alerts & Recommendation",
        summary = "An alert that fires when gold's move and the market regime line up in a specific way.",
        howItWorks = "Fires bearish when gold is falling and the H4 regime is Risk-Off; bullish when gold is rising and the H4 regime is Risk-On. It also checks whether the H1 regime independently agrees, as a secondary confirmation flag.",
        whyItMatters = "The original push-notification type the app sends, alongside price-level alerts and the newer state-transition alerts — a live, regime-aware gold read rather than a bare price alert. Tap the book icon on a Gold Signal notification's history card (2026-09-04) for the Gold Signal Playbook — what the Technical Regime gate is and isn't confirming, and the specific case (Regime E: Liquidity shock) where gold's own \"safe-haven\" behaviour can invert.",
    ),
    LibraryEntry(
        id = "recommendation-engine",
        term = "Recommendation Engine",
        category = "Alerts & Recommendation",
        summary = "The AI-narrated headline trade idea (Insights) and, on Home, one small glyph per ranked setup — deterministic picks, AI only writing Insights' own explanation.",
        howItWorks = "A fully deterministic \"seed\" picks the bias, action, primary pair, direction, confidence, and next catalyst from data already in signals.json — no model is involved in that decision. An optional AI call then writes the human-readable headline, rationale, and invalidation text around that seed; if the model's wording ever contradicted the seed, the seed wins, not the model. If the AI call fails for any reason, no recommendation is published at all, rather than showing something half-written.",
        whyItMatters = "Worth knowing which parts of a recommendation are computed and which are narrated — the pair, direction and confidence are a real signal; the prose around them is commentary on that signal, not a separate one. Insights keeps the full AI-narrated version (headline, rationale, invalidation, next catalyst) on its own slower cadence. 2026-09-06 — Home's own version of this reads a different, related deterministic list instead of the single narrated pick: one small glyph per pair in `ranked.top` (Setup Rank's own top-3, never all 12 — see that entry), sitting in a row above the wheel, horizontally scrollable on the rare chance more ever qualify at once. Tapping a glyph opens a full-width panel below the row (pushing the wheel/CSM strip down, not floating over them) showing that pair's direction, its Setup Score, and its own Regime/Trend/Momentum/Volatility/Structure consensus dots (the same five the pair sheet's Overview tab shows) — never the AI text, and refreshes every hourly scan since no model call is involved. Two genuinely different vantage points on related picks, not the same sentence shown twice.",
    ),
    LibraryEntry(
        id = "level-ema-alerts",
        term = "Level & EMA Alerts",
        category = "Alerts & Recommendation",
        summary = "A user-configured price-level alert type, separate from the Gold Signal — currently greyed out in Settings, not yet available.",
        howItWorks = "Level alerts would fire when price crosses a user-set level, read from data/level_alerts.json — a file the previous (Forex1212 web dashboard) iteration wrote via the GitHub API. That dashboard doesn't exist in ATOM FX, and nothing on-device writes that file yet, so the toggle in Settings > Notifications is greyed out (2026-09-06) rather than presented as live. EMA touch alerts (price touching its own H4 EMA200/EMA50) were removed the same way one step earlier — the checker function still exists in level_ema_alerts.py but scan_h1.py no longer calls it.",
        whyItMatters = "Kept, not deleted — Pieter's call, in case an on-device \"set alert\" row on the pair sheet gets built later, at which point this becomes a real, live alert type again.",
    ),
    LibraryEntry(
        id = "conviction",
        term = "Conviction (Positioning)",
        category = "Regime & Macro",
        summary = "A −100..+100 per-currency crowding read from free CFTC positioning data, updated weekly, not hourly.",
        howItWorks = "Six inputs blend into one score: CFTC leveraged-fund positioning (a 52-week percentile, contrarian at extremes — deeply crowded long is bearish, deeply crowded short is bullish), open-interest momentum (is new money entering with the trend), asset-manager-vs-leveraged-fund alignment (structural vs tactical agreement), a CSM extreme read (contrarian — broadly bought/sold), an extension read (how many of the currency's pairs are overextended on reset_score, in the currency's own direction), and a breadth read (how broadly the currency's own move is confirmed — the same breadth used elsewhere in the app). The last two are adapted from the original design to use data ATOM FX already computes hourly, not a literal port. EWMA-smoothed week to week so it doesn't flicker. CFTC publishes Fridays covering the prior Tuesday, so this is always a few days old by nature — a positioning overlay, not a live signal.",
        whyItMatters = "Positioning tells you who's already in a trade, which price action alone can't — a currency can look technically strong while positioning is dangerously crowded, or wobbly while positioning is actually clean. A reading of 80 or beyond either way gets a \"Conviction Extreme\" flag on the Currency Detail sheet, and crossing that line fires a Positioning alert — tap its book icon (2026-09-04) for the Positioning Playbook, on why an extreme reading is usually the OPPOSITE of naive crowding logic: two of the six inputs feeding this score are already contrarian-weighted, so reaching ±80 needs nearly every component confirming at once, not one lopsided input. 2026-09-09 — if the CFTC feed itself has been down for over 9 days (rare; the normal weekly publish lag is only 3), the three COT-derived inputs drop out and the score becomes technical-only (CSM extreme + extension + breadth), capping its own honest ceiling at ±50. The Extreme alert's own crossing line adapts to ±40 in that state rather than staying stuck at an unreachable 80, and says \"technical only — COT data unavailable\" so it's never mistaken for a fully positioning-confirmed extreme.",
    ),
    LibraryEntry(
        id = "state-transition-alerts",
        term = "State-Transition Alerts",
        category = "Alerts & Recommendation",
        summary = "Six push alerts (added 2026-09-04) that fire the moment something changes, never for a condition that's merely still true.",
        howItWorks = "Each of the six compares this scan's value against last scan's, only on the app's own hourly cadence: Setup (a pair's Continuation Score newly crosses 45, the same qualifying line `rank.py` itself gates on — updated 2026-09-05, previously reaching Tradeable/A+ Potential before that gate retired), Structure (a pair's H4 structure newly reads a fresh BOS or CHoCH), Regime (the H4 regime flips, or the Macro Archetype changes), Volatility (a pair's ATR percentile newly crosses 90), and Alignment (a pair's D1/H4/H1 pills newly all agree at Strong Buy or Strong Sell). The very first scan after the feature shipped can't fire anything — there's nothing yet to compare against.",
        whyItMatters = "Edge-triggered, not level-triggered, on purpose: an alert that re-fires every hour for a condition that hasn't moved just trains you to ignore the channel. Each has its own Settings toggle (Structure covers both BOS and CHoCH; Regime covers both the H4 flip and an Archetype change). Four of the six — Structure, Regime, Volatility, Alignment — carry a book icon on their Notification History card (2026-09-04) opening a Playbook: the deeper theory behind that specific firing, not just the one-line \"what to consider\" text every alert already has. Setup doesn't get one — its mechanism is already the Overall/Continuation Score entries in full. Notification History itself groups all nine alert types under the same five headline concepts (2026-09-05) — Regime, Trend, Volatility, Structure, plus an OTHER bucket for the ones that don't cleanly fit (Gold, Level, Positioning, and Setup itself, which is a ranking-threshold crossing rather than one of the five).",
    ),
    LibraryEntry(
        id = "bb_reversal_criteria",
        term = "BB Reversal Touch (D1)",
        category = "Alerts & Recommendation",
        summary = "A D1 candle wicks a 12-period, 2-sigma Bollinger Band — the touch alone is the whole alert, on purpose.",
        howItWorks = "Every hourly scan, each pair's current (possibly still-forming) D1 candle is checked against a 12-period SMA +-2 standard deviations. A wick touch counts even if the candle closes back inside — the alert fires once, the moment a pair transitions from not-touching to touching either band, and won't fire again for the same ongoing touch. Band-width trend (expanding/converging/flat, vs. ~5 D1 bars ago) rides along in the notification for context. $BB_REVERSAL_HOW_TO_READ",
        whyItMatters = "$BB_REVERSAL_WHY_IT_MATTERS Deliberately NOT auto-confirmed (2026-09-09, Pieter's own redesign, superseding the original Phase 4 plan's midline-retest logic) — add the pair to the Watchlist (bookmark icon on its sheet) to track how it develops over the following D1 sessions; each watchlist card carries this same checklist behind its own tap-to-reveal.",
    ),
)
