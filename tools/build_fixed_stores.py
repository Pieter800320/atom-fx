"""
ATOM FX research — rebuild the D1/H4 stores on CORRECTED bars (2026-09-19).

Twelvedata's hourly forex labels are Australia/Sydney local time, not UTC (see docs/RESEARCH_LOG.md caveat). This converts the raw hourly cache
(`data/h1_cache/<PAIR>_deep_h1.csv`, untouched) to true UTC with main's `scanner/extend/fx_week.py`, drops closed-market rows, and writes:

  data/fix_d1_ny/<PAIR>.csv    D1 on the 17:00-New-York close (Sunday rolls into Monday)      date, open, high, low, close
  data/fix_h4_ny/<PAIR>.csv    H4 on the New-York-session blocks (17/21/01/05/09/13 NY) = the app's and TradingView's grid
  data/fix_h4_utc/<PAIR>.csv   H4 on true UTC blocks (00/04/08/...): the like-for-like re-run of the earlier "H4 UTC" primaries
                               H4 files: datetime (UTC open), open, high, low, close, n_h1, trading_day

    py -m tools.build_fixed_stores
"""
import importlib.util
from pathlib import Path

import pandas as pd

ROOT = Path(__file__).parent.parent
MAIN = Path(r"C:\Users\vande\AndroidStudioProjects\atom-fx")
_spec = importlib.util.spec_from_file_location("fx_week_main", MAIN / "scanner" / "extend" / "fx_week.py")
fw = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(fw)

PAIRS = ["EURUSD", "GBPUSD", "USDJPY", "USDCAD", "NZDUSD", "AUDUSD", "USDCHF",
         "EURJPY", "GBPJPY", "CADJPY", "AUDJPY", "NZDJPY"]


def agg(df, key, min_rows):
    g = df.groupby(key, sort=True)
    out = g.agg(open=("open", "first"), high=("high", "max"), low=("low", "min"), close=("close", "last"),
                n_h1=("close", "size"), utc_open=("utc", "first")).reset_index()
    return out[out["n_h1"] >= min_rows].reset_index(drop=True)


def main():
    for d in ("fix_d1_ny", "fix_h4_ny", "fix_h4_utc"):
        (ROOT / "data" / d).mkdir(parents=True, exist_ok=True)
    for pair in PAIRS:
        raw = pd.read_csv(ROOT / "data" / "h1_cache" / f"{pair}_deep_h1.csv")
        h1 = fw.normalize_frame(raw[["datetime", "open", "high", "low", "close"]])          # true UTC, closed-market rows dropped
        h1["utc"] = pd.to_datetime(h1["datetime"])
        clk = pd.to_datetime(fw.to_trading_clock(h1[["datetime", "open", "high", "low", "close"]])["datetime"].to_numpy())
        h1["clk"] = clk

        d1 = agg(h1.assign(day=h1["clk"].dt.normalize()), "day", 8).rename(columns={"day": "date"})
        d1[["date", "open", "high", "low", "close"]].to_csv(ROOT / "data" / "fix_d1_ny" / f"{pair}.csv", index=False, float_format="%.6f")

        for name, col in (("fix_h4_ny", "clk"), ("fix_h4_utc", "utc")):
            blk = h1[col].dt.floor("4h")
            h4 = agg(h1.assign(blk=blk), "blk", 3)
            h4["datetime"] = h4["utc_open"].dt.strftime("%Y-%m-%d %H:%M:%S")
            h4["trading_day"] = (h4["blk"].dt.normalize() if name == "fix_h4_ny" else h4["utc_open"].dt.normalize()).dt.strftime("%Y-%m-%d")
            h4[["datetime", "open", "high", "low", "close", "n_h1", "trading_day"]].to_csv(
                ROOT / "data" / name / f"{pair}.csv", index=False, float_format="%.6f")
        print(f"{pair}: {len(d1)} D1 (NY close), {len(h4)} H4 blocks, {d1['date'].iloc[0].date()} .. {d1['date'].iloc[-1].date()}")


if __name__ == "__main__":
    main()
