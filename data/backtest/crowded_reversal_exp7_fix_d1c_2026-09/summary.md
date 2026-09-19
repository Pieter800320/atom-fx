# Experiment 7 — shading turns on after a stretched score (d1c, full)

Verdict (`confirm10`, pre-registered gates): **FAIL — lift >= 0.05; 95% CI lower bound > 0; both halves lift > 0; excluding best pair (USDCAD) lift > 0; F6 flagged reversal rate > 50%**

Onset bars in the window: green 127, red 96

| arm | events | flagged reversal rate | matched baseline | lift (95% CI) |
|---|---|---|---|---|
| confirm10 | 107 | 49.5% | 54.4% | -4.8% (-10.4% .. 2.1%) |
| confirm5 | 79 | 54.4% | 56.2% | -1.7% (-9.1% .. 6.6%) |
| bare_onset | 214 | 52.3% | 56.2% | -3.9% (-11.0% .. 1.7%) |

## Gates (`confirm10`)

- PASS: n de-clustered events >= 100
- FAIL: lift >= 0.05
- FAIL: 95% CI lower bound > 0
- FAIL: both halves lift > 0
- PASS: excluding JPY crosses lift > 0
- FAIL: excluding best pair (USDCAD) lift > 0
- FAIL: F6 flagged reversal rate > 50%

Halves (split after 2023-10): first -7.8% (n=58), second -1.2% (n=49). Ex-JPY 1.2% (n=53). Ex-best-pair (USDCAD) -7.1%.
Outcomes: {'reversal': 53, 'continuation': 54, 'timeout': 0, 'ties': 3}.

## Per pair (`confirm10`)

  pair  n_events      lift  contribution
AUDJPY        10 -0.094318     -0.943182
AUDUSD         4 -0.083333     -0.333333
CADJPY         9 -0.166667     -1.500000
EURJPY        10 -0.048077     -0.480769
EURUSD        14  0.029221      0.409091
GBPJPY         8 -0.100962     -0.807692
GBPUSD        13 -0.018065     -0.234848
NZDJPY         5 -0.095238     -0.476190
NZDUSD         4  0.009091      0.036364
USDCAD        11  0.151515      1.666667
USDCHF         7 -0.130612     -0.914286
USDJPY        12 -0.133929     -1.607143
