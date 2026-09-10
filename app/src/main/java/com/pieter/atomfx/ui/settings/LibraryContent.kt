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
)

// Signals Roadmap §5 — the reversal checklist is written out in full because it's deliberately
// not hardcoded into the bb_touch alert itself (see bb_touch.py's own doc comment): a touch alone
// fires the notification, but whether it's actually a good reversal candidate is a judgment call,
// informed by this. Shared verbatim between this Library entry and the Watchlist card's own
// tap-to-reveal section (WatchlistScreen.kt) — one source of text, so the two surfaces can never
// drift apart.
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
    "judgment stays with you, informed by this checklist and the Watchlist."

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
        summary = "The wheel's middle ring always shows the same 12 pairs — the 4 corner wings pick which value fills them: Setup, Trend, Momentum, or Volatility.",
        howItWorks = "Wedge size is always a magnitude of signal — a bigger wedge always means a stronger reading, on every wing. Colour is the separate channel that carries direction, except on Volatility.\n" +
            "Setup: the Continuation Score, cross-timeframe.\n" +
            "Trend: ADX, fixed at H4 — no D1 or H1 ADX exists.\n" +
            "Momentum: fixed at H4, 0–100, 50 neutral.\n" +
            "Volatility: fixed at D1, the ATR percentile.\n" +
            "Momentum's fill folds around its own neutral point before filling — 0 and 100 are both extremes, not \"none\" and \"max.\" Trend's colour comes from the H4 pill specifically, not the pair's overall direction.",
        whyItMatters = "Setup, Trend, and Momentum share one green/grey/red traffic light — green bull, red bear, grey neutral. Volatility is its own separate blue/amber system: blue inside its 20–70 sane band, amber outside it — a risk-sizing read, not a directional one. Structure isn't a wing at all: it's event-based, not a magnitude, so it gets its own Overview row and its own alert instead.",
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
        howItWorks = "Finds swing pivots (a bar strictly higher/lower than several bars on both sides), reads the trend from the last two swings (higher highs + higher lows = bull, the reverse = bear), then classifies the latest close. Breaking beyond the last swing in the trend's own direction is a BOS — continuation. Breaking the opposite way is a CHoCH — a potential reversal warning. A strength score, 0–1, from how far price broke past that swing relative to ATR, scales the pair's technical score up to +30% on a BOS or down to −60% on a CHoCH.",
        whyItMatters = "The one purely price-action-based signal in the engine — no oscillator, just where price actually broke. A fresh BOS or CHoCH fires a Structure alert. Tap its Notification History card's book icon for the Structure Playbook, the deeper read on what a break implies and where it can mislead.",
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
        whyItMatters = "ADX measures trend strength, not direction — CSM and pills already cover that. Two pairs can share the same bias and read completely differently here.\n" +
            "0–24: no real trend.\n" +
            "25–39: trending.\n" +
            "40–59: very strong.\n" +
            "60+: extreme — often exhaustion-prone, not simply \"more is better.\"",
    ),
    LibraryEntry(
        id = "five-state-score",
        term = "The 5-State Technical Score",
        category = "Momentum & Price Action",
        summary = "The underlying Strong Buy / Buy / Neutral / Sell / Strong Sell read behind every pill colour.",
        howItWorks = "Blends an EMA200 trend read, a 3-vote momentum group (EMA50 vs price, DMI, and MACD histogram direction), and a graduated RSI score, then scales the total by ADX strength — below ADX 15 it zeroes out entirely, since there's no real trend to read. A structure multiplier (see BOS/CHoCH) and a same-direction-conflict penalty adjust it further before the five-state label is assigned by regime-specific thresholds.",
        whyItMatters = "Every pill you see — D1/H4/H1, bull, bull_strong, neutral, bear, bear_strong — comes from this one engine, the single source of truth for \"what does this timeframe think.\" The pair sheet's 3-TF alignment strip is this same read, compacted. Three Strong Buys or three Strong Sells in a row also fires an Alignment alert — tap its book icon for the Alignment Playbook, on why three independently-computed timeframes agreeing is genuine confluence, not circular.",
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
        howItWorks = "Recomputes CSM on a price history sliced back by a fixed offset — about a day for D1/H4, six hours for H1 — using the exact same CSM function, then subtracts: now minus then.",
        whyItMatters = "CSM gives a level; Delta gives a direction of travel — together they separate a currency that's strong-and-fading from one that's strong-and-accelerating. The Currency Strength strip's Strength/Flow toggle switches which one it plots: Strength shows level bars; Flow replaces the chart with a diverging view off a zero-line, sized by Delta.",
    ),
    LibraryEntry(
        id = "currency-flow",
        term = "Currency Flow — Leader / Laggard",
        category = "Currency Strength",
        summary = "The fastest-moving currencies right now, by CSM Delta — not the strongest/weakest in absolute terms.",
        howItWorks = "Leader is the currency with the highest H4 CSM Delta; laggard is the lowest. Absolute leader/laggard is a separate, simpler read: whichever currency has the highest or lowest raw CSM level right now, regardless of how fast it's moving.",
        whyItMatters = "A currency can be an absolute laggard — weak — while still being the flow leader, getting less weak fastest. The two numbers answer different questions and stay separate on purpose. This lives in the Currency Strength strip's own Flow view, per currency.",
    ),
    LibraryEntry(
        id = "breadth",
        term = "Breadth",
        category = "Currency Strength",
        summary = "The share of a currency's 18-pair relationships that agree with its net direction — proof a move isn't just one pair.",
        howItWorks = "Each currency appears in a different number of the 18 CSM pairs: USD 7, JPY 6, AUD 5, GBP 5, EUR 4, CHF/CAD/NZD 3 each. Breadth is how many of those relationships point the same way as the currency's own net direction, divided by how many it appears in at all — always a percentage, never a raw count, since the totals differ per currency.\n" +
            "70% or above: strong.\n" +
            "50–69%: moderate.\n" +
            "Below 50%: weak.\n" +
            "This is a confidence read, not a direction read — it says how unanimous the agreement is, not which way. A currency can be strong-and-weakening just as easily as strong-and-strengthening; the app shows the actual direction as its own word (strengthening/weakening/flat) alongside the confidence band, not instead of it.",
        whyItMatters = "The difference between \"EUR is strong\" — broad, real — and \"EUR is strong against JPY\" — one relationship, which could be a JPY story, not a EUR one. Also the difference between \"broadly agreed\" and \"broadly agreed to be rising\" — a currency's pairs can unanimously confirm it's weakening just as easily as strengthening.",
    ),
    LibraryEntry(
        id = "rotation",
        term = "Rotation",
        category = "Currency Strength",
        summary = "A quadrant map of every currency's strength against the momentum of that strength — who's leading, who's fading.",
        howItWorks = "Each currency is plotted by H4 CSM (strength, left to right) against H4 CSM Delta (momentum of that strength, bottom to top).\n" +
            "Leading: strong and still gaining.\n" +
            "Weakening: strong but losing ground.\n" +
            "Lagging: weak and still losing.\n" +
            "Improving: weak but gaining ground.\n" +
            "A short trail behind each dot shows its last few readings.",
        whyItMatters = "Strength alone doesn't say which way things are heading. A currency can be the strongest on the board and already fading (Weakening), or the weakest and turning the corner (Improving) — Rotation is on the Insights tab.",
    ),
    LibraryEntry(
        id = "pulse",
        term = "Pulse",
        category = "Regime & Macro",
        summary = "0–100. Is today's market broad and confirmed enough to trust a signal, or thin and contradictory?",
        howItWorks = "An average of up to four reads: how many currencies agree with their own direction (Breadth), how many evidence axes support the current regime (Regime), whether today's currency-strength spread is unusually wide (Separation), and how much of the market is in an expanding volatility phase.\n" +
            "70 or above: confirmed.\n" +
            "50–69: mixed.\n" +
            "Below 50: noise.\n" +
            "Needs at least two of the four reads available, or it shows nothing rather than a misleading single-input score.",
        whyItMatters = "A high setup score in a noise market is a weaker bet than the same score in a confirmed one — Pulse is the market-wide context, on the Insights tab.",
    ),
    LibraryEntry(
        id = "thrust",
        term = "Thrust",
        category = "Currency Strength",
        summary = "How many currencies are breadth-strong minus how many are breadth-weak, from -8 to +8 — is the market splitting into winners and losers, or staying flat?",
        howItWorks = "Counts each of the 8 currencies' own breadth direction: +1 for strong, -1 for weak, 0 for flat, summed.",
        whyItMatters = "A run of readings near zero says the market is undecided even if individual pairs look busy; a run moving toward +8 or -8 says conviction is building broadly, not just in one pair — Thrust is on the Insights tab.",
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
        whyItMatters = "Below roughly 20: the market's too quiet to trust a breakout. Above 70: you might be entering into a volatility spike. 20–70 is the app's own sane entry band. Crossing 90 fires a Volatility alert — tap its book icon for the Volatility Playbook, on why an expansion is a risk-sizing signal first, an opportunity signal second. Also the wheel's Volatility wing and the pair sheet's Overview Volatility row — same field, same band, surfaced in two more places. Reads as a risk-sizing gate, not a fourth vote on direction: \"grey/neutral\" here means conditions are sane for whatever the other wings already suggest, not a buy signal on its own.",
    ),
    LibraryEntry(
        id = "h4-regime",
        term = "D1 Structural Regime",
        category = "Regime & Macro",
        summary = "Risk-On / Risk-Off / Mixed / Ranging — the app's read of the whole market's current mood.",
        howItWorks = "Four votes are cast:\n" +
            "Safe-havens JPY/CHF vs risk currencies AUD/NZD/CAD, by CSM.\n" +
            "USD vs the rest of the majors (EUR/GBP/AUD/NZD/CAD), by CSM.\n" +
            "Pill direction across the same commodity-bloc pairs as vote 1.\n" +
            "A ranging override — forces Ranging outright if fewer than 40% of all 12 pairs have any directional pill at all.\n" +
            "Two or more votes agreeing wins. All three agreeing: High confidence. Two: Medium. No majority: Mixed. Votes 1 and 2 are forced to Mixed rather than trusted when a timeframe's underlying CSM spread ranks unusually thin against its own recent history — a signal that a CSM gap that scan is more likely rescaling noise than a real read. The same classifier also runs at H4 and H1, shown side by side on the Regime sheet; D1 is the wheel's own headline read.",
        whyItMatters = "This is the regime shown at the wheel's hub, and it gates Factor 1 — nothing passes Regime under a Mixed or Ranging backdrop. Tap the Regime sheet's book icon for the Regime Playbook — the deeper per-regime read, and how this technical read relates to the Macro Archetype and to Gold Signal's own confirmation gate.",
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
        whyItMatters = "The anti-double-counting discipline the Macro screen is built on: ten raw instruments collapse to five real, independent pieces of evidence. The archetype persists for days rather than flipping on ordinary daily noise, which is what makes the archetype-change push notification worth reading when it fires.",
    ),
    LibraryEntry(
        id = "evidence-axis",
        term = "Evidence Axis",
        category = "Regime & Macro",
        summary = "One of five buckets — Risk, Rates, USD, Commodity, Safe-haven — that groups correlated cross-asset signals so they're only counted once.",
        howItWorks = "Risk: the net of SPX/VIX/Copper/BTC direction.\n" +
            "Rates: US10Y and US3M direction.\n" +
            "USD: DXY direction alone.\n" +
            "Commodity: WTI or Copper direction.\n" +
            "Safe-haven: Gold direction.\n" +
            "Each axis also carries a same-day read: today confirms this trend, today is fighting this trend, or no fresh move today.",
        whyItMatters = "Without axis grouping, a single risk-off move could look like four or five \"independent\" confirmations just because VIX, SPX, Copper, and BTC all move together. The confirm/fight/quiet tag turns Evidence into an actual decision cue: is today's price action backing the story, or just the story alone.",
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
        whyItMatters = "One of the app's push notification types, alongside state-transition alerts — a live, regime-aware gold read rather than a bare price alert. Tap a Gold Signal notification's book icon for the Gold Signal Playbook — what the Technical Regime gate is and isn't confirming, and the specific case where gold's own safe-haven behaviour can invert.",
    ),
    LibraryEntry(
        id = "recommendation-engine",
        term = "Recommendation Engine",
        category = "Alerts & Recommendation",
        summary = "The AI-narrated headline trade idea (Insights) and, on Home, one small glyph per ranked setup — deterministic picks, AI only writing Insights' own explanation.",
        howItWorks = "A fully deterministic \"seed\" picks the bias, action, primary pair, direction, confidence, and next catalyst from data already in signals.json — no model is involved in that decision. An optional AI call then writes the human-readable headline, rationale, and invalidation text around that seed; if the model's wording ever contradicted the seed, the seed wins. If the AI call fails, no recommendation is published at all, rather than showing something half-written.",
        whyItMatters = "The pair, direction, and confidence are a real signal; the prose around them is commentary on that signal, not a separate one. Insights shows the full AI-narrated version — headline, rationale, invalidation, next catalyst — on its own slower cadence. Home shows a different, related list instead: one small glyph per pair in the app's own top-ranked setups, at most three at once. Tapping a glyph opens a panel with that pair's direction, its Setup Score, and its Regime/Trend/Momentum/Volatility/Structure consensus — never the AI text, and refreshed every hourly scan since no model call is involved.",
    ),
    LibraryEntry(
        id = "level-ema-alerts",
        term = "Level & EMA Alerts",
        category = "Alerts & Recommendation",
        summary = "A user-configured price-level alert type, separate from the Gold Signal — currently greyed out in Settings, not yet available.",
        howItWorks = "Level alerts would fire when price crosses a user-set level, read from a file nothing on-device writes yet — the on-device \"set alert\" flow doesn't exist. The Settings toggle is greyed out rather than presented as live. EMA touch alerts — price touching its own H4 EMA200/EMA50 — are inactive the same way: the calculation still exists, but nothing calls it.",
        whyItMatters = "Kept, not removed, for when an on-device \"set alert\" row ships — at which point this becomes a real, live alert type.",
    ),
    LibraryEntry(
        id = "conviction",
        term = "Conviction (Positioning)",
        category = "Regime & Macro",
        summary = "A −100..+100 per-currency crowding read from free CFTC positioning data, updated weekly, not hourly.",
        howItWorks = "Six inputs blend into one score:\n" +
            "CFTC leveraged-fund positioning — a 52-week percentile, contrarian at extremes: deeply crowded long is bearish, deeply crowded short is bullish.\n" +
            "Open-interest momentum — is new money entering with the trend.\n" +
            "Asset-manager-vs-leveraged-fund alignment — structural vs tactical agreement.\n" +
            "A CSM extreme read, contrarian.\n" +
            "An extension read — how many of the currency's pairs are overextended in its own direction.\n" +
            "A breadth read.\n" +
            "EWMA-smoothed week to week so it doesn't flicker. CFTC data publishes Fridays covering the prior Tuesday, so this is always a few days old by nature — a positioning overlay, not a live signal.",
        whyItMatters = "Positioning shows who's already in a trade, which price action alone can't — a currency can look technically strong while positioning is dangerously crowded, or wobbly while positioning is actually clean. A reading of 80 or beyond either way is a Conviction Extreme, shown on the Currency Detail sheet, and crossing that line fires a Positioning alert — tap its book icon for the Positioning Playbook, on why an extreme reading is usually the opposite of naive crowding logic. If CFTC data goes stale, the three COT-derived inputs drop out, the score becomes technical-only, and the Extreme alert's own threshold adapts to 40 — marked \"technical only — COT data unavailable\" so it's never mistaken for a fully positioning-confirmed extreme.",
    ),
    LibraryEntry(
        id = "state-transition-alerts",
        term = "State-Transition Alerts",
        category = "Alerts & Recommendation",
        summary = "Six push alerts that fire the moment something changes, never for a condition that's merely still true.",
        howItWorks = "Each compares this scan's value against last scan's, on the app's own hourly cadence:\n" +
            "Setup — a pair's Continuation Score newly crosses 45.\n" +
            "Structure — a pair's H4 structure newly reads a fresh BOS or CHoCH.\n" +
            "Regime — the H4 regime flips, or the Macro Archetype changes.\n" +
            "Volatility — a pair's ATR percentile newly crosses 90.\n" +
            "Alignment — a pair's D1/H4/H1 pills newly all agree at Strong Buy or Strong Sell.\n" +
            "The very first scan after a fresh install can't fire anything — there's nothing yet to compare against.",
        whyItMatters = "Edge-triggered, not level-triggered, on purpose: an alert that re-fires every hour for a condition that hasn't moved just trains you to ignore the channel. Each has its own Settings toggle. Structure, Regime, Volatility, and Alignment carry a book icon on their Notification History card, opening a Playbook — the deeper theory behind that specific firing, not just the one-line \"what to consider\" text every alert already has. Setup doesn't get one; its mechanism is already the Setup/Continuation Score entries in full.",
    ),
    LibraryEntry(
        id = "bb_reversal_criteria",
        term = "BB Reversal Touch (D1)",
        category = "Alerts & Recommendation",
        summary = "A D1 candle wicks a 12-period, 2-sigma Bollinger Band — the touch alone is the whole alert, on purpose.",
        howItWorks = "Every hourly scan, each pair's current (possibly still-forming) D1 candle is checked against a 12-period SMA ±2 standard deviations. A wick touch counts even if the candle closes back inside — the alert fires once, the moment a pair transitions from not-touching to touching either band, and won't fire again for the same ongoing touch. Band-width trend (expanding/converging/flat, vs. ~5 D1 bars ago) rides along in the notification for context.\n\n$BB_REVERSAL_HOW_TO_READ",
        whyItMatters = "$BB_REVERSAL_WHY_IT_MATTERS",
    ),
)
