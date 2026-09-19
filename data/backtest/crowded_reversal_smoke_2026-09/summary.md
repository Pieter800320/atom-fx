> **PLUMBING CHECK ONLY.** This ran the ported logic end-to-end on a short cache to prove
> it executes and fires. **No edge conclusion may be drawn from any number below.**

# Crowded Market reversal — barrier-race event study (smoke_2026-09)

Mode `smoke` · horizon 20 bars · barrier 1.0 x ATR(14) · tie -> continuation · bootstrap 1000 (month-clustered, seed 20260919)

Price: data/d1_nyclose, 12 pairs. COT: Legacy futures-only 2006-01-03 .. 2026-09-15. Commit `8b77f08d9c`.

## Verdicts

- **regime = suppress:** PLUMBING ONLY — no verdict (smoke run on a short cache)
- **regime = off:** PLUMBING ONLY — no verdict (smoke run on a short cache)

## Score >= 60, regime = suppress

- De-clustered events: **4** (reversal 2, continuation 2, timeout 0; of which same-bar ties 0). Same-bar top+bottom conflicts skipped: 0. Flags on censored bars: 0.
- Flagged reversal rate 50.0% vs like-for-like baseline 43.3% -> **lift 6.7%** (95% CI -43.5% .. 61.0%).
- Halves (split 2026-02): first -9.0% (n=3), second 55.0% (n=1).
- Excluding JPY crosses: -45.2% (n=1). Excluding best pair (CADJPY): -9.3% (n=3).

| gate | result |
|---|---|
| n_events >= 100 | FAIL |
| lift >= 0.05 | pass |
| CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | FAIL |
| excluding best pair (CADJPY) lift > 0 | FAIL |
| beats best single factor (rsi) | FAIL |

| pair | events | lift |
|---|---|---|
| AUDJPY | 2 | 8.7% |
| AUDUSD | 1 | -45.2% |
| CADJPY | 1 | 54.8% |
| EURJPY | 0 | n/a |
| EURUSD | 0 | n/a |
| GBPJPY | 0 | n/a |
| GBPUSD | 0 | n/a |
| NZDJPY | 0 | n/a |
| NZDUSD | 0 | n/a |
| USDCAD | 0 | n/a |
| USDCHF | 0 | n/a |
| USDJPY | 0 | n/a |

## Score >= 60, regime = off

- De-clustered events: **4** (reversal 2, continuation 2, timeout 0; of which same-bar ties 0). Same-bar top+bottom conflicts skipped: 0. Flags on censored bars: 0.
- Flagged reversal rate 50.0% vs like-for-like baseline 43.3% -> **lift 6.7%** (95% CI -43.5% .. 61.0%).
- Halves (split 2026-02): first -9.0% (n=3), second 55.0% (n=1).
- Excluding JPY crosses: -45.2% (n=1). Excluding best pair (CADJPY): -9.3% (n=3).

| gate | result |
|---|---|
| n_events >= 100 | FAIL |
| lift >= 0.05 | pass |
| CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | FAIL |
| excluding best pair (CADJPY) lift > 0 | FAIL |
| beats best single factor (rsi) | FAIL |

| pair | events | lift |
|---|---|---|
| AUDJPY | 2 | 8.7% |
| AUDUSD | 1 | -45.2% |
| CADJPY | 1 | 54.8% |
| EURJPY | 0 | n/a |
| EURUSD | 0 | n/a |
| GBPJPY | 0 | n/a |
| GBPUSD | 0 | n/a |
| NZDJPY | 0 | n/a |
| NZDUSD | 0 | n/a |
| USDCAD | 0 | n/a |
| USDCHF | 0 | n/a |
| USDJPY | 0 | n/a |

## Single-factor comparators (same events rules, same baseline)

| factor | events | flagged | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| pct_b | 173 | 60.1% | 48.2% | 11.9% | 2.9% .. 21.4% |
| z | 89 | 50.6% | 45.5% | 5.1% | -2.6% .. 19.1% |
| stretch | 89 | 55.1% | 45.5% | 9.6% | -1.8% .. 28.5% |
| rsi | 64 | 68.8% | 46.4% | 22.3% | 8.7% .. 38.2% |
| div | 45 | 51.1% | 44.1% | 7.0% | -8.1% .. 26.1% |
| climax | 52 | 51.9% | 50.0% | 2.0% | -10.3% .. 18.9% |
| cot | 10 | 50.0% | 50.4% | -0.4% | -50.4% .. 20.9% |

## Known limits of this harness

- D1 OHLC cannot sequence intrabar: a bar touching both barriers is scored continuation, for flagged AND baseline.
- COT percentile is computed on a business-day calendar; on 2026-09-19 it reproduced TradingView's label to within ~0.3 percentile points (net positions matched exactly). Attributed, not verified, to TradingView counting chart bars.
- 12 pairs are not independent (USD legs, JPY legs): the month-clustered bootstrap absorbs same-month clustering but not every cross-pair dependency.
