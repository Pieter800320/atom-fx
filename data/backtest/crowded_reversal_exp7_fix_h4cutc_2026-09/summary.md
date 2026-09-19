# Experiment 7 — shading turns on after a stretched score (h4cutc, full)

Verdict (`confirm10`, pre-registered gates): **FAIL — lift >= 0.05; 95% CI lower bound > 0; both halves lift > 0; excluding JPY crosses lift > 0**

Onset bars in the window: green 852, red 661

| arm | events | flagged reversal rate | matched baseline | lift (95% CI) |
|---|---|---|---|---|
| confirm10 | 813 | 52.4% | 52.0% | 0.4% (-2.2% .. 2.9%) |
| confirm5 | 647 | 52.4% | 51.9% | 0.5% (-2.9% .. 3.7%) |
| bare_onset | 1505 | 51.2% | 50.3% | 0.9% (-2.1% .. 3.5%) |

## Gates (`confirm10`)

- PASS: n de-clustered events >= 100
- FAIL: lift >= 0.05
- FAIL: 95% CI lower bound > 0
- FAIL: both halves lift > 0
- FAIL: excluding JPY crosses lift > 0
- PASS: excluding best pair (EURJPY) lift > 0
- PASS: F6 flagged reversal rate > 50%

Halves (split after 2023-07): first -1.7% (n=414), second 2.6% (n=399). Ex-JPY -0.9% (n=403). Ex-best-pair (EURJPY) 0.0%.
Outcomes: {'reversal': 426, 'continuation': 387, 'timeout': 0, 'ties': 12}.

## Per pair (`confirm10`)

  pair  n_events      lift  contribution
AUDJPY        69  0.040608      2.801974
AUDUSD        63 -0.058612     -3.692573
CADJPY        63 -0.057124     -3.598839
EURJPY        66  0.051033      3.368182
EURUSD        73 -0.000088     -0.006410
GBPJPY        71  0.010114      0.718072
GBPUSD        66 -0.041909     -2.765983
NZDJPY        65  0.043198      2.807843
NZDUSD        60  0.005103      0.306206
USDCAD        66  0.009479      0.625641
USDCHF        75  0.023148      1.736092
USDJPY        76  0.015038      1.142857
