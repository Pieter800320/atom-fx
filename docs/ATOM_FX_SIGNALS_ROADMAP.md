# ATOM FX — Signals & Notifications Roadmap

**v1.0 — 2026-09-04.** Written from a full audit of the live `scanner/` tree against both
upstream zips (`fx-signal-board-main`, `fx_technical-main`), byte-diffed file by file. See
§8 for the audit's own findings; this doc is the implementation plan that followed from it.

**How to use this doc:** one phase per implementation session, in order. Each item states its
Rule #1 tier, exactly what data it needs, and what "done" looks like, so a fresh session can
pick up any phase without re-deriving the design. Read [INDEX.md](INDEX.md) first as always;
this doc supplements — never supersedes — [ATOM_FX_ARCHITECTURE.md](ATOM_FX_ARCHITECTURE.md)
and [ATOM_FX_FUNCTIONAL_SPEC.md](ATOM_FX_FUNCTIONAL_SPEC.md). §9 tracks which of those two get
updated as each phase actually ships.

---

## 1. Ground rules for everything in this doc

- **Rule #1 still applies.** Every item below is tagged **EXTEND** (new module, reads frozen
  values, writes new `signals.json` keys — safe, no sign-off needed beyond the normal review)
  or **FROZEN-TOUCH** (edits an existing `scanner/*.py` file — stop, discuss, get explicit
  sign-off before writing code, every time, no exceptions). Only one item in this whole
  roadmap is FROZEN-TOUCH: §4, Rate Differential. Everything else is EXTEND.
- **Edge-triggered, not level-triggered.** Every new alert fires on a *transition* (state A →
  state B between this scan and the last), never on "still true this hour too." Nothing here
  should page you every hour for a condition that hasn't changed — that trains you to ignore
  the channel. Mechanically: each detector reads the previous scan's `signals.json` (already
  loaded as `prev` in `scan_h1.py` for the existing preserved-keys block) and compares before
  vs. after computing this scan's value. First-ever run (no `prev`) never fires — there's
  nothing to transition from.
- **One push schema.** Every alert sends `{"type": <alert_type>, "deeplink": <atomfx://...>,
  ...extra fields}` through `push/send_push.py`, exactly the pattern `scan_h1.py`'s Gold Signal
  already uses (see the `send_push_alert`/`send_push_level_alert` helpers). New alert types add
  a new `type` string and deeplink target, not a new transport.
- **One Settings toggle per alert type**, grouped under NOTIFICATIONS alongside the existing
  "Gold signal alerts" / "Level alerts" rows — never bundle a new alert type under an existing
  toggle, and never ship one with no way to turn it off. `UserPreferences.kt`'s
  `NotificationPrefs` grows one field per phase.
- **Cadence matters.** The hourly `scan_h1.py` run is not free real estate for everything.
  Phase 1 rides it (all inputs are already computed every hour). Phase 3 (COT) explicitly does
  **not** — CFTC publishes once a week — and needs its own job, the same way `scan_news.py`
  already runs on its own cadence separate from `scan_h1.py`.

---

## 2. Phase 1 — State-transition alerts (cheapest, do first)

