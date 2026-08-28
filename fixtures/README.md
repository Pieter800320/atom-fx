# fixtures/ — UI state fixtures

Full-contract `signals.json` documents, one per market **state**, for building and
testing the ATOM FX app (and a dev "gallery" screen) **without waiting for live data.**

These are hand-designed representative states — the app is a pure consumer, so they
only need to be plausible and to exercise every UI state. They are **not** scanner
output and the frozen calculations are never involved in producing them.

| File | State it exercises |
|---|---|
| `state_risk_on.json` | The happy path — Growth-positive risk-on (regime A). 3 tradeable pairs, EUR leading. |
| `state_risk_off.json` | Recession-shock risk-off (regime D). Havens bid, dollar-bloc sold, 4 tradeable shorts. |
| `state_ranging_no_setups.json` | **No dominant regime, 0 tradeable pairs** — the "NO A+ SETUPS" empty state; nodes near the nucleus. |
| `state_liquidity_shock.json` | Liquidity shock (regime E) — VIX spiking, confidence forced LOW, recommendation = STAND ASIDE. |

Regenerate with:

```bash
python tools/make_ui_fixtures.py
```

**How the app uses them:** bundle these under `app/src/main/assets/fixtures/` and add a
debug/dev "gallery" screen that loads each one so you can verify every component and
every state visually, offline, in seconds. `state_ranging_no_setups.json` also doubles
as the check that the wheel and Tradeable-Now band render correctly when there is
nothing to trade. (Stale-data and data-unavailable states are produced by an old or
missing `updated` field — no separate fixture needed.)
