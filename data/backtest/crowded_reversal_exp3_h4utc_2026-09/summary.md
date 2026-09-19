# Crowded Market reversal — exp3_h4utc_2026-09

Mode `full` · timeframe `h4utc` · primary = **flagged** · reversal = 1 x ATR(14) barrier race, 20 bars, same-bar-both -> continuation, timeout -> not a reversal · bootstrap 5000 (month-clustered, seed 20260919)

Price: `data/h4_utc_long` (12 pairs), 77 months in the evaluation window. COT: Legacy futures-only 2006-01-03 .. 2026-09-15. Commit `f8b162402e`.

## Verdicts

- **regime = suppress** — **flagged events (PRIMARY): INCONCLUSIVE — 87 de-clustered events < 100** · gradient (secondary): FAIL — slope per +20 pts >= 0.02; 95% CI lower bound > 0; both halves slope > 0; every leave-one-pair-out slope > 0 (worst: AUDUSD); composite rho beats best single factor (stretch)
- **regime = off** — **flagged events (PRIMARY): FAIL — lift >= 0.05; 95% CI lower bound > 0; both halves lift > 0; excluding JPY crosses lift > 0; excluding best pair (GBPJPY) lift > 0; beats best single-factor flagged lift (stretch)** · gradient (secondary): FAIL — slope per +20 pts >= 0.02; 95% CI lower bound > 0; both halves slope > 0; composite rho beats best single factor (stretch)

**ADX regime filter (gradient slope, suppress minus off, per +20 pts):** -0.001 (95% CI -0.008 .. 0.005) -> NO demonstrated value: CI includes 0 — the data cannot justify the ADX filter.

## Flagged events (score >= 60, rising edge, de-clustered per pair)

### regime = suppress

- **87 de-clustered events** (reversal 33, continuation 54, timeout 0; same-bar ties 0). Top+bottom same-bar conflicts skipped: 0; flags on censored bars: 0.
- Flagged reversal rate 37.9% vs like-for-like baseline 49.3% -> **lift -11.3%** (95% CI -21.6% .. -0.5%).
- Halves (split after 2023-07): first -16.5% (n=49), second -4.4% (n=38). Excluding JPY crosses: -19.3% (n=43). Excluding best pair (EURJPY): -13.1% (n=83).

| gate | result |
|---|---|
| n de-clustered events >= 100 | FAIL |
| lift >= 0.05 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | FAIL |
| excluding best pair (EURJPY) lift > 0 | FAIL |
| beats best single-factor flagged lift (stretch) | FAIL |

| pair | events | lift |
|---|---|---|
| AUDJPY | 3 | -15.1% |
| AUDUSD | 9 | -16.8% |
| CADJPY | 10 | -9.5% |
| EURJPY | 4 | 26.1% |
| EURUSD | 9 | -26.8% |
| GBPJPY | 5 | 12.8% |
| GBPUSD | 9 | -4.8% |
| NZDJPY | 8 | -0.7% |
| NZDUSD | 7 | -34.9% |
| USDCAD | 2 | -49.1% |
| USDCHF | 7 | -7.3% |
| USDJPY | 14 | -12.9% |

### regime = off

- **176 de-clustered events** (reversal 75, continuation 101, timeout 0; same-bar ties 0). Top+bottom same-bar conflicts skipped: 0; flags on censored bars: 1.
- Flagged reversal rate 42.6% vs like-for-like baseline 49.1% -> **lift -6.5%** (95% CI -13.2% .. 0.6%).
- Halves (split after 2023-07): first -8.9% (n=93), second -3.4% (n=83). Excluding JPY crosses: -8.5% (n=88). Excluding best pair (GBPJPY): -8.1% (n=163).

| gate | result |
|---|---|
| n de-clustered events >= 100 | pass |
| lift >= 0.05 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | FAIL |
| excluding best pair (GBPJPY) lift > 0 | FAIL |
| beats best single-factor flagged lift (stretch) | FAIL |

| pair | events | lift |
|---|---|---|
| AUDJPY | 8 | -10.5% |
| AUDUSD | 21 | -2.1% |
| CADJPY | 17 | -2.4% |
| EURJPY | 13 | -10.2% |
| EURUSD | 13 | -18.3% |
| GBPJPY | 13 | 14.2% |
| GBPUSD | 17 | -8.0% |
| NZDJPY | 14 | -0.5% |
| NZDUSD | 15 | 4.0% |
| USDCAD | 7 | -34.6% |
| USDCHF | 15 | -9.9% |
| USDJPY | 23 | -13.6% |

### Single factors, same flagged-event test (each factor's own rising edge)

| factor | events | flagged | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| pct_b | 5802 | 48.9% | 49.1% | -0.2% | -2.0% .. 1.6% |
| z | 2729 | 46.4% | 49.0% | -2.6% | -5.0% .. -0.2% |
| stretch | 3404 | 49.4% | 48.9% | 0.5% | -1.4% .. 2.5% |
| rsi | 3032 | 48.0% | 49.0% | -1.0% | -3.2% .. 1.3% |
| div | 1658 | 47.5% | 48.9% | -1.5% | -4.1% .. 1.5% |
| climax | 2732 | 47.9% | 49.3% | -1.4% | -4.0% .. 1.3% |
| cot | 77 | 46.8% | 49.2% | -2.4% | -15.3% .. 9.6% |

