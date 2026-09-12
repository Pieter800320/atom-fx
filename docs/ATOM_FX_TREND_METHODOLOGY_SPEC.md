# ATOM FX — Trend Pullback Methodology Spec

**Status:** v0.1 DRAFT — anti-drift contract for Claude Code.
**Owner:** Pieter. **Author of draft:** planning session (Cowork).
**Read before touching code:** `CLAUDE.md`, `scanner/FROZEN.md`, `docs/INDEX.md`,
`docs/ATOM_FX_SIGNALS_ROADMAP.md`, this file.

> This document defines **one** strategy: a Daily-biased, H4-confirmed, H1-executed
> **trend pullback continuation** detector that fires a notification. It is written to be
> executed by Claude Code in small, reviewable, test-backed increments. It adds nothing the
> strategy does not need (Rule 3). Every calculation is defined so it can be reproduced
> exactly (Rule 1). Nothing in here is to be treated as validated truth until the backtest in
> Task 5 says so (Rule 2).

---

## 0. The three project rules this spec is bound by

1. **Faithful or not at all.** Every number must be the correct formula on the correct candle
   convention. If a calculation cannot be produced faithfully, the feature is not built.
   "Faithful" means *correct convention + correct formula*, reusing the frozen helpers so the
   numbers are identical to the rest of the app — **not** byte-identical to any one broker
   (impossible on free data; FX has no single true price).
2. **No drift, no invention.** Parameter defaults below are drawn from documented professional
   practice and are labelled PROPOSED. None are claimed optimal. They are validated/tuned by
   the backtest (Task 5) **before** any alert goes live.
3. **Simplest thing that works.** One strategy, one detector, one signals.json block, one push
   type, one Settings toggle. No extra signals, screens, or parameters.

---

## 1. Decision Log (extract — mirror into `docs/10_DECISION_LOG.md`)

