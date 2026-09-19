> **PLUMBING CHECK ONLY.** Ran the ported logic end-to-end to prove it executes.
> **No edge conclusion may be drawn from any number below.**

# Crowded Market reversal — smoke_h4_2026-09

Mode `smoke` · timeframe `h4utc` · primary = **flagged** · reversal = 1 x ATR(14) barrier race, 20 bars, same-bar-both -> continuation, timeout -> not a reversal · bootstrap 200 (month-clustered, seed 20260919)

Price: `data/h4_utc_long` (1 pairs), 5 months in the evaluation window. COT: Legacy futures-only 2006-01-03 .. 2026-09-15. Commit `f8b162402e`.

## Verdicts

- **regime = suppress** — **flagged events (PRIMARY): PLUMBING ONLY — no verdict (smoke run)** · gradient (secondary): PLUMBING ONLY — no verdict (smoke run)
- **regime = off** — **flagged events (PRIMARY): PLUMBING ONLY — no verdict (smoke run)** · gradient (secondary): PLUMBING ONLY — no verdict (smoke run)

**ADX regime filter (gradient slope, suppress minus off, per +20 pts):** 0.015 (95% CI -0.053 .. 0.127) -> NO demonstrated value: CI includes 0 — the data cannot justify the ADX filter.

## Flagged events (score >= 60, rising edge, de-clustered per pair)

### regime = suppress

- **0 de-clustered events** (reversal 0, continuation 0, timeout 0; same-bar ties 0). Top+bottom same-bar conflicts skipped: 0; flags on censored bars: 0.
- Flagged reversal rate n/a vs like-for-like baseline n/a -> **lift n/a** (95% CI n/a .. n/a).
- Halves (split after 2026-07): first n/a (n=0), second n/a (n=0). Excluding JPY crosses: n/a (n=0). Excluding best pair (EURUSD): n/a (n=0).

| gate | result |
|---|---|
| n de-clustered events >= 100 | FAIL |
| lift >= 0.05 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | FAIL |
| excluding best pair (EURUSD) lift > 0 | FAIL |
| beats best single-factor flagged lift (cot) | FAIL |

| pair | events | lift |
|---|---|---|
| EURUSD | 0 | n/a |

### regime = off

- **0 de-clustered events** (reversal 0, continuation 0, timeout 0; same-bar ties 0). Top+bottom same-bar conflicts skipped: 0; flags on censored bars: 0.
- Flagged reversal rate n/a vs like-for-like baseline n/a -> **lift n/a** (95% CI n/a .. n/a).
- Halves (split after 2026-07): first n/a (n=0), second n/a (n=0). Excluding JPY crosses: n/a (n=0). Excluding best pair (EURUSD): n/a (n=0).

| gate | result |
|---|---|
| n de-clustered events >= 100 | FAIL |
| lift >= 0.05 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | FAIL |
| excluding best pair (EURUSD) lift > 0 | FAIL |
| beats best single-factor flagged lift (cot) | FAIL |

| pair | events | lift |
|---|---|---|
| EURUSD | 0 | n/a |

### Single factors, same flagged-event test (each factor's own rising edge)

| factor | events | flagged | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| pct_b | 32 | 62.5% | 49.2% | 13.3% | -0.1% .. 34.7% |
| z | 14 | 35.7% | 48.9% | -13.2% | -35.9% .. 3.5% |
| stretch | 11 | 9.1% | 48.8% | -39.8% | -49.5% .. -20.8% |
| rsi | 14 | 35.7% | 49.1% | -13.4% | -31.4% .. 17.1% |
| div | 7 | 28.6% | 49.4% | -20.8% | -45.5% .. -13.6% |
| climax | 16 | 68.8% | 49.4% | 19.4% | 13.0% .. 27.0% |
| cot | 1 | 100.0% | 48.3% | 51.7% | 46.2% .. 54.5% |

## Score gradient (every bar)

### regime = suppress

- Slope **0.046** reversal-probability per +20 score points (95% CI -0.003 .. 0.100); rho = 0.0430.
- Halves (split after 2026-07): 0.083 / 0.020. Excluding JPY crosses: 0.046. Weakest leave-one-pair-out: n/a (dropping EURUSD).
- rho vs best single factor (cot): -0.0151 (paired 95% CI -0.0786 .. -0.0053).

