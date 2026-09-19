"""
ATOM FX research — Python port of the "Crowded Market — Reversal Conditions" Pine indicator
(pine/crowded_reversal.pine on main, methodology: pine/CROWDED_REVERSAL_METHODOLOGY.md).

EXTEND tier, research use only: pure functions, no I/O, no signals.json, nothing frozen is
touched or imported. The port follows the Pine script FACTOR BY FACTOR so a backtest of this
module is a backtest of the indicator as written — not of a re-imagined version. Where Python
and Pine primitives could differ, the Pine behaviour is reproduced and the choice is stated:

  * ta.stdev is the POPULATION stdev (biased=true)            -> ddof=0
  * ta.rma / ta.atr / ta.rsi / ta.dmi are Wilder smoothing, seeded with an SMA of the first
    `n` valid values                                           -> rma()
  * ta.ema is seeded with the first source value               -> ewm(adjust=False)
  * ta.percentrank(x, n) = share of the previous `n` values (current bar excluded) that are
    <= the current value; NaN unless all `n` previous values exist        -> percentrank()
  * ta.pivothigh/low: strict extreme vs `left` bars before and `right` bars after (float ties
    are practically impossible on RSI; strictness on both sides is a stated simplification)
  * ta.valuewhen(cond, x, 1) = the PREVIOUS occurrence, current bar counts as occurrence 0
  * na comparisons are false                                   -> numpy NaN comparisons

COT alignment (the no-look-ahead rule): the Pine reads, on every chart bar in calendar week k
(Mon-Fri), the report with as-of date = the Tuesday of week k-1 (`close[1]` on a weekly request
with lookahead_on — verified 2026-09-19). That report was published on Friday of week k-1, so
it is public by Monday of week k. `cot_net_daily(..., week_lag=1)` reproduces exactly that;
week_lag=2 reproduces the PREVIOUS (buggy, double-lagged) Pine behaviour and exists only so the
port can be validated against the diagnostic label TradingView showed on 2026-09-19.

The COT percentile is computed on a business-day calendar (Mon-Fri) forward-filled from the
weekly reports with a 156*5 = 780-bar window — the same construction the Pine uses on a D1
chart — and is then sampled at the price dates. It is independent of how much price history
the caller has, so a short price cache still gets a correct 3-year COT percentile.
"""
import numpy as np
import pandas as pd
from numpy.lib.stride_tricks import sliding_window_view

# Pine defaults, verbatim (input names in comments). Overridable via compute_flags(params=...).
PINE_DEFAULTS = {
    "bb_len": 20, "bb_mult": 2.0, "bb_top": 1.0, "bb_bot": 0.0,
    "z_len": 100, "z_thresh": 2.0,
    "stretch_ema_len": 50, "stretch_atr_len": 14, "stretch_mult": 3.0,
    "w_bb": 10.0, "w_z": 8.0, "w_stretch": 7.0,
    "rsi_len": 14, "rsi_ob": 70.0, "rsi_obx": 80.0, "rsi_os": 30.0, "rsi_osx": 20.0,
    "div_left": 5, "div_right": 5, "div_min_gap": 5, "div_max_gap": 60, "div_hold": 5,
    "w_rsi": 12.0, "w_rsi_x": 3.0, "w_div": 25.0,
    "tr_atr_len": 14, "tr_mult": 1.8,
    "climax_require_wide": False, "width_pct_len": 252, "width_pct_thresh": 80.0,
    "w_climax": 10.0,
    "cot_enabled": True, "cot_lookback_weeks": 156, "cot_pct_hi": 90.0, "cot_pct_lo": 10.0,
    "w_cot": 25.0,
    "adx_len": 14, "adx_thresh": 30.0, "ema_trend_len": 200, "ema_slope_lb": 20,
    "regime_persist": 3, "regime_penalty": 0.5,
    "top_threshold": 60.0, "bottom_threshold": 60.0,
}

# Pine f_cftcCode, verbatim
CFTC_CODES = {
    "EUR": "099741", "GBP": "096742", "JPY": "097741", "CHF": "092741",
    "CAD": "090741", "AUD": "232741", "NZD": "112741", "USD": "098662",
}

BARS_PER_WEEK_D1 = 5


# ----------------------------------------------------------------------------------------
# Pine primitives
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
    run = 0            # consecutive valid values seen while unseeded
    acc = 0.0
    prev = np.nan
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
                out[i] = np.nan
                continue
            prev = (prev * (n - 1) + v) / n
            out[i] = prev
    return out


def true_range(high, low, close, first_bar_hl):
    """first_bar_hl=True -> ta.tr(true) (high-low on bar 0, used by ta.atr);
    False -> NaN on bar 0 (ta.tr / the script's own trueRange)."""
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


def adx(high, low, close, di_len, adx_len):
    """ta.dmi(di_len, adx_len)[2]."""
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


