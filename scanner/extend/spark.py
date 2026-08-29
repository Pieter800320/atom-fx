"""
ATOM FX — Spark (compact recent closes)  (EXTEND, FUNCTIONAL_SPEC §8)

Exposes a small array of recent closes per pair per timeframe so the app can draw
the simplified close-price LINE chart natively (no candles, no WebView, no on-device
market-data key). ~56 points x 3 TF x 12 pairs — small enough to ship in signals.json.

Placed as a TOP-LEVEL `spark` key: {PAIR: {d1:[...], h4:[...], h1:[...]}}
(per ARCHITECTURE §4.2 and FUNCTIONAL_SPEC §8).

Rule #1: read-only over the fetched OHLCV; computes nothing analytical.
"""
from scanner.config import PAIRS
from scanner.extend import potential_config as cfg


def _closes(df, n: int):
    if df is None or len(df) == 0:
        return []
    tail = df["close"].astype(float).iloc[-n:]
    return [round(float(c), cfg.SPARK_DP) for c in tail]


def compute_spark(ohlcv: dict) -> dict:
    """Returns {PAIR: {"d1":[...], "h4":[...], "h1":[...]}} for the 12 wheel pairs."""
    n = cfg.SPARK_BARS
    out = {}
    for pair in PAIRS:
        key = pair.replace("/", "")
        tfs = ohlcv.get(key, {})
        out[key] = {
            "d1": _closes(tfs.get("d1"), n),
            "h4": _closes(tfs.get("h4"), n),
            "h1": _closes(tfs.get("h1"), n),
        }
    return out
