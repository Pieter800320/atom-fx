# Experiment 9 inside bars — exp9_h4cny_2026-09 (full)

Verdict: **FAIL — F2 lift >= 5 points; F2 95% CI lower bound > 0; F3 both halves lift > 0; F6 reversal (continuation) rate > 50%**

Inside bars in the window: 18283

| arm | events | continuation rate | baseline | lift (95% CI) |
|---|---|---|---|---|
| inside_break (vs ordinary prior-bar breakouts) | 8639 | 49.8% | 49.3% | 0.5% (-0.5% .. 1.5%) |
| inside_vs_all (vs all bars, info) | 8639 | 49.8% | 49.3% | 0.5% (-0.7% .. 1.7%) |

Halves (after 2023-05): -0.4% (n=4372) / 1.4% (n=4267); ex-JPY 0.2%; ex-best 0.2%; outcomes {'reversal': 4304, 'continuation': 4325, 'timeout': 10, 'ties': 108}

## Gates

- PASS: F1 >= 100 de-clustered events
- FAIL: F2 lift >= 5 points
- FAIL: F2 95% CI lower bound > 0
- FAIL: F3 both halves lift > 0
- PASS: F4 excluding JPY crosses lift > 0
- PASS: F5 excluding best pair (EURJPY) lift > 0
- FAIL: F6 reversal (continuation) rate > 50%