def percentrank(x, n):
    """ta.percentrank: share (0-100) of the previous n values <= the current one."""
    x = np.asarray(x, dtype=float)
    out = np.full(len(x), np.nan)
    if len(x) <= n:
        return out
    windows = sliding_window_view(x, n)          # windows[j] = x[j : j+n]
    prev = windows[: len(x) - n]                 # for bar i = j+n: x[i-n : i]  (excludes bar i)
    cur = x[n:]
    with np.errstate(invalid="ignore"):
        cnt = (prev <= cur[:, None]).sum(axis=1)
    valid = ~np.isnan(cur) & ~np.isnan(prev).any(axis=1)
    res = 100.0 * cnt / n
    res[~valid] = np.nan
    out[n:] = res
    return out


def _pivots(x, left, right, high_side):
    """Value at the CONFIRMATION bar (pivot bar + right) or NaN. high_side=True -> pivot high."""
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


def divergence_active(high, low, rsi_v, p):
    """Regular bearish / bullish divergence, confirmed-pivot based, held `div_hold` bars.
    Returns (bearish_active, bullish_active) boolean arrays — the Pine's bearishDivActive /
    bullishDivActive, including the latch and the min/max pivot-gap gate."""
    n = len(high)
    left, right = p["div_left"], p["div_right"]
    piv_hi = _pivots(rsi_v, left, right, True)
    piv_lo = _pivots(rsi_v, left, right, False)

    def run(piv, price, bearish):
        confirmed = np.zeros(n, bool)
        prev = None                                   # (rsi_at_pivot, price_at_pivot, bar)
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


def _debounced_latch(raw, persist):
    out = np.zeros(len(raw), bool)
    streak, latch = 0, False
    for i, r in enumerate(raw):
        streak = streak + 1 if r else 0
        latch = True if streak >= persist else (latch if r else False)
        out[i] = latch
    return out


# ----------------------------------------------------------------------------------------
# COT alignment (no look-ahead)
# ----------------------------------------------------------------------------------------
def cot_net_daily(reports, calendar, week_lag=1):
    """Daily (business-day) net position as the Pine reads it.

    reports: DataFrame[date (as-of Tuesday), net]. calendar: DatetimeIndex of Mon-Fri days.
    On every day of calendar week k the value is the newest report with as-of date on or before
    (Monday of week k) - 5 - 7*(week_lag-1) days: for week_lag=1 that is the Tuesday of week k-1
    (published Friday k-1) — never a report dated in week k or later."""
    rep = reports.sort_values("date")[["date", "net"]].copy()
    rep["date"] = pd.to_datetime(rep["date"])
    monday = calendar - pd.to_timedelta(calendar.weekday, unit="D")
    key = pd.DataFrame({"day": calendar, "key": monday - pd.Timedelta(days=5 + 7 * (week_lag - 1))})
    merged = pd.merge_asof(key.sort_values("key"), rep, left_on="key", right_on="date",
                           direction="backward")
    merged = merged.sort_values("day")
    return pd.Series(merged["net"].to_numpy(dtype=float), index=calendar)


def cot_percentile_daily(reports, calendar, lookback_weeks=156, week_lag=1):
    net = cot_net_daily(reports, calendar, week_lag)
    window = int(round(lookback_weeks * BARS_PER_WEEK_D1))
    return pd.Series(percentrank(net.to_numpy(), window), index=calendar)


def combine_cot_pctl(base_pctl, quote_pctl):
    """Pine cotCombinedPctl: crowded-long base is bearish for the pair, crowded-long quote is
    bullish for it, so the quote leg is inverted; both legs are averaged when both resolve.
    Inputs are aligned arrays/Series (NaN = leg unavailable)."""
    b = np.asarray(base_pctl, dtype=float)
    q = np.asarray(quote_pctl, dtype=float)
    both = ~np.isnan(b) & ~np.isnan(q)
    out = np.where(both, (b + (100.0 - q)) / 2.0,
                   np.where(~np.isnan(b), b, np.where(~np.isnan(q), 100.0 - q, np.nan)))
    return out


def pair_legs(pair):
    """'EURUSD' -> ('EUR', 'USD')."""
    pair = pair.replace("/", "").upper()
    return pair[:3], pair[3:]


