# Experiment 9 inside bars — exp9_d1c_2026-09 (full)

Verdict: **FAIL — F2 lift >= 5 points; F2 95% CI lower bound > 0; F6 reversal (continuation) rate > 50%**

Inside bars in the window: 2704

| arm | events | continuation rate | baseline | lift (95% CI) |
|---|---|---|---|---|
| inside_break (vs ordinary prior-bar breakouts) | 1378 | 49.3% | 48.3% | 1.0% (-1.5% .. 3.8%) |
| inside_vs_all (vs all bars, info) | 1378 | 49.3% | 49.9% | -0.6% (-4.0% .. 2.5%) |

Halves (after 2023-07): 0.1% (n=697) / 2.1% (n=681); ex-JPY 0.9%; ex-best 0.6%; outcomes {'reversal': 680, 'continuation': 696, 'timeout': 2, 'ties': 13}

## Gates

- PASS: F1 >= 100 de-clustered events
- FAIL: F2 lift >= 5 points
- FAIL: F2 95% CI lower bound > 0
- PASS: F3 both halves lift > 0
- PASS: F4 excluding JPY crosses lift > 0
- PASS: F5 excluding best pair (NZDJPY) lift > 0
- FAIL: F6 reversal (continuation) rate > 50%
