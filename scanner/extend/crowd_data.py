"""
ATOM FX — data feeds for the Crowd score  (EXTEND, Signals Roadmap §5c)

Two things the Crowd score needs that no other module provides, kept apart from the maths in
`crowd_score.py` so each can be tested on its own:

  1. BARS — D1 and H4 built from the H1 already fetched this scan (`raw_ohlcv`), on the conventions the
     indicator was TESTED on and that TradingView uses:
       D1 : 17:00 America/New_York close.
       H4 : New York-session blocks starting 17:00, 21:00, 01:00, 05:00, 09:00, 13:00 New York time
            (Pieter, 2026-09-19 — verified against a TradingView H4 screenshot: the live candle closes at
            21:00 UTC = 17:00 NY). NOT the app's UTC 4-hour blocks the other H4 cards use.
     Both DROP market-closed bars — [Sunday 17:00 ET, Friday 17:00 ET) is the real FX week — because
     Twelvedata has emitted flat market-closed weekend bars since 2026-01-11. (`bb_touch._d1_ny_close` and
     the frozen aggregator do NOT do this; that is BUILD_STATUS outstanding item 18, deliberately not
     fixed here — this module never changes another module's numbers.)
     Bars are labelled with their NY-close TRADING DAY (Mon-Fri; the Sunday reopen rolls into Monday),
     the label TradingView uses. The sibling series label by the session's OPEN date; that difference
     is known and documented (Roadmap §5c).
     Only COMPLETED bars are returned (no repaint): a bar counts as complete once a later bar exists, or
     once the clock has passed its close AND the H1 bar ending at that close is present in the data.

  2. COT — CFTC Legacy futures-only, Non-Commercial long/short, 8 contracts (EUR GBP JPY CHF CAD AUD NZD +
     the ICE Dollar Index for USD). A DIFFERENT series from `cot.py`'s TFF report (leveraged funds) that
     feeds Conviction — the two coexist and must not be unified. Stored in data/cot_legacy/legacy_nc.csv
     (committed; refreshed weekly by `scan_cot.py`). Alignment (unit-tested, the no-look-ahead rule): a bar
     in calendar week k reads the report dated the Tuesday of week k-1 — never one dated in week k or later.
     Percentile: 156 weeks on a Mon-Fri calendar, exactly as the Pine does it.

Rule #1: EXTEND. Reads OHLCV and a data file only; touches no frozen file and no other module.
"""
import csv
import datetime
import io
import urllib.request
import zipfile
from pathlib import Path

import numpy as np
import pandas as pd

ROOT = Path(__file__).parent.parent.parent
LEGACY_CSV = ROOT / "data" / "cot_legacy" / "legacy_nc.csv"
NY = "America/New_York"
NY_CLOSE_HOUR = 17
_OFFSET = pd.Timedelta(hours=24 - NY_CLOSE_HOUR)        # 7h: shifts 17:00 NY onto the next midnight

# currency -> CFTC contract code (identical to the Pine script's f_cftcCode)
CFTC_CODES = {
    "EUR": "099741", "GBP": "096742", "JPY": "097741", "CHF": "092741",
    "CAD": "090741", "AUD": "232741", "NZD": "112741", "USD": "098662",
}
COT_LOOKBACK_WEEKS = 156
BARS_PER_WEEK = 5                                         # Mon-Fri calendar, as the Pine's daily conversion


# ----------------------------------------------------------------------------------------
# BARS
# ----------------------------------------------------------------------------------------
def within_fx_week(ny_naive: pd.DatetimeIndex) -> np.ndarray:
    """True for bars inside the real FX week [Sunday 17:00 ET, Friday 17:00 ET). ny_naive: tz-dropped
    America/New_York wall-clock times."""
    wd, hr = ny_naive.weekday, ny_naive.hour
    closed = (wd == 5) | ((wd == 6) & (hr < NY_CLOSE_HOUR)) | ((wd == 4) & (hr >= NY_CLOSE_HOUR))
    return ~np.asarray(closed)


def _prepare(h1_df: pd.DataFrame):
    """(df indexed by UTC-aware time with float OHLC, FX-week-filtered, ny_naive index aligned to it)."""
    df = h1_df.copy()
    df["dt"] = pd.to_datetime(df["datetime"], utc=True)
    df = df.sort_values("dt").drop_duplicates(subset="dt").set_index("dt")
    for col in ("open", "high", "low", "close"):
        df[col] = pd.to_numeric(df[col], errors="coerce")
    ny_naive = df.index.tz_convert(NY).tz_localize(None)
    keep = within_fx_week(ny_naive)
    return df[keep], ny_naive[keep]


def _wall_to_utc(wall: pd.Series) -> pd.Series:
    """NY wall-clock timestamps -> UTC-aware. DST-ambiguous / non-existent wall times (only possible in the
    closed-market window, which is filtered out) become NaT rather than raising."""
    return wall.dt.tz_localize(NY, ambiguous="NaT", nonexistent="NaT").dt.tz_convert("UTC")


