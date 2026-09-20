# Crowded Market — Reversal Conditions (G10 FX)

**File:** `crowded_reversal.pine` (Pine Script v6, indicator, overlay)
**Scope:** G10 FX majors, starting-point defaults for D1 and H4 (not tuned). In testing only D1
in 2021-2026 showed any tendency; H4 and D1 in 2009-2020 showed none (§6).
**Status:** Backtested once, as a pre-registered study (Experiment 2, 2026-09-19 — see §6 and
`docs/RESEARCH_LOG.md` on the `research` branch): a **small** statistical tendency for reversals to
become more likely as the score rises, with the regime filter **off**; with it on, the same test
narrowly failed. On **H4** a second pre-registered study (Experiment 3) found **no** such tendency.
This is not evidence of a profitable trading rule. Committed on `main`.

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
  fading strong trends. **That was the reasoning for making the filter mandatory. It was tested
  and did not hold up (§6):** on 2021-2026 data, suppressing counter-trend scores inside strong
  ADX trends made the score a *worse* predictor of reversal than no filter. The filter is now
  optional and **off by default** (changed 2026-09-19); the rationale above is kept for the record.

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

**Regime adjustment (optional, applied after scoring — default Off since 2026-09-19, see §6):**
- **Off** (default): no adjustment. Chosen because Experiment 2 found Suppress made the score a
  worse reversal predictor (§6). The strong-trend background shading still draws, as information.
- **Suppress** (the previous default): if ADX ≥ 30 and the 200-EMA is sloping in the trend's
  direction (and price is on the trend side of that EMA), the **counter-trend** score (top in an
  uptrend, bottom in a downtrend) is set to **zero**. The with-trend score is untouched.
- **Penalize**: same trigger condition, but the counter-trend score is multiplied by a
  configurable penalty factor (default 0.5) instead of zeroed. **Never tested.**

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
  `_F_NCP_L`/`_F_NCP_S` suffix specifically was not independently confirmed the same way.
  **Update 2026-09-19:** the new per-leg COT diagnostic label (input "Show COT diagnostic label")
  on EURUSD D1 showed real numbers for both legs (EUR `COT:099741_F_NCP_L`/`_S`: net -24925,
  pctl ~15; USD `COT:098662_F_NCP_L`/`_S` — the ICE Dollar Index contract: net 17025, pctl 84.1;
  combined pctl 15.4, which matches the (EUR + (100 - USD)) / 2 formula). So both tickers
  **resolve**.
  **Series identity confirmed 2026-09-19** against CFTC's own Legacy Futures-Only history file
  (`https://www.cftc.gov/files/dea/history/deacot2026.zip`, `annual.txt`, columns
  "Noncommercial Positions-Long/Short (All)"): EUR net -24,925 and USD net 17,025 are **exactly**
  the CFTC 2026-09-01 report values (EUR 203,477 long / 228,402 short; USD 28,240 / 11,215). So
  `_F_NCP_L`/`_F_NCP_S` is the Legacy, futures-only, non-commercial series, and 098662 is the ICE
  Dollar Index contract.
  **Staleness observed:** when read on Sat 2026-09-19, the value was the 09/01 report, TWO
  reports behind the newest (09/15, released Fri 09/18; 09/08 was the one before). The `[1]`
  offset accounts for one week. **Narrowed same day:** TradingView's own `COT:099741_F_NCP_L`
  daily chart already showed the 09/15 report (209,000) stamped on 09/15 — the Tuesday as-of
  date — so its feed is fresh and stamps the as-of date, not the release date. The extra week is
  therefore on the script's side: `close[1]` stacked on top of `lookahead_off`'s own one-bar HTF
  lag on historical bars. **Confirmed** by the diagnostic's offset test (plain weekly request
  198,509 vs the script's `close[1]` 203,477) and fixed the same day — see §4. For a slow 156-week percentile this is
  low-impact live, but it matters for backtest alignment (what was actually known at each bar).
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
  script) uses `close[1]` with `lookahead = barmerge.lookahead_on` (changed 2026-09-19, see below)
  — the standard, documented no-repaint pattern for higher-timeframe data. On every chart bar,
  historical and live, it returns the **previous completed weekly bar**, never a still-forming
  current week. `lookahead_on` is only look-ahead-prone *without* the `[1]`; with it, the value
  returned was already final before the current chart bar's week began.
  **Why it changed:** the original `close[1]` + `lookahead_off` stacked two one-week lags on
  historical bars. Verified with the diagnostic label on EURUSD D1 (Sat 2026-09-19): the plain
  weekly request (`W close`) returned 198,509 (the 09/08 report), while the script's `close[1]`
  returned 203,477 (the 09/01 report) — one report older than necessary, and TradingView's own
  feed already held 09/15 (209,000). **Verified after the fix (same chart, same day):** the label's offset test read `now=198509`
