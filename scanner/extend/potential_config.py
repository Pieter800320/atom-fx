"""
ATOM FX — EXTEND thresholds (one place).

Every tunable number the EXTEND layer uses lives here. Changing anything in this
file changes only *where nodes sit on the wheel* / how new keys are shaped — it
never touches a frozen calculation or a firing condition (Rule #1 is unaffected).

References: ATOM_FX_ARCHITECTURE.md §5.1 / §5.3 / §5.4, FUNCTIONAL_SPEC.md §8.
"""

# ── CSM Delta (§5.1) ──────────────────────────────────────────────────────────
# Offset (in bars of that TF) at which the "past" CSM snapshot is taken.
# H4 is the primary flow timeframe for the wheel.
OFFSET = {"d1": 1, "h4": 6, "h1": 6}

# The frozen CSM functions BLEND timeframes (compute_csm_d1 uses D1+H4;
# compute_csm_h4 uses H4+H1). To take a temporally coherent "N-ago" snapshot we
# roll every timeframe the function consumes back by the *same wall-clock* amount
# as OFFSET, expressed in that timeframe's own bars (1 D1 = 6 H4 = 24 H1).
# This is the deterministic interpretation of the spec's "sliced price history".
# Tune here if you want a different flow horizon.
PAST_SLICE = {
    "d1": {"d1": 1, "h4": 6},   # 1 day ago
    "h4": {"h4": 6, "h1": 24},  # ~1 day ago
    "h1": {"h1": 6},            # 6 hours ago
}

CSM_DELTA_DP = 1        # decimal places for csm_delta values
FLOW_TF      = "h4"     # timeframe the currency_flow object summarises

# ── Breadth (§5.3) ────────────────────────────────────────────────────────────
# Bands compare by pct (never raw support), because each currency appears a
# different number of times in the frozen 16-pair set (USD=7 … NZD=3).
BREADTH_TFS       = ("h4", "d1")   # h4 primary; d1 additional
BREADTH_STRONG    = 0.70
BREADTH_MODERATE  = 0.50

# ── Six-factor Potential engine (§5.4) ────────────────────────────────────────
REGIME_SOURCE   = "h4"    # which frozen regime the REGIME factor consults
REGIME_FIT_MIN  = 7       # rank._regime_sc-equivalent must be >= this to pass
FLOW_MIN        = 4.0     # directional csm_delta spread (base-quote) to pass flow
BREADTH_MIN     = 0.50    # breadth pct required for the supporting currency
CMP_BULL        = 60      # cmp >= this passes momentum for a bull thesis
CMP_BEAR        = 40      # cmp <= this passes momentum for a bear thesis
STRUCTURE_REQUIRE_BOS = False   # False: BOS or none OK (only counter-CHoCH blocks)
ENTRY_CONT_MIN  = 70      # continuation score required to be "entrable"
RESET_MAX       = 55      # reset_score ceiling (only applied if reset present)
ATR_LO          = 20      # ATR percentile floor (only applied if atr_pct present)
ATR_HI          = 70      # ATR percentile ceiling (only applied if atr_pct present)

# Ordered factors — the sequence a pair must pass to advance outward.
FACTOR_ORDER = ("regime", "flow", "breadth", "momentum", "structure", "entry")

# Level -> base potential score, plus a bounded quality modifier from setup_rank.
BASE_SCORE   = {0: 10, 1: 25, 2: 40, 3: 55, 4: 70, 5: 85, 6: 100}
QUALITY_SPAN = 7          # +/- points the rank quality can shift the score
NEUTRAL_RANK = 5.0        # rank used for the quality modifier when a pair is gated out

# State bands (§5.4).
STATE_LOW_MAX       = 2    # levels 0..2 -> low
STATE_WATCH_MAX     = 5    # levels 3..5 -> watch (6 -> tradeable)
APLUS_RANK          = 8.5  # level 6 AND setup_rank >= this -> aplus

# ── Spark / line chart (FUNCTIONAL_SPEC §8) ───────────────────────────────────
SPARK_BARS = 56           # recent closes per pair per TF (~48-64 range)
SPARK_DP   = 6            # rounding (matches frozen prev_close rounding)

# ── Rotation (2026-09-10) ──────────────────────────────────────────────────────
# Comet-trail length — how many recent (x,y) scans each currency's rotation.py
# history keeps. 6 gives a short, readable trail without the chart getting busy.
ROTATION_HISTORY_LEN = 6

# ── Market Pulse (2026-09-10) ──────────────────────────────────────────────────
# Window length for the plotted history line — matches csm_dispersion.py's own
# WINDOW so both "how unusual is today" style series cover the same span.
PULSE_HISTORY_LEN = 60
# Band thresholds deliberately reuse BREADTH_STRONG/BREADTH_MODERATE's own 0.70/
# 0.50 split (as percentages) rather than inventing a third arbitrary number —
# one consistent "how confident is 'strong'" threshold across the app.
PULSE_HIGH     = BREADTH_STRONG * 100     # >= this -> "confirmed"
PULSE_MODERATE = BREADTH_MODERATE * 100   # >= this -> "mixed"; below -> "noise"

# ── Breadth Thrust (2026-09-10) ─────────────────────────────────────────────────
THRUST_HISTORY_LEN = 30

# ── Contract ──────────────────────────────────────────────────────────────────
SCHEMA_VERSION = 7        # bump whenever a key is added or a shape changes
# 2026-09-10: +rotation, +pulse, +breadth_thrust, +csm_dispersion_pct (all additive).
# 2026-09-10 (2nd): +pairs.<PAIR>.bb_d1.pctb/.pctb_sma, +percent_b_board (all additive).
# 2026-09-10 (3rd): +pairs.<PAIR>.bb_d1.pctb_dates, +percent_b_board.dates (all additive).
# 2026-09-10 (4th): pairs.<PAIR>.bb_d1 (and percent_b_board) now built from an NY 17:00-close D1,
# not the frozen aggregator's UTC-midnight D1 — same keys, but the underlying numbers shift
# (confirmed against a live LiteFinance USDJPY %B read that didn't match the old UTC-midnight
# convention). Not additive — flag if anything on-device caches/diffs old vs new bb_d1 values.
# 2026-09-10 (5th): +percent_b_currency (all additive) — currency-normalized %B, base/quote
# sign-corrected (Pieter's own catch: percent_b_board mixes pairs with no regard for which side
# of the pair is base vs. quote, so it isn't a clean read of any one currency's stretch).
# 2026-09-10 (6th): Macro Archetype rework (Pieter's ask, "the name should accurately reflect
# reality, and the conclusion should be actionable") — REGIME_LIB's 10 display names changed
# (still additive to the schema itself — same code letters, same shape); +macro_regime.
# news_corroboration, +macro_regime.secondary.distinct_axes (both additive); primary's own
# `confidence` can now read Low on a tied axis-count where it previously read Medium/High —
# same field, materially different meaning in the tied case, flag if anything caches/diffs it.
