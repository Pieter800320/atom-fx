# Experiment 8 — D1 and H4 Crowd scores coincide (T=30, full)

Verdict (`coincide`): **FAIL — lift >= 0.05; 95% CI lower bound > 0**

| arm | events | reversal rate | baseline | lift (95% CI) |
|---|---|---|---|---|
| coincide | 318 | 53.1% | 48.7% | 4.4% (-1.8% .. 10.5%) |
| coincide_dmatched | 318 | 53.1% | 50.8% | 2.3% (-3.8% .. 8.2%) |
| h4_alone | 2156 | 48.1% | 49.0% | -0.9% (-4.1% .. 2.4%) |

## Gates (`coincide`)

- PASS: n de-clustered events >= 100
- FAIL: lift >= 0.05
- FAIL: 95% CI lower bound > 0
- PASS: both halves lift > 0
- PASS: excluding JPY crosses lift > 0
- PASS: excluding best pair (GBPJPY) lift > 0
- PASS: F6 reversal rate > 50%
- PASS: F7 lift beats H4-alone (the D1 coincidence must add something)

Halves (after 2023-11): 2.7% (n=144) / 6.6% (n=174). Ex-JPY 2.8%. Ex-best-pair (GBPJPY) 3.2%. Outcomes {'reversal': 169, 'continuation': 149, 'timeout': 0, 'ties': 4}.