> **Shipped 2026-09-04.** All six alerts + the 3-TF strip landed together: backend in
> `scanner/extend/state_alerts.py` (wired into `scan_h1.py`), five Settings toggles in
> `NotificationPrefs`/`SettingsScreen.kt`/`AtomFxMessagingService.kt` (Structure covers BOS+CHoCH;
> Regime covers the H4 flip + Archetype change — both merged per Pieter's call), and the strip as
> `TfAlignmentStrip.kt` in the Pair sheet. Tests in `tests/test_extend.py`. Docs updated:
> `ATOM_FX_ARCHITECTURE.md` §7, `ATOM_FX_DESIGN.md` §14.7, `LibraryContent.kt`.

Every item in this phase reads data already computed every hour. No new fetch, no new
indicator, no frozen file touched. The work is entirely: detect the transition, format the
push, wire the toggle. All **EXTEND**.

### 2.1 Level 6 / A+ setup reached

- **Trigger:** `potential.<PAIR>.state` transitions into `"tradeable"` or `"aplus"` from
  anything else (edge-triggered against `prev.get("potential", {}).get(pair, {}).get("state")`).
- **Data source:** `scanner/extend/potential.py` — already computed every scan, nothing new.
- **New keys:** none (reads existing `potential` block; no new `signals.json` field required
  unless you want a `last_alert_potential` timestamp mirroring the Gold Signal pattern).
- **Push:** `type: "potential_state"`, title `"{PAIR} reached {STATE}"`, body names the
  direction and Setup Rank (`"LONG · Setup 8.7/10"`), deeplink `atomfx://pair/{PAIR}`.
- **Settings toggle:** "Setup alerts."
- **Effort:** S.

### 2.2 Structure event — fresh BOS or CHoCH

- **Trigger:** `pairs.<PAIR>.structure.h4.event` changes from the previous scan's value
  (specifically alert on a *new* `"CHoCH"` — the "unmistakable warning" case — and optionally
  a *new* `"BOS"`; decide at implementation time whether BOS deserves its own toggle or folds
  into the same one, given BOS fires more often).
- **Data source:** `structure.py` (frozen, unchanged) via `pairs.<PAIR>.structure.h4`.
- **Push:** `type: "structure_event"`, title `"{PAIR} — {EVENT} on H4"`, body strength +
  direction, deeplink to the pair's Breakdown tab.
- **Settings toggle:** "Structure alerts."
- **Effort:** S.

### 2.3 H4 regime flip

- **Trigger:** `regime_h4.regime` differs from `prev.get("regime_h4", {}).get("regime")`.
  (`regime.py`'s own `stable` field already computes this exact comparison for its own output —
  reuse the logic, don't recompute it a second way.)
- **Push:** `type: "regime_flip"`, title `"Regime: {OLD} → {NEW}"`, body confidence, deeplink
  `atomfx://regime`.
- **Settings toggle:** "Regime alerts."
- **Effort:** S.

### 2.4 Macro Archetype change

- **Trigger:** `macro_regime.primary.code` differs from the previous scan's value.
- **Push:** `type: "archetype_change"`, title `"Macro: {OLD_NAME} → {NEW_NAME}"`, body the new
  narrative line (`macro_regime.narrative` already exists), deeplink to Macro tab.
- **Settings toggle:** can share "Regime alerts" with §2.3, or split — flag as an open call for
  whoever implements this phase, not a hard requirement either way.
- **Effort:** S.

### 2.5 ATR Percentile spike

- **Trigger:** `pairs.<PAIR>.atr_pct` crosses above a threshold (suggest 90) this scan having
  been at or below it last scan.
- **Push:** `type: "volatility_spike"`, title `"{PAIR} — volatility expanding"`, body the ATR
  percentile value, deeplink to the pair's Entry breakdown.
- **Settings toggle:** "Volatility alerts."
- **Effort:** S.

### 2.6 Full 3-TF alignment (Strong Buy/Sell across D1, H4, H1)

- **Trigger:** `pairs.<PAIR>.pills.{d1,h4,h1}` all equal `"bull_strong"` (or all
  `"bear_strong"`) this scan, and were not all-aligned last scan.
- **Data source:** `pills.py` (frozen, unchanged) — this is literally the same 5-state engine
  `fx_technical`'s dashboard strip read from; see §8.4 for the full trace.
- **Push:** `type: "tf_alignment"`, title `"{PAIR} — D1/H4/H1 aligned {DIRECTION}"`, deeplink to
  the pair sheet (see §2.7 for the paired UI element).
- **Settings toggle:** "Alignment alerts."
- **Effort:** S.

### 2.7 UI companion: the 3-TF strip

Not an alert — the visual §8.4 concluded is worth building regardless of §2.6. A compact
D1/H4/H1 readout using the same "label above, tinted square, centred value" recipe already
established this session (Momentum tab, Regime sheet, Currency Detail sheet): three squares,
each showing the abbreviated label (SB/B/N/S/SS) from `pills.{d1,h4,h1}`, tinted
`bull_strong`→bright bull, `bull`→dim bull, `neutral`→grey, `bear`→dim bear, `bear_strong`→bright
bear. Natural home: Pair sheet header, near the existing D1/H4/H1 sparklines, since it's the
same three timeframes already anchoring that row.

- **Rule #1 tier:** NEW (pure Android UI, reads an existing frozen field — `pills` — nothing
  computed in Kotlin).
- **Effort:** S–M (design is already settled by precedent; only new work is the SB/B/N/S/SS
  abbreviation + colour mapping).

---

## 3. Phase 2 — Rate Differential (⚠ FROZEN-TOUCH — stop before writing code)

> **Read this before doing anything else in this section.** `cont_score.py`'s Continuation
> Score has a component literally written as `rate_score = 5` — a hardcoded neutral constant,
> not a parameter `compute_cont()` accepts. Making it real means **editing a frozen file**,
> which Rule #1 and `CLAUDE.md` §1 both require stopping for. Pieter asked to be reminded when
> this phase comes up rather than have it get implemented as a matter of course — **this is
> that reminder.** Do not start §3 without first talking through, explicitly:
> 1. Whether to edit `cont_score.py` in place (touches a frozen file — needs Pieter's direct,
>    conscious sign-off, not an assumption) vs. some EXTEND-safe alternative (e.g. an EXTEND
>    module that recomputes a *parallel* continuation score with real rates, sitting alongside
>    the frozen one rather than replacing it — more code, zero Rule #1 risk).
> 2. Whether a 5%-weighted component is worth the frozen-file risk at all, given the
>    alternative above exists.
> 3. If editing in place: run `python -m tests.make_golden` *before* the change to snapshot
>    current behaviour, make the edit, confirm `test_rule1_frozen` fails in exactly the
>    expected/reviewed way (the whole point of the test), then only regenerate the golden file
>    with Pieter's explicit go-ahead.

**If given the go-ahead**, the shape of the fix (from `fx_technical`'s `scan_rates.py`, already
built once):

- **Data source:** a manually-maintained `data/rates_manual.json` — 8 central bank policy
  rates (Fed/ECB/BOE/BOJ/SNB/RBA/BOC/RBNZ), hand-updated on the rare occasions a bank actually
  moves (roughly 8 decisions/year across all eight banks combined — this is not a live feed and
  doesn't need to be).
- **New module:** `scanner/scan_rates.py` (or an `extend/` equivalent) publishes
  `data/rates.json` from the manual source — this part is pure EXTEND, no frozen-file risk.
- **The frozen-touch part:** `cont_score.py::compute_cont()` needs a `rate_diff` parameter and
  logic to score it (was it in `fx_technical`'s original? confirm before designing new scoring
  logic from scratch, or the number becomes an invented judgment call, not a restored one).
- **Effort:** M, mostly due to the process above, not the code itself.

---

## 4. Phase 3 — COT-based Conviction / crowding overlay

> **Shipped 2026-09-04.** New weekly job (`scanner/scan_cot.py` + `.github/workflows/
> scan_cot.yml`, its own cadence, never rides `scan_h1.py`), fetching CFTC's public TFF
> report (`scanner/extend/cot.py`, ported near-verbatim) and scoring it via
> `scanner/extend/conviction.py` (the original's 6-input design, with Inputs 5/6 adapted
> to ATOM's actual data — `reset_score`/`RESET_MAX` in place of `is_extended()`,
> `breadth.pct` in place of an RSI vote — agreed with Pieter before writing code, see the
> module's own docstring for the full rationale). New `conviction` signals.json key, a
> new Currency Detail sheet section, the optional `conviction_extreme` alert shipped too
> (own "Positioning alerts" toggle), and a new `atomfx://currency/<CCY>` deeplink.
> `push/alert_helpers.py` split out of `scan_h1.py` (verbatim move, confirmed inert to
> Rule #1) so both orchestrators can send pushes. Docs updated: `ATOM_FX_ARCHITECTURE.md`
> §4.2/§7, `ATOM_FX_FUNCTIONAL_SPEC.md` §12, `LibraryContent.kt`.

The single highest-leverage gap from the audit (§8.3): free data, already built once, currently
unused, and explicitly named as the top opportunity in `ATOM_FX_FUNCTIONAL_SPEC.md`'s own "not
yet" list — which turns out to be wrong about *why* it wasn't done (data availability), not
about whether it's worth doing.

- **Data source:** CFTC's public Traders in Financial Futures (TFF) report, free, no API key —
  ported from `fx_technical/scanner/cot.py`. Weekly cadence (CFTC publishes Fridays, covering
  the prior Tuesday — there is an inherent ~3-day lag; this is a positioning overlay, not a live
  signal, and should be presented that way).
- **New job:** a weekly script (own cadence, not `scan_h1.py` — mirrors how `scan_news.py`
  already runs independently), downloading and parsing the TFF `fut_fin_txt` zip per currency.
- **New module:** `scanner/extend/conviction.py`, porting `fx_technical`'s 6-input design
  (COT net-speculator percentile, COT open-interest momentum, COT disaggregated
  asset-manager-vs-leveraged-fund alignment, CSM extreme, cross-pair extension composite, RSI
  breadth) into a −100..+100 per-currency score. Read `fx_technical/scanner/conviction.py` in
  full before implementing — the hysteresis-banded scoring in there is deliberate (prevents
  weekly flip-flop near thresholds) and worth preserving, not simplifying away.
- **New key:** `conviction: {CCY: {score, ...}}` in `signals.json`.
- **New UI:** a "Conviction" reading on the Currency Detail sheet — reuse the established
  tinted-square or evidence-card language rather than inventing a third pattern; a positioning
  extreme (e.g. `|score| >= 80`) reads naturally as a "crowded — contrarian risk" callout, same
  register as the Structure tab's CHoCH warning.
- **New alert (optional, discuss at implementation time):** push when a currency's conviction
  score crosses into an extreme band — `type: "conviction_extreme"`.
- **Settings toggle:** "Positioning alerts" (only needed if the optional alert above ships).
- **Rule #1 tier:** EXTEND throughout — reads frozen CSM/RSI values plus new COT data, writes
  new keys, touches nothing frozen.
- **Effort:** L — the biggest single item in this roadmap. Budget its own session, likely more
  than one (fetch/parse plumbing, the 6-input scorer, the UI, the alert).

---

## 5. Phase 4 — Bollinger Band touch + reversal alerts

The one genuinely different *signal class* missing from the app — everything in the current
Six-Factor engine is trend-following; this is mean-reversion.

- **Data source:** none new — 20-period SMA ± 2σ on existing OHLCV, exactly
  `fx_technical/scanner/bb.py`'s `compute_bb()`.
- **New module:** `scanner/extend/bb_signal.py`, porting the band-touch detection and the
  midline-retest reversal-quality scoring from `bb.py`. Read that file in full first — it
  already distinguishes "touched the band" (Message 1) from "retraced to the 20-SMA after a
  touch" (Message 2, the actual reversal confirmation), which is the part worth keeping; a
  touch alone is not a signal, the *retest* is.
- **State:** needs its own small persisted state file (`data/bb_state.json` in the original —
  same pattern as `level_ema_alerts.py`'s existing state file) to track "already touched,
  waiting for midline retest" across scans.
- **New key:** a `bb_signal` block per pair, or fold into `pairs.<PAIR>` — decide at
  implementation time based on how `structure` is already shaped there.
- **Push:** `type: "reversal_setup"`, fires on the midline-retest confirmation, not the raw
  touch (raw touches are frequent and not yet actionable).
- **New UI surface:** worth a small badge/callout, likely alongside Structure on the pair
  sheet's Breakdown tab, since it's the same "what does price action say" register — but this
  is a genuinely new concept for the app's visual language (a *setup forming* state, not a
  pass/fail factor), so treat the UI as its own design pass, not an assumed reuse of an existing
  pattern.
- **Settings toggle:** "Reversal alerts."
- **Rule #1 tier:** EXTEND.
- **Effort:** M–L.

---

## 6. Suggested order

1. **Phase 1** (§2) — all six items plus the strip, one implementation session, ships together
   since they share the same detection mechanism and push schema.
2. **Phase 3** (§4, COT) — biggest standalone value-add, no frozen-file risk, but budget real
   time for it.
3. **Phase 4** (§5, Bollinger) — second signal class, rounds out the engine.
4. **Phase 2** (§3, Rate Differential) — last, and only after the FROZEN-TOUCH conversation in
   §3's callout has actually happened. Do not let "it's just 5% weight" make this feel smaller
   than it is — it is the only item here that can invalidate the Rule #1 golden test.

## 7. Cross-cutting engineering notes

- `UserPreferences.kt`'s `NotificationPrefs` data class grows one boolean per new toggle
  (Setup, Structure, Regime, Volatility, Alignment, Positioning, Reversal) — plan the Settings
  NOTIFICATIONS group's layout for seven-plus rows before Phase 1 ships its first four or five,
  rather than bolting rows on ad hoc each phase.
- Every new detector needs `prev` (the previous scan's full `signals.json`) available at the
  comparison point in `scan_h1.py` — confirm it's already in scope there (it is, for the
  preserved-keys block) before assuming a new load is needed.
- `push/send_push.py`'s existing `data` payload shape (`type`, `deeplink`, optional extra
  fields) is the contract every new alert type should extend, not fork.

## 8. Audit summary (2026-09-03/04) — findings this roadmap is built on

1. **Rule #1 compliance verified byte-for-byte.** Every frozen `scanner/*.py` file is
   byte-identical to `fx-signal-board-main` (line-ending differences aside). The only two files
   that differ (`scan_h1.py`, `scan_news.py`) show exclusively additive keys, EXTEND-layer
   calls, and delivery-channel changes — zero unauthorized calculation edits found.
2. **The premise that `fx-signal-board-main` and `fx_technical-main` "should in essence be the
   same" is false.** `fx_technical` is a substantially broader R&D codebase; `fx-signal-board`
   is a distilled subset with the same core formulas (byte-identical logic, reformatted) but
   several whole modules dropped: `bb.py`, `conviction.py`, `cot.py`, `scan_rates.py`,
   `scan_calendar.py`, `cooldown.py`.
3. **The "3-TF indicator" is not missing** — it's `score.py::score_pair()`, unchanged, already
   running every hour, already exposed as `pills.{d1,h4,h1}`. What's missing is only the compact
   visual strip (§2.7) and an alignment alert (§2.6), not any calculation.
4. **Biggest gap:** no positioning/crowding signal (COT), despite being free, previously built,
   and named as the top opportunity in the app's own Functional Spec.
5. **What ATOM FX does that neither upstream project does at all:** the Six-Factor wheel
   engine, the Macro Archetype classifier, and the deterministic-seed-plus-AI-narration
   Recommendation design — all original to this app, not present in either zip.

## 9. Doc-sync obligations

As each phase ships, update the doc it actually changes the contract of — don't let this
roadmap become the only place a shipped feature is documented:

- Phase 1 → new keys and alert types get a row in `ATOM_FX_ARCHITECTURE.md`'s `signals.json`
  contract table; the 3-TF strip gets a subsection in `ATOM_FX_DESIGN.md` near the pair sheet.
- Phase 2 → if `cont_score.py` is edited, `ATOM_FX_ARCHITECTURE.md`'s Rule #1 section needs a
  dated note recording *that it happened and why*, the same way any frozen-file exception
  should leave a paper trail.
- Phase 3 → new `conviction` key + Currency Detail UI addition, documented in both
  `ATOM_FX_ARCHITECTURE.md` and `ATOM_FX_FUNCTIONAL_SPEC.md`.
- Phase 4 → new `bb_signal`/reversal concept, same two docs, plus `GLOSSARY.md` gets the new
  term (verbatim naming, per that doc's own rule).
- Each phase should also get a new entry in `LibraryContent.kt` (`app/src/main/java/.../ui/
  settings/`) — the in-app study library should stay in lockstep with what's actually shipped,
  not just what existed at the time it was first written.