# ----------------------------------------------------------------------------------------
# The indicator
# ----------------------------------------------------------------------------------------
def compute_flags(df, cot_combined_pctl=None, params=None):
    """Port of the Pine indicator over one pair's OHLC.

    df: DataFrame with open/high/low/close (oldest first), one row per bar.
    cot_combined_pctl: array aligned to df (Pine's cotCombinedPctl), or None = COT unavailable
                       (the factor's weight then stays in the denominator but can never fire —
                       exactly the Pine's behaviour when a leg does not resolve).
    Returns a DataFrame indexed like df with the raw factors, raw top/bottom scores (0-100), the
    strong-trend latches, per-factor booleans (for single-factor comparators) and `atr`."""
    p = dict(PINE_DEFAULTS)
    if params:
        p.update(params)
    h = df["high"].to_numpy(float)
    l = df["low"].to_numpy(float)
    c = df["close"].to_numpy(float)
    n = len(c)

    # --- 1. overextension
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

    # --- 2. momentum & divergence
    rsi_v = rsi(c, p["rsi_len"])
    bear_div, bull_div = divergence_active(h, l, rsi_v, p)

    # --- 3. volatility climax
    tr = true_range(h, l, c, False)
    c_atr = atr(h, l, c, p["tr_atr_len"])
    tr_spike = (c_atr != 0) & (tr >= p["tr_mult"] * c_atr)
    if p["climax_require_wide"]:
        with np.errstate(divide="ignore", invalid="ignore"):
            width = np.where(basis != 0, (upper - lower) / basis * 100.0, np.nan)
        wp = percentrank(width, p["width_pct_len"])
        width_ok = ~np.isnan(wp) & (wp >= p["width_pct_thresh"])
    else:
        width_ok = np.ones(n, bool)
    climax_up = tr_spike & (c > upper) & width_ok
    climax_dn = tr_spike & (c < lower) & width_ok

    # --- 4. COT
    if cot_combined_pctl is None:
        cot_pctl = np.full(n, np.nan)
    else:
        cot_pctl = np.asarray(cot_combined_pctl, dtype=float)
    cot_top = p["cot_enabled"] & (cot_pctl >= p["cot_pct_hi"])
    cot_bot = p["cot_enabled"] & (cot_pctl <= p["cot_pct_lo"])

    # --- 5. regime
    ema_t = ema(c, p["ema_trend_len"])
    adx_v = adx(h, l, c, p["adx_len"], p["adx_len"])
    lb = p["ema_slope_lb"]
    ema_prev = np.concatenate((np.full(lb, np.nan), ema_t[:-lb]))
    strong = adx_v >= p["adx_thresh"]
    raw_up = strong & (ema_t > ema_prev) & (c > ema_t)
    raw_dn = strong & (ema_t < ema_prev) & (c < ema_t)
    strong_up = _debounced_latch(raw_up, p["regime_persist"])
    strong_dn = _debounced_latch(raw_dn, p["regime_persist"])

    # --- 6. confluence scoring (per-factor booleans kept for single-factor comparators)
    top_f = {
        "pct_b": pct_b >= p["bb_top"], "z": z >= p["z_thresh"], "stretch": stretch >= p["stretch_mult"],
        "rsi": rsi_v >= p["rsi_ob"], "rsi_x": rsi_v >= p["rsi_obx"],
        "div": bear_div, "climax": climax_up, "cot": cot_top,
    }
    bot_f = {
        "pct_b": pct_b <= p["bb_bot"], "z": z <= -p["z_thresh"], "stretch": stretch <= -p["stretch_mult"],
        "rsi": rsi_v <= p["rsi_os"], "rsi_x": rsi_v <= p["rsi_osx"],
        "div": bull_div, "climax": climax_dn, "cot": cot_bot,
    }
    weights = {"pct_b": p["w_bb"], "z": p["w_z"], "stretch": p["w_stretch"], "rsi": p["w_rsi"],
               "rsi_x": p["w_rsi_x"], "div": p["w_div"], "climax": p["w_climax"],
               "cot": p["w_cot"] if p["cot_enabled"] else 0.0}
    total_w = sum(weights.values())
    top_pts = sum(np.where(top_f[k], w, 0.0) for k, w in weights.items())
    bot_pts = sum(np.where(bot_f[k], w, 0.0) for k, w in weights.items())
    top_score = 100.0 * top_pts / total_w if total_w > 0 else np.zeros(n)
    bot_score = 100.0 * bot_pts / total_w if total_w > 0 else np.zeros(n)

    out = pd.DataFrame({
        "pct_b": pct_b, "z": z, "stretch": stretch, "rsi": rsi_v, "adx": adx_v, "atr": c_atr,
        "cot_pctl": cot_pctl, "strong_up": strong_up, "strong_dn": strong_dn,
        "top_score": top_score, "bot_score": bot_score,
    }, index=df.index)
    for k, v in top_f.items():
        out[f"top_{k}"] = v
    for k, v in bot_f.items():
        out[f"bot_{k}"] = v
    return out


def apply_regime(flags, mode="suppress", penalty=0.5):
    """Pine's topScoreAdj / botScoreAdj. mode: 'suppress' (default), 'penalize', 'off'."""
    top, bot = flags["top_score"].to_numpy(), flags["bot_score"].to_numpy()
    up, dn = flags["strong_up"].to_numpy(), flags["strong_dn"].to_numpy()
    if mode == "suppress":
        top, bot = np.where(up, 0.0, top), np.where(dn, 0.0, bot)
    elif mode == "penalize":
        top, bot = np.where(up, top * penalty, top), np.where(dn, bot * penalty, bot)
    elif mode != "off":
        raise ValueError(f"unknown regime mode: {mode}")
    return top, bot


def rising_edge(cond):
    """Pine `flag and not flag[1]` (bar 0 counts as a new flag if it is true)."""
    cond = np.asarray(cond, bool)
    prev = np.concatenate(([False], cond[:-1]))
    return cond & ~prev