def completed_bars(h1_df: pd.DataFrame, kind: str, now_utc=None) -> pd.DataFrame:
    """Completed D1 / H4 bars from raw H1. See the module doc for the completeness rule.

    Returns DataFrame[trading_day (Timestamp, NY-close trading day), open, high, low, close], oldest first."""
    cols = ["trading_day", "open", "high", "low", "close"]
    if h1_df is None or len(h1_df) == 0:
        return pd.DataFrame(columns=cols)
    df, ny_naive = _prepare(h1_df)
    if df.empty:
        return pd.DataFrame(columns=cols)
    shifted = ny_naive + _OFFSET
    trading_day = shifted.floor("D")
    if kind == "d1":
        key = trading_day
        end_wall = (key - _OFFSET) + pd.Timedelta(days=1)
    elif kind == "h4":
        key = shifted.floor("4h")
        end_wall = (key - _OFFSET) + pd.Timedelta(hours=4)
    else:
        raise ValueError("kind must be 'd1' or 'h4'")

    tmp = pd.DataFrame({"open": df["open"].to_numpy(), "high": df["high"].to_numpy(), "low": df["low"].to_numpy(),
                        "close": df["close"].to_numpy(), "h1_start": df.index, "td": np.asarray(trading_day),
                        "end_wall": np.asarray(end_wall), "key": np.asarray(key)})
    bars = tmp.groupby("key").agg(open=("open", "first"), high=("high", "max"), low=("low", "min"),
                                  close=("close", "last"), trading_day=("td", "last"),
                                  end_wall=("end_wall", "last"), last_h1=("h1_start", "max"))
    bars = bars.dropna(subset=["open", "close"]).sort_index()
    bars["end_utc"] = _wall_to_utc(pd.Series(pd.to_datetime(bars["end_wall"].to_numpy()), index=bars.index)).to_numpy()

    if now_utc is None:
        now_utc = pd.Timestamp.now(tz="UTC")
    now_utc = pd.Timestamp(now_utc)
    now_utc = now_utc.tz_localize("UTC") if now_utc.tzinfo is None else now_utc.tz_convert("UTC")

    # completeness: the LAST bar only can be incomplete (any earlier bar has a later one after it)
    n = len(bars)
    keep = np.ones(n, dtype=bool)
    if n:
        last = bars.iloc[-1]
        end = last["end_utc"]
        final_h1_present = pd.notna(end) and last["last_h1"] >= end - pd.Timedelta(hours=1)
        complete = pd.notna(end) and now_utc >= end and final_h1_present
        keep[-1] = bool(complete)
    out = bars.loc[keep, ["trading_day", "open", "high", "low", "close"]].reset_index(drop=True)
    out["trading_day"] = pd.to_datetime(out["trading_day"])
    return out


# ----------------------------------------------------------------------------------------
# COT (Legacy futures-only, non-commercial)
# ----------------------------------------------------------------------------------------
def load_legacy_store(path=None) -> pd.DataFrame:
    """DataFrame[code (str), date (Timestamp, the report's as-of Tuesday), net] — empty if the file is absent."""
    path = Path(path or LEGACY_CSV)
    if not path.exists():
        return pd.DataFrame(columns=["code", "date", "net"])
    rep = pd.read_csv(path, dtype={"code": str}, parse_dates=["date"])
    rep["net"] = rep["nc_long"] - rep["nc_short"]
    return rep[["code", "date", "net"]]


def percentrank(x, n):
    """ta.percentrank: share (0-100) of the previous n values <= the current one; NaN unless all n exist."""
    from numpy.lib.stride_tricks import sliding_window_view
    x = np.asarray(x, dtype=float)
    out = np.full(len(x), np.nan)
    if len(x) <= n:
        return out
    windows = sliding_window_view(x, n)
    prev = windows[: len(x) - n]
    cur = x[n:]
    with np.errstate(invalid="ignore"):
        cnt = (prev <= cur[:, None]).sum(axis=1)
    valid = ~np.isnan(cur) & ~np.isnan(prev).any(axis=1)
    res = 100.0 * cnt / n
    res[~valid] = np.nan
    out[n:] = res
    return out


def _net_daily(reports: pd.DataFrame, calendar: pd.DatetimeIndex, week_lag: int = 1):
    """(daily net Series, daily as-of-date Series): on every day of calendar week k, the newest report with
    as-of date on or before (Monday of week k) - 5 - 7*(week_lag-1) days — for week_lag=1 the Tuesday of week
    k-1 (published Friday k-1), never one dated in week k or later."""
    rep = reports.sort_values("date")[["date", "net"]].copy()
    rep["date"] = pd.to_datetime(rep["date"])
    rep["asof"] = rep["date"]
    monday = calendar - pd.to_timedelta(calendar.weekday, unit="D")
    key = pd.DataFrame({"day": calendar, "key": monday - pd.Timedelta(days=5 + 7 * (week_lag - 1))})
    merged = pd.merge_asof(key.sort_values("key"), rep, left_on="key", right_on="date",
                           direction="backward").sort_values("day")
    return (pd.Series(merged["net"].to_numpy(dtype=float), index=calendar),
            pd.Series(merged["asof"].to_numpy(), index=calendar))


