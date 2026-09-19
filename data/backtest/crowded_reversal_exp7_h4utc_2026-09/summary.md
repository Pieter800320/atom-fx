# Experiment 7 — shading turns on after a stretched score (h4utc, full)

Verdict (`confirm10`, pre-registered gates): **FAIL — lift >= 0.05; 95% CI lower bound > 0; both halves lift > 0; F6 flagged reversal rate > 50%**

Onset bars in the window: green 871, red 668

| arm | events | flagged reversal rate | matched baseline | lift (95% CI) |
|---|---|---|---|---|
| confirm10 | 800 | 49.6% | 48.9% | 0.7% (-2.0% .. 3.4%) |
| confirm5 | 642 | 50.0% | 49.0% | 1.0% (-1.9% .. 4.0%) |
| bare_onset | 1526 | 48.8% | 50.6% | -1.8% (-4.7% .. 0.7%) |

## Gates (`confirm10`)

- PASS: n de-clustered events >= 100
- FAIL: lift >= 0.05
- FAIL: 95% CI lower bound > 0
- FAIL: both halves lift > 0
- PASS: excluding JPY crosses lift > 0
- PASS: excluding best pair (USDCAD) lift > 0
- FAIL: F6 flagged reversal rate > 50%

Halves (split after 2023-07): first -0.0% (n=408), second 1.4% (n=392). Ex-JPY 0.0% (n=407). Ex-best-pair (USDCAD) 0.1%.
Outcomes: {'reversal': 397, 'continuation': 402, 'timeout': 1, 'ties': 12}.

## Per pair (`confirm10`)

  pair  n_events      lift  contribution
AUDJPY        66 -0.044021     -2.905366
AUDUSD        66  0.020673      1.364407
CADJPY        67  0.053928      3.613158
EURJPY        60  0.015152      0.909091
EURUSD        71 -0.048151     -3.418750
GBPJPY        64 -0.028833     -1.845298
GBPUSD        76 -0.043340     -3.293860
NZDJPY        64  0.062400      3.993590
NZDUSD        54 -0.019863     -1.072600
USDCAD        61  0.086183      5.257143
USDCHF        79  0.015065      1.190141
USDJPY        72  0.029750      2.141975