```
DECISION-001 — Canonical D1 boundary
Decision: ATOM FX Daily candles close at 17:00 America/New_York (DST-aware), with the
          Sunday session rolled into Monday. Built in a NEW scanner/extend/ module; the
          frozen aggregator is untouched. EMA200 is seeded from a one-time deep-history
          H1 backfill, then carried forward by persisting the NY-close D1 series.
Status: DECIDED (2026-09-11)
Reason: Measured on 12 pairs over 200 days — UTC-midnight D1 disagrees with the broker
        convention on ~4.0% of days (EMA50 bias) / ~2.9% (EMA200), concentrated at the
        EMA-crossover zone where this strategy enters, plus a weekly phantom Sunday candle.
        Fails Rule 1 for an execution-grade methodology.
Affected: data layer, all D1 calculations, structure detection, this spec.
Evidence: tools/verify_candle_fidelity.py output, 2026-09-11.

DECISION-002 — Pullback quality: require BOTH the 38.2–61.8% Fib zone AND price within
              ema50_near_atr of the H4 50 EMA (confluence).      Status: DECIDED (2026-09-11)
DECISION-003 — Stop basis: H1 pullback structural low − stop_buffer_atr×ATR(H1) (tighter,
              better R:R).                                        Status: DECIDED (2026-09-11)
DECISION-004 — Position sizing: OUT of v1. App emits entry/stop/target/R:R + pip distance;
              user sizes by own risk-% rule.                     Status: DECIDED (2026-09-11)
DECISION-005 — Parameter defaults                    Status: PROPOSED → DECIDED after Task 5

DECISION-006 — Unify D1 NY-close on one helper: scanner/extend/bb_touch.py's _d1_ny_close now
              delegates to scanner/extend/agg_nyclose.aggregate_d1_nyclose_dated instead of
              re-bucketing independently; date label standardized to the session's CLOSE day
              (agg_nyclose's convention) instead of bb_touch's old OPEN-day label — same
              bars/values, label shifts by exactly one calendar day.
                                                                    Status: DECIDED (2026-09-11)

DECISION-007 — Drop bars outside the real FX week [Sun 17:00 ET, Fri 17:00 ET) in the NY-close
              extend path (scanner/extend/agg_nyclose.py, before grouping; shared by both
              aggregate_d1_nyclose and aggregate_d1_nyclose_dated, so they cannot diverge).
                                                                    Status: DECIDED (2026-09-12)
Reason: Twelvedata began emitting market-closed weekend bars for these pairs on 2026-01-11 —
        a Sunday 00:00-16:59 ET pre-open block and a recurring Saturday 00:00-03:00 ET block.
        Neither appears on any broker/TradingView daily; under the +7h close-day rule alone
        they landed on their own phantom Saturday/Sunday D1 candles, breaking history
        uniformity. A genuine Sunday 17:00-ET-or-later reopen is unaffected and still rolls
        into Monday as before.
Affected: scanner/extend/agg_nyclose.py (both aggregators); scanner/extend/bb_touch.py
        inherits it via DECISION-006's delegation (bands/%B for any pair whose H1 history
        contains these weekend bars); tools/bootstrap_d1_nyclose.py's per-pair summary now
        also reports a phantom-Saturday count alongside phantom-Sunday.
Evidence: manual H1 diagnostic on EUR/USD, 2026-09-12 — identical weekend blocks present via
        both the "recent" and end_date-paginated fetch paths for the same calendar dates,
        ruling out a pagination/fetch-path artifact; confirmed absent before 2026-01-11 via
        the same paginated fetch style one week earlier.

DECISION-008 — Backtest modeling assumptions (tools/backtest_trend_pullback.py, Task 5).
                                                                    Status: DECIDED (2026-09-12)
Decision: No look-ahead — at each step the detector sees only H1 bars up to and including the
        current bar (a bounded rolling window, never anything later). Entry = the detector's
        own returned `entry` (signal-bar close); stop/target = the detector's own outputs,
        never recomputed by the backtest. Trade resolution walks forward bar by bar (H1):
        LONG loses if bar.low <= stop, wins if bar.high >= target — a bar hitting BOTH
        resolves as a STOP (conservative); SHORT mirrors. MAX_HOLD_BARS = 360 (~15 trading
        days); if neither hits by then, exit at that bar's close, exit_reason="timeout". One
        open position per pair at a time; scanning resumes on the bar after the exit bar.
        Params = trend_pullback.PARAMS, the detector's own defaults — no sweep in this task.
Reason: A baseline edge check must replay the SHIPPED detector faithfully (calling
        evaluate_from_h1 itself, never a re-implementation of any gate) under assumptions a
        reasonable trader would actually apply, without look-ahead and without tuning
        parameters to the very sample being used to judge them.
Affected: tools/backtest_trend_pullback.py only. No frozen file, scan_h1.py, or the detector
        touched. Gates further work (Task 4 live wiring, DECISION-005 param ratification) on
        review of this backtest's results — not run automatically as part of it.

DECISION-009 — Gate D decoupled: the H1 entry trigger is now the swing-high/low break ALONE
              (H1 close beyond the prior minor swing, body close only). The bullish/bearish
              reversal candle (engulfing/pin) is recorded as context in the `trigger` output
              field ("engulfing"/"pin" when the breaking bar happens to be one, else "break")
              — it no longer has to land on the SAME bar as the break to count.
                                                                    Status: DECIDED (2026-09-12)
Reason: The original same-bar AND (spec §4 Gate D, pre-revision) requires one H1 bar to both
        reverse the pullback AND already close back through the opposing swing extreme — in a
        real pullback the reversal candle marks the LOW, while the break confirming the
        pullback is over typically lands several bars later. The baseline backtest's --funnel
        diagnostic found 0 of 78 Gate-D-reaching bars satisfied the same-bar AND. This task's
        own re-verification independently sampled Gate-D-reaching bars from the 3 cached
        pairs' real H1 history (EUR/USD, GBP/USD, USD/JPY), using the SAME rolling-window
        evaluate_from_h1 call, re-deriving the break/candle booleans via the module's own
        exported helpers (no gate reimplemented). A full exact (every-bar) re-scan proved too
        heavy for this session (a background run was killed on low system memory); two
        smaller sampled passes (every 25th bar across all 3 pairs, n=10; every 10th bar on
        EUR/USD alone, n=7 — 17 Gate-D-reaching bars total, not a full census) gave:
        break-alone passed 1/17 (5.9%); the old candle-requirement-alone passed 2/17 (11.8%);
        BOTH together (today's actual AND, pre-fix) passed 0/17 (0%) — zero co-occurrence in
        every sample checked, consistent with and corroborating the original 78-bar finding.
        Break-alone and candle-alone each occur independently; neither sample ever found them
        on the same bar — confirming the candle was the blocker, not a separate bug in the
        break/swing-high logic. (Sample sizes are small by necessity, not by choice — the
        qualitative finding, zero co-occurrence, is what both the 78-bar and 17-bar checks
        agree on; the exact break-alone/candle-alone base rates would need a full scan to
        pin down precisely, which the backtest itself will still do post-fix.)
Affected: scanner/extend/trend_pullback.py (Gate D only — Gates A/B/C, DECISION-002/003/004,
        risk outputs, and states are unchanged); tests/test_trend_pullback.py.

DECISION-010 — Gate D changed again, SUPERSEDES DECISION-009: the H1 entry trigger is now
              "enter WHILE STILL IN THE ZONE" — a body close past the prior H1 bar's own
              high (LONG) / low (SHORT), evaluated on the same bar Gate C is true. The
              DECISION-009 swing-high/low break is removed entirely (last_swing_high(h1) is
              no longer needed for LONG's gate at all). The reversal candle (engulfing/pin)
              stays context-only in `trigger` ("engulfing"/"pin" when applicable, else
              "confirm" — renamed from DECISION-009's "break", since there is no break left).
                                                                    Status: DECIDED (2026-09-12)
Reason: DECISION-009's break trigger only confirmed AFTER price had already closed back
        beyond the H1 swing high/low it had to break — but `target` is `leg_high`/`leg_low`,
        the SAME kind of level one timeframe up. By the time price broke its own H1 swing,
        it had typically already eaten most of the distance up to `target`, so
        `target - entry` collapsed toward zero and R:R could never clear `min_rr` — a
        geometric incoherence between the trigger and the target, not a parameter-tuning
        problem. The post-DECISION-009 backtest confirmed this directly: 4 of 78 candidates
        reached Gate D, 0 fired, all 4 armed on R:R. Entering INSIDE the pullback zone instead
        (a minimal "turning up" confirmation, not a break of anything) keeps the full
        leg_high/leg_low distance available as reward against a stop that's still tight (just
        below/above the pullback's own low/high) — the entry and the target are no longer
        fighting over the same level.
Affected: scanner/extend/trend_pullback.py (Gate D and the entry/trigger fields only — Gates
        A/B/C, DECISION-002/003/004's stop/target formulas, min_rr, and states are unchanged);
        tests/test_trend_pullback.py.
```

