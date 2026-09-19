# Experiment 7 — shading turns on after a stretched score (d1utc, full)

Verdict (`confirm10`, pre-registered gates): **FAIL — lift >= 0.05; 95% CI lower bound > 0; both halves lift > 0; excluding best pair (USDCAD) lift > 0; F6 flagged reversal rate > 50%**

Onset bars in the window: green 123, red 97

| arm | events | flagged reversal rate | matched baseline | lift (95% CI) |
|---|---|---|---|---|
| confirm10 | 103 | 45.6% | 49.8% | -4.2% (-9.8% .. 2.3%) |
| confirm5 | 77 | 48.1% | 50.4% | -2.3% (-10.0% .. 5.9%) |
| bare_onset | 209 | 48.8% | 56.3% | -7.5% (-15.4% .. -1.1%) |

## Gates (`confirm10`)

- PASS: n de-clustered events >= 100
- FAIL: lift >= 0.05
- FAIL: 95% CI lower bound > 0
- FAIL: both halves lift > 0
- PASS: excluding JPY crosses lift > 0
- FAIL: excluding best pair (USDCAD) lift > 0
- FAIL: F6 flagged reversal rate > 50%

Halves (split after 2023-10): first -1.5% (n=56), second -6.8% (n=47). Ex-JPY 0.8% (n=49). Ex-best-pair (USDCAD) -6.3%.
Outcomes: {'reversal': 47, 'continuation': 56, 'timeout': 0, 'ties': 3}.

## Per pair (`confirm10`)

  pair  n_events      lift  contribution
AUDJPY        11  0.046832      0.515152
AUDUSD         5  0.019048      0.095238
CADJPY         8 -0.287500     -2.300000
EURJPY        10 -0.005000     -0.050000
EURUSD        11  0.044289      0.487179
GBPJPY         8 -0.091346     -0.730769
GBPUSD         9 -0.093154     -0.838384
NZDJPY         5  0.060000      0.300000
NZDUSD         5  0.080000      0.400000
USDCAD        11  0.140152      1.541667
USDCHF         8 -0.164286     -1.314286
USDJPY        12 -0.200000     -2.400000
