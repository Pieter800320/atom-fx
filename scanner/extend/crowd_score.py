"""
ATOM FX — Crowd score  (EXTEND, Signals Roadmap §5c)

The "Crowded Market — Reversal Conditions" indicator (pine/crowded_reversal.pine, methodology
pine/CROWDED_REVERSAL_METHODOLOGY.md) as a per-pair D1 + H4 series for the ChartSheet's Crowd score card.

Eight yes/no conditions, each with a fixed weight (out of 100), add to a TOP score (price stretched up and
crowded long) and a BOTTOM score (the mirror), each 0-100:
  %B outside its 20-period +/-2 sigma bands 10 · z-score vs SMA(100) >= 2 8 · ATR-stretch vs EMA(50) >= 3 7 ·
  RSI(14) >= 70 / <= 30 12 (+3 at 80/20) · regular divergence (RSI pivots, confirmed 5 bars late, held 5 bars) 25 ·
  volatility climax (true range >= 1.8 x ATR through a band) 10 · COT positioning at a 3-year extreme 25.

THIS IS CONTEXT, NOT A SIGNAL. Four pre-registered, run-once studies (docs/RESEARCH_LOG.md on the `research`
branch, Experiments 2-5) found a small tendency for reversals to rise with the D1 score in 2021-2026 only —
absent in 2009-2020, absent on H4 — and no support/resistance-return edge. Nothing here is a probability.

PORT: the numerics are the tested Python port of the Pine logic (research branch, `crowded_reversal.py`),
factor for factor — population stdev, Wilder rma/RSI/ATR, EMA seeded with the first value, percentrank over the
previous n values, strict pivots, `valuewhen`, the divergence latch. `tests/golden/crowd_score_golden.json` pins
them to that version's own output. Two deliberate differences: the REGIME FILTER is not ported (Off by default
since the study found it made the score a worse predictor, so ADX/EMA200 are not computed) and the
`climax_require_wide` option is hard-off (its default).

Bars and COT come from `crowd_data.py` (NY-close D1, NY-aligned H4, completed bars only, Legacy COT). Output,
inside each `pairs.<PAIR>`:

    crowd_series = { "d1" | "h4": { dates:[str], top:[float], bottom:[float],
                                     latest:{ bar_date, top, bottom, top_on:[str], bottom_on:[str],
                                              cot_pctl, cot_ok, cot_asof } | null } }

Rule #1: EXTEND. Reads OHLCV and the Legacy COT file, writes one new key, touches nothing else.
"""
import numpy as np
import pandas as pd

from scanner.extend import crowd_data

# Pine defaults, verbatim (regime filter deliberately absent)
P = {
    "bb_len": 20, "bb_mult": 2.0, "bb_top": 1.0, "bb_bot": 0.0,
    "z_len": 100, "z_thresh": 2.0,
    "stretch_ema_len": 50, "stretch_atr_len": 14, "stretch_mult": 3.0,
    "rsi_len": 14, "rsi_ob": 70.0, "rsi_obx": 80.0, "rsi_os": 30.0, "rsi_osx": 20.0,
    "div_left": 5, "div_right": 5, "div_min_gap": 5, "div_max_gap": 60, "div_hold": 5,
    "tr_atr_len": 14, "tr_mult": 1.8,
    "cot_pct_hi": 90.0, "cot_pct_lo": 10.0,
}
WEIGHTS = {"pct_b": 10.0, "z": 8.0, "stretch": 7.0, "rsi": 12.0, "rsi_x": 3.0, "div": 25.0, "climax": 10.0, "cot": 25.0}
FACTOR_NAMES = {"pct_b": "bb_pctb", "z": "zscore", "stretch": "atr_stretch", "rsi": "rsi", "rsi_x": "rsi_extreme",
                "div": "divergence", "climax": "climax", "cot": "cot"}
TAIL = 90                    # points per series, oldest first — matches the sibling series
FIRST_SCORED = 100           # the 100-bar z-score's first defined bar; earlier bars are never scored
FLAG_LINE = 60.0             # the indicator's own flag line (footer state / marker), not a tuned threshold
# The Pine's strong-trend shading (2026-09-20, Pieter's ask: the background on the app's chart). Pine defaults, verbatim: ADX(14, 14) >= 30, price on the same side of the 200 EMA
# as its 20-bar slope, and 3 consecutive bars to ENTER (it leaves the moment the raw condition breaks). Shipped as a per-bar state; the APP draws it (reversed colours, Design 19.4b).
REGIME = {"adx_len": 14, "adx_thresh": 30.0, "ema_len": 200, "slope_lb": 20, "persist": 3}
_EMPTY = {"dates": [], "top": [], "bottom": [], "regime": [], "latest": None}


