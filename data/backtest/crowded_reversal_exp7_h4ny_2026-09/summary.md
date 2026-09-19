# Experiment 7 — shading turns on after a stretched score (h4ny, full)

Verdict (`confirm10`, pre-registered gates): **FAIL — lift >= 0.05; 95% CI lower bound > 0; both halves lift > 0**

Onset bars in the window: green 843, red 643

| arm | events | flagged reversal rate | matched baseline | lift (95% CI) |
|---|---|---|---|---|
| confirm10 | 773 | 50.1% | 49.8% | 0.3% (-2.4% .. 3.0%) |
| confirm5 | 624 | 49.0% | 49.7% | -0.7% (-3.9% .. 2.6%) |
| bare_onset | 1476 | 49.5% | 50.3% | -0.8% (-3.9% .. 1.7%) |

## Gates (`confirm10`)

- PASS: n de-clustered events >= 100
- FAIL: lift >= 0.05
- FAIL: 95% CI lower bound > 0
- FAIL: both halves lift > 0
- PASS: excluding JPY crosses lift > 0
- PASS: excluding best pair (NZDUSD) lift > 0
- PASS: F6 flagged reversal rate > 50%

Halves (split after 2023-07): first 0.4% (n=403), second -0.3% (n=370). Ex-JPY 1.4% (n=399). Ex-best-pair (NZDUSD) 0.1%.
Outcomes: {'reversal': 387, 'continuation': 386, 'timeout': 0, 'ties': 14}.

## Per pair (`confirm10`)

  pair  n_events      lift  contribution
AUDJPY        59 -0.042287     -2.494953
AUDUSD        57  0.031714      1.807692
CADJPY        61 -0.029802     -1.817910
EURJPY        61 -0.003008     -0.183463
EURUSD        65  0.026936      1.750871
GBPJPY        58  0.021866      1.268247
GBPUSD        79 -0.007368     -0.582090
NZDJPY        63  0.014116      0.889336
NZDUSD        58  0.035175      2.040143
USDCAD        64  0.004284      0.274194
USDCHF        76  0.003994      0.303559
USDJPY        72 -0.011867     -0.854418
