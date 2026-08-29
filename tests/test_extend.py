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


EXPECTED_APPEARANCES = {"USD": 7, "AUD": 5, "GBP": 4, "JPY": 4,
                        "EUR": 3, "CHF": 3, "CAD": 3, "NZD": 3}


def test_csm_delta_shape():
    ohlcv, csm_now = _fixture()
    d = csm_delta.compute_csm_delta(ohlcv, csm_now)
    for tf in ("d1", "h4", "h1"):
        assert set(d[tf]) == set(EXPECTED_APPEARANCES), tf
        for v in d[tf].values():
            assert isinstance(v, float)


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
def test_macro_regime_risk_on():
    ma = {
        "spx":    {"direction": "up",   "delta_pct": 0.5},
        "vix":    {"direction": "down", "delta_pct": -3.0},
        "copper": {"direction": "up",   "delta_pct": 1.5},
        "dxy":    {"direction": "flat", "delta_pct": 0.0},
        "us10y":  {"direction": "up",   "delta_bp": 2.0},
        "us3m":   {"direction": "flat", "delta_bp": 0.0},
        "wti":    {"direction": "flat", "delta_pct": 0.0},
        "gold":   {"direction": "up",   "delta_pct": 1.2},
        "curve":  {"direction": "up",   "delta_bp": 3.0},
        "btc":    {"direction": "up",   "delta_pct": 0.5},
    }
    mr = macro_regime.classify_macro_regime(ma, updated="2026-08-28T00:00:00+00:00")
    assert mr["primary"]["code"] in macro_regime.REGIME_LIB
    assert mr["primary"]["code"] == "A", mr["primary"]          # growth-positive risk-on
    assert mr["primary"]["confidence"] in ("Low", "Medium", "High")
    assert set(mr["currency_bias"]) == {"strong", "weak"}
    assert len(mr["evidence"]) == 5
    assert mr["gold_overlay"] in ("defensive", "diversification", "neutral")


def test_macro_regime_empty():
    assert macro_regime.classify_macro_regime({}) == {}


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


# ── script runner ─────────────────────────────────────────────────────────────────
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