(new pattern) vs `previous=203477` (old pattern), as predicted. Effect: the script
  now reads one report fresher than before (still never a report that wasn't public on that bar).
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
- `lookahead_on` appears in exactly one place — the COT request above — and always together with
  the `[1]` offset. (The diagnostic label also requests the previous `lookahead_off` pattern, for
  comparison only; it never feeds the score.) Before 2026-09-19 no `lookahead_on` existed anywhere.

---

## 5. What this is NOT

- **This is not a mechanical buy/sell system.** It flags a *condition* — elevated confluence of
  overextension, exhaustion, climax, and (where available) crowded positioning — not a signal
  that has been shown to precede a profitable trade. Turning a flag into a trade decision
  (entry, sizing, stop, target) is a separate design problem this indicator does not solve.
- **Extremes persist.** A market can stay "crowded" and keep moving for a long time, especially
  in FX during a genuine macro trend (a central bank cycle, a risk-on/risk-off regime). The ADX
  regime filter was meant to mitigate this, but it did not improve the score's predictive value
  when tested (§6), so it is off by default — the risk of fading a strong trend is not removed.
- **No performance claim is made anywhere in this deliverable** — not a win rate, not an accuracy
  percentage, not "high probability" language. The one measurement made (§6) is a small
  statistical tendency in a barrier-race test, not a win rate and not a profit. Anyone telling you
  a script like this "works" without having tested it is telling you something they don't know.
- **It has been backtested once, and only in a limited way** (§6). TradingView's visual chart
  hindsight — scrolling back and seeing that a label lined up with a turn — is still not
  validation. H4 has now been tested and showed no effect (§6). Not yet tested: costs, sizing,
  stops or entries; thresholds other than the defaults; other pairs; earlier market regimes; live forward data. The result also did **not**
  show that the composite beats its best single factor — the extra complexity has not yet
  demonstrated it earns its keep.
- **The COT factor's ticker and series are verified** (§3.2, 2026-09-19: values match CFTC's
  Legacy futures-only report exactly, both legs resolve, including the ICE Dollar Index). It is
  still weekly, futures-based proxy data; don't read it as same-day information.
- **Defaults are starting points, not tuned parameters.** Every threshold and weight in this
  script came from general TA convention, not from fitting this specific tool's history. Retuning
  them without out-of-sample validation just moves the overfitting from "which indicator" to
  "which threshold" — the same trap in a different shape.

This is meant to be a transparent, auditable **input** to your own research process — not a
finished trading system, and not a claim about what will happen next.

---

## 6. Experiment 2 — what was actually measured (2026-09-19)

Full record, pre-registration and reproduction steps: `docs/RESEARCH_LOG.md` on the `research`
branch (code: `scanner/extend/{crowded_reversal,barrier_race,score_gradient}.py`, runner
`tools/backtest_crowded_reversal.py`, outputs `data/backtest/crowded_reversal_exp2_2026-09/`).

**Test.** Pre-registered before any result existed and run once. For every bar and each side, did
price reach 1×ATR(14) in the reversal direction before 1×ATR against it, within 20 bars (a bar
touching both counts as *no* reversal; no barrier in 20 bars also counts as no reversal)? The
question: does that probability rise with the score, controlling for pair and side? 12 pairs, D1,
NY-close bars aggregated from H1, 2021-01 to 2026-09 (the deepest NY-close history the data
provider offers), ~35,000 observations per arm, month-clustered bootstrap, seven fixed gates.

