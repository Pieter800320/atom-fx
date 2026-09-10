"""
ATOM FX — EXTEND layer tests.

Two kinds of check:
  1. Potential-engine sequential logic on CRAFTED inputs (arch §10) — the three
     documented scenarios plus the no-direction nucleus. No frozen engine needed.
  2. Shape / invariant checks for csm_delta, currency_flow, breadth, spark,
     macro_regime and the recommendation seed, run on the deterministic synthetic
     OHLCV fixture (reused from the Rule #1 probe).

These test EXTEND behaviour only. The Rule #1 guard (test_rule1_frozen.py) proves
the frozen keys are unchanged; this file never asserts on frozen numbers.

Run:  python -m tests.test_extend      (or: pytest tests/test_extend.py)
"""
from scanner.extend import potential as pot
from scanner.extend import potential_config as cfg
from scanner.extend import csm_delta, breadth, spark, macro_regime, recommendation
from scanner.extend import state_alerts
from scanner.extend import conviction
from scanner.extend import bb_touch
from scanner.extend import rotation, market_pulse
from scanner import scan_h1
from scanner import csm
import pandas as pd


# ── helpers ─────────────────────────────────────────────────────────────────────
def _ctx(regime="Risk-On", conf="High", csm_delta_h4=None, breadth_h4=None,
         setup_ranks=None, reset=None, atr_pct=None):
    return {
        "regime_h4": {"regime": regime, "confidence": conf},
        "csm_delta": {"h4": csm_delta_h4 or {}},
        "breadth":   {"h4": breadth_h4 or {}},
        "setup_ranks": setup_ranks or {},
        "reset": reset or {},
        "atr_pct": atr_pct or {},
    }


def _block(d1="bull", cmp=70, struct=None, cont=80):
    return {
        "pills": {"d1": d1},
        "mom": {"cmp": cmp},
        "structure": {"h4": struct or {"direction": "bull", "event": "BOS"}},
        "cont": cont,
    }


# ── 1. Potential sequential logic (crafted) ──────────────────────────────────────
def test_all_pass_level6_tradeable():
    ctx = _ctx(
        csm_delta_h4={"EUR": 8.0, "USD": -6.0},
        breadth_h4={"EUR": {"dir": "strong", "pct": 1.0}, "USD": {"dir": "weak", "pct": 1.0}},
        setup_ranks={"EURUSD": 7.3},
    )
    r = pot.compute_pair_potential("EURUSD", _block(), ctx)
    assert r["level"] == 6, r
    assert r["state"] == "tradeable"
    assert r["blocked_at"] is None
    assert all(r["factors"].values())
    assert r["direction"] == "bull"


def test_aplus_requires_high_rank():
    ctx = _ctx(
        csm_delta_h4={"EUR": 8.0, "USD": -6.0},
        breadth_h4={"EUR": {"dir": "strong", "pct": 1.0}, "USD": {"dir": "weak", "pct": 1.0}},
        setup_ranks={"EURUSD": 9.0},   # >= APLUS_RANK 8.5
    )
    r = pot.compute_pair_potential("EURUSD", _block(), ctx)
    assert r["level"] == 6 and r["state"] == "aplus", r


def test_structure_fail_level4_watch():
    ctx = _ctx(
        csm_delta_h4={"AUD": 6.0, "JPY": -5.0},
        breadth_h4={"AUD": {"dir": "strong", "pct": 0.75}, "JPY": {"dir": "weak", "pct": 1.0}},
        setup_ranks={"AUDJPY": 8.0},
    )
    block = _block(cmp=65, struct={"direction": "neutral", "event": "none"})
    r = pot.compute_pair_potential("AUDJPY", block, ctx)
    assert r["level"] == 4, r
    assert r["state"] == "watch"
    assert r["blocked_at"] == "structure"
    assert r["factors"]["momentum"] is True
    assert r["factors"]["structure"] is False
    assert r["factors"]["entry"] is False


def test_regime_fail_level0_low_despite_good_momentum():
    # Ranging regime => REGIME factor can never pass, even with good everything else.
    ctx = _ctx(
        regime="Ranging", conf="Low",
        csm_delta_h4={"USD": 9.0, "CHF": -9.0},
        breadth_h4={"USD": {"dir": "strong", "pct": 1.0}, "CHF": {"dir": "weak", "pct": 1.0}},
    )
    block = _block(d1="bull", cmp=90, struct={"direction": "bull", "event": "BOS"}, cont=95)
    r = pot.compute_pair_potential("USDCHF", block, ctx)
    assert r["level"] == 0, r
    assert r["state"] == "low"
    assert r["blocked_at"] == "regime"
    assert not any(r["factors"].values())


def test_no_direction_nucleus():
    r = pot.compute_pair_potential("USDJPY", _block(d1="neutral"), _ctx())
    assert r["direction"] == "none"
    assert r["level"] == 0 and r["state"] == "low"
    assert r["blocked_at"] is None
    assert not any(r["factors"].values())


def test_entry_blocks_at_level5_when_cont_low():
    ctx = _ctx(
        csm_delta_h4={"NZD": 6.0, "USD": -6.0},
        breadth_h4={"NZD": {"dir": "strong", "pct": 1.0}, "USD": {"dir": "weak", "pct": 1.0}},
        setup_ranks={"NZDUSD": 5.7},
    )
    block = _block(d1="bull", cmp=64, struct={"direction": "bull", "event": "none"}, cont=50)
    r = pot.compute_pair_potential("NZDUSD", block, ctx)
    assert r["level"] == 5 and r["blocked_at"] == "entry" and r["state"] == "watch", r


def test_bear_thesis_flow_and_breadth_orientation():
    # bear USDJPY: needs USD weak-flow vs JPY, and (USD weak breadth OR JPY strong breadth)
    ctx = _ctx(
        csm_delta_h4={"USD": -6.0, "JPY": 2.0},   # spread -8 <= -FLOW_MIN => pass bear
        breadth_h4={"USD": {"dir": "weak", "pct": 1.0}, "JPY": {"dir": "strong", "pct": 1.0}},
    )
    block = _block(d1="bear", cmp=35, struct={"direction": "bear", "event": "BOS"}, cont=75)
    r = pot.compute_pair_potential("USDJPY", block, ctx)
    assert r["direction"] == "bear"
    assert r["factors"]["flow"] and r["factors"]["breadth"] and r["factors"]["momentum"]
    assert r["level"] == 6, r


def test_score_clamped_and_quality_bounded():
    ctx = _ctx(
        csm_delta_h4={"EUR": 8.0, "USD": -6.0},
        breadth_h4={"EUR": {"dir": "strong", "pct": 1.0}, "USD": {"dir": "weak", "pct": 1.0}},
        setup_ranks={"EURUSD": 10.0},
    )
    r = pot.compute_pair_potential("EURUSD", _block(), ctx)
    assert 0 <= r["score"] <= 100
    assert -cfg.QUALITY_SPAN <= r["quality"] <= cfg.QUALITY_SPAN