# ----------------------------------------------------------------------------------------
# Pine primitives (tested; see the module doc)
# ----------------------------------------------------------------------------------------
def sma(x, n):
    return pd.Series(x, dtype=float).rolling(n, min_periods=n).mean().to_numpy()


def pstdev(x, n):
    """ta.stdev: population (biased) standard deviation."""
    return pd.Series(x, dtype=float).rolling(n, min_periods=n).std(ddof=0).to_numpy()


def ema(x, n):
    return pd.Series(x, dtype=float).ewm(alpha=2.0 / (n + 1), adjust=False).mean().to_numpy()


def rma(x, n):
    """ta.rma: Wilder smoothing, seeded with the SMA of the first n consecutive valid values."""
    x = np.asarray(x, dtype=float)
    out = np.full(len(x), np.nan)
    run, acc, prev = 0, 0.0, np.nan
    for i, v in enumerate(x):
        if np.isnan(prev):
            if np.isnan(v):
                run, acc = 0, 0.0
                continue
            run += 1
            acc += v
            if run == n:
                prev = acc / n
                out[i] = prev
        else:
            if np.isnan(v):
                continue
            prev = (prev * (n - 1) + v) / n
            out[i] = prev
    return out


def true_range(high, low, close, first_bar_hl):
    prev_close = np.concatenate(([np.nan], close[:-1]))
    tr = np.maximum(high - low, np.maximum(np.abs(high - prev_close), np.abs(low - prev_close)))
    if first_bar_hl:
        tr[0] = high[0] - low[0]
    return tr


def atr(high, low, close, n):
    return rma(true_range(high, low, close, True), n)


def rsi(close, n):
    chg = np.concatenate(([np.nan], np.diff(close)))
    up = rma(np.where(np.isnan(chg), np.nan, np.maximum(chg, 0.0)), n)
    dn = rma(np.where(np.isnan(chg), np.nan, np.maximum(-chg, 0.0)), n)
    with np.errstate(divide="ignore", invalid="ignore"):
        out = np.where(dn == 0, 100.0, np.where(up == 0, 0.0, 100.0 - 100.0 / (1.0 + up / dn)))
    out[np.isnan(up) | np.isnan(dn)] = np.nan
    return out


def _pivots(x, left, right, high_side):
    """Value at the CONFIRMATION bar (pivot bar + right) or NaN. Strict extreme vs `left` before / `right` after."""
    x = np.asarray(x, dtype=float)
    n = len(x)
    out = np.full(n, np.nan)
    for c in range(left + right, n):
        p = c - right
        v = x[p]
        if np.isnan(v):
            continue
        window = x[p - left: p + right + 1]
        if np.isnan(window).any():
            continue
        others = np.concatenate((x[p - left: p], x[p + 1: p + right + 1]))
        if (high_side and (v > others).all()) or (not high_side and (v < others).all()):
            out[c] = v
    return out


def divergence_active(high, low, rsi_v, p=P):
    """Regular bearish / bullish divergence between price and RSI pivots, confirmed `div_right` bars after the
    pivot, gated by a min/max pivot gap, held `div_hold` bars. Returns (bearish_active, bullish_active)."""
    n = len(high)
    left, right = p["div_left"], p["div_right"]
    piv_hi = _pivots(rsi_v, left, right, True)
    piv_lo = _pivots(rsi_v, left, right, False)

    def run(piv, price, bearish):
        confirmed = np.zeros(n, bool)
        prev = None
        for t in range(n):
            if np.isnan(piv[t]):
                continue
            cur = (piv[t], price[t - right], t - right)
            if prev is not None:
                gap = cur[2] - prev[2]
                if p["div_min_gap"] <= gap <= p["div_max_gap"]:
                    if bearish and cur[1] > prev[1] and cur[0] < prev[0]:
                        confirmed[t] = True
                    if (not bearish) and cur[1] < prev[1] and cur[0] > prev[0]:
                        confirmed[t] = True
            prev = cur
        active = np.zeros(n, bool)
        latch, last = False, None
        for t in range(n):
            if confirmed[t]:
                latch, last = True, t
            else:
                latch = latch if (last is not None and (t - last) < p["div_hold"]) else False
            active[t] = confirmed[t] or latch
        return active

    return run(piv_hi, high, True), run(piv_lo, low, False)


