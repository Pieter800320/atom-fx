# Crowded Market — Reversal Conditions (G10 FX)

**File:** `crowded_reversal.pine` (Pine Script v6, indicator, overlay)
**Scope:** G10 FX majors, tuned as a starting point for H4 and D1.
**Status:** Not backtested. Not committed. For review, compilation, and validation in your own
Python backtester before any live use.

---

## 0. What problem this is trying to solve

"Crowded market" here means the literal thing: a market where positioning, momentum, and price
extension have all moved to one side at once, and evidence exists (via COT) that speculative
participants are concentrated there too. That concentration is what makes a reversal — a
squeeze, an unwind, a snap-back — more likely than usual. It does **not** mean the move is over,
and it does not mean fading it is profitable on its own. It means several independent, named,
recognized conditions have aligned. What you do with that information, and whether it holds up
historically on your own data, is a separate question this indicator does not answer.

---

## 1. Research: the recognized methods this is built from

Every factor below is a synthesis built from a named, established technique — nothing here was
invented. Sources are general/canonical (these are decades-old, widely taught concepts, not tied
to one single publication), cited by name so you can verify independently.

### 1.1 Envelope / mean-reversion extremes
- **Bollinger Bands and %B** (John Bollinger). %B expresses where price sits relative to the
  bands on a 0–1 scale (below 0 = below the lower band, above 1 = above the upper band). This is
  the most standard "is price stretched relative to its own recent volatility" measure in
  technical analysis.
- **Keltner Channels** were researched as an alternative/complementary envelope (ATR-based rather
  than stdev-based). Not separately implemented as a distinct factor — the ATR-stretch factor
  (below) captures the same "volatility-normalized distance from a mean" idea Keltner channels
  are built on, without duplicating Bollinger's stdev-based envelope with a near-identical
  ATR-based one.

### 1.2 Statistical stretch
- **Z-score of price vs. a moving average.** A standard statistical measure of how many standard
  deviations price has moved from its own recent mean. |z| ≥ 2 is the conventional "statistically
  unusual" threshold (~95% of a normal distribution sits within ±2σ) — FX returns aren't strictly
  normal (fat tails), so this is a useful heuristic, not a probability guarantee.
- **ATR-normalized distance from a moving average.** The same "distance from the mean" idea,
  denominated in ATR units instead of price-stdev units — makes the "how stretched" question
  comparable across pairs and volatility regimes (3 ATR on a quiet pair and 3 ATR on a volatile
  one represent a similarly unusual move, even though the price-stdev z-score of the same move
  could differ).

### 1.3 Momentum exhaustion & divergence
- **Wilder's RSI**, at both the standard 70/30 threshold and a stricter 80/20 "extreme" tier.
- **Regular (classic) bullish/bearish divergence**: price makes a new extreme, the oscillator
  does not confirm it. This is the strongest single factor in the whole script (see §2, weights)
  because it is a direct statement about momentum failing to confirm price — not just "price is
  far from average," but "the move itself is running out of underlying force." Detected here via
  **confirmed pivots on the RSI series** (`ta.pivothigh`/`ta.pivotlow`), the standard technique —
  the same conceptual algorithm underlying TradingView's own built-in "Divergence Indicator," ​
  re-implemented independently here rather than copied.
- **DeMark TD Sequential** was researched as a recognized exhaustion-counting concept (a bar-count
  method for identifying likely exhaustion points). It is **not reimplemented** — TD Sequential is
  a specific, trademarked counting algorithm with its own precise setup/countdown rules, and
  reproducing it faithfully is a project in itself. It's noted here because it's part of the
  established vocabulary of "exhaustion," and because regular divergence (which *is* implemented)
  captures a closely related idea — momentum failing to keep pace with price — through a simpler,
  fully transparent mechanism.

