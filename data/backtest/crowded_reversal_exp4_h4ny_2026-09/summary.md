# Crowded Market flags -> previous support/resistance — exp4_h4ny_2026-09

Mode `full` · timeframe `h4ny` · target = most recent confirmed swing level (swing_n 5, 0.5-5 ATR away, <= 120 bars old), stop = equal distance on the other side (1:1), 20 bars, same-bar-both -> failure, timeout -> failure · baseline matched on pair x side x distance bucket · bootstrap 5000 (month-clustered, seed 20260919)

Price: `data/h4_ny_long` (12 pairs). Bars in window 111565; a valid level exists on 82% of bars (resistance above) and 84% (support below). COT: Legacy futures-only. Commit `94f24665ed`.

## Verdicts (flagged-event gates F1-F6)

- **regime = suppress:** INCONCLUSIVE — 82 de-clustered events < 100
- **regime = off:** FAIL — lift >= 0.05; 95% CI lower bound > 0; both halves lift > 0; excluding JPY crosses lift > 0; excluding best pair (EURJPY) lift > 0; beats best single-factor flagged lift (cot)

## Flagged events, regime = suppress

- **82 de-clustered events** (reached the level 16, stopped 18, timeout 48; same-bar ties 0). Top+bottom same-bar conflicts skipped: 0; flags with no valid level / no forward bars (excluded): 25.
- Flagged success rate 19.5% vs distance-matched baseline 22.2% -> **lift -2.6%** (95% CI -11.0% .. 5.9%).
- Halves (split after 2023-07): first -2.9% (n=46), second -1.8% (n=36). Excluding JPY crosses: -9.9% (n=41). Excluding best pair (EURJPY): -6.3% (n=76).

| gate | result |
|---|---|
| n de-clustered events >= 100 | FAIL |
| lift >= 0.05 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | FAIL |
| excluding best pair (EURJPY) lift > 0 | FAIL |
| beats best single-factor flagged lift (cot) | FAIL |

| pair | events | lift |
|---|---|---|
| AUDJPY | 2 | -16.8% |
| AUDUSD | 4 | -20.5% |
| CADJPY | 8 | -20.3% |
| EURJPY | 6 | 44.1% |
| EURUSD | 5 | -21.8% |
| GBPJPY | 9 | 12.2% |
| GBPUSD | 4 | -21.1% |
| NZDJPY | 7 | 2.3% |
| NZDUSD | 9 | -2.9% |
| USDCAD | 8 | -20.6% |
| USDCHF | 11 | 5.5% |
| USDJPY | 9 | -0.6% |

## Flagged events, regime = off

- **126 de-clustered events** (reached the level 24, stopped 31, timeout 71; same-bar ties 0). Top+bottom same-bar conflicts skipped: 0; flags with no valid level / no forward bars (excluded): 71.
- Flagged success rate 19.0% vs distance-matched baseline 22.1% -> **lift -3.1%** (95% CI -9.3% .. 3.4%).
- Halves (split after 2023-07): first -3.0% (n=61), second -2.3% (n=65). Excluding JPY crosses: -6.7% (n=64). Excluding best pair (EURJPY): -4.9% (n=116).

| gate | result |
|---|---|
| n de-clustered events >= 100 | pass |
| lift >= 0.05 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | FAIL |
| excluding best pair (EURJPY) lift > 0 | FAIL |
| beats best single-factor flagged lift (cot) | FAIL |

| pair | events | lift |
|---|---|---|
| AUDJPY | 4 | -16.8% |
| AUDUSD | 8 | -8.0% |
| CADJPY | 12 | -11.7% |
| EURJPY | 10 | 18.4% |
| EURUSD | 10 | -3.3% |
| GBPJPY | 12 | 3.9% |
| GBPUSD | 5 | -21.1% |
| NZDJPY | 12 | 0.5% |
| NZDUSD | 13 | -1.1% |
| USDCAD | 11 | -13.1% |
| USDCHF | 17 | -4.2% |
| USDJPY | 12 | 1.1% |

## What the matching is matching (filter-off events by level distance)

| level distance | events | flagged success | baseline success (all valid bars) | baseline bars |
|---|---|---|---|---|
| [0.5,1) ATR | 1 | 100.0% | 48.7% | 24929 |
| [1,2) ATR | 0 | n/a | 47.3% | 51213 |
| [2,3) ATR | 11 | 54.5% | 37.6% | 45464 |
| [3,5] ATR | 114 | 14.9% | 20.2% | 62926 |

## Single factors, same test (each factor's own rising edge)

| factor | events | flagged | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| pct_b | 3520 | 26.5% | 26.0% | 0.5% | -0.9% .. 1.9% |
| z | 1563 | 20.5% | 22.7% | -2.2% | -4.6% .. 0.2% |
| stretch | 2008 | 22.0% | 24.2% | -2.2% | -4.1% .. -0.3% |
| rsi | 1742 | 20.1% | 23.1% | -3.0% | -5.1% .. -0.9% |
| div | 1326 | 35.4% | 35.4% | 0.1% | -2.5% .. 2.7% |
| climax | 1890 | 25.5% | 24.4% | 1.1% | -1.1% .. 3.2% |
| cot | 64 | 37.5% | 34.6% | 2.9% | -8.1% .. 14.3% |

## Known limits

- 'Previous support/resistance' is defined as the most recent confirmed swing (a judgment call); round numbers, multi-touch zones or a chart reader's discretion are different definitions and different experiments.
- Success = the level was reached before an equal adverse move within 20 bars. It is not profit: no costs, entry rule, sizing or discretionary exit.
- Bars overlap and the 12 pairs share USD/JPY and COT legs; one macro era per store; OHLC cannot sequence intrabar (tie -> failure).