| gate | result |
|---|---|
| n_months >= 48 | FAIL |
| slope per +20 pts >= 0.02 | pass |
| 95% CI lower bound > 0 | FAIL |
| both halves slope > 0 | pass |
| excluding JPY crosses slope > 0 | pass |
| every leave-one-pair-out slope > 0 (worst: EURUSD) | FAIL |
| composite rho beats best single factor (cot) | FAIL |

| score bucket | obs | reversal rate | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| [0,10) | 1000 | 48.4% | 49.4% | -1.0% | -1.6% .. -0.0% |
| [10,20) | 34 | 50.0% | 49.0% | 1.0% | -21.8% .. 12.6% |
| [20,30) | 156 | 54.5% | 48.6% | 5.9% | -0.4% .. 21.6% |
| [30,40) | 12 | 66.7% | 48.8% | 17.9% | -3.9% .. 39.9% |
| [40,50) | 9 | 33.3% | 49.4% | -16.0% | -49.4% .. -3.3% |
| [50,60) | 1 | 0.0% | 50.2% | -50.2% | -52.5% .. -44.9% |
| [60,100] | 0 | n/a | n/a | n/a | n/a .. n/a |

### regime = off

- Slope **0.031** reversal-probability per +20 score points (95% CI -0.038 .. 0.124); rho = 0.0323.
- Halves (split after 2026-07): 0.025 / 0.040. Excluding JPY crosses: 0.031. Weakest leave-one-pair-out: n/a (dropping EURUSD).
- rho vs best single factor (cot): -0.0258 (paired 95% CI -0.0918 .. 0.0261).

| gate | result |
|---|---|
| n_months >= 48 | FAIL |
| slope per +20 pts >= 0.02 | pass |
| 95% CI lower bound > 0 | FAIL |
| both halves slope > 0 | pass |
| excluding JPY crosses slope > 0 | pass |
| every leave-one-pair-out slope > 0 (worst: EURUSD) | FAIL |
| composite rho beats best single factor (cot) | FAIL |

| score bucket | obs | reversal rate | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| [0,10) | 924 | 48.5% | 49.4% | -0.9% | -2.6% .. 0.6% |
| [10,20) | 50 | 52.0% | 49.1% | 2.9% | -12.5% .. 17.6% |
| [20,30) | 207 | 51.7% | 48.7% | 3.0% | -14.1% .. 19.8% |
| [30,40) | 19 | 63.2% | 48.8% | 14.3% | 4.5% .. 40.8% |
| [40,50) | 11 | 36.4% | 49.3% | -13.0% | -49.4% .. -0.3% |
| [50,60) | 1 | 0.0% | 50.2% | -50.2% | -52.5% .. -44.9% |
| [60,100] | 0 | n/a | n/a | n/a | n/a .. n/a |

## Exploratory: ranging vs trending bars (filter-off score; NO gates, cannot promote or rescue a verdict)

'Trending' = the Pine's debounced ADX>=30 + EMA200-slope state (either direction) at the bar; 'ranging' = neither. Baselines are computed inside each stratum.

| stratum | observations | gradient slope per +20 pts (95% CI) | flagged events | flagged lift (95% CI) |
|---|---|---|---|---|
| trending | 370 | 0.085 (-0.096 .. 0.432) | 0 | n/a (n/a .. n/a) |
| ranging | 842 | 0.024 (-0.037 .. 0.079) | 0 | n/a (n/a .. n/a) |

## Known limits

- D1/H4 OHLC cannot sequence intrabar: a bar touching both barriers is scored continuation, for every bar alike.
- Gradient observations are ALL bars, so consecutive observations overlap; the month-clustered bootstrap absorbs same-month dependence, not every cross-pair dependency (12 pairs share USD/JPY legs and COT legs).
- One macro era (~2020-2026, incl. the 2022 USD surge); nothing here speaks to earlier regimes.
- COT percentile is computed on a business-day calendar and reproduced TradingView's label to within ~0.3 percentile points.
- H4 stores: the provider omits about one H1 bar most Monday mornings (3-bar blocks); the UTC alignment keeps the Sunday-reopen and Friday-close STUB blocks (1-3 H1 bars), as the app's own aggregator does.