## Score gradient (every bar)

### regime = suppress

- Slope **0.001** reversal-probability per +20 score points (95% CI -0.009 .. 0.011); rho = 0.0007.
- Halves (split after 2023-07): -0.008 / 0.011. Excluding JPY crosses: 0.001. Weakest leave-one-pair-out: -0.001 (dropping AUDUSD).
- rho vs best single factor (stretch): -0.0063 (paired 95% CI -0.0181 .. 0.0053).

| gate | result |
|---|---|
| n_months >= 48 | pass |
| slope per +20 pts >= 0.02 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves slope > 0 | FAIL |
| excluding JPY crosses slope > 0 | pass |
| every leave-one-pair-out slope > 0 (worst: AUDUSD) | FAIL |
| composite rho beats best single factor (stretch) | FAIL |

| score bucket | obs | reversal rate | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| [0,10) | 198395 | 49.1% | 49.2% | -0.1% | -0.2% .. 0.1% |
| [10,20) | 8286 | 50.3% | 48.9% | 1.4% | -0.3% .. 3.3% |
| [20,30) | 17716 | 49.4% | 49.3% | 0.1% | -1.3% .. 1.6% |
| [30,40) | 2680 | 48.4% | 49.0% | -0.5% | -3.7% .. 2.8% |
| [40,50) | 1011 | 50.3% | 49.1% | 1.2% | -2.6% .. 5.2% |
| [50,60) | 581 | 44.8% | 49.4% | -4.7% | -9.2% .. 0.2% |
| [60,100] | 165 | 43.6% | 49.1% | -5.5% | -13.9% .. 4.1% |

### regime = off

- Slope **0.002** reversal-probability per +20 score points (95% CI -0.007 .. 0.013); rho = 0.0023.
- Halves (split after 2023-07): -0.005 / 0.012. Excluding JPY crosses: 0.004. Weakest leave-one-pair-out: 0.001 (dropping AUDUSD).
- rho vs best single factor (stretch): -0.0047 (paired 95% CI -0.0128 .. 0.0028).

| gate | result |
|---|---|
| n_months >= 48 | pass |
| slope per +20 pts >= 0.02 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves slope > 0 | FAIL |
| excluding JPY crosses slope > 0 | pass |
| every leave-one-pair-out slope > 0 (worst: AUDUSD) | pass |
| composite rho beats best single factor (stretch) | FAIL |

| score bucket | obs | reversal rate | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| [0,10) | 186667 | 49.1% | 49.2% | -0.1% | -0.3% .. 0.1% |
| [10,20) | 11035 | 50.5% | 48.9% | 1.6% | -0.1% .. 3.4% |
| [20,30) | 23091 | 49.6% | 49.2% | 0.4% | -0.9% .. 1.7% |
| [30,40) | 4607 | 47.9% | 48.9% | -1.0% | -3.5% .. 1.8% |
| [40,50) | 1867 | 49.2% | 49.1% | 0.2% | -2.8% .. 3.4% |
| [50,60) | 1219 | 47.3% | 49.3% | -2.1% | -6.3% .. 2.6% |
| [60,100] | 348 | 48.0% | 49.1% | -1.1% | -7.1% .. 5.5% |

## Exploratory: ranging vs trending bars (filter-off score; NO gates, cannot promote or rescue a verdict)

'Trending' = the Pine's debounced ADX>=30 + EMA200-slope state (either direction) at the bar; 'ranging' = neither. Baselines are computed inside each stratum.

| stratum | observations | gradient slope per +20 pts (95% CI) | flagged events | flagged lift (95% CI) |
|---|---|---|---|---|
| trending | 51558 | 0.005 (-0.012 .. 0.025) | 90 | -1.1% (-9.1% .. 7.7%) |
| ranging | 177276 | 0.001 (-0.010 .. 0.012) | 87 | -11.4% (-21.7% .. -0.4%) |

## Known limits

- D1/H4 OHLC cannot sequence intrabar: a bar touching both barriers is scored continuation, for every bar alike.
- Gradient observations are ALL bars, so consecutive observations overlap; the month-clustered bootstrap absorbs same-month dependence, not every cross-pair dependency (12 pairs share USD/JPY legs and COT legs).
- One macro era (~2020-2026, incl. the 2022 USD surge); nothing here speaks to earlier regimes.
- COT percentile is computed on a business-day calendar and reproduced TradingView's label to within ~0.3 percentile points.
- H4 stores: the provider omits about one H1 bar most Monday mornings (3-bar blocks); the UTC alignment keeps the Sunday-reopen and Friday-close STUB blocks (1-3 H1 bars), as the app's own aggregator does.
