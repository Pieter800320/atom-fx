package com.pieter.atomfx.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A tolerant, minimal mirror of `signals.json` (Architecture §4) — only the fields the wheel
 * and its sheets actually show are modeled; everything else in the document is decoded with
 * `ignoreUnknownKeys = true` and simply skipped (Architecture §8.3: the app never re-derives a
 * value, it only reads one that's already there). Every field is nullable/defaulted so an
 * absent EXTEND key (a normal, documented state — Architecture §4.2) never fails parsing.
 */
@Serializable
data class Signals(
    val updated: String? = null,
    // 2026-09-18 — scanner/scan_m15.py's own freshness marker, separate from `updated`
    // above on purpose: `updated` drives the app's staleness check (Architecture §8.4)
    // against the WHOLE scan_h1.py dataset (alerts, regime, CSM, everything); this one
    // tracks only the last successful M15 chart refresh, on its own faster cadence.
    // Not surfaced in the UI yet — kept in the model so a future staleness indicator on
    // the M15 glance-panel cards doesn't need a schema change to add one.
    @SerialName("m15_updated") val m15Updated: String? = null,
    @SerialName("regime_d1") val regimeD1: RegimeBlock? = null,
    @SerialName("regime_h4") val regimeH4: RegimeBlock? = null,
    @SerialName("regime_h1") val regimeH1: RegimeBlock? = null,
    val csm: Map<String, Map<String, Double>> = emptyMap(),
    @SerialName("csm_delta") val csmDelta: Map<String, Map<String, Double>> = emptyMap(),
    @SerialName("currency_flow") val currencyFlow: CurrencyFlow? = null,
    val breadth: BreadthBlock = BreadthBlock(),
    val pairs: Map<String, PairBlock> = emptyMap(),
    val potential: Map<String, PotentialEntry> = emptyMap(),
    val calendar: CalendarBlock? = null,
    val recommendation: RecommendationBlock? = null,
    val ranked: RankedBlock? = null,
    @SerialName("deep_analysis") val deepAnalysis: DeepAnalysisBlock? = null,
    val breaking: BreakingBlock? = null,
    val catalyst: CatalystBlock? = null,
    @SerialName("week_ahead") val weekAhead: WeekAheadBlock? = null,
    val correlations: CorrelationsBlock? = null,
    @SerialName("macro_assets") val macroAssets: Map<String, MacroAssetEntry> = emptyMap(),
    val macro: MacroSummary? = null,
    @SerialName("regime_w1") val regimeW1: RegimeBlock? = null,
    @SerialName("macro_regime") val macroRegime: MacroRegimeBlock? = null,
    val spark: Map<String, SparkEntry> = emptyMap(),
    val conviction: ConvictionBlock? = null,
    // 2026-09-10 (5th, Pieter's own catch) — percent_b_board (the market-wide average of every
    // pair's own %B/signal line) mixes every pair's raw %B with no regard for which side of the
    // pair is base vs. quote (EURUSD falling = USD strong, USDCAD falling = USD weak — opposite
    // USD stories averaged together unfixed). This is the currency-direction-corrected version
    // instead: one entry per currency code (same 8 as CSM). See `bb_touch.py`'s
    // `compute_currency_percent_b` for the base/quote mirroring (100 - value on the quote side,
    // since %B is a 0-100 position, not a signed return CSM's own base/quote split can just
    // negate). Rotation/Pulse/Thrust/percent_b_board themselves (2026-09-10's original "Whole
    // Market Indicators" experiment) were retired 2026-09-10 (2nd) — the card they lived on was
    // removed once Currency %B (shown per-pair on ChartSheet instead) proved to be the useful one
    // of the five; `PercentBBoardBlock` survives only as this field's own map-value shape.
    @SerialName("percent_b_currency") val percentBCurrency: Map<String, PercentBBoardBlock> = emptyMap(),
    @SerialName("schema_version") val schemaVersion: Int? = null,
)

@Serializable
data class CalendarBlock(
    val events: List<CalendarEvent> = emptyList(),
)

@Serializable
data class CalendarEvent(
    val day: String? = null,
    val time: String? = null,
    val iso: String? = null,
    val currency: String? = null,
    val name: String? = null,
    val forecast: String? = null,
    val previous: String? = null,
    val note: String? = null,
)

@Serializable
data class RegimeBlock(
    val regime: String? = null,
    val confidence: String? = null,
    val score: Double? = null,
    val stable: Boolean? = null,
    // Only present on regime_w1 — harmless no-ops for regime_d1/h4/h1, which reuse this block.
    val signals: Int? = null,
    val total: Int? = null,
)

@Serializable
data class CurrencyFlow(
    val leader: String? = null,
    @SerialName("leader_delta") val leaderDelta: Double? = null,
    val laggard: String? = null,
    @SerialName("laggard_delta") val laggardDelta: Double? = null,
    @SerialName("absolute_leader") val absoluteLeader: String? = null,
    @SerialName("absolute_laggard") val absoluteLaggard: String? = null,
    @SerialName("driver_spread") val driverSpread: Double? = null,
)

// 2026-09-10 — `dir`/`net` were already computed by `breadth.py` (`compute_breadth`) but never
// declared here, so kotlinx.serialization silently dropped them on every parse. `band` and `dir`
// are two different axes that happen to share confusingly overlapping vocabulary ("strong"/"weak"
// on both) — `band` is how UNANIMOUS the currency's pairs agree (support/total, direction-blind);
// `dir` is the actual direction of that agreed-on net move (`strong`=net positive, `weak`=net
// negative, `flat`=net zero). A currency can be `band: strong` (near-unanimous) while `dir: weak`
// (that unanimous move is a weakening one) — CurrencyDetailSheet.kt's own colour bug was
// conflating the two. Never read `band` as a stand-in for direction; use `dir`.
@Serializable
data class BreadthEntry(
    val support: Int? = null,
    val total: Int? = null,
    val pct: Double? = null,
    val band: String? = null,
    val net: Double? = null,
    val dir: String? = null,
)

/**
 * 2026-09-04 — was a raw `Map<String, Map<String, BreadthEntry>>` (dynamic "h4"/"d1" keys); now a
 * real shape so the new `pairs` sub-block (added to `breadth.py` this session — same base-minus-
 * quote differential technique `conviction.py`'s own `pairs` block already uses) can sit alongside
 * the per-currency `h4`/`d1` maps without forcing every leaf in the JSON object to share one type.
 * Every current call site only ever read the `h4` key (breadth has no real per-timeframe UI use
 * yet), so `signals.breadth["h4"]` becomes `signals.breadth.h4` — a mechanical rename, not a
 * behavior change.
 */
@Serializable
data class BreadthBlock(
    val h4: Map<String, BreadthEntry> = emptyMap(),
    val d1: Map<String, BreadthEntry> = emptyMap(),
    val pairs: Map<String, Map<String, Int>> = emptyMap(), // tf ("h4"/"d1") -> pair -> signed score
)

@Serializable
data class PairBlock(
    val pills: Pills? = null,
    val mom: Momentum? = null,
    val adx: Double? = null,
    val cont: Int? = null,
    val structure: StructureBlock? = null,
    // Added 2026-09-03 — scan_h1.py already computed these to feed cont/the WHY-checklist ENTRY
    // gate; now also written to signals.json so EntrySheet can show real numbers.
    @SerialName("reset_score") val resetScore: Int? = null,
    @SerialName("atr_pct") val atrPct: Int? = null,
    // 2026-09-09 — the SAME direction (D1-else-H4 pill fallback, cont_score.pill_direction)
    // compute_cont() itself used to decide whether/how to score this pair, exposed so the app
    // can tint the wheel's Overall wing off the basis its own displayed number actually used
    // (was reading signals.potential[pair].direction, an unrelated, demoted subsystem — see
    // WheelMapper.kt's own doc comment for the bug this replaces). "bull" | "bear" | null.
    val direction: String? = null,
    // Signals Roadmap §5 (2026-09-09) — D1 12-period/2-sigma Bollinger touch state. Null
    // (whole object, matching bb_touch.py's own "not enough history yet" convention) rather
    // than a null-fielded object, so ignoreUnknownKeys/defaults never need to fight a partial
    // shape — same lesson as csm_dispersion_history's own on-device parse crash this session:
    // match the backend's actual shape exactly, including its null cases.
    @SerialName("bb_d1") val bbD1: BbD1? = null,
    // 2026-09-17 (Pieter's ask, ChartSheet RSI/MACD glance panel) — `scanner/extend/
    // momentum_series.py` calls the same frozen `_rsi`/`_macd` score.py already runs for its own
    // scoring, just keeping a short history instead of only the latest bar. One of these per
    // timeframe; a missing key (rather than an empty object) means that TF's history was too
    // short to compute — same "match the backend's null case exactly" convention bb_d1 uses.
    @SerialName("momentum_series") val momentumSeries: Map<String, MomentumSeries> = emptyMap(),
    // 2026-09-18 (Pieter's ask) — the other half of the glance panel: stock-standard 20-period
    // %B + a numeric BandWidth series, per timeframe. Deliberately a SEPARATE key from `bbD1`
    // above, which stays 12-period and D1-only because the BB touch alert is built on it —
    // `scanner/extend/bollinger_series.py`'s own doc comment has the full reasoning.
    @SerialName("bollinger_series") val bollingerSeries: Map<String, BollingerSeries> = emptyMap(),
    // 2026-09-19 (schema v12, Pieter's ask) — the Crowd score: the Crowded Market indicator's top/
    // bottom score per completed bar, "d1"/"h4" only (H1/M15 have no data by design). A missing key
    // (older cached JSON, or a pair whose backend module failed) means no card — never an error.
    // `scanner/extend/crowd_score.py`'s own doc comment has the full contract and the evidence
    // caveat: this is context, not a signal.
    @SerialName("crowd_series") val crowdSeries: Map<String, CrowdSeries> = emptyMap(),
)

/** `pairs.<PAIR>.crowd_series.<d1|h4>` (schema v12) — see `scanner/extend/crowd_score.py`.
 *  `top`/`bottom` are 0-100 scores per COMPLETED bar, oldest-first, the same length as `dates` (D1 ~50
 *  points, H4 up to 90). D1 is labelled by the NY-close trading day, H4 by the same (six blocks a day,
 *  New York-session aligned — TradingView's alignment, not the UTC blocks the sibling H4 series use).
 *  An empty block (`dates` empty, `latest` null) means too little history; the card is then hidden. */
@Serializable
data class CrowdSeries(
    val dates: List<String> = emptyList(),
    val top: List<Double> = emptyList(),
    val bottom: List<Double> = emptyList(),
    val latest: CrowdLatest? = null,
)

/** The newest completed bar's state. `topOn`/`bottomOn` name the conditions that are on (Phase 2's
 *  notification body uses them); `cotOk` false means the COT input could not be resolved for this
 *  pair, so the score is capped at 75 and the card says "No COT" rather than a state word. */
@Serializable
data class CrowdLatest(
    @SerialName("bar_date") val barDate: String? = null,
    val top: Double = 0.0,
    val bottom: Double = 0.0,
    @SerialName("top_on") val topOn: List<String> = emptyList(),
    @SerialName("bottom_on") val bottomOn: List<String> = emptyList(),
    @SerialName("cot_pctl") val cotPctl: Double? = null,
    @SerialName("cot_ok") val cotOk: Boolean = false,
    @SerialName("cot_asof") val cotAsof: String? = null,
)

/** `pairs.<PAIR>.bollinger_series.<d1|h4|h1>` (schema v10) — see `scanner/extend/
 *  bollinger_series.py`. `pctb`/`bandwidth`/`squeeze` are the same length and share an index;
 *  `pctbSma` is shorter by its own 20-bar smoothing window and right-aligns under `pctb`'s tail,
 *  the same contract `PercentBOscillator` already draws `bbD1.pctbSma` under. `squeeze` marks
 *  bars where BandWidth is at its lowest in 125 bars (Bollinger's own published definition —
 *  Pieter's explicit call over inventing an untuned percentile). */
@Serializable
data class BollingerSeries(
    val dates: List<String> = emptyList(),
    val pctb: List<Double> = emptyList(),
    @SerialName("pctb_sma") val pctbSma: List<Double> = emptyList(),
    val bandwidth: List<Double> = emptyList(),
    val squeeze: List<Boolean> = emptyList(),
)

@Serializable
data class MomentumSeries(
    // 2026-09-18 (restyle ask) — one ISO date per rsi/macd_line point, oldest-first. Recovered
    // independently of the frozen aggregator's own (date-less) H4/D1 bars -- see
    // `scanner/extend/tf_dates.py` and `momentum_series.py`'s own doc comment.
    val dates: List<String> = emptyList(),
    val rsi: List<Double> = emptyList(),
    @SerialName("macd_line") val macdLine: List<Double> = emptyList(),
    @SerialName("macd_signal") val macdSignal: List<Double> = emptyList(),
    @SerialName("macd_histogram") val macdHistogram: List<Double> = emptyList(),
)

@Serializable
data class BbD1(
    val touching: String? = null,
    val sma: Double? = null,
    val upper: Double? = null,
    val lower: Double? = null,
    @SerialName("width_pct") val widthPct: Double? = null,
    @SerialName("width_trend") val widthTrend: String? = null,
    // 2026-09-10 — %B (Pieter's ask): (close-lower)/(upper-lower)*100 over the same rolling bands
    // above, plus its own 12-period SMA as a signal line. Oldest-first, up to 56 recent D1 bars.
    // Not clamped to 0-100 here — a real close outside the bands legitimately pierces past 0/100;
    // only the chart that draws this clamps for display. Computed fresh every scan straight from
    // D1 closes (bb_touch.py already holds the full rolling series, not just today's snapshot),
    // so unlike rotation/pulse/breadth_thrust this needs no scan-to-scan history round-trip.
    val pctb: List<Double> = emptyList(),
    @SerialName("pctb_sma") val pctbSma: List<Double> = emptyList(),
    // 2026-09-10 (2nd) — one ISO date ("YYYY-MM-DD") per pctb point, same order/length. Empty
    // when the backend's own alignment check against the frozen D1 frame didn't match (see
    // bb_touch.py's compute_bb_d1 doc comment) — never partially/mis-aligned, only all-or-nothing.
    @SerialName("pctb_dates") val pctbDates: List<String> = emptyList(),
)

@Serializable
data class Pills(
    val d1: String? = null,
    val h4: String? = null,
    val h1: String? = null,
)

@Serializable
data class Momentum(
    val d1: Int? = null,
    val dd1: Int? = null,
    val h4: Int? = null,
    val dh4: Int? = null,
    val h1: Int? = null,
    val dh1: Int? = null,
    val cmp: Int? = null,
    val dcmp4: Int? = null,
    val dcmp8: Int? = null,
    val dcmp12: Int? = null,
)

@Serializable
data class StructureBlock(
    val h4: StructureEntry? = null,
    val d1: StructureEntry? = null,
)

@Serializable
data class StructureEntry(
    val direction: String? = null,
    val event: String? = null,
    val strength: Double? = null,
    // 2026-09-17 (full-system audit) — `scanner/structure.py` already computes and writes this
    // (0.40-1.30, scales the H4/D1 momentum score up on a same-direction BOS, down on a
    // counter-trend CHoCH — see structure.py:112-116), and LibraryContent.kt already explains
    // the concept to the user, but it was silently dropped here (`ignoreUnknownKeys`) with no
    // UI ever showing the real per-pair value. Now surfaced in StructureSheet.kt.
    val multiplier: Double? = null,
)

@Serializable
data class PotentialEntry(
    val direction: String? = null,
    val level: Int? = null,
    val state: String? = null,
    val score: Int? = null,
    val factors: PotentialFactors? = null,
    @SerialName("blocked_at") val blockedAt: String? = null,
    @SerialName("setup_rank") val setupRank: Double? = null,
    val quality: Int? = null,
)

@Serializable
data class RecommendationBlock(
    val headline: String? = null,
    val bias: String? = null,
    val action: String? = null,
    @SerialName("primary_pair") val primaryPair: String? = null,
    val direction: String? = null,
    val confidence: String? = null,
    val rationale: String? = null,
    val invalidation: String? = null,
    @SerialName("next_catalyst") val nextCatalyst: NextCatalyst? = null,
    val headlines: List<String> = emptyList(),
    @SerialName("generated_at") val generatedAt: String? = null,
)

/** `rank.py::rank_pairs` scored+sorted (never re-derived here) — corrected 2026-09-17, this
 *  previously said "capped to the top 3 by `scan_news.py::call_ranked_analysis`," which was
 *  backwards on both counts. `scan_h1.py` is `top`'s sole writer (hourly, edge-triggered
 *  `_recommendation_alerts`) — `scan_news.py` deliberately does NOT write `top`, only
 *  `text`/`updated`, after a 2026-09-17 bugfix where it silently also wrote `top` on its own
 *  ~2h cadence with no alert, letting a pair enter/exit with zero notification (see
 *  `ATOM_FX_SIGNALS_ROADMAP.md` §5b's own bugfix note). And there's no top-3 cap any more either
 *  — every pair scoring >= `RECOMMENDATION_MIN_SCORE` (6.5) qualifies, no upper bound. Home's
 *  per-pair Recommendation glyphs (`StatusStrip.kt`) read this list directly, one glyph per
 *  entry, with no count assumption. If you're about to touch how this field gets written,
 *  read the bugfix note above first — `scan_h1.py` must stay the only writer. */
@Serializable
data class RankedBlock(
    val text: String? = null,
    val top: List<RankedEntry> = emptyList(),
)

@Serializable
data class RankedEntry(
    val pair: String? = null,
    val direction: String? = null,
    val score: Double? = null,
)

@Serializable
data class NextCatalyst(
    val event: String? = null,
    val iso: String? = null,
)

@Serializable
data class DeepAnalysisBlock(
    val text: String? = null,
    @SerialName("generated_at") val generatedAt: String? = null,
)

/** Functional Spec §7 — top-3 Haiku-curated breaking headlines. */
@Serializable
data class BreakingBlock(
    val headlines: List<String> = emptyList(),
    // Functional Spec §7 theme tagging — same length/order as [headlines], one of
    // "risk"/"rates"/"usd"/"commodity"/"safe_haven" per item, or null if untagged.
    val themes: List<String?> = emptyList(),
    val updated: String? = null,
)

/** Functional Spec §7 — the adversarial "does any headline conflict with the top setups?" check. */
@Serializable
data class CatalystBlock(
    val text: String? = null,
    val updated: String? = null,
)

/** Functional Spec §7 row 50 — generated Sunday evenings, persisted ~24h server-side; a normal,
 *  documented absence the rest of the week (Architecture §4.2), not a bug. */
@Serializable
data class WeekAheadBlock(
    val text: String? = null,
    @SerialName("generated_at") val generatedAt: String? = null,
)

@Serializable
data class PotentialFactors(
    val regime: Boolean = false,
    val flow: Boolean = false,
    val breadth: Boolean = false,
    val momentum: Boolean = false,
    val structure: Boolean = false,
    val entry: Boolean = false,
)

@Serializable
data class CorrelationsBlock(
    val pairs: List<String> = emptyList(),
    val matrix: List<List<Double>> = emptyList(),
)

@Serializable
data class MacroAssetEntry(
    val value: Double? = null,
    @SerialName("delta_pct") val deltaPct: Double? = null,
    @SerialName("delta_bp") val deltaBp: Double? = null,
    val direction: String? = null,
    val label: String? = null,
)

@Serializable
data class MacroSummary(
    val label: String? = null,
    val signals: Int? = null,
    val total: Int? = null,
    val confidence: String? = null,
    val stable: Boolean? = null,
)

@Serializable
data class MacroRegimeBlock(
    val primary: MacroArchetype? = null,
    val secondary: MacroArchetype? = null,
    @SerialName("gold_overlay") val goldOverlay: String? = null,
    @SerialName("usd_regime") val usdRegime: String? = null,
    @SerialName("currency_bias") val currencyBias: CurrencyBias? = null,
    val evidence: List<MacroEvidence> = emptyList(),
    val conflicts: List<String> = emptyList(),
    val narrative: String? = null,
    val updated: String? = null,
    // 2026-09-10 (Pieter's ask) — "confirmed" (a supporting axis has a same-theme recent
    // headline, scan_news.py's own tag_theme() output), "price_only" (no matching headline),
    // or "unknown" (nothing to check against). See macro_regime.py's `_news_corroboration`.
    @SerialName("news_corroboration") val newsCorroboration: String? = null,
)

@Serializable
data class MacroArchetype(
    val code: String? = null,
    val name: String? = null,
    val confidence: String? = null,
    @SerialName("distinct_axes") val distinctAxes: Int? = null,
)

@Serializable
data class CurrencyBias(
    val strong: List<String> = emptyList(),
    val weak: List<String> = emptyList(),
)

@Serializable
data class MacroEvidence(
    val axis: String? = null,
    val read: String? = null,
    val supports: Boolean = false,
    // "confirming" | "diverging" | "quiet" — 2026-09-06: is TODAY's daily move backing this
    // axis's own W1 trend, fighting it, or silent (see macro_regime.py's own doc comment).
    // A property of the axis itself, not of whichever regime `supports` happens to be about.
    @SerialName("confirms_today") val confirmsToday: String? = null,
)

/** Signals Roadmap §4 — the COT-based Conviction/crowding overlay. Weekly cadence (its own
 *  `scan_cot.py` job, not the hourly scan), so `updated`/`cotDate` can lag `signals.updated`
 *  by up to several days — presented as a positioning overlay, not a live signal. */
@Serializable
data class ConvictionBlock(
    val currencies: Map<String, ConvictionEntry> = emptyMap(),
    val pairs: Map<String, Int> = emptyMap(),
    @SerialName("cot_date") val cotDate: String? = null,
    @SerialName("cot_stale") val cotStale: Boolean? = null,
    val updated: String? = null,
)

@Serializable
data class ConvictionEntry(
    val conviction: Int? = null,
    val direction: Int? = null,
    @SerialName("cot_available") val cotAvailable: Boolean? = null,
)

@Serializable
data class SparkEntry(
    val d1: List<Double> = emptyList(),
    val h4: List<Double> = emptyList(),
    val h1: List<Double> = emptyList(),
)

/** 2026-09-10 (2nd) — pointwise mean of every pair's `bb_d1.pctb`/`pctbSma`, oldest-first. See
 *  `bb_touch.py`'s `compute_board_percent_b` for the exact aggregation. Same shape reused
 *  (2026-09-10, 5th) as each value in `Signals.percentBCurrency`'s per-currency map. */
@Serializable
data class PercentBBoardBlock(
    val line: List<Double> = emptyList(),
    val signal: List<Double> = emptyList(),
    val dates: List<String> = emptyList(),
)