# ── 2. Shape / invariant checks on the synthetic fixture ──────────────────────────
def _fixture():
    from tests.frozen_probe import build_fixture
    from scanner import csm as _csm
    ohlcv = build_fixture()
    csm_now = _csm.compute_csm(ohlcv)
    return ohlcv, csm_now


# 2026-09-10 — STRENGTH_PAIRS gained EUR/JPY and GBP/JPY (csm.py, Pieter's sign-off):
# GBP 4->5, JPY 4->6, EUR 3->4. Others unchanged.
EXPECTED_APPEARANCES = {"USD": 7, "AUD": 5, "GBP": 5, "JPY": 6,
                        "EUR": 4, "CHF": 3, "CAD": 3, "NZD": 3}


def test_csm_delta_shape():
    ohlcv, csm_now = _fixture()
    d = csm_delta.compute_csm_delta(ohlcv, csm_now)
    for tf in ("d1", "h4", "h1"):
        assert set(d[tf]) == set(EXPECTED_APPEARANCES), tf
        for v in d[tf].values():
            assert isinstance(v, float)


def test_slice_ohlcv_bar_offset_correct():
    """Regression for the audit's #off-by-one risk: _slice_ohlcv must drop exactly n
    bars from the TAIL, and a zero drop must be a true no-op — verified against plain
    pandas slicing, independent of the function under test."""
    from scanner.extend.csm_delta import _slice_ohlcv
    ohlcv, _ = _fixture()
    sample = next(iter(ohlcv.values()))
    d1_len, h4_len = len(sample["d1"]), len(sample["h4"])

    sliced = _slice_ohlcv(ohlcv, {"d1": 0, "h4": 0})
    out = next(iter(sliced.values()))
    assert len(out["d1"]) == d1_len and out["d1"].equals(sample["d1"])
    assert len(out["h4"]) == h4_len and out["h4"].equals(sample["h4"])

    for n in (1, 6, 24):
        sliced_n = _slice_ohlcv(ohlcv, {"h4": n})
        out_n = next(iter(sliced_n.values()))
        assert len(out_n["h4"]) == h4_len - n
        assert out_n["h4"].equals(sample["h4"].iloc[: h4_len - n])


def test_csm_delta_numeric_correctness():
    """Independently re-slices with plain pandas (not _slice_ohlcv) and recomputes
    "past" CSM via the same frozen csm.compute_csm_<tf> the module calls, then
    asserts compute_csm_delta's output matches now-minus-past exactly — a sign flip
    or wrong offset in the module's own slicing would fail this, unlike the old
    shape-only check."""
    ohlcv, csm_now = _fixture()
    d = csm_delta.compute_csm_delta(ohlcv, csm_now)

    for tf, fn in (("d1", csm.compute_csm_d1), ("h4", csm.compute_csm_h4), ("h1", csm.compute_csm_h1)):
        drops = cfg.PAST_SLICE[tf]
        past_ohlcv = {
            key: {t: (df.iloc[: len(df) - drops[t]] if t in drops and df is not None else df)
                  for t, df in tfs.items()}
            for key, tfs in ohlcv.items()
        }
        csm_past = fn(past_ohlcv)
        for c in EXPECTED_APPEARANCES:
            expected = round(csm_now[tf].get(c, 0.0) - csm_past.get(c, 0.0), cfg.CSM_DELTA_DP)
            assert d[tf][c] == expected, (tf, c, d[tf][c], expected)


def test_currency_flow_shape():
    ohlcv, csm_now = _fixture()
    d = csm_delta.compute_csm_delta(ohlcv, csm_now)
    flow = csm_delta.compute_currency_flow(csm_now, d)
    for k in ("leader", "laggard", "absolute_leader", "absolute_laggard",
              "leader_delta", "laggard_delta", "driver_spread", "tf"):
        assert k in flow
    assert flow["tf"] == "h4"
    # driver_spread must equal leader_delta - laggard_delta (within rounding)
    assert abs(flow["driver_spread"] - (flow["leader_delta"] - flow["laggard_delta"])) < 0.05


def test_breadth_totals_match_frozen_appearances():
    ohlcv, _ = _fixture()
    b = breadth.compute_breadth(ohlcv)
    for ccy, exp in EXPECTED_APPEARANCES.items():
        assert b["h4"][ccy]["total"] == exp, (ccy, b["h4"][ccy])
        cell = b["h4"][ccy]
        assert 0.0 <= cell["pct"] <= 1.0
        assert cell["band"] in ("strong", "moderate", "weak")
        assert cell["dir"] in ("strong", "weak", "flat")


def test_breadth_contributions_match_csm_raw():
    """Regression for the audit's #maintenance-fragility finding: breadth.py hand-
    duplicates csm.py's internal per-pair accumulation (necessary — csm.py doesn't
    expose it), with nothing previously cross-checking the two stay in sync. Compares
    breadth._contributions directly against csm.py's own _raw_<tf> functions (added
    this session for CSM dispersion) — any future change to either side's accumulation
    formula that desyncs them now fails here instead of silently drifting."""
    from scanner.extend.breadth import _contributions
    ohlcv, _ = _fixture()
    raw_fns = {"d1": csm._raw_d1, "h4": csm._raw_h4, "h1": csm._raw_h1}
    for tf, fn in raw_fns.items():
        expected = fn(ohlcv)
        actual = _contributions(ohlcv, tf)
        for c in EXPECTED_APPEARANCES:
            assert actual.get(c, []) == expected.get(c, []), (tf, c, actual.get(c), expected.get(c))


def _synthetic_d1(closes, highs=None, lows=None):
    closes = pd.Series(closes, dtype=float)
    highs = pd.Series(highs, dtype=float) if highs is not None else closes + 0.0005
    lows = pd.Series(lows, dtype=float) if lows is not None else closes - 0.0005
    return pd.DataFrame({"high": highs, "low": lows, "close": closes})


_TIGHT_D1 = [1.1000, 1.1005, 1.0995, 1.1003, 1.0997, 1.1002, 1.0998, 1.1004, 1.0996,
             1.1001, 1.0999, 1.1002, 1.0998, 1.1003, 1.0997, 1.1001, 1.0999]


def test_bb_touch_none_when_calm():
    # Small explicit wicks (+-0.0001) that stay well inside this series' own ~0.0005-wide
    # 2-sigma band (verified: sma~1.1000, band ~1.09950-1.10050).
    closes = pd.Series(_TIGHT_D1, dtype=float)
    df = pd.DataFrame({"high": closes + 0.0001, "low": closes - 0.0001, "close": closes})
    r = bb_touch.compute_bb_d1(df)
    assert r is not None and r["touching"] == "none"


