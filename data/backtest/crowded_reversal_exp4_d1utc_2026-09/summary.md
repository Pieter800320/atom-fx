# Crowded Market flags -> previous support/resistance — exp4_d1utc_2026-09

Mode `full` · timeframe `d1utc` · target = most recent confirmed swing level (swing_n 5, 0.5-5 ATR away, <= 120 bars old), stop = equal distance on the other side (1:1), 20 bars, same-bar-both -> failure, timeout -> failure · baseline matched on pair x side x distance bucket · bootstrap 5000 (month-clustered, seed 20260919)

Price: `data/d1_utc_daily` (12 pairs). Bars in window 55176; a valid level exists on 85% of bars (resistance above) and 86% (support below). COT: Legacy futures-only. Commit `94f24665ed`.

## Verdicts (flagged-event gates F1-F6)

- **regime = suppress:** INCONCLUSIVE — 32 de-clustered events < 100
- **regime = off:** INCONCLUSIVE — 56 de-clustered events < 100

## Flagged events, regime = suppress

- **32 de-clustered events** (reached the level 5, stopped 10, timeout 17; same-bar ties 0). Top+bottom same-bar conflicts skipped: 0; flags with no valid level / no forward bars (excluded): 16.
- Flagged success rate 15.6% vs distance-matched baseline 18.6% -> **lift -3.0%** (95% CI -13.3% .. 7.7%).
- Halves (split after 2017-10): first -16.2% (n=14), second 6.9% (n=18). Excluding JPY crosses: -3.0% (n=21). Excluding best pair (USDCHF): -7.7% (n=28).

| gate | result |
|---|---|
| n de-clustered events >= 100 | FAIL |
| lift >= 0.05 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | FAIL |
| excluding best pair (USDCHF) lift > 0 | FAIL |
| beats best single-factor flagged lift (div) | FAIL |

| pair | events | lift |
|---|---|---|
| AUDJPY | 2 | -16.1% |
| AUDUSD | 3 | -16.5% |
| CADJPY | 2 | 22.6% |
| EURJPY | 0 | n/a |
| EURUSD | 5 | -16.4% |
| GBPJPY | 1 | -18.9% |
| GBPUSD | 6 | -15.6% |
| NZDJPY | 1 | -18.6% |
| NZDUSD | 3 | 13.8% |
| USDCAD | 0 | n/a |
| USDCHF | 4 | 30.2% |
| USDJPY | 5 | -1.7% |

## Flagged events, regime = off

- **56 de-clustered events** (reached the level 10, stopped 16, timeout 30; same-bar ties 0). Top+bottom same-bar conflicts skipped: 0; flags with no valid level / no forward bars (excluded): 60.
- Flagged success rate 17.9% vs distance-matched baseline 18.6% -> **lift -0.8%** (95% CI -9.4% .. 8.6%).
- Halves (split after 2017-10): first -10.6% (n=29), second 9.1% (n=27). Excluding JPY crosses: -5.1% (n=35). Excluding best pair (USDCHF): -3.0% (n=51).

| gate | result |
|---|---|
| n de-clustered events >= 100 | FAIL |
| lift >= 0.05 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | FAIL |
| excluding best pair (USDCHF) lift > 0 | FAIL |
| beats best single-factor flagged lift (div) | FAIL |

| pair | events | lift |
|---|---|---|
| AUDJPY | 5 | -3.9% |
| AUDUSD | 4 | -16.4% |
| CADJPY | 3 | 8.8% |
| EURJPY | 1 | 80.5% |
| EURUSD | 10 | -15.1% |
| GBPJPY | 4 | 5.7% |
| GBPUSD | 9 | -4.4% |
| NZDJPY | 2 | 23.5% |
| NZDUSD | 6 | -2.9% |
| USDCAD | 1 | -12.3% |
| USDCHF | 5 | 21.7% |
| USDJPY | 6 | -3.8% |

## What the matching is matching (filter-off events by level distance)

| level distance | events | flagged success | baseline success (all valid bars) | baseline bars |
|---|---|---|---|---|
| [0.5,1) ATR | 0 | n/a | 48.9% | 13863 |
| [1,2) ATR | 0 | n/a | 47.3% | 28375 |
| [2,3) ATR | 7 | 57.1% | 34.0% | 23812 |
| [3,5] ATR | 49 | 12.2% | 17.1% | 28535 |

## Single factors, same test (each factor's own rising edge)

| factor | events | flagged | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| pct_b | 1741 | 24.3% | 24.3% | 0.0% | -2.0% .. 2.2% |
| z | 809 | 18.3% | 21.0% | -2.7% | -5.5% .. 0.4% |
| stretch | 801 | 17.9% | 20.4% | -2.6% | -5.3% .. 0.4% |
| rsi | 749 | 15.9% | 19.4% | -3.5% | -6.4% .. -0.5% |
| div | 639 | 36.6% | 34.5% | 2.2% | -1.5% .. 5.9% |
| climax | 544 | 20.2% | 22.1% | -1.9% | -5.8% .. 2.4% |
| cot | 170 | 32.4% | 32.4% | -0.0% | -7.7% .. 7.6% |

## Known limits

- 'Previous support/resistance' is defined as the most recent confirmed swing (a judgment call); round numbers, multi-touch zones or a chart reader's discretion are different definitions and different experiments.
- Success = the level was reached before an equal adverse move within 20 bars. It is not profit: no costs, entry rule, sizing or discretionary exit.
- Bars overlap and the 12 pairs share USD/JPY and COT legs; one macro era per store; OHLC cannot sequence intrabar (tie -> failure).
