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
from scanner import scan_h1


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


# ── 3. State-transition alerts (Signals Roadmap §2) ───────────────────────────────
def test_state_alerts_no_prev_never_fires():
    out = {"potential": {"EURUSD": {"state": "tradeable", "direction": "bull", "setup_rank": 8.0}}}
    assert state_alerts.compute_state_alerts(out, {}) == []
    assert state_alerts.compute_state_alerts(out, None) == []


def test_state_alerts_potential_state_fires_on_transition():
    prev = {"potential": {"EURUSD": {"state": "watch"}}}
    out = {"potential": {"EURUSD": {"state": "tradeable", "direction": "bull", "setup_rank": 7.5}}}
    alerts = state_alerts.compute_state_alerts(out, prev)
    assert len(alerts) == 1 and alerts[0]["type"] == "potential_state"
    assert alerts[0]["direction"] == "bull"
    # no-op rerun (same state both sides) fires nothing
    assert state_alerts.compute_state_alerts(out, out) == []


def test_state_alerts_structure_event_fires_on_new_event():
    prev = {"pairs": {"EURUSD": {"structure": {"h4": {"event": "none"}}}}}
    out = {"pairs": {"EURUSD": {"structure": {"h4": {"event": "BOS", "direction": "bull", "strength": 0.8}}}}}
    alerts = state_alerts.compute_state_alerts(out, prev)
    assert len(alerts) == 1 and alerts[0]["type"] == "structure_event"
    assert state_alerts.compute_state_alerts(out, out) == []


def test_state_alerts_regime_flip_fires():
    prev = {"regime_h4": {"regime": "Risk-Off", "confidence": "High", "stable": True}}
    out = {"regime_h4": {"regime": "Risk-On", "confidence": "Medium", "stable": False}}
    alerts = state_alerts.compute_state_alerts(out, prev)
    assert len(alerts) == 1 and alerts[0]["type"] == "regime_flip"
    assert alerts[0]["regime_flip_to"] == "Risk-On"
    # a stable regime (no flip) fires nothing
    stable_out = {"regime_h4": {"regime": "Risk-Off", "confidence": "High", "stable": True}}
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
    prev = {"currencies": {"EUR": {"conviction": 50}}}
    new = {"currencies": {"EUR": {"conviction": 85}}}
    alerts = conviction.compute_conviction_alerts(new, prev)
    assert len(alerts) == 1 and alerts[0]["type"] == "conviction_extreme"
    assert alerts[0]["direction"] == "bull"
    # already extreme last week -> no repeat fire
    assert conviction.compute_conviction_alerts(new, new) == []
    # first-ever run (no prev) never fires
    assert conviction.compute_conviction_alerts(new, None) == []
    assert conviction.compute_conviction_alerts(new, {}) == []


# ── 5. Cross-cadence key preservation regression (2026-09-04 bug) ────────────────
def test_conviction_survives_hourly_rebuild():
    # Bug precedent: "conviction" (scan_cot.py's weekly key) was missing from
    # scan_h1.py's PRESERVED_KEYS for one hour after scan_cot.py first shipped,
    # silently wiping the weekly COT data on the very next hourly scan. Every
    # non-scan_h1-owned key currently in signals.json must stay listed here.
    assert "conviction" in scan_h1.PRESERVED_KEYS
    assert "gold_signal" in scan_h1.PRESERVED_KEYS  # sanity: existing key still there too


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