def test_bb_touch_detects_upper_wick():
    df = _synthetic_d1(_TIGHT_D1)
    # Blow the LAST bar's high far above any plausible 2-sigma band on this tight series --
    # a wick touch, not a close beyond the band (Pieter: "a candle wick touch is fine").
    df.loc[df.index[-1], "high"] = 1.20
    assert bb_touch.compute_bb_d1(df)["touching"] == "upper"


def test_bb_touch_detects_lower_wick():
    df = _synthetic_d1(_TIGHT_D1)
    df.loc[df.index[-1], "low"] = 1.00
    assert bb_touch.compute_bb_d1(df)["touching"] == "lower"


def test_bb_touch_width_trend_expanding():
    # Flat for the lookback window, then a volatility burst in the most recent bars.
    tight = _TIGHT_D1[:12]
    burst = [1.1050, 1.0950, 1.1080, 1.0920, 1.1100]
    r = bb_touch.compute_bb_d1(_synthetic_d1(tight + burst))
    assert r["width_trend"] == "expanding"


def test_bb_touch_none_when_insufficient_history():
    assert bb_touch.compute_bb_d1(_synthetic_d1([1.1000] * 5)) is None


def test_bb_touch_alert_edge_triggered():
    out = {"pairs": {"EURUSD": {
        "bb_d1": {"touching": "upper", "width_trend": "flat"},
        "adx": 25.0, "reset_score": 70,
        "pills": {"d1": "bear", "h4": "bear", "h1": "bear"},
    }}}
    prev_none = {"pairs": {"EURUSD": {"bb_d1": {"touching": "none"}}}}
    alerts = state_alerts._bb_touch_alerts(out, prev_none)
    assert len(alerts) == 1 and alerts[0]["type"] == "bb_touch"
    assert alerts[0]["direction"] == "bear"
    assert alerts[0]["deeplink"] == "atomfx://pair/EURUSD"

    # Already touching upper last scan too -> no repeat fire.
    prev_same = {"pairs": {"EURUSD": {"bb_d1": {"touching": "upper"}}}}
    assert state_alerts._bb_touch_alerts(out, prev_same) == []


# ── %B / Board %B (2026-09-10) ────────────────────────────────────────────────────
def test_percent_b_present_after_min_history():
    r = bb_touch.compute_bb_d1(_synthetic_d1(_TIGHT_D1))
    assert r is not None
    assert len(r["pctb"]) == len(_TIGHT_D1) - bb_touch.BB_PERIOD + 1  # 17-12+1=6
    assert r["pctb_sma"] == []  # not enough pctb points yet for a 12-period smoothing


def test_percent_b_numeric_correctness():
    df = _synthetic_d1(_TIGHT_D1)
    closes = df["close"]
    sma = closes.rolling(bb_touch.BB_PERIOD).mean()
    std = closes.rolling(bb_touch.BB_PERIOD).std()
    upper = sma + bb_touch.BB_SIGMA * std
    lower = sma - bb_touch.BB_SIGMA * std
    expected = [round(v, 2) for v in ((closes - lower) / (upper - lower) * 100).dropna().tail(bb_touch.PCTB_LINE_BARS)]
    r = bb_touch.compute_bb_d1(df)
    assert r["pctb"] == expected


def test_percent_b_not_clamped_outside_bands():
    # A long flat run, then a final bar closing far outside the just-built band -- %B for that
    # bar should read past 0/100, not clamped (clamping is the chart's job, not the backend's).
    closes = [1.1000] * 30 + [1.3000]
    r = bb_touch.compute_bb_d1(_synthetic_d1(closes))
    assert r["pctb"][-1] > 100


def test_percent_b_sma_is_12_period_rolling_mean():
    closes = [1.1000 + 0.0001 * (i % 5) for i in range(40)]
    df = _synthetic_d1(closes)
    c = df["close"]
    sma = c.rolling(bb_touch.BB_PERIOD).mean()
    std = c.rolling(bb_touch.BB_PERIOD).std()
    upper = sma + bb_touch.BB_SIGMA * std
    lower = sma - bb_touch.BB_SIGMA * std
    pctb = (c - lower) / (upper - lower) * 100
    expected_sma = [round(v, 2) for v in pctb.rolling(bb_touch.PCTB_SIGNAL_PERIOD).mean().dropna().tail(bb_touch.PCTB_LINE_BARS)]
    r = bb_touch.compute_bb_d1(df)
    assert r["pctb_sma"] == expected_sma


def test_compute_board_percent_b_pointwise_mean():
    pairs_out = {
        "EURUSD": {"bb_d1": {"pctb": [10.0, 20.0, 30.0], "pctb_sma": [15.0, 25.0]}},
        "GBPUSD": {"bb_d1": {"pctb": [50.0, 60.0], "pctb_sma": [55.0, 65.0, 75.0]}},
        "USDJPY": {"bb_d1": None},  # no read yet -- excluded
    }
    board = bb_touch.compute_board_percent_b(pairs_out)
    # shortest common pctb length is 2 -> both right-aligned to their last 2 points
    assert board["line"] == [35.0, 45.0]
    assert board["signal"] == [40.0, 50.0]


def test_compute_board_percent_b_empty_when_no_pairs_ready():
    assert bb_touch.compute_board_percent_b({"EURUSD": {"bb_d1": None}}) == {"line": [], "signal": [], "dates": []}


def test_d1_ny_close_boundary_at_17_ny_edt():
    # 2026-09-10 is EDT (UTC-4): 17:00 NY == 21:00 UTC. The H1 bar starting 20:00 UTC (16:00-17:00
    # EDT, before the boundary) belongs to the SESSION LABELED THE PREVIOUS calendar date (it
    # covers Sep-09 17:00 EDT -> Sep-10 17:00 EDT); the bar starting 21:00 UTC (right at 17:00 EDT)
    # starts the next session, labeled Sep-10.
    h1_df = pd.DataFrame({
        "datetime": ["2026-09-10 20:00:00", "2026-09-10 21:00:00"],
        "open": [1.1, 1.2], "high": [1.1, 1.2], "low": [1.1, 1.2], "close": [1.1, 1.2],
    })
    d1 = bb_touch._d1_ny_close(h1_df)
    assert d1["date"].astype(str).tolist() == ["2026-09-09", "2026-09-10"]


def test_attach_bb_d1_uses_ny_close_dates_when_raw_ohlcv_given():
    import numpy as np

    rng = pd.date_range("2026-06-01", periods=40 * 24, freq="h", tz="UTC")
    rng = rng[~rng.dayofweek.isin([5, 6])]
    closes = 1.1000 + np.cumsum(np.random.default_rng(1).normal(0, 0.0005, len(rng)))
    h1_df = pd.DataFrame({
        "datetime": rng.strftime("%Y-%m-%d %H:%M:%S"),
        "open": closes, "high": closes + 0.0003, "low": closes - 0.0003, "close": closes,
    })
    pairs_out = {"EURUSD": {}}
    bb_touch.attach_bb_d1(pairs_out, {}, {"EURUSD": h1_df})
    r = pairs_out["EURUSD"]["bb_d1"]
    assert len(r["pctb_dates"]) == len(r["pctb"])
    # the last pctb_date must be _d1_ny_close's own last (most recent) session date, not the
    # frozen UTC-midnight aggregator's
    d1_ny = bb_touch._d1_ny_close(h1_df)
    assert r["pctb_dates"][-1] == d1_ny["date"].astype(str).tolist()[-1]


