# Experiment 8 — D1 and H4 Crowd scores coincide (T=60, full)

Verdict (`coincide`): **INCONCLUSIVE — 9 de-clustered events < 100**

| arm | events | reversal rate | baseline | lift (95% CI) |
|---|---|---|---|---|
| coincide | 9 | 66.7% | 49.5% | 17.1% (-8.1% .. 49.9%) |
| coincide_dmatched | 9 | 66.7% | 60.4% | 6.3% (-25.0% .. 28.5%) |
| h4_alone | 143 | 50.3% | 48.8% | 1.6% (-9.6% .. 13.1%) |

## Gates (`coincide`)

- FAIL: n de-clustered events >= 100
- PASS: lift >= 0.05
- FAIL: 95% CI lower bound > 0
- PASS: both halves lift > 0
- PASS: excluding JPY crosses lift > 0
- PASS: excluding best pair (GBPUSD) lift > 0
- PASS: F6 reversal rate > 50%
- PASS: F7 lift beats H4-alone (the D1 coincidence must add something)

Halves (after 2023-11): 25.5% (n=4) / 11.2% (n=5). Ex-JPY 17.1%. Ex-best-pair (GBPUSD) 0.4%. Outcomes {'reversal': 6, 'continuation': 3, 'timeout': 0, 'ties': 0}.
