"""
ATOM FX research — build the H4 stores for Experiment 3 from the cached raw H1.

Reads data/h1_cache/<PAIR>_deep_h1.csv (written by tools/fetch_d1_deep.py; gitignored, ~6.6 years of
Twelvedata H1 per pair) and writes, per alignment, a committed CSV per pair plus a _meta.json:

    data/h4_utc_long/<PAIR>.csv   the APP's convention (UTC 4-hour blocks) — Experiment 3 PRIMARY
    data/h4_ny_long/<PAIR>.csv    TradingView-style NY-aligned blocks       — robustness check

Both go through scanner.extend.agg_h4.aggregate_h4_ts (FX-week filtered; timestamps kept).

    py -m tools.build_h4_store
"""
import json
import sys
from pathlib import Path

import pandas as pd

ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(ROOT))

from scanner.extend.agg_h4 import aggregate_h4_ts

PAIRS = ["EURUSD", "GBPUSD", "USDJPY", "USDCAD", "NZDUSD", "AUDUSD", "USDCHF",
         "EURJPY", "GBPJPY", "CADJPY", "AUDJPY", "NZDJPY"]
RAW_DIR = ROOT / "data" / "h1_cache"
OUT = {"utc": ROOT / "data" / "h4_utc_long", "ny": ROOT / "data" / "h4_ny_long"}


def main():
    meta = {a: {"pairs": {}} for a in OUT}
    for pair in PAIRS:
        raw = RAW_DIR / f"{pair}_deep_h1.csv"
        if not raw.exists():
            print(f"{pair}: no raw H1 cache - skipped")
            continue
        h1 = pd.read_csv(raw)
        for alignment, out_dir in OUT.items():
            out_dir.mkdir(parents=True, exist_ok=True)
            h4 = aggregate_h4_ts(h1, alignment)
            h4.to_csv(out_dir / f"{pair}.csv", index=False, float_format="%.6f")
            meta[alignment]["pairs"][pair] = {
                "h1_bars_in": int(len(h1)), "h4_bars": int(len(h4)),
                "first": str(h4["datetime"].iloc[0]), "last": str(h4["datetime"].iloc[-1]),
                "stub_blocks_lt4_h1": int((h4["n_h1"] < 4).sum())}
        print(f"{pair}: H1 {len(h1)} -> H4 utc {meta['utc']['pairs'][pair]['h4_bars']} "
              f"(stubs {meta['utc']['pairs'][pair]['stub_blocks_lt4_h1']}), "
              f"ny {meta['ny']['pairs'][pair]['h4_bars']} (stubs {meta['ny']['pairs'][pair]['stub_blocks_lt4_h1']})")
    for alignment, out_dir in OUT.items():
        meta[alignment].update({
            "source": "data/h1_cache/<PAIR>_deep_h1.csv (Twelvedata H1, tools/fetch_d1_deep.py)",
            "aggregation": f"scanner.extend.agg_h4.aggregate_h4_ts(alignment='{alignment}'), FX-week filtered (DECISION-007)"})
        (out_dir / "_meta.json").write_text(json.dumps(meta[alignment], indent=2))


if __name__ == "__main__":
    main()