def test_attach_bb_d1_falls_back_to_frozen_d1_without_raw_ohlcv():
    pairs_out = {"EURUSD": {}}
    ohlcv = {"EURUSD": {"d1": _synthetic_d1(_TIGHT_D1)}}
    bb_touch.attach_bb_d1(pairs_out, ohlcv, raw_ohlcv=None)
    r = pairs_out["EURUSD"]["bb_d1"]
    assert r is not None
    assert r["pctb_dates"] == []


def test_compute_bb_d1_dates_length_mismatch_falls_back_to_empty():
    df = _synthetic_d1(_TIGHT_D1)
    r = bb_touch.compute_bb_d1(df, dates=["2026-01-01"])  # deliberately wrong length
    assert r["pctb_dates"] == []


def _synthetic_h1(seed: int, base: float, n: int = 1300) -> pd.DataFrame:
    import numpy as np
    rng = np.random.default_rng(seed)
    steps = rng.normal(0, base * 0.0015, n).cumsum()
    close = np.abs(base + steps) + base * 0.05
    openp = np.empty(n); openp[0] = close[0]; openp[1:] = close[:-1]
    wick = np.abs(rng.normal(0, base * 0.0008, n))
    high = np.maximum(openp, close) + wick
    low = np.minimum(openp, close) - wick
    ts = pd.date_range("2024-01-01", periods=n, freq="1h", tz="UTC")
    return pd.DataFrame({
        "datetime": ts.strftime("%Y-%m-%d %H:%M:%S"),
        "open": openp, "high": high, "low": low, "close": close,
    })


def test_compute_currency_percent_b_base_quote_correctly_signed():
    # EURUSD: EUR is base, USD is quote. USDCAD: USD is base, CAD is quote. USD therefore
    # gets TWO contributions here in different roles — a mirrored one from EURUSD, a direct
    # one from USDCAD — the exact mixing this function exists to get right.
    raw_ohlcv = {
        "EURUSD": _synthetic_h1(seed=1, base=1.2),
        "USDCAD": _synthetic_h1(seed=2, base=1.35),
    }
    result = bb_touch.compute_currency_percent_b(raw_ohlcv)

    eurusd_bb = bb_touch.compute_bb_d1(
        bb_touch._d1_ny_close(raw_ohlcv["EURUSD"]),
        bb_touch._d1_ny_close(raw_ohlcv["EURUSD"])["date"].astype(str).tolist(),
    )
    usdcad_bb = bb_touch.compute_bb_d1(
        bb_touch._d1_ny_close(raw_ohlcv["USDCAD"]),
        bb_touch._d1_ny_close(raw_ohlcv["USDCAD"])["date"].astype(str).tolist(),
    )

    # EUR: base-only, one contributor -> exactly EURUSD's own pctb.
    assert result["EUR"]["line"] == eurusd_bb["pctb"]
    # CAD: quote-only, one contributor -> exactly the 100-mirror of USDCAD's own pctb.
    assert result["CAD"]["line"] == [round(100 - v, 2) for v in usdcad_bb["pctb"]]
    # USD: quote in EURUSD (mirrored) + base in USDCAD (direct) -> pointwise mean of both,
    # right-aligned to the shorter of the two.
    mirrored_eurusd = [round(100 - v, 2) for v in eurusd_bb["pctb"]]
    n = min(len(mirrored_eurusd), len(usdcad_bb["pctb"]))
    expected_usd = [
        round((a + b) / 2, 2)
        for a, b in zip(mirrored_eurusd[-n:], usdcad_bb["pctb"][-n:])
    ]
    assert result["USD"]["line"] == expected_usd
    # a currency with zero contributing pairs in this restricted raw_ohlcv gets an empty read.
    assert result["JPY"] == {"line": [], "signal": [], "dates": []}


def test_compute_currency_percent_b_shape_all_currencies():
    from scanner.csm import STRENGTH_PAIRS
    raw_ohlcv = {
        pair.replace("/", ""): _synthetic_h1(seed=100 + i, base=100.0 if "JPY" in pair else 1.2)
        for i, pair in enumerate(STRENGTH_PAIRS)
    }
    result = bb_touch.compute_currency_percent_b(raw_ohlcv)
    assert set(result) == set(bb_touch.CURRENCIES)
    for currency, block in result.items():
        # dates is sized to match "line" specifically (the raw %B series); "signal" (the
        # smoothed series) is naturally shorter by its own rolling window — same asymmetry
        # compute_board_percent_b's own line/signal already have, nothing new here.
        assert len(block["dates"]) == len(block["line"]), currency
        assert len(block["line"]) > 0 and len(block["signal"]) > 0, currency


def test_spark_shape():
    ohlcv, _ = _fixture()
    s = spark.compute_spark(ohlcv)
    from scanner.config import PAIRS
    assert set(s) == {p.replace("/", "") for p in PAIRS}
    for key, tfs in s.items():
        for tf in ("d1", "h4", "h1"):
            assert 0 < len(tfs[tf]) <= cfg.SPARK_BARS, (key, tf, len(tfs[tf]))
            assert all(isinstance(x, float) for x in tfs[tf])


# ── 3. Macro regime + recommendation seed ─────────────────────────────────────────
# 2026-09-06 — `classify_macro_regime` now picks the regime NAME off a W1 (5-session) axis
# read, not the 1-day one; `ma`/`ma_w1` below plays that role in these tests. See
# `macro_regime.py`'s own module doc comment for why (the handbook's own "the path matters,
# not the level" discipline — a single day's wobble shouldn't swap the whole regime name).
def test_macro_regime_risk_on():
    ma_w1 = {
        "spx":    {"direction": "up",   "delta_pct": 3.5},
        "vix":    {"direction": "down", "delta_pct": -18.0},
        "copper": {"direction": "up",   "delta_pct": 2.5},
        "dxy":    {"direction": "flat", "delta_pct": 0.0},
        "us10y":  {"direction": "up",   "delta_bp": 5.0},
        "us3m":   {"direction": "flat", "delta_bp": 0.0},
        "wti":    {"direction": "flat", "delta_pct": 0.0},
        "gold":   {"direction": "up",   "delta_pct": 4.5},
        "curve":  {"direction": "up",   "delta_bp": 16.0},
        "btc":    {"direction": "up",   "delta_pct": 16.0},
    }
    mr = macro_regime.classify_macro_regime(ma_w1, updated="2026-08-28T00:00:00+00:00")
    assert mr["primary"]["code"] in macro_regime.REGIME_LIB
    assert mr["primary"]["code"] == "A", mr["primary"]          # growth-positive risk-on
    assert mr["primary"]["confidence"] in ("Low", "Medium", "High")
    assert set(mr["currency_bias"]) == {"strong", "weak"}
    assert len(mr["evidence"]) == 5
    assert mr["gold_overlay"] in ("defensive", "diversification", "neutral")
    # No daily dict passed — every axis falls back to comparing W1 against itself, so nothing
    # can read as diverging; "quiet" or "confirming" only.
    assert all(e["confirms_today"] in ("confirming", "quiet") for e in mr["evidence"])


