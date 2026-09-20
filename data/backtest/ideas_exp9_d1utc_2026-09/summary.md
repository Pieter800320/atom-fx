# Experiment 9 inside bars — exp9_d1utc_2026-09 (full)

Verdict: **FAIL — F2 lift >= 5 points**

Inside bars in the window: 7729

| arm | events | continuation rate | baseline | lift (95% CI) |
|---|---|---|---|---|
| inside_break (vs ordinary prior-bar breakouts) | 3917 | 51.3% | 49.7% | 1.7% (0.2% .. 3.2%) |
| inside_vs_all (vs all bars, info) | 3917 | 51.3% | 49.6% | 1.7% (-0.0% .. 3.5%) |

Halves (after 2017-05): 2.6% (n=1920) / 0.8% (n=1997); ex-JPY 2.0%; ex-best 1.4%; outcomes {'reversal': 2011, 'continuation': 1899, 'timeout': 7, 'ties': 29}

## Gates

- PASS: F1 >= 100 de-clustered events
- FAIL: F2 lift >= 5 points
- PASS: F2 95% CI lower bound > 0
- PASS: F3 both halves lift > 0
- PASS: F4 excluding JPY crosses lift > 0
- PASS: F5 excluding best pair (NZDJPY) lift > 0
- PASS: F6 reversal (continuation) rate > 50%