### 1.4 Volatility / climax
- **True range / ATR expansion spikes coinciding with a band pierce**, the classic **Wyckoff
  "buying climax" / "selling climax"** signature: an abnormally large range move, often (not
  always) marking exhaustion of the current leg rather than the start of a sustained new one.
  This factor does not claim every climax reverses — it flags the *conditions* of a climax
  (abnormal range + band pierce), which is what Wyckoff's concept actually describes.

### 1.5 Positioning / crowding (COT)
- **CFTC Commitments of Traders (COT) contrarian analysis.** When large speculators /
  non-commercial traders are positioned at a multi-year extreme (very long or very short), that
  positioning itself is a source of fragility — a large one-sided position is vulnerable to
  being unwound (stopped out, margin-called, or simply taking profit together), which can
  accelerate a reversal once it starts. This is the literal, textbook meaning of "crowded," and
  it is the one factor here that measures *market participants*, not just price/momentum
  patterns derived from price alone. See §4 and §6 for the FX-specific caveats — COT is the
  single most caveated factor in this script, deliberately.

### 1.6 Regime context
- **ADX** (Wilder) as a trend-strength filter, and **EMA slope** for trend direction. This pairing
  answers the one question every other factor above is silent on: *is this a market where fading
  extremes actually works right now?* Overbought/oversold, band-touches, and even divergence can
  all persist through a strong, healthy trend — professional discretionary traders know this, and
  a mean-reversion-flavoured tool that doesn't account for it will systematically lose money
  fading strong trends. This is why the regime filter is **mandatory**, not a toggle-off option
  (the "Off" mode exists only for comparison/testing, and is not the default).

---

## 2. Factor list, defaults, and the confluence scoring scheme

Every factor is a **boolean threshold check** (met / not met), not a sliding scale — this keeps
the whole scheme transparent and auditable: every point in the final score traces back to one
named, documented condition being true or false on that bar. When a factor's condition is met, it
contributes its own fixed **weight** (an input, freely retunable) to either the **top (bearish)**
score or the **bottom (bullish)** score. The two scores are computed completely independently —
a bar can in principle score on both sides at once (e.g. genuinely conflicting signals), which is
informative in itself rather than being forced into one verdict.

**Normalization.** The raw point total for each side is divided by the sum of the weights of
*currently active* factors (i.e. `cotEnabled = false` removes COT's weight from the denominator
too), then scaled to 0–100. This means the score is always meaningfully 0–100 regardless of which
factors you've toggled on — if you turn three factors off, the remaining ones can still sum to a
"full" 100.

| # | Factor | Default threshold | Default weight | Notes |
|---|--------|-------------------|-----------------|-------|
| 1 | %B extreme | ≥ 1.0 (top) / ≤ 0.0 (bottom) | 10 | Bollinger(20, 2.0) |
| 2 | Z-score extreme | |z| ≥ 2.0 | 8 | vs SMA(100) |
| 3 | ATR stretch extreme | |stretch| ≥ 3.0 ATR | 7 | close vs EMA(50), ATR(14) |
| 4 | RSI extreme | ≥ 70 (top) / ≤ 30 (bottom) | 12 | Wilder RSI(14) |
| 5 | RSI extreme tier (bonus, additive) | ≥ 80 / ≤ 20 | 3 | Stacks on top of #4 |
| 6 | Regular divergence | confirmed pivot divergence | **25** | Strongest single factor |
| 7 | Volatility climax | TR ≥ 1.8×ATR(14) + band pierce | 10 | Optional bandwidth-percentile gate |
| 8 | COT positioning extreme | ≥90th / ≤10th percentile (156wk) | **25** | Strongest, tied with divergence |

Default active total = 100 (10+8+7+12+3+25+10+25). A bar needs to cross the **score threshold**
(default 60 for both top and bottom) to be flagged — meaning, by default, roughly "divergence or
COT extreme, *plus* one or two corroborating factors," or "most of the non-COT, non-divergence
factors all agreeing at once" — never a single oscillator reading alone. This is a direct,
deliberate implementation of the brief's "confluence, not a single oscillator" requirement.