def test_macro_regime_empty():
    assert macro_regime.classify_macro_regime({}) == {}


def test_macro_regime_confirms_today():
    """The fast 1-day read tags each axis confirming/diverging/quiet against the W1 trend —
    a property of the axis, not of whichever regime happens to win (module doc comment)."""
    ma_w1 = {
        "spx": {"direction": "up", "delta_pct": 3.5}, "vix": {"direction": "down", "delta_pct": -18.0},
        "copper": {"direction": "flat"}, "btc": {"direction": "flat"},
        "dxy": {"direction": "flat"}, "us10y": {"direction": "flat"}, "us3m": {"direction": "flat"},
        "wti": {"direction": "flat"}, "gold": {"direction": "flat"}, "curve": {"direction": "flat"},
    }
    # Today: SPX and VIX both fight the weekly risk-on trend (SPX down, VIX up) — diverging.
    ma_daily_diverge = {**ma_w1, "spx": {"direction": "down", "delta_pct": -0.5}, "vix": {"direction": "up", "delta_pct": 6.0}}
    mr = macro_regime.classify_macro_regime(ma_w1, ma_daily_diverge, updated="2026-08-28T00:00:00+00:00")
    risk_evidence = next(e for e in mr["evidence"] if e["axis"] == "risk")
    assert risk_evidence["confirms_today"] == "diverging"

    # Today: no daily move at all on the risk instruments — quiet, not confirming/diverging.
    ma_daily_quiet = {**ma_w1, "spx": {"direction": "flat"}, "vix": {"direction": "flat"}}
    mr2 = macro_regime.classify_macro_regime(ma_w1, ma_daily_quiet, updated="2026-08-28T00:00:00+00:00")
    assert next(e for e in mr2["evidence"] if e["axis"] == "risk")["confirms_today"] == "quiet"

    # Today: SPX up / VIX down again, same direction as the W1 trend — confirming.
    ma_daily_confirm = {**ma_w1, "spx": {"direction": "up", "delta_pct": 0.6}, "vix": {"direction": "down", "delta_pct": -6.0}}
    mr3 = macro_regime.classify_macro_regime(ma_w1, ma_daily_confirm, updated="2026-08-28T00:00:00+00:00")
    assert next(e for e in mr3["evidence"] if e["axis"] == "risk")["confirms_today"] == "confirming"


def test_macro_regime_hysteresis_sticky_on_tie():
    """A regime only flips if the new leader clears the standing code's own axis count —
    a tie (or the standing code still scoring just as well) keeps the standing regime rather
    than falling through to the alphabetical tie-break `scored.sort` would otherwise apply."""
    # Growth-positive risk-on (A: risk+commodity) and Disinflationary easing (C: rates+risk+usd)
    # can both score 2 axes on the same inputs depending on rates/usd reads — construct exactly
    # that tie and confirm the standing code (A) survives instead of falling through to C.
    ma_w1 = {
        "spx": {"direction": "up"}, "vix": {"direction": "down"},   # risk axis -> risk_on
        "copper": {"direction": "up"}, "wti": {"direction": "flat"}, # commodity axis -> up
        "us10y": {"direction": "down"}, "us3m": {"direction": "down"},  # rates axis -> down
        "dxy": {"direction": "down"},                                   # usd axis -> down
        "gold": {"direction": "flat"}, "curve": {"direction": "flat"}, "btc": {"direction": "flat"},
    }
    # A scores {risk, commodity} = 2. C scores {rates, risk, usd} = 3 — not actually a tie on
    # these inputs (C legitimately wins on merit), so first confirm the fresh (no prior regime)
    # pick is C, matching the plain axis count with no standing regime to protect yet.
    fresh = macro_regime.classify_macro_regime(ma_w1, updated="2026-08-28T00:00:00+00:00")
    assert fresh["primary"]["code"] == "C"

    # Now simulate A as the STANDING regime on the exact same inputs — A's own recomputed count
    # (2) is less than C's (3), so this is a real, earned flip, not hysteresis kicking in.
    prev = {"primary": {"code": "A"}}
    flipped = macro_regime.classify_macro_regime(ma_w1, prev_regime=prev, updated="2026-08-28T00:00:00+00:00")
    assert flipped["primary"]["code"] == "C"

    # Construct a genuine tie: drop USD support so C falls to 2 axes ({rates, risk}), matching
    # A's own 2 ({risk, commodity}) exactly. With NO standing regime, the plain sort's
    # alphabetical tie-break picks A (it sorts before C in REGIME_LIB). The actual hysteresis
    # test: make C the STANDING regime on this same tied input — the naive tie-break would
    # still swap it for A, but the sticky rule should keep C, since A only ties C, it doesn't
    # beat it.
    ma_tie = {**ma_w1, "dxy": {"direction": "flat"}}
    no_prev = macro_regime.classify_macro_regime(ma_tie, updated="2026-08-28T00:00:00+00:00")
    assert no_prev["primary"]["code"] == "A"  # confirms the tie — alphabetical tie-break picks A

    prev_c = {"primary": {"code": "C"}}
    sticky = macro_regime.classify_macro_regime(ma_tie, prev_regime=prev_c, updated="2026-08-28T00:00:00+00:00")
    assert sticky["primary"]["code"] == "C"  # tie keeps the standing regime, not the tie-break's A

    # 2026-09-10 (Pieter's ask) — a genuine tie on distinct axes is the ambiguous case, not a
    # weaker version of a clear win: both `no_prev` (A, 2 axes) and `sticky` (C, 2 axes) are
    # tied against their own runner-up here, so BOTH should read Low confidence even though
    # 2 axes alone would normally mean Medium. Same underlying tie, whichever code wins it.
    assert no_prev["primary"]["confidence"] == "Low"
    assert sticky["primary"]["confidence"] == "Low"
    assert fresh["primary"]["confidence"] == "High"  # NOT tied (3 vs A's 2) — margin=1, unaffected