| | regime filter ON (previous default) | regime filter OFF (now the default) |
|---|---|---|
| Slope per +20 score points (95% CI) | +0.026 (−0.002 .. +0.059) | +0.041 (+0.018 .. +0.070) |
| Verdict on the pre-registered gates | **FAIL** (narrowly) | **PASS** (all 7) |

**ADX filter, by the pre-registered rule:** on-minus-off = −0.015 (95% CI −0.026 .. −0.004) — the
filter made the score a *worse* predictor. That is why it is now off by default.

**Read it modestly.** The effect is small (bars scoring 10-30 reversed roughly 4 points more often
than baseline; the top of the score range has too few observations to say more); it says nothing
about profit; the composite was not clearly better than its best single factor (ATR-stretch); the
non-default arm was the one that passed; it is one macro era; and the flagged-event test
(score ≥ 60) had only 25-32 events, so it is inconclusive.

### Experiment 3 — the same test on H4 (2026-09-19)

Prompted by the observation, from looking at charts, that the flags "work on both D1 and H4." Also
pre-registered before any H4 result existed, and run once. Same reversal definition (1×ATR barrier
race, 20 bars — on H4 that is about 3.3 trading days), same 12 pairs, ~77 months (2020-05 to 2026-09),
H4 bars built from the same H1 history in two alignments: the app's UTC 4-hour blocks (primary) and
NY-aligned blocks (what TradingView's FX H4 uses). Primary test: the flags themselves (score ≥ 60,
filter off), 176 / 206 de-clustered events — enough, unlike D1.

| filter OFF | H4, UTC blocks (primary) | H4, NY-aligned |
|---|---|---|
| Flagged reversal rate vs like-for-like baseline | 42.6% vs 49.1% | 51.9% vs 49.2% |
| Lift (95% CI) | −6.5% (−13.2 .. +0.6) | +2.8% (−4.8 .. +10.9) |
| Score gradient per +20 pts (95% CI) | +0.002 (−0.007 .. +0.013) | +0.004 (−0.005 .. +0.015) |
| Verdict on the pre-registered gates | **FAIL** | FAIL |

**Read it plainly:** on H4 the score does not predict reversals — the reversal rate sits at 47-51% in
every score bucket, and each single factor is flat too. The two bar alignments even disagree on the sign of
the flagged lift, with both intervals including zero, so no H4 flag-level number should be read as a
finding. "Especially in ranging markets" was checked as an exploratory split and is not supported. The ADX
filter neither helped nor hurt on H4. Why D1 showed a small tendency and H4 none is **not** established
(chance, the longer horizon, or bar-convention effects are all possible). The "trades back to the previous
support or resistance" outcome is tested separately below. Full record: `docs/RESEARCH_LOG.md`, Experiment 3.

### Experiment 7 — the shading turning on right after a stretched score (2026-09-19)

Pieter's screenshot observation: the strong-trend background is painted late (ADX, the EMA200 slope and a 3-bar debounce all lag) and
tops/bottoms seem to occur right as the opposite background appears. The co-occurrence is real: for flags with no shading yet, the
opposite shading switched on within 10 bars for 45% (D1) / 63% (H4) of them, against 7-8% for any unshaded bar; and the flag bar is ADX's
20-bar high for about half of flags. But the shading arrives AFTER the flag, so the tradeable moment is the bar where it turns on. Pre-registered,
run once: that bar, after a score above 30 in the previous 10 bars, against other onset bars of the same colour.

| shading turns on after a score > 30 | D1 (native daily, 2009-2026) | H4 (UTC blocks) |
|---|---|---|
| Events | 320 | 800 |
| Reversed vs other onset bars | 48.8% vs 50.3% | 49.6% vs 48.9% |
| Lift (95% CI) | −1.6% (−5.2 .. +2.3) | +0.7% (−2.0 .. +3.4) |
| Verdict | **FAIL** | **FAIL** |

