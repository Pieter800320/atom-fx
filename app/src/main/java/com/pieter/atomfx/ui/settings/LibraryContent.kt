package com.pieter.atomfx.ui.settings

/**
 * The Library — "a full library that explains every single element, calculation and item in the
 * app," replacing the old six-line ABOUT section.
 *
 * Every entry here is grounded directly in the frozen `scanner` package's own source (and the
 * `scanner.extend` additive layer) — read in full while writing this, not paraphrased from a doc.
 * Numbers (weights, thresholds) are quoted exactly as they appear in code; if a frozen file or an
 * EXTEND config value ever changes, this file goes stale and needs a re-read, the same way any
 * other "expose, don't recompute" surface would.
 *
 * [howItWorks] describes the calculation in prose, not code — accurate, but for a reader who
 * wants to understand the mechanism, not re-implement it. [whyItMatters] is the actual "why do I
 * care" context — what a term means for reading the app day to day.
 *
 * Prose rules for every field: `docs/ATOM_FX_LIBRARY_STYLE.md`. Short version — no developer
 * names/dates/change-history in the strings themselves (that belongs in this file's own comments
 * and in git, not in what the app displays), lead with the answer, lists for list-shaped things.
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
    "Market Hours",
)

// Signals Roadmap §5 — the reversal checklist is written out in full because it's deliberately
// not hardcoded into the bb_touch alert itself (see bb_touch.py's own doc comment): a touch alone
// fires the notification, but whether it's actually a good reversal candidate is a judgment call,
// informed by this. 2026-09-16 (Pieter's ask) — no longer shared with the Watchlist card, which
// dropped its BB-touch-specific tap-to-reveal section for a general pair-state layout
// (WatchlistScreen.kt); this checklist now lives only here.
const val BB_REVERSAL_HOW_TO_READ =
    "Check five things:\n" +
    "ADX — below 20 means ranging, where a touch is more likely to mean-revert cleanly; above 20 " +
    "means trending, so only trust a touch that's a pullback in the trend's own direction.\n" +
    "D1/H4/H1 pills — the classic pullback signature is the higher timeframe still holding its " +
    "trend while the lower ones have cooled or flipped.\n" +
    "Reset Score, read in the touch's implied direction — a low reading means price is genuinely " +
    "stretched from its own equilibrium, corroborating the touch independently.\n" +
    "Band-width trend — a touch on expanding bands is more likely a breakout candle than " +
    "exhaustion; a touch on narrow or converging bands is the better reversal candidate.\n" +
    "Structure — a fresh CHoCH in the touch's own direction is real, independent confirmation " +
    "that price action itself is turning."

const val BB_REVERSAL_WHY_IT_MATTERS =
    "A bare band touch is close to meaningless on its own — the bands show whether price is high " +
    "or low relative to its recent history, not whether to buy or sell. The five checks above " +
    "separate a touch that's about to mean-revert from one that's just a pause before the trend " +
    "continues. None are hardcoded into the alert — the notification stays broad, and the " +
    "judgment stays with you, informed by this checklist."

val LIBRARY_ENTRIES: List<LibraryEntry> = listOf(
    LibraryEntry(
        id = "potential",
        term = "Setup (Continuation Score)",
        category = "The Wheel",
        summary = "0–100. The wheel's default reading — a continuous, ungated setup-quality score.",
        howItWorks = "This is `cont`, the frozen Continuation Score (see Continuation Score under Setup Quality for the full weighted breakdown). The Setup wing is a straight read of the same number that drives the app's own pair ranking.",
        whyItMatters = "Setup answers \"how far along is this idea\" with one continuous score, not a pass/fail gate. Its wedge fill and colour both read off this same number and the same direction, so green/red always matches what's being scored.",
    ),
    LibraryEntry(
        id = "level-state",
        term = "Setup Bands",
        category = "The Wheel",
        summary = "The Continuation Score split into four plain-language bands: LOW SETUP, DEVELOPING, STRONG SETUP, A+ SETUP.",
        howItWorks = "LOW SETUP is below 45 — the same qualifying line ranking itself gates on. DEVELOPING is 45–64. STRONG SETUP is 65–84. A+ SETUP is 85 and above.",
        whyItMatters = "These four bands are what the pair sheet's header word and the wheel's Setup wing colour language sort pairs into — the fastest read of \"is this worth a look\" in the app.",
    ),
    LibraryEntry(
        id = "wheel-four-wings",
        term = "The Wheel's 4 Wings",
        category = "The Wheel",
        summary = "Four corner wings choose what the wheel's 12 pairs show: Setup, Trend, Momentum or Volatility.",
        howItWorks = "Wedge size is the strength of the reading: bigger is stronger, on every wing. Colour carries direction, except on Volatility.\n" +
            "Setup: the Continuation Score, cross-timeframe.\n" +
            "Trend: ADX at H4. No D1 or H1 ADX exists. Colour comes from the H4 pill, not the pair's overall direction.\n" +
            "Momentum: H4, 0–100, 50 neutral. The fill folds around 50, so 0 and 100 both read as extremes.\n" +
            "Volatility: the D1 ATR percentile.",
        whyItMatters = "Setup, Trend and Momentum share one traffic light: green bull, red bear, grey neutral. Volatility uses blue inside 20–70 and amber outside, and says nothing about direction. Structure is an event, not a magnitude, so it has its own Overview row and alert.",
    ),
    LibraryEntry(
        id = "mom1212",
        term = "MOM1212",
        category = "Momentum & Price Action",
        summary = "The core momentum oscillator, 0–100, one reading per timeframe (D1/H4/H1).",
        howItWorks = "Compares a 12-bar moving average to itself 12 bars earlier, normalised by 14-bar ATR, then squashed through a sigmoid curve so it always lands between 0 and 100 with 50 as neutral. Each timeframe also carries its own delta — how much that reading has moved over a fixed lookback (5 bars on D1, 30 on H4, 120 on H1).",
        whyItMatters = "This is what \"momentum\" means everywhere in the app — the raw ingredient both CMP and the Momentum tab's bars are built from.",
    ),
    LibraryEntry(
        id = "cmp",
        term = "CMP (Composite Momentum Position)",
        category = "Momentum & Price Action",
        summary = "0–100. One number blending D1/H4/H1 momentum into a single composite read.",
        howItWorks = "Weighted 50% D1, 30% H4, 20% H1 — computed on the raw, pre-sigmoid momentum values and squashed once at the end, not averaged from three already-squashed numbers.",
        whyItMatters = "CMP is what Factor 4 (Momentum) gates on, and what the Momentum tab's CMP square shows.",
    ),
    LibraryEntry(
        id = "structure-events",
        term = "Structure — BOS / CHoCH",
        category = "Momentum & Price Action",
        summary = "Market-structure events built from genuine swing highs/lows, not an indicator.",
        howItWorks = "Swing pivot: a bar strictly higher or lower than several bars on both sides.\n" +
            "Trend: higher highs and higher lows is bull. The reverse is bear.\n" +
            "BOS: the latest close breaks the last swing in the trend's direction. Continuation.\n" +
            "CHoCH: it breaks the opposite way. A potential reversal warning.\n" +
            "Strength, 0–1: how far price broke past the swing, relative to ATR. It scales the technical score up to +30% on a BOS or down to −60% on a CHoCH.",
        whyItMatters = "The one signal built purely on price action: where price actually broke, with no oscillator. A fresh BOS or CHoCH fires a Structure alert, and its Notification History card opens the Structure Playbook. The multiplier for a pair is on the Pair sheet, Breakdown, Structure.",
    ),
    LibraryEntry(
        id = "adx",
        term = "ADX (Average Directional Index)",
        category = "Momentum & Price Action",
        summary = "0–100. Wilder's classic trend-strength indicator — how strongly a pair is trending, independent of which way.",
        howItWorks = "14-period directional movement, ATR-normalised: +DI and −DI track how much of each bar's range moved bullish vs bearish, DX is the spread between them, and ADX is DX's own 14-period average. No neutral midpoint — 0 means no directional movement, higher means a stronger, more persistent trend either way. Computed once at H4 and reused everywhere ADX appears.\n" +
            "Below 15: the technical score zeroes out — no real trend to read.\n" +
            "15–20: scores at half weight.\n" +
            "20–25: three-quarters weight.\n" +
            "25+: full weight.\n" +
            "The Continuation Score caps at 45 below ADX 20.",
        whyItMatters = "ADX measures trend strength, not direction. Two pairs can share the same bias and read completely differently here.\n" +
            "0–24: no real trend\n" +
            "25–39: trending\n" +
            "40–59: very strong\n" +
            "60+: extreme, often exhaustion-prone, not simply \"more is better\"",
    ),
    LibraryEntry(
        id = "five-state-score",
        term = "The 5-State Technical Score",
        category = "Momentum & Price Action",
        summary = "The underlying Strong Buy / Buy / Neutral / Sell / Strong Sell read behind every pill colour.",
        howItWorks = "Three parts:\n" +
            "An EMA200 trend read.\n" +
            "A 3-vote momentum group: EMA50 vs price, DMI, and MACD histogram direction.\n" +
            "A graduated RSI score.\n" +
            "The total is scaled by ADX strength. Below ADX 15 it is zero.\n" +
            "A structure multiplier (see BOS / CHoCH) and a same-direction-conflict penalty adjust it. Regime-specific thresholds then assign the five-state label.",
        whyItMatters = "Every D1, H4 and H1 pill comes from this one engine. The Pair sheet's alignment strip is the same read, compacted. Three Strong Buys or three Strong Sells fire an Alignment alert, whose Playbook explains why three separate timeframes agreeing is real confluence.",
    ),
    LibraryEntry(
        id = "percent-b",
        term = "%B",
        category = "Momentum & Price Action",
        summary = "Where price sits inside its Bollinger Bands, 0–100. 50 is the middle band; 0 and 100 are the outer bands.",
        howItWorks = "(close − lower band) ÷ (upper band − lower band) × 100, on 20-period ±2σ bands. Below 0 or above 100 means price closed outside its bands: a real reading, not an error. A 20-period average of %B is computed as a signal line but not drawn here.\n" +
            "Footer word: Stretched high at 90 or above (bear-tinted), Stretched low at 10 or below (bull-tinted), Normal range between.\n" +
            "D1 closes at 17:00 New York, matching most charting platforms. H4 and H1 use UTC boundaries. M15 comes from a separate fetch about every 45 minutes, so it can lag the others.\n" +
            "The Bollinger touch alert uses 12-period bands, a tighter setting, not a different formula.\n" +
            "Shown on long-press of a wheel node: the first of four indicator cards, with one timeframe row driving all four.",
        whyItMatters = "Two very different situations both count as \"price is high\": grinding along the upper band in a strong trend, or spiking into it and about to snap back. %B alone doesn't tell you which — reading it alongside Trend/Structure does.",
    ),
    LibraryEntry(
        id = "bandwidth",
        term = "BandWidth",
        category = "Momentum & Price Action",
        summary = "Band width as a percentage of price: a direct read of how volatile a pair is now.",
        howItWorks = "(upper band − lower band) ÷ middle band × 100, on the same 20-period bands as %B. Rising: bands expanding, volatility building. Falling: bands converging, the market going quiet.\n" +
            "No fixed scale. A BandWidth of 1.0 is ordinary on D1 and wide on H1, and it differs by pair. The shape is the read, not the number.\n" +
            "Squeeze: amber dots and a shaded column mark bars where BandWidth is the lowest of the past 125 bars. That is Bollinger's own squeeze definition.\n" +
            "Footer word: Quiet (squeeze) when the current bar qualifies, Normal otherwise. Available on D1, H4, H1 and M15.",
        whyItMatters = "A squeeze says a move is coiling, not which way it breaks. On expanding bands a touch is a breakout already running, and fading it is dangerous. On converging bands a touch is the better reversal candidate.",
    ),
    LibraryEntry(
        id = "currency-percent-b",
        term = "Currency %B",
        category = "Momentum & Price Action",
        summary = "Each currency's own %B: how stretched it is against its own recent range, corrected for its side of each pair.",
        howItWorks = "Averaging raw pair %B fails: EUR/USD falling means USD is strengthening, but USD/CAD falling means USD is weakening.\n" +
            "So each pair's %B feeds its base currency directly and its quote currency mirrored around 100, the same idea CSM uses.\n" +
            "Built on 12-period D1 bands, unlike the pair %B chart's 20.\n" +
            "Shown on a currency's sheet (tap a bar in the Currency Strength strip): header \"%B (12)\" with the value, footer Stretched high, Stretched low or Normal range.",
        whyItMatters = "A stretched currency %B with CSM not confirming is only a pair-level candidate once the other leg agrees. Read it beside that currency's CSM and breadth, then check the pair.",
    ),
    LibraryEntry(
        id = "momentum-rsi-macd",
        term = "Momentum — RSI & MACD",
        category = "Momentum & Price Action",
        summary = "RSI (14) and MACD (12, 26, 9) as charts over time, not just the latest reading.",
        howItWorks = "RSI and MACD are the same values the technical score already computes. The card keeps a run of past values so they can be drawn.\n" +
            "RSI: 0–100, dashed lines at 30 and 70, centre line at 50, no signal line. Footer word: Overbought, Oversold or Neutral, read at the same 30 and 70.\n" +
            "MACD: the histogram (MACD line minus signal line) as bars tinted by sign, with the MACD and signal lines over it. The histogram has its own vertical scale, because it is far smaller than either line.\n" +
            "MACD footer: Bullish or Bearish from the histogram's sign, plus building or fading from whether its distance from zero grew or shrank versus the previous bar. A sign flip always reads building.\n" +
            "D1 closes at 17:00 New York, as in %B. H4 and H1 use UTC boundaries, so a small gap to one broker's candles is a data-vendor difference. M15 comes from a separate fetch about every 45 minutes.\n" +
            "Shown on long-press of a wheel node: the third and fourth of four indicator cards.",
        whyItMatters = "RSI at 70 or above, or 30 or below, is a stretch, like a %B band touch: due to revert, not automatically a trend ending. A MACD histogram crossing zero is the earliest sign of a momentum shift.",
    ),
    // Crowd score (Signals Roadmap §5c) — the evidence paragraph in whyItMatters is deliberate and must stay:
    // the score is context, and the studies behind it (docs/RESEARCH_LOG.md, research branch, Experiments 2-5)
    // found only a small D1 tendency in one era. Prose follows ATOM_FX_LIBRARY_STYLE.md (no developer narration).
    LibraryEntry(
        id = "crowd-score",
        term = "Crowd score",
        category = "Momentum & Price Action",
        summary = "How many stretch and crowding conditions line up on one side of a pair, 0–100. Context, not a signal.",
        howItWorks = "Two scores, 0–100. Crowd top: price stretched up and crowded long. Crowd bottom: the mirror. Conditions add points:\n" +
            "Price beyond its 20-period Bollinger band: 10\n" +
            "Z-score of 2 or more: 8\n" +
            "3 or more ATRs from the 50-period average: 7\n" +
            "RSI beyond 70 or 30: 12, plus 3 beyond 80 or 20\n" +
            "Price and RSI diverging: 25\n" +
            "A volatility spike through the band: 10\n" +
            "Speculators at a three-year extreme in the weekly COT report: 25\n" +
            "The dashed line is 60. At or above it the footer reads Crowded top or Crowded bottom. Both sides at 60: Mixed.\n" +
            "COT lags about a week. \"No COT\" means it is unavailable, and the score caps at 75.\n" +
            "Shown on long-press of a wheel node, below MACD, on D1 and H4 only. D1 closes at 17:00 New York. H4 follows TradingView's New York-session blocks. Only completed bars count.\n" +
            "Alerts (Settings, off by default) fire when a score rises into a higher band: 1–19, 20–39, 40–59, 60+. Choose a minimum for D1 and for H4: Any, 20, 40 or 60.",
        whyItMatters = "In 2021–2026 testing, reversals became slightly more likely as the D1 score rose. That did not hold in 2009–2020 or on H4, and flagged bars did not reach the previous support or resistance more often. Context, never a prediction or an entry.",
    ),
    LibraryEntry(
        id = "csm",
        term = "CSM (Currency Strength Model)",
        category = "Currency Strength",
        summary = "0–100 per currency, per timeframe — how strong or weak each of the 8 currencies is right now.",
        howItWorks = "Every one of 18 fixed currency pairs contributes an ATR-normalised return to both its base currency (positive) and its quote currency (negative). Each currency's contributions are averaged, then stretched so the weakest of the 8 sits at 0 and the strongest at 100.\n" +
            "D1: 14-bar D1 (70%) plus H4 (30%).\n" +
            "H4: 5-bar H4 (80%) plus 8-bar H1 (20%).\n" +
            "H1: pure 6-bar H1.",
        whyItMatters = "CSM is the foundation Currency Flow, Breadth, and the home screen's Currency Strength strip are all built on.",
    ),
    LibraryEntry(
        id = "csm-delta",
        term = "CSM Delta",
        category = "Currency Strength",
        summary = "How much a currency's CSM has moved over a defined lookback — \"getting stronger\" vs \"strong.\"",
        howItWorks = "Recomputes CSM on price history sliced back by a fixed offset (about a day for D1 and H4, six hours for H1), using the same CSM function, then subtracts: now minus then.",
        whyItMatters = "CSM gives the level. Delta gives the direction of travel, separating strong-and-fading from strong-and-accelerating. On the Currency Strength strip, Strength plots level bars and Flow plots a diverging view off zero, sized by Delta.",
    ),
    LibraryEntry(
        id = "currency-flow",
        term = "Currency Flow — Leader / Laggard",
        category = "Currency Strength",
        summary = "The fastest-moving currencies right now, by CSM Delta — not the strongest/weakest in absolute terms.",
        howItWorks = "Leader is the currency with the highest H4 CSM Delta; laggard is the lowest. Absolute leader/laggard is a separate, simpler read: whichever currency has the highest or lowest raw CSM level right now, regardless of how fast it's moving.",
        whyItMatters = "A currency can be an absolute laggard yet the flow leader: weak, but getting less weak fastest. The two reads answer different questions and stay separate. Both live in the Currency Strength strip's Flow view, per currency.",
    ),
    LibraryEntry(
        id = "breadth",
        term = "Breadth",
        category = "Currency Strength",
        summary = "The share of a currency's pair relationships that agree with its net direction. A move is not just one pair.",
        howItWorks = "Each currency appears in a different number of the 18 CSM pairs: USD 7, JPY 6, AUD 5, GBP 5, EUR 4, CHF, CAD and NZD 3 each. Breadth is how many of those relationships point the same way as the currency's net direction, divided by how many it appears in. It is always a percentage, never a raw count.\n" +
            "70% or above: strong.\n" +
            "50–69%: moderate.\n" +
            "Below 50%: weak.\n" +
            "Breadth is confidence, not direction. The app shows direction as its own word (strengthening, weakening, flat) beside the band.",
        whyItMatters = "The difference between \"EUR is strong\" (broad) and \"EUR is strong against JPY\" (one relationship, possibly a JPY story). A currency's pairs can agree it is weakening just as easily as strengthening.",
    ),
    LibraryEntry(
        id = "setup-rank",
        term = "Setup Rank",
        category = "Setup Quality",
        summary = "0–10. A deterministic, frozen quality score for a pair's trade idea — independent of Setup.",
        howItWorks = "A weighted blend: Continuation 25%, CMP 20%, momentum delta 15%, D1 CSM divergence 20%, regime fit 10%, cross-asset tailwinds 10%. Pairs with a Continuation Score below 45 are excluded from ranking entirely.",
        whyItMatters = "Not shown as its own row — it feeds the app's internal pair ranking. Its score also appears per pair as \"SETUP SCORE\" inside each Home recommendation glyph's own panel.",
    ),
    LibraryEntry(
        id = "continuation",
        term = "Continuation Score",
        category = "Setup Quality",
        summary = "0–100. How likely the current move is to keep going, once a direction has actually committed on D1 or H4.",
        howItWorks = "Direction comes from the D1 pill; if D1 reads neutral, it falls back to H4's own pill. A genuine 0 only applies when neither D1 nor H4 has any bias at all.\n" +
            "Timeframe alignment across D1/H4/H1: 35%.\n" +
            "Entry position, a blend of Reset Score and ATR Percentile: 23%.\n" +
            "CSM divergence: 16%.\n" +
            "Regime fit: 13%.\n" +
            "H4 structure confirmation — does the latest H4 BOS/CHoCH agree with the trade's direction: 5%.\n" +
            "Session fit: 8%.\n" +
            "Capped at 45 if H4 ADX is below 20, and capped further on a counter-regime trade. CSM divergence is itself capped to \"unreliable\" when H4's own CSM dispersion ranks in the bottom 20% of its recent scan history.",
        whyItMatters = "The single number Setup Rank and the pair sheet's header both lean on hardest — the app's best answer to \"will this actually continue.\"",
    ),
    LibraryEntry(
        id = "reset-score",
        term = "Reset Score",
        category = "Setup Quality",
        summary = "0–100. A directional mean-reversion oscillator — how \"stretched\" price is from its recent equilibrium.",
        howItWorks = "Blends an RSI-derived position, distance from the 20-period SMA, a momentum-divergence term, and a volatility z-score. Read directionally: for a long, oversold or below-mean scores low — a fresh, good entry; for a short, overbought or above-mean scores low.",
        whyItMatters = "A low Reset Score means price has pulled back to a sensible entry in your own direction; a high one means you'd be chasing an already-extended move. Not shown as its own row, but still feeds Conviction's extension input.",
    ),
    LibraryEntry(
        id = "atr-percentile",
        term = "ATR Percentile",
        category = "Setup Quality",
        summary = "0–100. Where today's volatility (ATR) ranks against the last 52 bars.",
        howItWorks = "A straight percentile rank — what share of the last 52 ATR readings sit below today's.",
        whyItMatters = "20–70 is the app's entry band. Below about 20 the market is too quiet to trust a breakout. Above 70 you may be entering a volatility spike.\n" +
            "It gates risk, not direction: grey means conditions are sane, not a buy.\n" +
            "Crossing 90 fires a Volatility alert.",
    ),
    LibraryEntry(
        id = "h4-regime",
        term = "D1 Structural Regime",
        category = "Regime & Macro",
        summary = "Risk-On / Risk-Off / Mixed / Ranging — the app's read of the whole market's current mood.",
        howItWorks = "Three votes and an override:\n" +
            "Safe-havens JPY and CHF vs risk currencies AUD, NZD and CAD, by CSM.\n" +
            "USD vs the rest of the majors (EUR, GBP, AUD, NZD, CAD), by CSM.\n" +
            "Pill direction across the same commodity-bloc pairs as vote 1.\n" +
            "Ranging override: forces Ranging if fewer than 40% of the 12 pairs have any directional pill.\n" +
            "Two or more votes agreeing wins. All three: High confidence. Two: Medium. No majority: Mixed.\n" +
            "Votes 1 and 2 become Mixed when a timeframe's CSM spread ranks unusually thin against its own recent history, since that gap is more likely rescaling noise than a real read.\n" +
            "The same classifier also runs at H4 and H1, side by side on the Regime sheet. D1 is the wheel's headline read.",
        whyItMatters = "This is the regime at the wheel's hub, and it gates Factor 1: nothing passes Regime under a Mixed or Ranging backdrop. The Regime sheet's Playbook covers each regime and how it relates to the Macro Archetype and Gold Signal's confirmation gate.",
    ),
    LibraryEntry(
        id = "macro-archetype",
        term = "Macro Archetype",
        category = "Regime & Macro",
        summary = "One of ten named macro regimes (A–J) from the FX macro handbook, matched against real cross-asset moves.",
        howItWorks = "Ten regimes each have a fixed strong/weak currency signature — Growth-positive risk-on, for example, favours AUD/NZD/CAD over JPY/CHF. Five evidence axes (Risk, Rates, USD, Commodity, Safe-haven) are each read up/down/flat, and every archetype scores by how many distinct axes support it, not how many correlated indicators happen to agree.\n" +
            "3 or more distinct axes: High confidence.\n" +
            "2: Medium.\n" +
            "1 or fewer: Low.\n" +
            "Liquidity Shock is always capped at Low. Axes read on a 5-session basis, so the pick doesn't flip on one ordinary day — a sudden VIX spike is the one exception, read off today's move alone, since a real shock is a today event a 5-day average would smooth away.",
        whyItMatters = "Ten raw instruments collapse to five independent pieces of evidence, so correlated moves are not counted twice. The archetype persists for days, which makes an archetype-change alert worth reading.",
    ),
    LibraryEntry(
        id = "evidence-axis",
        term = "Evidence Axis",
        category = "Regime & Macro",
        summary = "One of five buckets (Risk, Rates, USD, Commodity, Safe-haven) that group correlated cross-asset signals so each is counted once.",
        howItWorks = "Risk: the net of SPX/VIX/Copper/BTC direction.\n" +
            "Rates: US10Y and US3M direction.\n" +
            "USD: DXY direction alone.\n" +
            "Commodity: WTI or Copper direction.\n" +
            "Safe-haven: Gold direction.\n" +
            "Each axis also carries a same-day read: today confirms this trend, today is fighting this trend, or no fresh move today.",
        whyItMatters = "Without grouping, one risk-off move looks like four independent confirmations, because VIX, SPX, Copper and BTC move together. The confirm, fight or quiet tag shows whether today's price action backs the story.",
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
        summary = "The 10 non-FX instruments behind macro context: VIX, US10Y, US3M, the 10Y–3M curve, DXY, Gold, S&P 500, Copper, WTI, Bitcoin.",
        howItWorks = "Each gets a direction (up/down/flat) and a percentage or basis-point change over its window — the raw material every evidence axis and every archetype signature is built from. In the Cross-Assets sheet, an instrument whose Evidence Axis currently supports the regime gets \"confirms regime\" appended to its caption — a plain-text read of the same axis data, not a separate calculation.",
        whyItMatters = "The backbone of the Macro screen and the Cross-Assets sheet. \"Confirms regime\" is the fastest way to see, instrument by instrument, which moves are actually backing the regime you're trading and which are just noise.",
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
        whyItMatters = "One of the push alert types: a regime-aware gold read, not a bare price alert. Its Notification History card opens the Gold Signal Playbook: what the Technical Regime gate does and does not confirm, and when gold's safe-haven behaviour can invert.",
    ),
    LibraryEntry(
        id = "recommendation-engine",
        term = "Recommendation Engine",
        category = "Alerts & Recommendation",
        summary = "Insights' AI-narrated headline trade idea, and Home's glyph per ranked setup. Picks are deterministic; AI only writes the words.",
        howItWorks = "A deterministic seed picks bias, action, primary pair, direction, confidence and next catalyst from signals.json. No model decides.\n" +
            "On Insights, an optional AI call writes the headline, rationale and invalidation around that seed. If its wording contradicts the seed, the seed wins. If the call fails, nothing is published.\n" +
            "On Home, one glyph per pair scoring 6.5 or higher on the 0–10 ranking scale. There is no fixed count: one pair, several, or none. It refreshes every hourly scan, with no model call.\n" +
            "Tapping a glyph shows the pair's direction, Setup Score, and Regime, Trend, Momentum, Volatility and Structure consensus. Never AI text.\n" +
            "A Recommendation alert (its own Settings toggle) fires when a pair newly qualifies or a listed pair flips direction. Never for pairs holding steady.",
        whyItMatters = "The pair, direction and confidence are the signal. The prose around them is commentary on it, not a separate signal.",
    ),
    LibraryEntry(
        id = "level-ema-alerts",
        term = "Level & EMA Alerts",
        category = "Alerts & Recommendation",
        summary = "A user-configured price-level alert type, separate from the Gold Signal — currently greyed out in Settings, not yet available.",
        howItWorks = "Level alerts would fire when price crosses a user-set level, read from a file nothing on-device writes yet, because the on-device set-alert flow does not exist. The Settings toggle is greyed out for that reason.\n" +
            "EMA touch alerts (price touching its H4 EMA200 or EMA50) are inactive the same way: the calculation exists, but nothing calls it.",
        whyItMatters = "Kept, not removed, for when an on-device \"set alert\" row ships — at which point this becomes a real, live alert type.",
    ),
    LibraryEntry(
        id = "conviction",
        term = "Conviction (Positioning)",
        category = "Regime & Macro",
        summary = "A −100..+100 per-currency crowding read from free CFTC positioning data, updated weekly, not hourly.",
        howItWorks = "Six inputs blend into one score:\n" +
            "CFTC leveraged-fund positioning: a 52-week percentile, contrarian at extremes. Deeply crowded long is bearish, deeply crowded short is bullish.\n" +
            "Open-interest momentum: is new money entering with the trend.\n" +
            "Asset-manager vs leveraged-fund alignment: structural vs tactical agreement.\n" +
            "A CSM extreme read, contrarian.\n" +
            "An extension read: how many of the currency's pairs are overextended in its own direction.\n" +
            "A breadth read.\n" +
            "The score is EWMA-smoothed week to week so it does not flicker. CFTC data publishes Fridays for the prior Tuesday, so it is always a few days old: a positioning overlay, not a live signal.\n" +
            "80 or beyond either way is a Conviction Extreme, shown on the Currency Detail sheet. Crossing it fires a Positioning alert; its Playbook explains why an extreme reading is usually the opposite of naive crowding logic.\n" +
            "If CFTC data goes stale, the three COT-derived inputs drop out and the score becomes technical-only. The Extreme threshold then adapts to 40, and the alert is marked \"technical only, COT data unavailable\".",
        whyItMatters = "Positioning shows who is already in a trade, which price action alone cannot. A currency can look technically strong while positioning is dangerously crowded, or wobbly while positioning is clean.",
    ),
    LibraryEntry(
        id = "state-transition-alerts",
        term = "State-Transition Alerts",
        category = "Alerts & Recommendation",
        summary = "Push alerts that fire the moment something changes, never for a condition that is merely still true.",
        howItWorks = "Each compares this scan with the last one, on the hourly scan:\n" +
            "Structure: a pair's H4 structure newly reads a BOS or CHoCH.\n" +
            "Regime: the D1 regime flips, or the Macro Archetype changes.\n" +
            "Volatility: a pair's ATR percentile newly crosses 90.\n" +
            "Alignment: a pair's D1, H4 and H1 pills newly all read Strong Buy or Strong Sell.\n" +
            "The first scan after a fresh install fires nothing: there is nothing to compare against.\n" +
            "BB touch, Recommendation, Crowd score, Positioning and Gold Signal have their own entries.",
        whyItMatters = "Edge-triggered on purpose: an alert that re-fires every hour for an unchanged condition trains you to ignore the channel. Each has its own Settings toggle. Their Notification History cards open a Playbook with the theory behind that firing.",
    ),
    LibraryEntry(
        id = "bb_reversal_criteria",
        term = "BB Reversal Touch (D1)",
        category = "Alerts & Recommendation",
        summary = "A D1 candle wicks a 12-period, 2-sigma Bollinger Band — the touch alone is the whole alert, on purpose.",
        howItWorks = "Every hourly scan, each pair's current (possibly still-forming) D1 candle is checked against a 12-period SMA ±2 standard deviations — this D1 candle closes 17:00 New York, matching most retail charting platforms, not UTC midnight like the app's other D1 signals. A wick touch counts even if the candle closes back inside — the alert fires once, the moment a pair transitions from not-touching to touching either band, and won't fire again for the same ongoing touch. Band-width trend (expanding/converging/flat, vs. ~5 D1 bars ago) rides along in the notification for context.\n\n$BB_REVERSAL_HOW_TO_READ",
        whyItMatters = "$BB_REVERSAL_WHY_IT_MATTERS",
    ),
    LibraryEntry(
        id = "trading-sessions",
        term = "Trading Sessions",
        category = "Market Hours",
        summary = "When the four major FX centres (Sydney, Tokyo, London, New York) are open, in your device's local time.",
        howItWorks = "Sydney 07:00–16:00, Tokyo 09:00–18:00, London 08:00–17:00, New York 08:00–17:00, each in that city's own time.\n" +
            "Computed from each city's real timezone, not a fixed UTC offset, so daylight saving is handled all year.\n" +
            "A pure clock feature: no signals.json field backs it, and nothing here is a trading calculation.",
        whyItMatters = "London–New York and Tokyo–London overlaps light the header dot: two sessions open at once, historically the highest-volume, highest-volatility stretches. One open session does not light it. Tap the header clock for a timeline and per-session countdown.",
    ),
)