def test_macro_regime_news_corroboration():
    """2026-09-10 (Pieter's ask) — scan_news.py's own tag_theme() axis tags, fed back in as
    `news_themes`, should corroborate or flag a regime whose defining axes have no recent
    matching headline at all. Reuses test_macro_regime_risk_on's own inputs (regime A,
    confirming axes {risk, commodity})."""
    ma_w1 = {
        "spx":    {"direction": "up",   "delta_pct": 3.5},
        "vix":    {"direction": "down", "delta_pct": -18.0},
        "copper": {"direction": "up",   "delta_pct": 2.5},
        "dxy":    {"direction": "flat", "delta_pct": 0.0},
        "us10y":  {"direction": "up",   "delta_bp": 5.0},
        "us3m":   {"direction": "flat", "delta_bp": 0.0},
        "wti":    {"direction": "flat", "delta_pct": 0.0},
        "gold":   {"direction": "up",   "delta_pct": 4.5},
        "curve":  {"direction": "up",   "delta_bp": 16.0},
        "btc":    {"direction": "up",   "delta_pct": 16.0},
    }
    # No news_themes at all (e.g. a scan_news.py outage) -> fails quiet, doesn't assert either way.
    no_news = macro_regime.classify_macro_regime(ma_w1, updated="2026-08-28T00:00:00+00:00")
    assert no_news["news_corroboration"] == "unknown"

    # Recent headlines exist, but none touch this regime's own supporting axes (risk, commodity)
    # -> price-only, a materially weaker claim than one the headlines are actively talking about.
    unrelated = macro_regime.classify_macro_regime(ma_w1, updated="2026-08-28T00:00:00+00:00", news_themes=["rates", "safe_haven"])
    assert unrelated["news_corroboration"] == "price_only"

    # A headline tagged onto one of the actually-supporting axes -> confirmed.
    confirmed = macro_regime.classify_macro_regime(ma_w1, updated="2026-08-28T00:00:00+00:00", news_themes=["rates", "commodity"])
    assert confirmed["news_corroboration"] == "confirmed"


def test_build_macro_assets_w1_basic():
    macro = {
        "spx":   {"close": 5160.0, "prev_close": 5100.0, "w1_close": 5000.0, "label": "S&P 500"},  # +3.2% W1 -> up
        "vix":   {"close": 18.0,   "prev_close": 19.0,   "w1_close": 22.0,   "label": "VIX"},        # -18.2% W1 -> down
        "dxy":   {"close": 104.0,  "prev_close": 103.9,  "w1_close": 104.05, "label": "DXY"},         # ~-0.05% -> flat
        "us10y": {"close": 4.31,   "prev_close": 4.28,   "w1_close": 4.10,   "label": "US 10Y"},      # +21bp -> up
        "us3m":  {"close": 5.30,   "prev_close": 5.29,   "w1_close": 5.30,   "label": "US 3M"},       # 0bp -> flat
    }
    out = macro_regime.build_macro_assets_w1(macro)
    assert out["spx"]["direction"] == "up"
    assert out["vix"]["direction"] == "down"
    assert out["dxy"]["direction"] == "flat"
    assert out["us10y"]["direction"] == "up"
    assert out["us3m"]["direction"] == "flat"
    # curve (10Y-3M) computed, not fetched — same pattern as build_macro_assets' own curve entry.
    assert "curve" in out


def test_recommendation_seed_deterministic():
    signals = {
        "regime_h4": {"regime": "Risk-On", "confidence": "High"},
        "potential": {"EURUSD": {"state": "tradeable", "level": 6, "direction": "bull"}},
        "ranked": {"top": [{"pair": "EURUSD", "direction": "bull", "score": 7.3}]},
        "gold_signal": {"h4_confidence": "Medium"},
        "calendar": {"events": [{"name": "PCE", "iso": "2099-01-01T00:00:00+00:00"}]},
    }
    seed = recommendation.build_seed(signals)
    assert seed["bias"] == "risk_on"
    assert seed["action"] == "trade"
    assert seed["primary_pair"] == "EURUSD" and seed["direction"] == "bull"
    assert seed["confidence"] == "Medium"        # min(Medium gold, High regime)
    assert seed["next_catalyst"]["event"] == "PCE"
    # full object with deterministic framing (no model)
    rec = recommendation.build_recommendation(signals, use_model=False)
    assert rec["primary_pair"] == "EURUSD"
    assert rec["bias"] == "risk_on" and rec["action"] == "trade"
    assert rec["headline"] and rec["rationale"] and rec["invalidation"]


def test_recommendation_stand_aside_when_no_setups():
    signals = {
        "regime_h4": {"regime": "Ranging", "confidence": "Low"},
        "potential": {"EURUSD": {"state": "low", "level": 1, "direction": "none"}},
        "ranked": {"top": []},
        "gold_signal": {},
        "calendar": {"events": []},
    }
    seed = recommendation.build_seed(signals)
    assert seed["action"] == "stand_aside"
    assert seed["bias"] == "mixed"
    # no ranked top and highest-level potential pair chosen
    assert seed["primary_pair"] == "EURUSD"


# ── 3. State-transition alerts (Signals Roadmap §2) ───────────────────────────────
def test_state_alerts_no_prev_never_fires():
    out = {"pairs": {"EURUSD": {"cont": 62, "pills": {"d1": "bull"}}}}
    assert state_alerts.compute_state_alerts(out, {}) == []
    assert state_alerts.compute_state_alerts(out, None) == []


def test_state_alerts_potential_state_fires_on_transition():
    # 2026-09-05 — trigger moved from Level 6/A+ (`potential.state`) to crossing `cont >= 45`
    # (rank.py's own qualifying threshold), matching the Simplification Rework's retirement of
    # the six-factor gate. Same alert `type` ("potential_state"), new underlying condition.
    prev = {"pairs": {"EURUSD": {"cont": 30, "pills": {"d1": "bull"}}}}
    out = {"pairs": {"EURUSD": {"cont": 62, "pills": {"d1": "bull"}}}}
    alerts = state_alerts.compute_state_alerts(out, prev)
    assert len(alerts) == 1 and alerts[0]["type"] == "potential_state"
    assert alerts[0]["direction"] == "bull"
    # no-op rerun (already qualifying both sides) fires nothing
    assert state_alerts.compute_state_alerts(out, out) == []


def test_state_alerts_structure_event_fires_on_new_event():
    prev = {"pairs": {"EURUSD": {"structure": {"h4": {"event": "none"}}}}}
    out = {"pairs": {"EURUSD": {"structure": {"h4": {"event": "BOS", "direction": "bull", "strength": 0.8}}}}}
    alerts = state_alerts.compute_state_alerts(out, prev)
    assert len(alerts) == 1 and alerts[0]["type"] == "structure_event"
    assert state_alerts.compute_state_alerts(out, out) == []