**Read it plainly:** the co-occurrence is what a strong, stretched move looks like on the way; it is not where the move ends. The bar the
shading appears on reverses about half the time, like any other bar. Not tested: ADX-strength or ADX-peak conditions, other windows, entries,
exits and costs. This was the second look at the same data family; a forward (live) test is the only clean confirmation left.

### Experiment 6 — score > 30 against the opposite background colour (2026-09-19)

Pieter's strategy idea, pre-registered and run once: a Crowd **top** score above 30 while the strong-trend background is **green**
(or a **bottom** score above 30 while it is **red**) — a counter-trend fade inside a strong trend. Same 1 x ATR / 20-bar
reversal race as above, but measured against bars in the **same regime** (a downward move is naturally rarer in a strong uptrend, so
a plain baseline would flatter it). Two independent primary tests, both required.

| score > 30 against the opposite colour | D1 (native daily, 2009-2026) | H4 (UTC blocks) |
|---|---|---|
| De-clustered events | 380 | 908 |
| Flagged reversed vs same-regime baseline | 51.1% vs 51.6% | 47.9% vs 50.7% |
| Lift (95% CI) | −0.5% (−5.7 .. +5.2) | −2.8% (−6.1 .. +1.1) |
| Score > 30 on ANY background | 48.2% | 47.9% |
| Verdict | **FAIL** | **FAIL** |

**Read it plainly:** inside a strong trend, a counter-trend score above 30 reversed about as often as a coin flip, and no more
often than any other bar in that same strong trend. The colour added nothing to the score, and the score alone was at or below
50%. Not tested: other thresholds, entries, exits, stops and costs. This says the *signal* does not raise reversal odds; it does
not rule out every way of trading it.

### Experiment 5 — an earlier era, and is a single factor as good? (2026-09-19)

Pre-registered, run once. Native Twelvedata daily bars (UTC-based, so a different bar convention from the
NY-close bars above) reach back to 2008, giving an era the D1 result had never seen.

| composite score, filter off | 2009-2020 (out-of-sample) | 2021-2026 (same bars) |
|---|---|---|
| Score gradient per +20 pts (95% CI) | **−0.001 (−0.020 .. +0.020)** | +0.036 (+0.011 .. +0.063) |
| Pre-registered replication verdict | **CONTRADICTED** | replicates |

**Read it plainly:** the D1 tendency **did not appear in 2009-2020** — the reversal rate is flat across score buckets there.
Because the same daily bars reproduce it in 2021-2026, the bar convention is not the reason. It is either a real dependence on
the market regime or partly chance; these data cannot say which. With H4 showing nothing, the whole positive evidence is one
5.7-year window on D1. **ATR-stretch alone vs the whole composite:** in the era where the composite works its rho is 0.035 vs
0.039 for stretch alone (paired interval −0.015 .. +0.023) — comparable, but not provably as good within the pre-registered
margin, and the comparison is not interpretable where the composite does nothing. The composite beats %B, divergence and climax
alone but not stretch, z-score, RSI or COT alone.

### Experiment 4 — do flags trade back to the previous support/resistance? (2026-09-19)

Pre-registered, run once. "Previous support/resistance" = the most recent *confirmed* swing high or low (no look-ahead), 0.5-5 ATR
from the flag bar; success = reaching it before an equal adverse move within 20 bars (not profit); the baseline is matched on the
level's distance, because a stretched bar's last level is naturally far away.

| filter OFF | H4 UTC (primary) | H4 NY-aligned | D1 (2009-2026 / 2021-2026) |
|---|---|---|---|
| De-clustered events | 117 | 126 | 56 / 22 |
| Flagged vs matched baseline | 18.8% vs 22.2% | 19.0% vs 22.1% | 17.9% vs 18.6% / 27.3% vs 21.2% |
| Lift (95% CI) | −3.4% (−9.7 .. +4.1) | −3.1% (−9.3 .. +3.4) | −0.8% / +6.1%, intervals ≈ ±10-20 |
| Verdict | **FAIL** | FAIL | INCONCLUSIVE (too few flags) |