---

## 2. What is REUSED / FIXED / NEW (Rule #1 map)

**REUSED as-is (frozen — import read-only, never edit):**
- `scanner/score.py` — `_ema`, `_atr_series`, `_dmi`, `score_pair` (Wilder-correct RSI/ATR/ADX,
  EMAs). The methodology imports these so its numbers are identical to the app's.
- `scanner/structure.py::detect_structure` — trend direction + BOS/CHoCH.
- `scanner/pills.py` — the D1/H4/H1 5-state read, used as a cheap alignment corroboration.
- `scanner/fetch.py` — the H1 fetch + rate limiting.
- Rule #1 test harness: `tests/frozen_probe.py`, `make_golden.py`, `test_rule1_frozen.py`.
- Push transport + conventions: `push/send_push.py`, `push/alert_helpers.py`; edge-triggered,
  one-schema, one-toggle rules from `docs/ATOM_FX_SIGNALS_ROADMAP.md` §1.

**FIXED (the data-layer change from DECISION-001) — all NEW code, frozen files untouched:**
- New `scanner/extend/agg_nyclose.py` (D1 at 17:00 NY + EMA200 seed/carry-forward).

**NEW (methodology) — all in `scanner/extend/`:**
- `scanner/extend/swings.py` — expose swing highs/lows (Fib + stops need the actual pivots,
  which `structure.py` computes internally but does not return).
- `scanner/extend/trend_pullback.py` — the deterministic rule engine (§4–§5).
- `tools/backtest_trend_pullback.py` — parameter validation (§7, Task 5).
- Additive keys in `signals.json` + one push type + one Settings toggle.

**FROZEN-TOUCH:** none. If any step seems to need one, STOP and raise it (per CLAUDE.md §1).

---

## 3. Data foundation — NY-close D1 (DECISION-001)

**Aggregation (proven in `tools/verify_candle_fidelity.py`):**
convert H1 UTC timestamps → `America/New_York` wall time → add 7h → floor to day = trading-day
key. Group by that key: `open=first, high=max, low=min, close=last`. This yields a 17:00-NY
close and rolls the Sunday 17:00 session into Monday (no phantom Sunday bar).

**EMA200 warm-up (the second fidelity issue — 210 bars is too few):**
- **Bootstrap (one-time):** a backfill routine paginates H1 back ~2–3 years (Twelvedata free:
  5000 bars/call, so multiple calls per pair with `end_date`, spaced for the rate limit — run
  once, off the hourly job) and writes the NY-close D1 history to `data/d1_nyclose/<PAIR>.json`.
- **Carry-forward (every scan):** the hourly job reads the persisted D1 history, appends only
  newly-closed trading day(s) from the fresh 5000-bar H1 fetch, recomputes EMAs on the full
  accumulated series. Hourly API cost stays at today's 1 call/pair.
- EMA50/EMA200 via the frozen `scanner.score._ema` (identical math to the rest of the app).