def test_state_alerts_regime_flip_fires():
    # 2026-09-09 — trigger moved from regime_h4 to regime_d1 (Pieter's ask: "the H4 Regime
    # changes too much"); classify_regime itself is untouched, only which field the alert reads.
    prev = {"regime_d1": {"regime": "Risk-Off", "confidence": "High", "stable": True}}
    out = {"regime_d1": {"regime": "Risk-On", "confidence": "Medium", "stable": False}}
    alerts = state_alerts.compute_state_alerts(out, prev)
    assert len(alerts) == 1 and alerts[0]["type"] == "regime_flip"
    assert alerts[0]["regime_flip_to"] == "Risk-On"
    # a stable regime (no flip) fires nothing
    stable_out = {"regime_d1": {"regime": "Risk-Off", "confidence": "High", "stable": True}}
    assert state_alerts.compute_state_alerts(stable_out, prev) == []


def test_state_alerts_archetype_change_fires():
    prev = {"macro_regime": {"primary": {"code": "A", "name": "Growth risk-on"}}}
    out = {"macro_regime": {"primary": {"code": "C", "name": "Disinflationary easing"}, "narrative": "x"}}
    alerts = state_alerts.compute_state_alerts(out, prev)
    assert len(alerts) == 1 and alerts[0]["type"] == "archetype_change"
    # Regime Playbook proof-of-concept, 2026-09-04 — the client keys its contextual
    # explanation off this field, so a regression here would silently break that lookup.
    assert alerts[0]["regime_code"] == "C"
    assert state_alerts.compute_state_alerts(out, out) == []


def test_state_alerts_volatility_spike_fires():
    prev = {"pairs": {"EURUSD": {"atr_pct": 70}}}
    out = {"pairs": {"EURUSD": {"atr_pct": 92}}}
    alerts = state_alerts.compute_state_alerts(out, prev)
    assert len(alerts) == 1 and alerts[0]["type"] == "volatility_spike"
    # already spiked last scan too -> no repeat fire
    assert state_alerts.compute_state_alerts(out, out) == []


def test_state_alerts_tf_alignment_fires():
    prev = {"pairs": {"EURUSD": {"pills": {"d1": "bull_strong", "h4": "bull", "h1": "bull"}}}}
    out = {"pairs": {"EURUSD": {"pills": {"d1": "bull_strong", "h4": "bull_strong", "h1": "bull_strong"}}}}
    alerts = state_alerts.compute_state_alerts(out, prev)
    assert len(alerts) == 1 and alerts[0]["type"] == "tf_alignment"
    assert alerts[0]["direction"] == "bull"
    assert state_alerts.compute_state_alerts(out, out) == []


# ── 4. Conviction score (Signals Roadmap §4) ──────────────────────────────────────
def test_conviction_cot_position_hysteresis():
    assert conviction._score_cot_position(90) == -2          # deeply crowded long
    assert conviction._score_cot_position(80, prev_score=0) == -1   # hysteresis zone, no prior lean
    assert conviction._score_cot_position(80, prev_score=-2) == -2  # hysteresis zone, prior lean sticks
    assert conviction._score_cot_position(50) == 0            # neutral zone
    assert conviction._score_cot_position(10) == +2           # deeply crowded short
    assert conviction._score_cot_position(None) == 0


def test_conviction_cot_disagg():
    assert conviction._score_cot_disagg(60, 60) == +2   # both long, full alignment
    assert conviction._score_cot_disagg(30, 30) == -2   # both short, full alignment
    assert conviction._score_cot_disagg(60, 30) == 0    # structural bull, tactical fade
    assert conviction._score_cot_disagg(30, 60) == -1   # tactical chases against structure


def test_conviction_extension_uses_reset_score_not_is_extended():
    # EUR/USD reset_score above RESET_MAX (55), D1 pill bull_strong -> counted extended.
    extended_pairs = {"EURUSD": {"reset_score": 70, "pills": {"d1": "bull_strong"}}}
    assert conviction._score_extension(extended_pairs, "EUR", 1) == -2  # 1/1 extended -> broadly extended
    # Below the threshold -> clean runway.
    clean_pairs = {"EURUSD": {"reset_score": 30, "pills": {"d1": "bull_strong"}}}
    assert conviction._score_extension(clean_pairs, "EUR", 1) == +1


def test_conviction_breadth_uses_existing_breadth_key():
    breadth_h4 = {"EUR": {"dir": "strong", "pct": 0.8, "total": 3}}
    # breadth's own direction (strong -> +1) agrees with d1_direction (+1) -> use pct band.
    assert conviction._score_breadth(breadth_h4, "EUR", 1) == +2
    # Disagreement between breadth's direction and the pill-derived d1 direction -> worst bucket.
    assert conviction._score_breadth(breadth_h4, "EUR", -1) == -1


def test_compute_conviction_shape_and_stability():
    cot_data = {
        "cot_date": "2026-08-25", "cot_stale": False,
        "currencies": {"EUR": {"available": True, "noncomm_pct": 90, "am_pct": 60, "lf_pct": 60,
                                "oi_current": 100, "oi_4w_ago": 100}},
    }
    pairs_block = {"EURUSD": {"pills": {"d1": "bull_strong"}, "reset_score": 30}}
    csm_d1 = {"EUR": 50, "USD": 50}
    breadth = {"h4": {}}

    result = conviction.compute_conviction(cot_data, pairs_block, csm_d1, breadth, prev_conviction=None)
    assert set(result["currencies"].keys()) == set(conviction.CURRENCIES)
    assert set(result["pairs"].keys()) == {p.replace("/", "") for p in conviction.PAIRS}
    assert result["cot_date"] == "2026-08-25" and result["cot_stale"] is False

    # EWMA stability: feeding the same result back as prev_conviction reproduces the same score.
    again = conviction.compute_conviction(cot_data, pairs_block, csm_d1, breadth, prev_conviction=result)
    assert again["currencies"]["EUR"]["conviction"] == result["currencies"]["EUR"]["conviction"]


def test_conviction_alerts_edge_trigger():
    # cot_available explicit on both sides — real compute_conviction output always sets
    # it; this locks the test to the full-data 80 threshold, not the degraded 40 one
    # (see test_conviction_alerts_degraded_threshold for that path).
    prev = {"currencies": {"EUR": {"conviction": 50, "cot_available": True}}}
    new = {"currencies": {"EUR": {"conviction": 85, "cot_available": True}}}
    alerts = conviction.compute_conviction_alerts(new, prev)
    assert len(alerts) == 1 and alerts[0]["type"] == "conviction_extreme"
    assert alerts[0]["direction"] == "bull"
    assert alerts[0]["cot_confirmed"] is True
    assert "technical only" not in alerts[0]["msg"]
    # already extreme last week -> no repeat fire
    assert conviction.compute_conviction_alerts(new, new) == []
    # first-ever run (no prev) never fires
    assert conviction.compute_conviction_alerts(new, None) == []
    assert conviction.compute_conviction_alerts(new, {}) == []


