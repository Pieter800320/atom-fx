# Crowded Market flags -> previous support/resistance — exp4_d1ny_2026-09

Mode `full` · timeframe `d1` · target = most recent confirmed swing level (swing_n 5, 0.5-5 ATR away, <= 120 bars old), stop = equal distance on the other side (1:1), 20 bars, same-bar-both -> failure, timeout -> failure · baseline matched on pair x side x distance bucket · bootstrap 5000 (month-clustered, seed 20260919)

Price: `data/d1_nyclose_long` (12 pairs). Bars in window 17647; a valid level exists on 83% of bars (resistance above) and 86% (support below). COT: Legacy futures-only. Commit `94f24665ed`.

## Verdicts (flagged-event gates F1-F6)

- **regime = suppress:** INCONCLUSIVE — 17 de-clustered events < 100
- **regime = off:** INCONCLUSIVE — 22 de-clustered events < 100

## Flagged events, regime = suppress

- **17 de-clustered events** (reached the level 3, stopped 4, timeout 10; same-bar ties 0). Top+bottom same-bar conflicts skipped: 0; flags with no valid level / no forward bars (excluded): 7.
- Flagged success rate 17.6% vs distance-matched baseline 21.2% -> **lift -3.6%** (95% CI -20.3% .. 19.9%).
- Halves (split after 2023-10): first 4.0% (n=4), second -3.4% (n=13). Excluding JPY crosses: 11.8% (n=6). Excluding best pair (USDCAD): -7.7% (n=16).

| gate | result |
|---|---|
| n de-clustered events >= 100 | FAIL |
| lift >= 0.05 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | pass |
| excluding best pair (USDCAD) lift > 0 | FAIL |
| beats best single-factor flagged lift (rsi) | FAIL |

| pair | events | lift |
|---|---|---|
| AUDJPY | 3 | 18.3% |
| AUDUSD | 0 | n/a |
| CADJPY | 2 | -19.7% |
| EURJPY | 1 | -22.1% |
| EURUSD | 1 | -21.3% |
| GBPJPY | 2 | -20.8% |
| GBPUSD | 2 | 31.2% |
| NZDJPY | 3 | -27.6% |
| NZDUSD | 1 | -21.0% |
| USDCAD | 1 | 63.3% |
| USDCHF | 1 | -13.0% |
| USDJPY | 0 | n/a |

## Flagged events, regime = off

- **22 de-clustered events** (reached the level 6, stopped 4, timeout 12; same-bar ties 0). Top+bottom same-bar conflicts skipped: 0; flags with no valid level / no forward bars (excluded): 9.
- Flagged success rate 27.3% vs distance-matched baseline 21.2% -> **lift 6.1%** (95% CI -10.9% .. 27.7%).
- Halves (split after 2023-10): first 35.1% (n=7), second -5.5% (n=15). Excluding JPY crosses: 12.0% (n=9). Excluding best pair (USDJPY): 2.6% (n=21).

| gate | result |
|---|---|
| n de-clustered events >= 100 | FAIL |
| lift >= 0.05 | pass |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | pass |
| excluding best pair (USDJPY) lift > 0 | pass |
| beats best single-factor flagged lift (rsi) | pass |

| pair | events | lift |
|---|---|---|
| AUDJPY | 3 | 18.3% |
| AUDUSD | 1 | -17.4% |
| CADJPY | 2 | -19.7% |
| EURJPY | 2 | 27.9% |
| EURUSD | 2 | -21.3% |
| GBPJPY | 2 | -20.8% |
| GBPUSD | 2 | 31.2% |
| NZDJPY | 3 | -27.6% |
| NZDUSD | 1 | -21.0% |
| USDCAD | 1 | 63.3% |
| USDCHF | 2 | 31.6% |
| USDJPY | 1 | 78.5% |

## What the matching is matching (filter-off events by level distance)

| level distance | events | flagged success | baseline success (all valid bars) | baseline bars |
|---|---|---|---|---|
| [0.5,1) ATR | 0 | n/a | 50.4% | 4187 |
| [1,2) ATR | 0 | n/a | 49.0% | 8434 |
| [2,3) ATR | 2 | 50.0% | 37.2% | 7365 |
| [3,5] ATR | 20 | 25.0% | 20.9% | 9742 |

## Single factors, same test (each factor's own rising edge)

| factor | events | flagged | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| pct_b | 595 | 25.7% | 26.2% | -0.5% | -4.2% .. 3.5% |
| z | 270 | 23.3% | 23.2% | 0.1% | -5.2% .. 6.0% |
| stretch | 312 | 24.0% | 23.6% | 0.4% | -4.1% .. 5.4% |
| rsi | 238 | 28.6% | 23.9% | 4.6% | -0.9% .. 10.6% |
| div | 217 | 38.7% | 36.6% | 2.1% | -5.2% .. 9.4% |
| climax | 240 | 26.7% | 25.9% | 0.7% | -6.9% .. 9.0% |
| cot | 41 | 39.0% | 37.5% | 1.5% | -14.0% .. 16.8% |

## Known limits

- 'Previous support/resistance' is defined as the most recent confirmed swing (a judgment call); round numbers, multi-touch zones or a chart reader's discretion are different definitions and different experiments.
- Success = the level was reached before an equal adverse move within 20 bars. It is not profit: no costs, entry rule, sizing or discretionary exit.
- Bars overlap and the 12 pairs share USD/JPY and COT legs; one macro era per store; OHLC cannot sequence intrabar (tie -> failure).