def cot_percentile_by_currency(store: pd.DataFrame, calendar: pd.DatetimeIndex) -> dict:
    """{ccy: (pctl Series on `calendar`, asof Series)} for every currency the store covers."""
    out = {}
    window = int(round(COT_LOOKBACK_WEEKS * BARS_PER_WEEK))
    for ccy, code in CFTC_CODES.items():
        rows = store[store["code"] == code]
        if rows.empty:
            continue
        net, asof = _net_daily(rows, calendar)
        out[ccy] = (pd.Series(percentrank(net.to_numpy(), window), index=calendar), asof)
    return out


def combined_pctl_for_pair(pair: str, per_ccy: dict, days: pd.DatetimeIndex):
    """(combined percentile array aligned to `days`, as-of date array). Pine cotCombinedPctl: crowded-long BASE
    is bearish for the pair and crowded-long QUOTE is bullish, so the quote leg is inverted; both legs are
    averaged when both resolve. NaN = unavailable."""
    base, quote = pair[:3], pair[3:6]

    def leg(ccy):
        if ccy not in per_ccy:
            return np.full(len(days), np.nan), np.full(len(days), np.datetime64("NaT"))
        pctl, asof = per_ccy[ccy]
        return pctl.reindex(days).to_numpy(), asof.reindex(days).to_numpy()

    (b, ba), (q, qa) = leg(base), leg(quote)
    both = ~np.isnan(b) & ~np.isnan(q)
    comb = np.where(both, (b + (100.0 - q)) / 2.0, np.where(~np.isnan(b), b, np.where(~np.isnan(q), 100.0 - q, np.nan)))
    asof = np.where(~pd.isna(ba), ba, qa)
    return comb, asof


# -- weekly refresh ----------------------------------------------------------------------
_URL = "https://www.cftc.gov/files/dea/history/deacot{year}.zip"
_COL_DATE, _COL_CODE = "As of Date in Form YYYY-MM-DD", "CFTC Contract Market Code"
_COL_LONG, _COL_SHORT = "Noncommercial Positions-Long (All)", "Noncommercial Positions-Short (All)"


def parse_year_zip(raw: bytes) -> list:
    """[(code, date_iso, nc_long, nc_short)] for the 8 mapped contracts in one CFTC annual zip."""
    wanted = set(CFTC_CODES.values())
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        reader = csv.reader(io.TextIOWrapper(z.open(z.namelist()[0]), encoding="latin-1"))
        header = next(reader)
        i_date, i_code = header.index(_COL_DATE), header.index(_COL_CODE)
        i_long, i_short = header.index(_COL_LONG), header.index(_COL_SHORT)
        return [(r[i_code].strip(), r[i_date].strip(), int(r[i_long]), int(r[i_short]))
                for r in reader if r[i_code].strip() in wanted]


def merge_rows(existing: pd.DataFrame, new_rows: list) -> pd.DataFrame:
    """Union by (code, date); a re-published report replaces the stored one. Sorted, columns code/date/nc_long/nc_short."""
    add = pd.DataFrame(new_rows, columns=["code", "date", "nc_long", "nc_short"])
    add["code"] = add["code"].astype(str)
    both = pd.concat([existing, add], ignore_index=True) if len(existing) else add
    both["date"] = pd.to_datetime(both["date"]).dt.strftime("%Y-%m-%d")
    both = both.drop_duplicates(subset=["code", "date"], keep="last")
    return both.sort_values(["code", "date"]).reset_index(drop=True)


def refresh_legacy_store(path=None, today=None, fetch=None) -> dict:
    """Append the newest CFTC reports to the committed store. NEVER raises and never shrinks the file: on any
    failure the existing store is left untouched and the reason returned. `fetch(year) -> bytes` is injectable
    for tests; the default downloads CFTC's annual zip (also last year's, during January)."""
    path = Path(path or LEGACY_CSV)
    today = today or datetime.date.today()
    years = [today.year] + ([today.year - 1] if today.month == 1 else [])

    def _download(year):
        req = urllib.request.Request(_URL.format(year=year), headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.read()

    fetch = fetch or _download
    try:
        existing = pd.read_csv(path, dtype={"code": str}) if path.exists() else pd.DataFrame(
            columns=["code", "date", "nc_long", "nc_short"])
        rows = []
        for y in years:
            rows.extend(parse_year_zip(fetch(y)))
        if not rows:
            return {"ok": False, "reason": "no rows parsed", "added": 0}
        merged = merge_rows(existing, rows)
        added = len(merged) - len(existing)
        path.parent.mkdir(parents=True, exist_ok=True)
        merged.to_csv(path, index=False)
        return {"ok": True, "added": int(added), "latest": str(merged["date"].max())}
    except Exception as e:                                # noqa: BLE001 — a COT refresh must never break a scan
        return {"ok": False, "reason": f"{type(e).__name__}: {e}", "added": 0}