def test_conviction_alerts_degraded_threshold():
    """P3 audit item #1 fix — a currency without COT confirmation this scan can still
    alert once it clears the DEGRADED threshold (40), which the old fixed-80 threshold
    made structurally unreachable whenever cot_available was False; the alert says so."""
    prev = {"currencies": {"EUR": {"conviction": 20, "cot_available": False}}}
    new = {"currencies": {"EUR": {"conviction": 45, "cot_available": False}}}
    alerts = conviction.compute_conviction_alerts(new, prev)
    assert len(alerts) == 1 and alerts[0]["type"] == "conviction_extreme"
    assert alerts[0]["cot_confirmed"] is False
    assert "technical only" in alerts[0]["msg"]

    # Below the degraded threshold (40) -> no fire, even though it would clear a
    # hypothetical fixed-80 basis being irrelevant here.
    quiet = {"currencies": {"EUR": {"conviction": 35, "cot_available": False}}}
    assert conviction.compute_conviction_alerts(quiet, prev) == []

    # Already extreme last week ON ITS OWN (degraded) BASIS -> no repeat fire.
    assert conviction.compute_conviction_alerts(new, new) == []

    # Crossing from a degraded extreme into a full-data reading that does NOT itself
    # clear the full-data 80 threshold -> no fire (evaluated on the current scan's own
    # cot_available, not grandfathered off last week's degraded pass).
    recovered_but_not_extreme = {"currencies": {"EUR": {"conviction": 60, "cot_available": True}}}
    assert conviction.compute_conviction_alerts(recovered_but_not_extreme, new) == []


# ── 5. Cross-cadence key preservation regression (2026-09-04 bug) ────────────────
def test_conviction_survives_hourly_rebuild():
    # Bug precedent: "conviction" (scan_cot.py's weekly key) was missing from
    # scan_h1.py's PRESERVED_KEYS for one hour after scan_cot.py first shipped,
    # silently wiping the weekly COT data on the very next hourly scan. Every
    # non-scan_h1-owned key currently in signals.json must stay listed here.
    assert "conviction" in scan_h1.PRESERVED_KEYS
    assert "gold_signal" in scan_h1.PRESERVED_KEYS  # sanity: existing key still there too


# ── script runner ─────────────────────────────────────────────────────────────────
# ── Rotation / Pulse / Thrust (2026-09-10) ────────────────────────────────────────
def test_rotation_shape():
    ohlcv, csm_now = _fixture()
    d = csm_delta.compute_csm_delta(ohlcv, csm_now)
    r = rotation.compute_rotation(csm_now["h4"], d["h4"], None)
    assert r["tf"] == "h4"
    assert set(r["points"]) == set(EXPECTED_APPEARANCES)
    for ccy, pt in r["points"].items():
        assert 0.0 <= pt["x"] <= 100.0, (ccy, pt)
        assert pt["quadrant"] in ("leading", "weakening", "lagging", "improving")
        assert r["history"][ccy] == [[pt["x"], pt["y"]]]


def test_rotation_quadrant_boundaries():
    # x==50/y==0 land on the "strong, still gaining" side by convention (>=, not >).
    cases = [
        ((50.0, 0.0), "leading"),
        ((100.0, 5.0), "leading"),
        ((50.0, -0.01), "weakening"),
        ((80.0, -10.0), "weakening"),
        ((49.99, -0.01), "lagging"),
        ((0.0, -20.0), "lagging"),
        ((49.99, 0.0), "improving"),
        ((10.0, 30.0), "improving"),
    ]
    for (x, y), expected in cases:
        r = rotation.compute_rotation({"EUR": x}, {"EUR": y}, None)
        assert r["points"]["EUR"]["quadrant"] == expected, (x, y, r["points"]["EUR"])


def test_rotation_history_caps_and_orders():
    history = None
    for i in range(cfg.ROTATION_HISTORY_LEN + 4):
        r = rotation.compute_rotation({"EUR": float(i)}, {"EUR": 0.0}, history)
        history = r["history"]
    assert len(history["EUR"]) == cfg.ROTATION_HISTORY_LEN
    # oldest-first: the last entry must be the most recent scan appended.
    assert history["EUR"][-1] == [float(cfg.ROTATION_HISTORY_LEN + 3), 0.0]


def test_thrust_matches_manual_count():
    breadth_h4 = {
        "EUR": {"dir": "strong"}, "USD": {"dir": "strong"}, "GBP": {"dir": "strong"},
        "AUD": {"dir": "weak"}, "NZD": {"dir": "weak"},
        "CAD": {"dir": "flat"}, "CHF": {"dir": "flat"}, "JPY": {"dir": "strong"},
    }
    t = breadth.compute_thrust(breadth_h4, None)
    assert t["h4"] == 4 - 2  # 4 strong, 2 weak, 2 flat -> +2
    assert -8 <= t["h4"] <= 8
    assert t["history"] == [2]


def test_thrust_history_caps():
    history = None
    for _ in range(cfg.THRUST_HISTORY_LEN + 5):
        t = breadth.compute_thrust({"EUR": {"dir": "strong"}}, history)
        history = t["history"]
    assert len(history) == cfg.THRUST_HISTORY_LEN


def test_pulse_null_when_fewer_than_two_axes():
    p = market_pulse.compute_pulse({}, None, 55.0, {}, None)
    assert p["score"] is None
    assert p["band"] is None
    assert p["history"] == []  # a None score is never appended


def test_pulse_band_thresholds():
    breadth_h4 = {c: {"pct": 1.0} for c in ("EUR", "USD", "GBP", "AUD")}  # unanimity=100
    macro_regime = {"primary": {"distinct_axes": 5}}                     # regime_clarity=100
    p_high = market_pulse.compute_pulse(breadth_h4, macro_regime, 100.0, {}, None)
    assert p_high["score"] == 100.0
    assert p_high["band"] == "confirmed"

    macro_regime_low = {"primary": {"distinct_axes": 0}}                 # regime_clarity=0
    p_low = market_pulse.compute_pulse({c: {"pct": 0.0} for c in ("EUR", "USD")},
                                        macro_regime_low, 0.0, {}, None)
    assert p_low["score"] == 0.0
    assert p_low["band"] == "noise"


def test_pulse_history_caps():
    history = None
    for _ in range(cfg.PULSE_HISTORY_LEN + 5):
        p = market_pulse.compute_pulse({"EUR": {"pct": 1.0}}, {"primary": {"distinct_axes": 3}},
                                        None, {}, history)
        history = p["history"]
    assert len(history) == cfg.PULSE_HISTORY_LEN


def test_pulse_skips_unscoreable_scans_in_history():
    history = None
    for _ in range(3):
        p = market_pulse.compute_pulse({}, None, None, {}, history)  # 0 axes available -> None
        history = p["history"]
    assert history == []


if __name__ == "__main__":
    import traceback
    fns = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
    passed = failed = 0
    for fn in fns:
        try:
            fn()
            print(f"  PASS  {fn.__name__}")
            passed += 1
        except Exception:
            print(f"  FAIL  {fn.__name__}")
            traceback.print_exc()
            failed += 1
    print(f"\n{passed} passed, {failed} failed")
    raise SystemExit(1 if failed else 0)