**Acceptance:** ≥ 500 NY-close D1 bars available per pair after bootstrap; the phantom-Sunday
count is 0; a spot-check of 3 pairs' latest 10 daily OHLC matches a broker/TradingView 17:00-NY
daily chart to within normal feed differences.

---

## 4. The strategy — computable rules

Evaluated every hourly scan, per pair. LONG stated; **SHORT is the exact mirror**. All gates
must pass. Each gate names its data source and its PROPOSED parameter.

**Gate A — D1 trend established (bias).** On NY-close D1:
- `EMA50 > EMA200`, AND `close > EMA200`, AND EMA50 rising:
  `EMA50[t] > EMA50[t - ema50_slope_lookback]`.
- Corroboration: `detect_structure(d1).direction == "bull"` (higher highs & higher lows).
- *Basis:* price above both EMAs, 50 above 200, HH/HL structure.

**Gate B — trend strength (anti-range filter).** On NY-close D1:
- `ADX(D1) >= adx_min` AND ADX rising: `ADX[t] > ADX[t - adx_rising_lookback]`.
- *Basis:* trade only when ADX confirms a trend, not a range.

**Gate C — pullback present & of quality (DECISION-002).** On H4:
- Retracement depth into the last completed H4 up-swing (from `swings.py`) lies in
  `[pullback_fib_min, pullback_fib_max]` (the 38.2–61.8% zone), AND
- price is near value: `abs(close - EMA50_H4) <= ema50_near_atr * ATR(H4)`, AND
- **not extended / not broken:** reject if retracement `> fib_invalidation` (78.6% → reversal
  risk) or if `close - EMA50_H4 > extended_reject_atr * ATR(H4)` (chasing).
- **DECISION-002 (DECIDED):** **both** the Fib-zone test **and** the EMA-distance test must
  pass (confluence → fewer, higher-quality signals; aligns Rule 3).

**Gate D — H1 entry trigger, "enter WHILE STILL IN THE ZONE" (DECISION-010, supersedes
DECISION-009 — see the Decision Log for why the swing-high/low break was removed entirely,
not just decoupled from the candle):
- The gate: a minimal "pullback turning up" confirmation, evaluated on the SAME H1 bar Gate C
  is true. H1 `close >` the PRIOR H1 bar's own high — body close only, no wick. (SHORT
  mirrors: `close <` the prior H1 bar's own low.) There is no swing break to satisfy;
  `last_swing_high(h1)` is no longer read for LONG's gate at all.
- A bullish reversal candle — **engulfing** (body engulfs prior body) **or** **pin/hammer**
  (lower wick ≥ 2× body, small upper wick) — is recorded as **context**, never a gate:
  `trigger` is `"engulfing"` or `"pin"` when the confirming bar happens to be one, else
  `"confirm"`.