**Read it plainly:** on H4 the flags did not reach the previous support/resistance more often than comparable bars. On D1 there
are too few flags to say anything. What the test exposed: at a flag, the previous level is usually far away (91% of flags had it
3-5 ATR from the close) and 55% of flags timed out inside 20 bars. Not tested: longer horizons, other definitions of
support/resistance, tighter stops, and any discretionary reading of a chart.

### Caveat added 2026-09-19 — the bar timestamps behind every experiment above
Twelvedata's hourly forex timestamps are **Australia/Sydney local time** (UTC+10 in AEST, UTC+11 in AEDT), not UTC (found by matching five
TradingView candles; `BUILD_STATUS.md` item 18). Every store used here was built assuming UTC, so the "17:00 New York close" D1 and the "New York-session" /
"UTC" H4 blocks in Experiments 2-7 were really cut at other times (D1 about 10 hours off, H4 blocks 2 hours off the NY grid), and the "native daily UTC"
bars are Twelvedata's own day boundary. The candles themselves are real; only their boundaries were misplaced. The reversal-odds conclusions are about
generic 4-hour and daily candles and are unlikely to hinge on where exactly the boundary sits, but that has NOT been re-tested. Re-running the primary
tests on correctly aligned bars is the honest check before anything is built on them.

### Update 2026-09-19 (evening) — re-run on corrected bars
Experiments 2, 3, 4, 6 and 7 were re-run on bars rebuilt with correct (UTC, then New York clock) boundaries, gates unchanged: **no verdict changed.** The D1 tendency survives at a similar size (slope +0.033 per +20 score points,
CI +0.008 .. +0.062, 2021-2026), but the composite is still not better than ATR-stretch alone; H4, the support/resistance test and both background strategies remain coin flips (lifts +1.9%, +0.6%, +0.5%, +0.4%). Experiment 5 (2009-2020)
cannot be re-run: there is no hourly data before 2020. Details: `docs/RESEARCH_LOG.md` on the research branch.

### Experiment 8 — D1 and H4 scores coincide (2026-09-19)
Pre-registered, run once, on the app's own corrected grid (D1 = 17:00-NY close, H4 = NY-session blocks). Event: the H4 score and the last completed D1 score both above 30 on the same side; the H4 bar's 1 x ATR / 20-bar reversal race.

| both > 30, same side | Events | Reversed vs all H4 bars | Lift (95% CI) | vs D1-state-matched baseline |
|---|---|---|---|---|
| H4 NY-aligned | 318 | 53.1% vs 48.7% | +4.4% (-1.8 .. +10.5) | +2.3% (-3.8 .. +8.2) |

**Read it plainly:** the closest any test has come: coincident events reversed a little more often (53.1%), positive in both halves and better than H4 alone (48.1%), but the interval includes zero and most of the difference comes from the D1 state itself.
Worth about +3 R a year before costs. **FAIL** on the pre-registered gates (lift below 5 points, CI includes 0); at the flag line (60) there were only 9 events. Ninth question on one data family: only forward data can confirm it.

### Experiments 9-11 — inside bars, COT holds, carry (2026-09-20)
Pieter's three ideas, pre-registered and run once. **Inside-bar breakouts** continued 51.3% vs 49.7% for ordinary breakouts on D1 (+1.7 points, CI +0.2..+3.2, tiny) and 49.8% vs 49.3% on H4 (nothing): NOT SUPPORTED. **Crowded COT extremes held 4-12 weeks** did not
reverse (8 weeks: -0.04 sigma, hit 44.1% vs 49.5%): NOT SUPPORTED. **Monthly policy-rate carry** returned +1.55% a year (CI -2.5%..+5.4%, Sharpe 0.17, max drawdown -28.6%; the +2.05% carry was partly eaten by -0.50% spot): FAIL. Details: `docs/RESEARCH_LOG.md` on the research branch.