**Why divergence and COT are weighted highest:** both are qualitatively different from the other
six factors. The other six are all, at bottom, restatements of "price/momentum is numerically far
from its own recent normal" — correlated with each other by construction (a big move will often
trip %B, z-score, ATR-stretch, and RSI simultaneously, because they're all measuring the same
underlying move from slightly different angles). Divergence and COT each add genuinely
**independent** information: divergence says the *internal* momentum of the move itself is
failing, and COT says *who is positioned* in the move — a large speculative extreme won't show up
in any price-derived factor at all, and vice versa. Weighting them highest reflects that they add
new information rather than mostly re-confirming the same thing.

**Regime adjustment (mandatory, applied after scoring):**
- Default **Suppress**: if ADX ≥ 30 and the 200-EMA is sloping in the trend's direction (and
  price is on the trend side of that EMA), the **counter-trend** score (top in an uptrend, bottom
  in a downtrend) is set to **zero**. The with-trend score is untouched.
- **Penalize**: same trigger condition, but the counter-trend score is multiplied by a
  configurable penalty factor (default 0.5) instead of zeroed.
- **Off**: no adjustment. Provided for comparison/testing only — not recommended for live use,
  since this removes the one thing standing between the tool and "fade every strong trend."

**Debounced entry (fixed 2026-09-18, on-device testing).** A first pass on live AUDUSD H4/D1
data showed the strong-trend background shading covering nearly the entire chart, including
clearly flat, range-bound stretches — the raw `ADX >= 30` condition was whipsawing on and off
every few bars, which a plain instantaneous check turned straight into flickering suppress/
penalize behaviour. Fixed with a simple, asymmetric debounce: the raw condition must hold for
`regimePersistBars` consecutive bars (default 3) before the regime filter actually **engages**,
but drops out **immediately** the instant the raw condition breaks — deliberate to enter, fast to
exit, so the filter doesn't keep suppressing on stale state once a trend has genuinely ended. A
diagnostic `Show ADX (diagnostic)` plot was also added (off by default) so ADX's actual behaviour
against its own threshold can be inspected directly on any symbol, rather than only inferred from
the shading.

All thresholds and weights above are **starting points for H4/D1 G10 majors**, chosen from
general TA convention (RSI 70/30, %B 0/1, z ≥ 2, ADX ≥ 30 are all standard textbook values) —
**not fit to any specific pair's history.** They have not been optimized, and doing so on limited
FX history is itself a well-known way to overfit (see §7). Retune deliberately, and validate any
retuning out-of-sample.

---

## 3. FX & Pine data caveats

### 3.1 General
- FX **spot** has no centralized, reliable volume feed — this script does not use volume for
  anything (deliberately; a volume-based factor would be fabricating data quality FX spot doesn't
  have).
- All price-based factors (§1.1–1.4) use whatever OHLC the chart provides. On H4/D1 this is
  normally fine, but be aware that different data vendors' aggregation of a 24-hour FX market into
  daily bars can differ slightly (session boundary conventions).

### 3.2 COT specifically — read this before trusting the COT factor
- **COT is weekly, not intrabar.** The CFTC's Commitments of Traders report reflects positions as
  of the **prior Tuesday**, and is published the **following Friday** — roughly a 3-to-4-day-old
  snapshot even on the day it's released, and up to 9 days stale just before the next release.
  This factor is a **slow-moving background condition**, never a same-day trigger. Do not expect
  it to react to news.
- **Futures are a proxy for spot, not spot itself.** FX spot itself has no COT report. This uses
  CME/ICE **currency futures** positioning as the recognized, industry-standard proxy for
  speculative FX positioning. Futures and spot positioning can and do diverge, especially for
  retail-heavy pairs where a large share of spot flow never touches the futures market at all.