# ----------------------------------------------------------------------------------------
# The score
# ----------------------------------------------------------------------------------------
def compute_scores(df: pd.DataFrame, cot_pctl=None, p=P) -> pd.DataFrame:
    """Top/bottom score (0-100) for every bar, plus each factor's on/off (columns top_<f>, bot_<f>).

    df: open/high/low/close, oldest first. cot_pctl: the pair's combined COT percentile per bar (Pine's
    cotCombinedPctl), or None — then the COT factor can never fire but its weight stays in the denominator
    (score ceiling 75), exactly the Pine's behaviour when a COT leg does not resolve."""
    h = df["high"].to_numpy(float)
    l = df["low"].to_numpy(float)
    c = df["close"].to_numpy(float)
    n = len(c)

    basis = sma(c, p["bb_len"])
    dev = p["bb_mult"] * pstdev(c, p["bb_len"])
    upper, lower = basis + dev, basis - dev
    rng = upper - lower
    with np.errstate(divide="ignore", invalid="ignore"):
        pct_b = np.where(rng != 0, (c - lower) / rng, np.nan)
        z_sd = pstdev(c, p["z_len"])
        z = np.where(z_sd != 0, (c - sma(c, p["z_len"])) / z_sd, np.nan)
        s_atr = atr(h, l, c, p["stretch_atr_len"])
        stretch = np.where(s_atr != 0, (c - ema(c, p["stretch_ema_len"])) / s_atr, np.nan)

    rsi_v = rsi(c, p["rsi_len"])
    bear_div, bull_div = divergence_active(h, l, rsi_v, p)

    tr = true_range(h, l, c, False)
    c_atr = atr(h, l, c, p["tr_atr_len"])
    tr_spike = (c_atr != 0) & (tr >= p["tr_mult"] * c_atr)
    climax_up = tr_spike & (c > upper)
    climax_dn = tr_spike & (c < lower)

    cot = np.full(n, np.nan) if cot_pctl is None else np.asarray(cot_pctl, dtype=float)
    top_f = {"pct_b": pct_b >= p["bb_top"], "z": z >= p["z_thresh"], "stretch": stretch >= p["stretch_mult"],
             "rsi": rsi_v >= p["rsi_ob"], "rsi_x": rsi_v >= p["rsi_obx"], "div": bear_div,
             "climax": climax_up, "cot": cot >= p["cot_pct_hi"]}
    bot_f = {"pct_b": pct_b <= p["bb_bot"], "z": z <= -p["z_thresh"], "stretch": stretch <= -p["stretch_mult"],
             "rsi": rsi_v <= p["rsi_os"], "rsi_x": rsi_v <= p["rsi_osx"], "div": bull_div,
             "climax": climax_dn, "cot": cot <= p["cot_pct_lo"]}
    total_w = sum(WEIGHTS.values())
    top_pts = sum(np.where(top_f[k], w, 0.0) for k, w in WEIGHTS.items())
    bot_pts = sum(np.where(bot_f[k], w, 0.0) for k, w in WEIGHTS.items())
    out = pd.DataFrame({"top_score": 100.0 * top_pts / total_w, "bot_score": 100.0 * bot_pts / total_w})
    for k, v in top_f.items():
        out[f"top_{k}"] = v
    for k, v in bot_f.items():
        out[f"bot_{k}"] = v
    return out


# ----------------------------------------------------------------------------------------
# Strong-trend regime (the Pine's background shading)
# ----------------------------------------------------------------------------------------
def dmi_adx(high, low, close, di_len, adx_len):
    """ta.dmi(di_len, adx_len)[2] — Wilder-smoothed +DI/-DI, DX, then its own Wilder average."""
    up = np.concatenate(([np.nan], np.diff(high)))
    down = -np.concatenate(([np.nan], np.diff(low)))
    plus_dm = np.where(np.isnan(up), np.nan, np.where((up > down) & (up > 0), up, 0.0))
    minus_dm = np.where(np.isnan(down), np.nan, np.where((down > up) & (down > 0), down, 0.0))
    trur = rma(true_range(high, low, close, False), di_len)
    with np.errstate(divide="ignore", invalid="ignore"):
        plus = 100.0 * rma(plus_dm, di_len) / trur
        minus = 100.0 * rma(minus_dm, di_len) / trur
        s = plus + minus
        dx = np.abs(plus - minus) / np.where(s == 0, 1.0, s)
    return 100.0 * rma(dx, adx_len)


def _debounced_latch(raw, persist):
    """Latch ON after `persist` consecutive raw bars, OFF the instant the raw condition breaks (the Pine's up/downStreak logic)."""
    out = np.zeros(len(raw), bool)
    streak, latch = 0, False
    for i, r in enumerate(raw):
        streak = streak + 1 if r else 0
        latch = True if streak >= persist else (latch if r else False)
        out[i] = latch
    return out


