# Data that is deliberately NOT in git

Twelve Data's terms (https://twelvedata.com/terms, last updated 2026-01-01) do not allow redistributing its raw data without a Redistribution Rights add-on or written agreement (sections 2.2(e), 2.3(b)); they DO allow
derived data that cannot recreate the original (2.2(c)). This repository is public, so **no candles are committed**: not the raw hourly cache, not the D1/H4 stores aggregated from it. Results (`data/backtest/`),
code and docs are committed; they cannot reproduce the candles. (Summary of the terms, not legal advice: read them.)

## What is local-only and how to rebuild it (you need your own Twelve Data key, set per terminal session, never committed)
| Local folder | What | Rebuild |
|---|---|---|
| `data/h1_cache/<PAIR>_deep_h1.csv` | raw hourly candles (Twelve Data labels are Australia/Sydney time!) | the deep-fetch tools in `tools/` (e.g. `tools/fetch_d1_deep.py`, 5,000 rows per call, walking back with `end_date`) |
| `data/fix_d1_ny/`, `fix_h4_ny/`, `fix_h4_utc/` | CORRECTED D1 (17:00-NY close) and H4 (NY-session / UTC blocks) stores | `py -m tools.build_fixed_stores` (needs main's `scanner/extend/fx_week.py` for the Sydney->UTC conversion) |
| `data/d1_utc_daily/`, `d1_nyclose*`, `h4_*_long/` | older stores built on the pre-fix labels | `tools/fetch_d1_utc_daily.py`, `tools/bootstrap_d1_nyclose.py`, `tools/build_h4_store.py` (superseded by `fix_*` for everything except the 2009-2020 native daily bars) |
| `data/_bis_cbpol.zip` | BIS central-bank policy rates (Experiment 11) | https://data.bis.org/static/bulk/WS_CBPOL_csv_flat.zip |

`data/cot_legacy/legacy_nc.csv` (CFTC public data) IS committed. Older commits on this branch still contain the price stores (they were pushed before this rule); removing them from history is a separate decision (see `BUILD_STATUS.md` item 20 on main).
