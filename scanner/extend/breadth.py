"""
ATOM FX — Currency breadth  (EXTEND §5.3)

"Is a currency's move broad, or produced by one or two pairs?"

Reuses the frozen CSM internals: in csm.py each pair contributes +combined to its
base and -combined to its quote across the 16-pair STRENGTH_PAIRS set. Breadth for
a currency = the share of its contributions that agree with its net direction.

Accuracy note (do NOT flatten to /8): each currency appears a different number of
times in the frozen 16-pair set (USD=7, AUD=5, GBP=4, JPY=4, EUR/CHF/CAD/NZD=3).
So `total` varies 3-7 and pass logic / colour bands compare by `pct`, never by raw
`support`. The UI may still show `support/total`.

Rule #1: this module calls the SAME frozen helpers csm.py uses (_adj_return) with
the SAME lookbacks/weights. It never edits csm.py or changes any frozen output.
"""
from scanner import csm
from scanner.config import CURRENCIES
from scanner.extend import potential_config as cfg


def _contributions(ohlcv: dict, tf: str) -> dict:
    """
    Recompute the signed per-currency contribution lists for `tf`, mirroring the
    exact accumulation inside csm.compute_csm_<tf> (before min-max normalisation).
    Returns {CCY: [signed ATR-normalised contributions]}.
    """
    raw = {c: [] for c in CURRENCIES}

    for pair in csm.STRENGTH_PAIRS:
        key = pair.replace("/", "")
        base, quote = pair.split("/")
        tfs = ohlcv.get(key, {})

        if tf == "d1":
            d1_ret = csm._adj_return(tfs.get("d1"))                       # lookback=LOOKBACK(14)
            h4_ret = csm._adj_return(tfs.get("h4"))                       # lookback=LOOKBACK(14)
            if d1_ret is None:
                continue
            combined = (csm.D1_WEIGHT * d1_ret + csm.H4_WEIGHT * h4_ret
                        if h4_ret is not None else d1_ret)
        elif tf == "h4":
            h4_ret = csm._adj_return(tfs.get("h4"), lookback=csm.H4_LOOKBACK)
            h1_ret = csm._adj_return(tfs.get("h1"), lookback=csm.H1_LOOKBACK)
            if h4_ret is None:
                continue
            combined = (csm.H4_CSM_W * h4_ret + csm.H1_CSM_W * h1_ret
                        if h1_ret is not None else h4_ret)
        else:  # h1
            h1_ret = csm._adj_return(tfs.get("h1"), lookback=csm.H1_ONLY_LOOKBACK)
            if h1_ret is None:
                continue
            combined = h1_ret

        if base in raw:
            raw[base].append(combined)
        if quote in raw:
            raw[quote].append(-combined)

    return raw


def _band(pct: float) -> str:
    if pct >= cfg.BREADTH_STRONG:
        return "strong"
    if pct >= cfg.BREADTH_MODERATE:
        return "moderate"
    return "weak"


def _breadth_for_tf(ohlcv: dict, tf: str) -> dict:
    contribs = _contributions(ohlcv, tf)
    out = {}
    for ccy in CURRENCIES:
        vals = contribs.get(ccy, [])
        total = len(vals)
        if total == 0:
            out[ccy] = {"support": 0, "total": 0, "pct": 0.0,
                        "band": "weak", "net": 0.0, "dir": "flat"}
            continue
        net = float(sum(vals) / total)
        net_sign = 1 if net > 0 else (-1 if net < 0 else 0)
        support = sum(1 for v in vals if (v > 0) == (net_sign > 0) and v != 0) \
            if net_sign != 0 else 0
        pct = round(support / total, 2)
        out[ccy] = {
            "support": support,
            "total":   total,
            "pct":     pct,
            "band":    _band(pct),
            "net":     round(net, 2),                       # signed strength (extension)
            "dir":     "strong" if net_sign > 0 else ("weak" if net_sign < 0 else "flat"),
        }
    return out


def compute_breadth(ohlcv: dict) -> dict:
    """Returns {"h4": {CCY:{support,total,pct,band,net,dir}}, "d1": {...}}."""
    return {tf: _breadth_for_tf(ohlcv, tf) for tf in cfg.BREADTH_TFS}