def regime_states(bars: pd.DataFrame) -> np.ndarray:
    """Per bar: +1 strong uptrend (the Pine shades it GREEN), -1 strong downtrend (RED), 0 none."""
    h, l, c = (bars[k].to_numpy(float) for k in ("high", "low", "close"))
    ema_t = ema(c, REGIME["ema_len"])
    adx_v = dmi_adx(h, l, c, REGIME["adx_len"], REGIME["adx_len"])
    lb = REGIME["slope_lb"]
    ema_prev = np.concatenate((np.full(lb, np.nan), ema_t[:-lb]))
    strong = adx_v >= REGIME["adx_thresh"]
    up = _debounced_latch(strong & (ema_t > ema_prev) & (c > ema_t), REGIME["persist"])
    dn = _debounced_latch(strong & (ema_t < ema_prev) & (c < ema_t), REGIME["persist"])
    return np.where(up, 1, np.where(dn, -1, 0)).astype(int)


# ----------------------------------------------------------------------------------------
# Series for one pair / timeframe, and the scan hook
# ----------------------------------------------------------------------------------------
def series_block(bars: pd.DataFrame, cot_comb, cot_asof, tail: int = TAIL) -> dict:
    """One timeframe's series block from COMPLETED bars (crowd_data.completed_bars). Fails quiet to the empty
    block when there is not enough history (a bar without every input is never scored)."""
    if bars is None or len(bars) <= FIRST_SCORED:
        return dict(_EMPTY)
    n = len(bars)
    sc = compute_scores(bars, cot_comb)
    start = max(FIRST_SCORED, n - tail)
    days = pd.to_datetime(bars["trading_day"])
    top = [round(float(v), 1) for v in sc["top_score"].iloc[start:]]
    bot = [round(float(v), 1) for v in sc["bot_score"].iloc[start:]]
    dates = [d.strftime("%Y-%m-%d") for d in days.iloc[start:]]
    regime = [int(v) for v in regime_states(bars)[start:]]

    last = n - 1
    cot_val = float(cot_comb[last]) if cot_comb is not None and not np.isnan(cot_comb[last]) else None
    asof_raw = cot_asof[last] if cot_asof is not None else None
    asof = None if asof_raw is None or pd.isna(asof_raw) else pd.Timestamp(asof_raw).strftime("%Y-%m-%d")
    latest = {
        "bar_date": days.iloc[last].strftime("%Y-%m-%d"),
        "top": top[-1], "bottom": bot[-1],
        "top_on": [FACTOR_NAMES[k] for k in WEIGHTS if bool(sc[f"top_{k}"].iloc[last])],
        "bottom_on": [FACTOR_NAMES[k] for k in WEIGHTS if bool(sc[f"bot_{k}"].iloc[last])],
        "cot_pctl": None if cot_val is None else round(cot_val, 1),
        "cot_ok": cot_val is not None,
        "cot_asof": asof,
    }
    return {"dates": dates, "top": top, "bottom": bot, "regime": regime, "latest": latest}


def crowd_series_for_pair(pair: str, h1_df, per_ccy: dict, now_utc=None) -> dict:
    out = {}
    for tf in ("d1", "h4"):
        bars = crowd_data.completed_bars(h1_df, tf, now_utc)
        if len(bars) <= FIRST_SCORED:
            out[tf] = dict(_EMPTY)
            continue
        days = pd.DatetimeIndex(pd.to_datetime(bars["trading_day"]))
        comb, asof = crowd_data.combined_pctl_for_pair(pair, per_ccy, days)
        out[tf] = series_block(bars, comb, asof)
    return out


def attach_crowd_series(pairs_out: dict, raw_ohlcv: dict | None, now_utc=None, store: pd.DataFrame | None = None) -> None:
    """Mutate pairs_out in place, adding a 'crowd_series' sub-key to each pair block — the same pattern
    `attach_bollinger_series`/`attach_momentum_series` use. Written by scan_h1.py ONLY (scan_m15.py never touches
    it, and scan_h1.py rebuilds pairs_out fresh each run), so no cross-cadence carry-forward is needed.

    One pair failing leaves its key absent and the scan continues — the same posture as every EXTEND module."""
    if not raw_ohlcv:
        return
    now = pd.Timestamp.now(tz="UTC") if now_utc is None else pd.Timestamp(now_utc)
    now = now.tz_localize("UTC") if now.tzinfo is None else now.tz_convert("UTC")
    store = crowd_data.load_legacy_store() if store is None else store
    per_ccy = {}
    if len(store):
        calendar = pd.bdate_range(pd.to_datetime(store["date"]).min(), now.tz_localize(None) + pd.Timedelta(days=10))
        per_ccy = crowd_data.cot_percentile_by_currency(store, calendar)
    for key, block in pairs_out.items():
        h1 = raw_ohlcv.get(key)
        if h1 is None:
            continue
        try:
            block["crowd_series"] = crowd_series_for_pair(key, h1, per_ccy, now)
        except Exception as e:                                # noqa: BLE001 — never break a scan for a chart
            print(f"  ⚠ crowd_series {key}: {type(e).__name__}: {e}")
