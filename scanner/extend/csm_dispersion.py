"""
ATOM FX — CSM dispersion percentile (EXTEND)

Why this exists: csm.py's compute_csm() min-max rescales the 8-currency basket to fill
exactly 0-100 every single scan, so the resulting numbers can't tell "today's real
cross-currency dispersion is wide" from "today's basket is thin and got stretched to fill
0-100 anyway" — yet two frozen consumers (cont_score.py's CSM Divergence component,
regime.py's safe-haven/USD-proxy votes) treat a CSM point-gap as an absolute, comparable-
across-time threshold. A first attempt at a fixed floor on the raw (pre-normalisation)
spread didn't hold up under calibration: because ATR-normalisation is deliberately
volatility-invariant, a "quiet" and a "normal" synthetic day produced statistically
identical raw-spread distributions. The number that's actually meaningful is whether
TODAY's raw spread is unusually thin relative to ITS OWN recent history — the same idea
score.py's atr_percentile() already uses for Volatility, just applied to a scalar that has
to be carried scan-to-scan rather than read off an OHLCV window.

Persistence follows conviction.py's own established pattern (scan_cot.py reads
`signals.get("conviction")` as `prev_conviction`, not a separate state file) rather than
level_ema_alerts.py's older separate-JSON-file approach: this module is a pure function —
it takes the previous scan's own history back in and returns the updated history for the
caller to embed in THIS scan's own signals.json (under csm.dispersion_history), no file I/O
of its own. Easier to test, and one fewer persistence mechanism in the codebase.
"""

# ~2.5 days of hourly scans — long enough that one unusually quiet hour can't flip the
# baseline, short enough to track an actual shift in market conditions rather than average
# it away entirely. Tune if real data shows it's too twitchy or too stale.
WINDOW = 60

# Percentile floor below which a TF's CSM-based confirmation is treated as unreliable —
# reuses the wheel's own existing "20" boundary (WheelCanvas.kt's Volatility wing already
# treats atr_pct < 20 as "too quiet to trust," not a new number invented for this).
LOW_DISPERSION_PCT = 20

_TFS = ("d1", "h4", "h1")


def compute_dispersion_percentile(dispersion: dict, prev_history: dict | None = None) -> tuple[dict, dict]:
    """
    dispersion   : csm.compute_csm()'s own "dispersion" key this scan — {"d1": float,
                   "h4": float, "h1": float}, the RAW pre-normalisation spread, not the
                   0-100 CSM values.
    prev_history : this function's own `history` return value from the PREVIOUS scan,
                   round-tripped through signals.json (caller reads it back as e.g.
                   `prev.get("csm", {}).get("dispersion_history")`) — {"d1": [...],
                   "h4": [...], "h1": [...]}. None on a first-ever run.

    Returns (percentile, history):
      percentile : {"d1": int|None, "h4": int|None, "h1": int|None} — today's percentile
                   rank (0-100, same "count strictly below / (n-1) * 100" formula
                   score.py's atr_percentile() uses) against the trailing WINDOW scans'
                   history for that TF. None until WINDOW readings have accumulated (the
                   first ~2.5 days after this ships) — every caller must treat None as "no
                   read yet, behave exactly as before this feature existed," not as 0.
      history    : the updated (capped at WINDOW, oldest dropped) history — the caller
                   embeds this in the CURRENT scan's own signals.json (csm.dispersion_history)
                   so the NEXT scan can read it back as `prev_history`.
    """
    history = {tf: list((prev_history or {}).get(tf, [])) for tf in _TFS}
    percentile = {}
    for tf in _TFS:
        val = dispersion.get(tf)
        h = history[tf]
        if val is not None:
            h.append(val)
            if len(h) > WINDOW:
                del h[: len(h) - WINDOW]
        if len(h) < WINDOW:
            percentile[tf] = None
        else:
            current = h[-1]
            n_below = sum(1 for v in h if v < current)
            percentile[tf] = round(n_below / (len(h) - 1) * 100)
    return percentile, history
