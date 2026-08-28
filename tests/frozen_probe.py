"""
Rule #1 regression probe.

Builds a DETERMINISTIC synthetic OHLCV fixture (no network, no market data) and
runs every FROZEN calculation on it, returning a plain dict of the results.

`make_golden.py` snapshots this dict once (right after you fork the scanner).
`test_rule1_frozen.py` recomputes it and asserts it still matches the snapshot.

If anyone changes a frozen scanner file in a way that alters ANY number, the
recomputed probe differs from the golden snapshot and the test fails. That is
the automated guardian of Rule #1.

This file is TEST code — it is allowed to import and call the frozen modules,
but it never modifies them.
"""
import numpy as np
import pandas as pd

from scanner.config import PAIRS
from scanner.aggregator import build_tfs
from scanner import csm, mom1212, regime, correlate, rank
from scanner import pills as pills_mod
from scanner.score import compute_reset_score, atr_percentile
from scanner.cont_score import compute_cont

# 16-pair CSM universe (what csm.py consumes) + 12 wheel pairs (what correlate/rank use)
CSM_PAIRS   = [p.replace("/", "") for p in csm.STRENGTH_PAIRS]
WHEEL_PAIRS = [p.replace("/", "") for p in PAIRS]
# fixture must cover the union so every consumer finds its keys
FIXTURE_PAIRS = sorted(set(CSM_PAIRS) | set(WHEEL_PAIRS))

N_H1_BARS = 1300           # H4 ≈ 325 bars, D1 ≈ 54 bars — enough to exercise the pipeline
FIXTURE_SEED = 20260101    # fixed — DO NOT change (would invalidate the golden)
ROUND_DP = 6               # rounding tolerance for cross-environment float stability


# ── deterministic OHLCV fixture ────────────────────────────────────────────────
def _one_series(seed: int, base: float, n: int) -> pd.DataFrame:
    """Deterministic seeded random-walk OHLCV with a valid datetime column."""
    rng = np.random.default_rng(seed)
    steps = rng.normal(0, base * 0.0015, n).cumsum()
    close = base + steps
    close = np.abs(close) + base * 0.05          # keep strictly positive
    openp = np.empty(n); openp[0] = close[0]; openp[1:] = close[:-1]
    wick  = np.abs(rng.normal(0, base * 0.0008, n))
    high  = np.maximum(openp, close) + wick
    low   = np.minimum(openp, close) - wick
    ts    = pd.date_range("2024-01-01", periods=n, freq="1h", tz="UTC")
    return pd.DataFrame({
        "datetime": ts.strftime("%Y-%m-%d %H:%M:%S"),
        "open": openp, "high": high, "low": low, "close": close,
    })


def build_fixture() -> dict:
    """{ 'EURUSD': {'h1':df,'h4':df,'d1':df}, ... } for the 16 CSM pairs."""
    ohlcv = {}
    for i, key in enumerate(FIXTURE_PAIRS):
        base = 100.0 if "JPY" in key else 1.2      # value scale is irrelevant to the maths
        seed = FIXTURE_SEED + i * 101
        h1 = _one_series(seed, base, N_H1_BARS)
        ohlcv[key] = build_tfs(h1)
    return ohlcv


def _pill_dir(pill: str):
    if pill in ("bull", "bull_strong"): return "bull"
    if pill in ("bear", "bear_strong"): return "bear"
    return "neutral"


# ── run every frozen calculation ───────────────────────────────────────────────
def compute_frozen() -> dict:
    ohlcv = build_fixture()
    out = {"csm": {}, "regime": {}, "pairs": {}, "correlations": None, "rank": []}

    # 1. CSM (16-pair universe, D1/H4/H1)
    out["csm"] = csm.compute_csm(ohlcv)

    # 2. Pills + per-pair scores for the 12 wheel pairs
    pair_pills = {}
    pair_scores = {}
    for key in WHEEL_PAIRS:
        tfs = {"d1": ohlcv[key]["d1"], "h4": ohlcv[key]["h4"], "h1": ohlcv[key]["h1"]}
        cls = pills_mod.classify_full(tfs, regime="unknown")
        pair_pills[key] = cls["pills"]
        pair_scores[key] = cls["scores"]

    # 3. Regimes (D1/H4/H1) from CSM + pills
    for tf in ("d1", "h4", "h1"):
        out["regime"][tf] = regime.classify_regime(out["csm"][tf], pair_pills, None, tf=tf)

    # 4. Per-pair momentum, adx, reset, atr%, continuation, structure
    for key in WHEEL_PAIRS:
        tfs = {"d1": ohlcv[key]["d1"], "h4": ohlcv[key]["h4"], "h1": ohlcv[key]["h1"]}
        mom = mom1212.compute_all(tfs)
        h4_score = pair_scores[key].get("h4")
        adx = h4_score["raw"]["adx"] if h4_score else None
        h4_dir = _pill_dir(pair_pills[key].get("h4", "neutral"))
        reset = compute_reset_score(ohlcv[key]["h4"]["close"].tolist(), direction=h4_dir)
        atrp = atr_percentile(ohlcv[key]["h4"])
        cont = compute_cont(key, pair_pills[key], adx, out["csm"]["h4"],
                            out["regime"]["h4"], reset_score=reset, atr_pct=atrp)
        structure = h4_score["structure"] if h4_score else None
        out["pairs"][key] = {
            "pills": pair_pills[key],
            "mom": mom,
            "adx": adx,
            "reset": reset,
            "atr_pct": atrp,
            "cont": cont,
            "structure": structure,
        }

    # 5. Correlation matrix
    out["correlations"] = correlate.compute_correlation(ohlcv)

    # 6. Setup rank (deterministic scorer)
    signals = {
        "pairs": {k: {"pills": v["pills"], "mom": v["mom"], "adx": v["adx"], "cont": v["cont"]}
                  for k, v in out["pairs"].items()},
        "csm": out["csm"],
        "regime_d1": out["regime"]["d1"],
        "macro_assets": {},
    }
    out["rank"] = rank.rank_pairs(signals)

    return _round(out)


# ── recursive rounding for float stability across numpy/pandas versions ─────────
def _round(x):
    if isinstance(x, float):
        return round(x, ROUND_DP)
    if isinstance(x, dict):
        return {k: _round(v) for k, v in x.items()}
    if isinstance(x, (list, tuple)):
        return [_round(v) for v in x]
    if isinstance(x, (np.floating,)):
        return round(float(x), ROUND_DP)
    if isinstance(x, (np.integer,)):
        return int(x)
    return x