- *Basis:* enter where the reward (to `target = leg_high`/`leg_low`) is still fully intact,
  not after price has already closed most of that distance by breaking its own H1 swing
  first (DECISION-009's failure mode — see the Decision Log for the backtest evidence).

**Alignment corroboration (reuse `pills`).** Reject the long if `pills.h4 == "bear_strong"` or
`pills.d1` is bearish — a cheap consistency check against the existing engine; never the primary
trigger.

**Edge-trigger:** the signal FIRES only on the scan where Gates A–D first become all-true
having not been all-true on the previous scan (compare against `prev` signals.json, per
ROADMAP §1). It never re-fires while still true.

---

## 5. Risk outputs (computed, not gating)

Emitted with the signal so the recommendation is act-ready; the app never sizes or executes.

- **Entry (DECISION-010):** current H1 close — WHILE STILL IN the H4 pullback zone Gate C
  qualified (and the H4 zone bounds for context), not after a swing break has already used up
  most of the distance to target (see DECISION-010).
- **Stop (DECISION-003, DECIDED):** `stop = min(pullback_leg_H1_lows) - stop_buffer_atr * ATR(H1)`
  — just below the pullback's own H1 swing low, minus the ATR buffer.
- **Target 1:** the swing high the pullback originated from → defines R:R.
- **R:R gate:** if `(target1 - entry) / (entry - stop) < min_rr`, **do not fire** (setup exists
  but reward is insufficient — a skip, not an alert).
- **Position sizing (DECISION-004, DECIDED):** OUT of v1. Emit entry/stop/target/R:R and
  stop-distance in pips; Pieter sizes by his own fixed risk-% rule. (Simplest; keeps the app out
  of giving sizing advice. Sizing calculator is a clean post-v1 add if wanted.)

---

## 6. signals.json contract (additive) + notification

**New key**, per pair, written by the EXTEND block in `scan_h1.py`:
```
pairs.<PAIR>.trend_pullback: {
  state:      "none" | "armed" | "fired",   // fired = all gates true this scan
  direction:  "long" | "short" | null,
  entry:      float|null, stop: float|null, target: float|null, rr: float|null,
  adx: float, fib_pct: float|null, ema50_dist_atr: float|null,
  trigger:    "engulfing" | "pin" | "confirm" | null,   // DECISION-010: context only, never a gate
  blocked_at: "A"|"B"|"C"|"D"|"rr"|null,     // first gate that failed, for UI transparency
  ts:         iso8601
}
schema_version bumped by 1.
```
`blocked_at` makes the wheel/sheet able to show *why* a near-miss isn't tradeable — no extra
compute, high UX value, still one block.

**Push** (only when `state` transitions to `fired`): `type: "trend_pullback"`, title
`"{PAIR} — trend pullback {DIRECTION}"`, body = entry / stop / target / R:R + the confluence
that fired, deeplink `atomfx://pair/{PAIR}`. Reuses `send_push_alert`. **One** Settings toggle:
"Trend pullback alerts" (`NotificationPrefs` gains one boolean). Never bundled under an existing
toggle.

---

## 7. Build plan — ordered CC tasks (small, test-backed, patch-sized)

Each task is one CC session, opens by re-reading this spec + the named docs, ends by running the
guards. **No task after Task 5 enables the push until the backtest is reviewed (Rule 1/2).**

1. **`extend/agg_nyclose.py` + bootstrap/carry-forward** (§3). Tests: NY boundary correctness
   across a DST change; Sunday rolled into Monday; phantom-Sunday count = 0; ≥500 bars after
   bootstrap. No frozen file touched; EMAs via frozen `_ema`.
2. **`extend/swings.py`** — return last N swing highs/lows (index, price) using the SAME
   strict-left/tolerant-right pivot rule as `structure.py` (consistency). Unit tests vs a fixture.
3. **`extend/trend_pullback.py`** — pure deterministic `evaluate(pair, tfs, params) -> dict`
   implementing §4–§5. Imports frozen `_ema/_atr_series/_dmi/detect_structure/pills`. Tests
   with fixtures for: valid long, valid short, range (Gate B fail), extended/no-pullback (Gate C
   fail), deep-pullback reversal (Gate C invalidation), R:R too low (skip).
4. **Wire into `scan_h1.py`** EXTEND block — additive keys only, `schema_version++`,
   edge-trigger vs `prev`. Confirm `test_rule1_frozen` still passes (frozen calcs unchanged).
5. **`tools/backtest_trend_pullback.py`** — replay history, report per-pair hit rate, avg R,
   expectancy, trade count, for the PROPOSED params; a small sweep around each default. **GATE:**
   review results with Pieter; ratify DECISION-005 (params) before anything fires.
6. **Push + toggle** — add `trend_pullback` type via `send_push_alert`; one `NotificationPrefs`
   toggle + Settings row + `AtomFxMessagingService` handling. Edge-triggered only.
7. **App surface (minimal, later)** — show the recommendation on the existing Insights/pair
   surface using established components; `blocked_at` drives a "why not tradeable" line. Reuse,
   don't invent, UI patterns.

---

## 8. Parameters — PROPOSED defaults (one config block; tuned in Task 5, not before)

> Drawn from documented professional practice. **None are validated yet.** They live in one
> place (`extend/potential_config.py`-style module) so Task 5 can tune them without hunting.

| Param | Default | Source / note |
|---|---|---|
| `adx_min` | 22 | ADX 20–25 "trending" band |
| `adx_rising_lookback` | 3 | ADX rising |
| `ema50_slope_lookback` (D1) | 5 | trend, not fresh cross |
| `pullback_fib_min` / `max` | 0.382 / 0.618 | classic retracement zone |
| `fib_invalidation` | 0.786 | beyond = reversal risk |
| `ema50_near_atr` (H4) | 1.0 | "at value" near the 50 EMA |
| `extended_reject_atr` (H4) | 1.5 | anti-chasing |
| `swing_n_h1 / h4 / d1` | 5 / 5 / 10 | matches existing structure use |
| `stop_buffer_atr` (H1) | 0.5 | beyond noise |
| `min_rr` | 2.0 | minimum 1:2 |

---

## 9. Out of scope (Rule 3 — explicit non-goals)

No second strategy; no auto-execution/broker integration; no position sizing engine in v1; no
new charting; no ML; no new timeframes. Any of these is a future PROPOSED decision, never a
"while we're here" addition.
