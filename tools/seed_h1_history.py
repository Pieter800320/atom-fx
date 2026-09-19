"""
ATOM FX — seed the persistent H1 history (scanner/extend/fx_week.py) from raw H1 CSVs already on disk.

The history store (data/h1_history/<PAIR>.csv) supplies the older bars the frozen D1 scorer needs once closed-market rows are
dropped (see fx_week.py's module doc: the depth trap). It is seeded ONCE here — from the raw Twelvedata H1 cache the research
work already fetched — and every scan_h1 run then extends it. No API call is made by this tool.

    py -m tools.seed_h1_history <dir containing <PAIR>_deep_h1.csv files>

Only the 12 wheel pairs are seeded (the CSM-only pairs do not need the depth). Each file is filtered to the real FX week and
trimmed to the newest STORE_ROWS rows. The first scan after the seed overlaps it (the fetch window is ~30 weeks), so the store
stays contiguous.
"""
import sys
from pathlib import Path

import pandas as pd

ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(ROOT))

from scanner.config import PAIRS
from scanner.extend import fx_week


def main(src_dir: str) -> None:
    src = Path(src_dir)
    for pair in PAIRS:
        key = pair.replace("/", "")
        path = src / f"{key}_deep_h1.csv"
        if not path.exists():
            print(f"{key}: {path.name} not found - skipped")
            continue
        raw = pd.read_csv(path)
        merged = fx_week.merge_history(fx_week.load_history(key), fx_week._clean(raw), fx_week.STORE_ROWS)
        fx_week._write_history(key, merged)
        print(f"{key}: {len(raw)} raw rows -> {len(merged)} real-FX-week rows, {merged['datetime'].iloc[0]} .. {merged['datetime'].iloc[-1]}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