- **Symbol mapping is explicit, not fabricated, and has a real failure mode.** The script maps
  each of the 8 relevant currencies to its own CFTC contract code, verified directly against
  CFTC.gov's own published Commitments of Traders contract listings:

  | Currency | CFTC code | Contract |
  |----------|-----------|----------|
  | EUR | 099741 | Euro FX (CME) |
  | GBP | 096742 | British Pound Sterling (CME) |
  | JPY | 097741 | Japanese Yen (CME) |
  | CHF | 092741 | Swiss Franc (CME) |
  | CAD | 090741 | Canadian Dollar (CME) |
  | AUD | 232741 | Australian Dollar (CME) |
  | NZD | 112741 | New Zealand Dollar (CME) |
  | USD | 098662 | U.S. Dollar Index (ICE Futures U.S.) |

  USD has no currency future of its own (it's the denominator of most of the others), so the ICE
  U.S. Dollar Index contract is used as the recognized USD-specific proxy.

  The actual TradingView **ticker string** built from each code
  (`COT:<code>_F_NCP_L` / `_F_NCP_S` — Non-Commercial Position, Legacy report, Long/Short,
  futures-only) follows TradingView's COT-feed suffix convention. **This exact suffix format is
  the single least-certain part of this whole script.** A first compile/run against live
  TradingView data caught the ticker *prefix* wrong (`CFTC:` — since corrected to `COT:`, verified
  by resolving a live TradingView symbol page for Euro FX open interest, `COT:099741_F_OI`); the
  `_F_NCP_L`/`_F_NCP_S` suffix specifically is still not independently confirmed the same way.
  TradingView's own support documentation describes the full ticker ID format as "somewhat
  complex," which is exactly why TradingView publishes an official `LibraryCOT` Pine library to
  generate these strings programmatically rather than hand-building them — and why this specific
  factor, more than any other in the script, should be treated as "verify against your own chart
  before trusting it," not "verify once and forget." This script deliberately does **not** depend
  on that library, because its exact current function signatures could not be verified with full
  confidence from outside the Pine Editor (see §5 for why that specific tradeoff was made).
  Instead:
  - Net position = Non-Commercial **Long − Short**, fetched as two separate, individually
    verifiable series (rather than trusting a single unverified compound "net" ticker to exist).
  - `ignore_invalid_symbol = true` is set on every COT `request.security()` call — an incorrect
    or unmapped ticker returns `na` instead of crashing the script.
  - Every downstream use of COT data explicitly checks for `na` (see `baseAvail`/`quoteAvail` in
    the script) and disables the factor gracefully — reducing the active-weight denominator
    rather than silently treating missing data as a real (e.g. zero) value.
  - A **ticker override** input exists per currency, per side (Long/Short), for exactly this
    reason: if the auto-built ticker doesn't resolve for a given currency, open TradingView's
    Symbol Search, find the correct COT ticker for that currency by hand, and paste it into the
    override field — no code change needed.
  - **If this happens to you:** it means the mapping needs a one-time manual correction, not that
    the underlying method is wrong. Please don't read a blank COT factor as "the tool is broken" —
    read it as the graceful-fallback working exactly as designed.

- **Base/quote sign orientation is explicit** (see the script's own `cotCombinedPctl`/
  `cotCombinedZ` comments): a currency being crowded-long is **bearish** for a pair where it's the
  **base** currency, and **bullish** for a pair where it's the **quote** currency (since quote
  strength = pair weakness). Both legs of a pair are mapped and combined (averaged, not summed,
  so a pair with only one resolvable leg isn't structurally under-weighted relative to one with
  two) — this generalizes uniformly to USD pairs (USD's own leg uses the Dollar Index) and
  non-USD crosses (both legs use their own currency futures), without special-casing either.

- **Normalization window is 156 weeks (~3 years) by default**, computed as a percentile rank (or
  optionally a z-score) of the current net position against its own recent history. On an H4/D1
  chart, the weekly COT value is forward-filled across many intrabar/daily bars; the script
  converts the 156-week window into an equivalent chart-bar count (~5 bars/day × 5 days/week)
  rather than running the percentile calculation over raw chart bars, which would otherwise
  either span far too short a real time window (H4) or overweight recent history unevenly.

---

## 4. No-repaint / no-look-ahead measures

- **`request.security()`** (COT only — there is no other higher-timeframe reference in this
  script) always uses `lookahead = barmerge.lookahead_off`, and the requested expression itself
  is offset with `[1]` (i.e. `close[1]` evaluated *inside* the weekly context) — meaning only the
  **last fully completed and released** weekly COT report is ever read, never a still-forming
  current week. This is the standard, documented no-repaint pattern for referencing
  higher-timeframe data from a lower-timeframe chart.
- **Signals are evaluated on bar close** by default (`Confirm signals on bar close`, on by
  default) — gated via `barstate.isconfirmed`. The confluence score *lines* still update intrabar
  (like any live oscillator plot would), but the top/bottom **flags** (labels + alerts) only fire
  once the bar that produced them is actually closed.
- **Divergence is lag, not repaint**, and this is stated plainly rather than glossed over: a pivot
  confirmed via `ta.pivothigh`/`ta.pivotlow` with a right-lookback of `divRight` bars (default 5)
  is, by construction, only knowable `divRight` bars after it happened. The script never claims
  to have detected a pivot before it was mechanically confirmable — the `divHoldBars` mechanism
  (default 5 bars) keeps a confirmed divergence "active" in the score for a short window
  afterward specifically so it can combine with other same-day factors, not to pretend it fired
  earlier than it did.
- No `lookahead_on` appears anywhere in the script.

---

## 5. What this is NOT

- **This is not a mechanical buy/sell system.** It flags a *condition* — elevated confluence of
  overextension, exhaustion, climax, and (where available) crowded positioning — not a signal
  that has been shown to precede a profitable trade. Turning a flag into a trade decision
  (entry, sizing, stop, target) is a separate design problem this indicator does not solve.
- **Extremes persist.** The regime filter reduces, but cannot eliminate, the risk of fading a
  strong trend. A market can stay "crowded" and keep moving for a long time, especially in FX
  during a genuine macro trend (a central bank cycle, a risk-on/risk-off regime). Suppression
  during a strong trend is a mitigation, not a guarantee.
- **No performance claim is made anywhere in this deliverable** — not a win rate, not an accuracy
  percentage, not "high probability" language. None of that has been measured. Anyone telling you
  a script like this "works" without having tested it is telling you something they don't know.
- **It has not been backtested.** TradingView's visual chart hindsight — scrolling back and
  seeing that a label lined up with a turn — is not validation; it is exactly the kind of
  after-the-fact pattern-matching that produces statistical artifacts (the thing you said you'd
  just spent weeks learning to kill). Before this informs any real decision, run it through your
  own Python backtester, on out-of-sample data, with realistic costs, and look specifically for
  whether the confluence score threshold actually has predictive value beyond what its individual
  components would give you separately — a combined 60+ score should outperform any single factor
  alone, or the extra complexity isn't earning its keep.
- **The COT factor is the weakest-verified part of this script**, specifically the exact
  TradingView ticker suffix convention (§3.2). Test it first, and don't trust a specific pair's
  COT reading until you've confirmed via TradingView's own COT chart (or Symbol Search) that the
  ticker this script builds actually matches the series you expect.
- **Defaults are starting points, not tuned parameters.** Every threshold and weight in this
  script came from general TA convention, not from fitting this specific tool's history. Retuning
  them without out-of-sample validation just moves the overfitting from "which indicator" to
  "which threshold" — the same trap in a different shape.

This is meant to be a transparent, auditable **input** to your own research process — not a
finished trading system, and not a claim about what will happen next.
