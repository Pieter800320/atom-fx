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
>
> **§2.1's own alert (`potential_state`/"Setup alerts") retired outright 2026-09-17** — see
> `ATOM_FX_BUILD_STATUS.md` §B: it fired on a pair's own `cont >= 45` alone, the loosest
> single-factor bar in the app (a live check found 9/12 pairs cleared it in one scan), a
> systematically noisier duplicate of Recommendation alerts' full weighted-composite gate.
> Detector, test, and the whole Kotlin toggle chain removed. §2.1 below is historical record of
> the original design, not current behavior.

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
  `atomfx://regime`. Payload also carries `regime_flip_to` (the new regime name) so the
  client can key its own content off it (see below) without re-parsing the title.
- **Settings toggle:** "Regime alerts."
- **Effort:** S.
- **2026-09-04 — Technical Regime Playbook** (living-handbook pass, part 2, after
  `archetype_change`'s own Regime Playbook below): `RegimeSheet` and a `regime_flip`
  Notification History card each carry a book+ icon at heading level that opens the **Reading
  Window** (see §2.4's own note and `ReadingWindow.kt`) with a per-regime explanation grounded
  in `regime.py`'s actual three-vote + ranging-override mechanics — what the vote count and
  score mean for this specific regime, how it differs from the Macro Archetype's confidence
  badge (same word, different formula), and Gold Signal's hard dependency on this exact
  classification. `app/.../push/TechnicalRegimePlaybookContent.kt` (content) +
  `app/.../ui/sheets/TechnicalRegimePlaybookDetail.kt` (the detail composable, hosted inside
  the shared Reading Window). No live evidence array exists on this data shape (`RegimeBlock`
  is just `regime/confidence/score/stable`), so unlike `archetype_change`'s fragment-assembly,
  this is static entry-level content per regime name.

### 2.4 Macro Archetype change

- **Trigger:** `macro_regime.primary.code` differs from the previous scan's value.
- **Push:** `type: "archetype_change"`, title `"Macro: {OLD_NAME} → {NEW_NAME}"`, body the new
  narrative line (`macro_regime.narrative` already exists), deeplink to Macro tab. Payload also
  carries `regime_code` (the new `primary.code`).
- **Settings toggle:** can share "Regime alerts" with §2.3, or split — flag as an open call for
  whoever implements this phase, not a hard requirement either way. (Shipped shared.)
- **Effort:** S.
- **2026-09-04 — Regime Playbook** (living-handbook pass, part 1 — the proof-of-concept the
  whole mechanism was proven on before §2.3's own extension above): `MacroScreen`'s archetype
  banner and an `archetype_change` Notification History card each carry a book+ icon
  (`BookPlusGlyph`, top-right at heading level) that opens a per-regime explanation assembled
  from the LIVE `macro_regime.evidence`/`conflicts` — which axis fragments and conflict
  callouts actually apply to this exact firing, not a static lookup. 10 regime entries (A–J),
  5 axis fragments, 3 conflict fragments, grounded in `macro_regime.py` and deepened against a
  professional review of `FX_Macro_Flow_Handbook_Expanded.pdf`.
  `app/.../push/RegimePlaybookContent.kt` (content) + `app/.../ui/macro/RegimePlaybookDetail.kt`
  (the detail composable). A standalone rewritten regime chapter was also published as its own
  document (same content, one source of truth) — first installment of a larger handbook
  rewrite, not the finished replacement.
  **Superseded same day (Pieter's own framing — "sheets inspect data, a window is for study and
  reading"):** the original inline expand/collapse row is gone; the book+ icon now opens a
  dedicated full-screen **Reading Window** (`app/.../ui/reading/ReadingWindow.kt` +
  `ReadingTarget.kt`), rendered via a `Dialog` (not a `Box`, since Material3's own
  `ModalBottomSheet` renders in its own always-on-top Popup window and would otherwise sit in
  front of a plain composable) so it wins the z-order over whatever's open underneath — a
  sheet, the Macro tab, or the Settings panel — and closes back to exactly that. Same pattern
  now shared by both Regime Playbooks (§2.3's Technical one included) and reachable from all
  three of RegimeSheet, MacroScreen, and Notification History.

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

## 5. Phase 4 — Bollinger Band touch alert + Watchlist

**Superseded 2026-09-09 (Pieter's own redesign)** — the original plan below this line described
an auto-confirming design (touch → wait → the *midline retest* is the actual signal, fired
automatically). Pieter's own instinct, after thinking through what actually makes a BB touch a
real reversal vs. a pause-before-continuation, was different: he doesn't yet know which
thresholds separate a good touch from a bad one, and would rather not hardcode an unvalidated
gate. The touch itself is the notification; judging whether a reversal is actually developing is
a manual review process, supported by a new **Watchlist** surface — not something the scanner
auto-confirms.

The one genuinely different *signal class* missing from the app — everything in the current
Six-Factor engine is trend-following; this is mean-reversion.

- **Timeframe: D1 only** (Pieter's call — H4 can follow later once D1 proves out).
- **Bands: 12-period SMA ± 2σ** — not the original `fx_technical/scanner/bb.py` port's
  20-period default. Pieter specified 12-period explicitly; this is a deliberate parameter
  choice, not an oversight.
  - **Note (2026-09-18):** a 20-period Bollinger read now also exists in the app, as the glance
    panel's %B/BandWidth charts (`scanner/extend/bollinger_series.py`, Design §19.4b). That is a
    **separate module on its own key** (`pairs.<PAIR>.bollinger_series`), added precisely so this
    alert's 12-period bands never had to move to serve a chart. Do not "unify" the two by
    re-parameterising `bb_touch.py` — that would change this alert's own firing behaviour, and
    `tests/test_extend.py::test_bollinger_series_does_not_disturb_bb_d1` guards against it.
- **Trigger:** a wick touch (high >= upper band, or low <= lower band) on the current
  (possibly still-forming) D1 candle — checked every hourly scan for fastest latency, not
  gated on candle close. Edge-triggered per this doc's own §1 rule: fires once on the
  none -> touching transition, not every hour the touch remains true.
- **No auto-confirmation, no retest logic, no `bb_state.json`.** The touch alone is the whole
  signal; there's no second "did it confirm" stage for the scanner to track.
- **Band-width trend (new, first-pass approximation):** current band width vs. band width
  N bars ago (tune N once real data exists) — expanding/converging/flat. This is the one
  genuinely new piece of math in this phase; nothing existing computes a width *trajectory*
  (ATR Percentile is a snapshot, not a trend). Pieter's own framing: a touch arriving via
  *expanding* bands (a breakout candle) argues against fading it; a touch on *narrow/converging*
  bands is a better reversal candidate.
- **New key:** `pairs.<PAIR>.bb_d1: {touching: "upper"|"lower"|"none", sma, upper, lower,
  width_pct, width_trend: "expanding"|"converging"|"flat"}`.
- **Push:** `type: "bb_touch"`, deeplinks to the pair sheet. The notification body includes a
  compact snapshot of the confirmation context (ADX, D1/H4/H1 pill alignment, Reset Score in
  the touch direction, band-width trend) — informational, never gates whether it fires.
- **Settings — reference, not a gate:** a new Library entry documents the full checklist
  Pieter wants to check by hand (ADX trend/range read, the pair's own D1/H4/H1 pill alignment
  — a pullback-in-trend signature, not the macro Risk regime — Reset Score, band-width trend,
  Structure/CHoCH confirmation) so the criteria are never forgotten even though they're not
  hardcoded into the alert itself.
- **New feature: Watchlist.** A pair added from its sheet (a toggle button on `PairHeader`)
  appears on a new Watchlist screen, reached via a new icon in the header next to the
  calendar/gear (Pieter's call — not a new bottom tab, not a bottom sheet). Taps through to the
  full pair sheet. Storage: same `SharedPreferences` + JSON + hot `StateFlow` pattern
  `NotificationHistoryStore` already uses — no new dependency. No auto-expiry in v1 — Pieter
  manages the list by hand; add one later if the list gets noisy in practice.
  **Card content redesigned 2026-09-16** — originally a BB-touch-specific snapshot (touch
  direction + how long ago, ADX, pill alignment, Reset Score, band-width trend, Structure) plus
  a tap-to-reveal "what to look for" reversal checklist (the same prose the BB Library entry
  carries). A watched pair is no longer assumed to be mid-reversal-watch, so the card now shows
  general pair state instead: pair + direction word + added time, a "★ RECOMMENDED" chip when
  the pair is currently in `ranked.top` (§5b), a "Setup {cont} · {six-factor state}" ripeness
  line, the same Regime/Trend/Momentum/Volatility/Structure five-factor consensus dot row
  StatusStrip's own recommendation panel shows (extracted into one shared
  `ui/components/ConsensusRow.kt` so the two surfaces can't drift, and so a colour bug fixed in
  one can't silently persist in the other), and the existing D1/H4/H1 pill row. The reversal
  checklist itself is unchanged and still lives in the Library, just no longer duplicated here.
- **Rule #1 tier:** EXTEND.
- **Effort:** L (touch detection is small; the Watchlist is a genuinely new screen + storage +
  nav entry + pair-sheet control, not a quick addition).

---

## 5b. Hourly recommendation ranking + edge-triggered alert

> **Shipped 2026-09-16.** Backend: `scanner/scan_h1.py` (fresh `rank_pairs()` call + local
> `_recommendation_alerts` detector). Settings toggle in `NotificationPrefs`/`SettingsScreen.kt`/
> `AtomFxMessagingService.kt`. Tests in `tests/test_extend.py`. Docs updated:
> `ATOM_FX_ARCHITECTURE.md` §4.1/§7, this section, `LibraryContent.kt`.

HOME's recommendation glyph row (`signals.ranked.top`) previously only refreshed on
`scan_news.py`'s slower cadence — the same cadence as its AI narrative call, even though the
underlying ranking (`rank.py::rank_pairs`, FROZEN, deterministic) doesn't need a model call at
all and every input it reads (`cont`/`csm.d1`/`regime_d1`/`pills`/`mom`/`adx`) is already
recomputed fresh every hourly scan. Only the 10%-weight cross-asset component still reads the
slower-cadence `macro_assets`.

- **Trigger:** `scan_h1.py` now calls the frozen `rank_pairs(out)` itself, right after `out` is
  assembled each hourly scan, and overwrites `ranked.top` with every pair scoring
  **>= `RECOMMENDATION_MIN_SCORE` (6.5, rank.py's own 0–10 weighted scale)** — `pair`/
  `direction`/`score`, no upper cap on count — `ranked.text`/`ranked.updated` (the Haiku
  narrative) are left alone, still written only by `scan_news.py`. Verified before building
  this that nothing in the app reads `ranked.text`, so the split cadence carries no UI mismatch
  risk.
- **Score floor, not a flat top-3 slice (2026-09-17, Pieter's ask, same-day follow-up).** The
  original ship used `ranked[:3]` — always exactly 3 regardless of how many pairs actually
  qualified or how weak the non-`cont` components scored. Checked live: on a strong trending
  day, 9 of 12 pairs can clear `rank.py`'s own `cont >= 45` gate at once (one broad
  USD-strength/risk-off theme wearing 9 pair labels, not 9 independent setups) — a flat top-3
  either hid genuine extra setups on a decorrelated day or, more often, just showed "the best 3
  of a flood." `RECOMMENDATION_MIN_SCORE = 6.5` was picked by simulating it against 40 scans'
  worth of historical `ranked.top` scores (5% would show zero pairs — rare enough to trust,
  vs. 7.0's 25%) and cross-checked against a live full re-rank (9 pairs cleared the base gate,
  only 3 cleared 6.5). Duplicated as the same-named constant in both `scan_h1.py` and
  `scan_news.py::call_ranked_analysis` (same house style as `state_alerts.py`'s own
  `_CONT_QUALIFY_THRESHOLD` cross-referencing `rank.py`'s 45) — a defensible first pass, not a
  frozen number, tune freely. HOME's glyph row and the Watchlist's "RECOMMENDED" chip both
  already iterate `signals.ranked.top` with no hardcoded count assumption (the glyph row was
  already built to horizontally scroll "if more than fit"), so no app-side change was needed.
- **Cadence: still rides the existing hourly `scan_h1` Apps Script trigger** — no scheduler
  change, and `scan_news.py` does **not** become hourly.
- **New alert — edge-triggered, per this doc's own §1 rule:** fires once per pair that, versus
  the previous scan's `ranked.top`, either newly appears in the fresh top (now score-floored,
  not count-capped) or stays in it but flips direction (long↔short). No `prev` (first-ever run)
  never fires. `type: "recommendation"`, payload `pair`/`direction`/`score`, deeplink
  `atomfx://pair/{PAIR}` — same `send_push_alert` path Gold Signal and the state-transition
  alerts already use.
- **Kept local to `scan_h1.py`**, not added to `scanner/extend/state_alerts.py` even though the
  edge-trigger *pattern* matches that module's six detectors exactly — `scan_h1.py` was already
  being edited on a concurrent branch (trend-pullback) at ship time, so this stayed a small,
  self-contained addition to ease that merge, rather than spreading across two files.
- **Settings toggle:** "Recommendation alerts", same one-boolean-per-type convention as every
  row above.
- **Rule #1 tier:** `rank.py` untouched, imported read-only. `scan_h1.py` is FROZEN-logic/EXTEND
  call-sites tier, same as every other addition to that file.
- **Bugfix (2026-09-17) — `scan_news.py` was a second, silent writer.** The "safe because the
  app only ever reads `top`... `text`/`updated` stay on `scan_news.py`'s own cadence" framing
  above describes the *intent*, but the shipped `scan_news.py::main()` kept persisting
  `ranked["top"]` too (from `call_ranked_analysis`'s own score-floored ranking, computed for the
  catalyst-check call's context) — a second ~2h-cadence writer to a field only `scan_h1.py` was
  meant to own, with no edge-trigger on that path at all. Real-world effect: a pair could
  enter/exit/flip in `ranked.top` during a news scan with **zero notification, ever** — caught
  live (Pieter: "5 chips appeared, no notification"), traced to `2bf1785` (a news-scan commit)
  silently adding AUDUSD/USDCHF, with the next h1 scan seeing them as already-present and
  correctly staying quiet. `scan_news.py` no longer writes `top` into `signals["ranked"]` (only
  `text`/`updated`) — `scan_h1.py` is now `ranked.top`'s sole writer, matching this section's
  original design.

---

## 5c. Crowd score — a context chart (Phase 1), then optional alerts (Phase 2)

> **Phase 1 BUILT and verified on a device (2026-09-19), on branch `feat/crowd-score` — not yet merged to `main`.**
> Pieter's decisions on all seven points are recorded in §3. Backend: `crowd_data.py`, `crowd_score.py`, schema v12
> (`ARCHITECTURE.md` §4.2). App: `CrowdScoreCard` under MACD (`DESIGN.md` §19.4b). **Phase 2 (alerts, §4) is not started.**
> One measured departure from this draft: D1 carries ~50 points, not 90 (§1.2, `BUILD_STATUS.md` item 18).

### What it is, and what the evidence says (stated up front, and repeated in the in-app Library)

The Crowded Market indicator (Pine on `main`: `pine/crowded_reversal.pine`; methodology
`pine/CROWDED_REVERSAL_METHODOLOGY.md`): eight weighted yes/no conditions — %B outside its bands,
z-score, ATR-stretch, RSI, RSI extreme tier, regular divergence, volatility climax, and COT
positioning — add to a **top score** and a **bottom score**, each 0–100. Top = price looks stretched
up and crowded long; bottom = the mirror. A score of 60 is the indicator's own flag line.

**It is context, not a signal.** Four pre-registered, run-once studies (`docs/RESEARCH_LOG.md` on the
`research` branch, Experiments 2–5) found: a small but real tendency for reversals to become more
likely as the D1 score rises in **2021–2026**; **no** such tendency in 2009–2020, **none on H4**, and
flagged bars (score ≥ 60) did **not** reach the previous support/resistance more often than
comparable bars. D1 flags at 60 were too rare (~25–32 events) to conclude anything on their own. So
the app presents the score as "how many stretch and crowding conditions coincide right now" and
never as a probability or an entry. This wording constraint applies to the card, the Library entry,
and — in Phase 2 — every notification.

### Naming (Glossary rule: one name per concept, no synonyms)

**Crowd score** (agreed, Pieter 2026-09-19) — shown as two readings, **Crowd top** and **Crowd bottom**. It must not
reuse *Potential*, *Setup Rank* or *Continuation score* (all existing 0–100 numbers, all
different). "Crowded Market — Reversal Conditions" stays as the indicator's TradingView title and
is quoted once in the Library for cross-reference. **`GLOSSARY.md` gets the new entry in the same
change that ships the key** (§9).

### Phase 1 scope — and what is deliberately NOT in it

In: a backend series per pair for D1 and H4, and one chart card on the long-press sheet.
**Not in Phase 1:** any push notification (Phase 2), any Settings toggle, any wheel/landing-screen
element (§17's no-scroll invariant is untouched — this lives only in a sheet), a regime filter
(see below), M15/H1 (no evidence, and H1/M15 bars are not what was tested).

### 1. Backend (EXTEND — Rule #1 safe: reads frozen OHLCV only, writes one new key, no frozen file touched)

**1.1 New module `scanner/extend/crowd_score.py`** — the tested port of the Pine logic. The research
work lives on the `research` branch (`scanner/extend/crowded_reversal.py`, Pine primitives
reproduced factor by factor, with its tests); `main` must not depend on that branch, so the module
and its tests are **copied to `main` as new files**, not imported. Parameters are the Pine defaults,
verbatim. The **regime filter is not ported** — it is Off by default because the study found it
made the score a worse predictor, so ADX and EMA200 are not computed at all (shorter warm-up, less
code). The trend-shading background of the TradingView version is therefore out of scope too.

**1.2 Bars** (from the H1 already fetched this scan — `raw_ohlcv`, 5000 H1 bars, no new API cost):
- **D1:** 17:00 America/New_York close, closed-market bars dropped (the studies' convention).
- **H4: TradingView's own alignment — New York-session blocks starting 17:00, 21:00, 01:00, 05:00, 09:00,
  13:00 New York time (= 21/01/05/09/13/17 UTC in summer, 22/02/06/10/14/18 UTC in winter), closed-market
  bars dropped, no stub blocks.** *Verified, not assumed:* a TradingView H4 screenshot (AUDUSD, OANDA,
  chart clock 19:58:32 UTC) shows a 01:01:27 countdown on the live candle, i.e. it closes at 21:00 UTC =
  17:00 New York; a UTC-aligned candle could never close at 21:00. The blocks come from the tested
  `aggregate_h4_ts(alignment="ny")` (research branch). **Consequence, accepted (Pieter, 2026-09-19):** this
  card's H4 candles start 1 h (summer) or 2 h (winter) away from the sibling H4 cards, which use the app's
  own UTC blocks; the Library entry says so. Both alignments were run in the studies and gave the same
  answer (flat), so the choice rests on the stated goal — comparing with TradingView — not on the evidence.
- **Completed bars only, and "on time" defined.** The still-forming bar is never scored (no repaint; the
  tested definition). A bar counts as complete when the scan runs at or after its close **and** the H1
  bar that ends at that close is present in the fetched data. `scan_h1` runs **every ~2 hours** (measured from the bot's commit times: 05:41, 07:41, 09:41 … — a workflow
  comment calls the Apps Script trigger "hourly", but the scan itself is 2-hourly), so the score for a just-closed D1
  or H4 bar appears at the **first scan after the close: up to ~2 hours later** (an H4 close is followed by a scan within
  0–2 h, so on average about an hour). Correction 2026-09-19 — the first draft said "hourly", which understated the
  delay by half. It is
  not real-time, and nothing here can make it so without changing the scan cadence. This series' right
  edge can lag the sibling cards by up to one bar.
- **D1 history is short — measured, and it corrects the first draft.** The draft assumed 5000 H1 bars ≈ 208 D1 bars
  (~108 scoreable). Twelvedata now returns market-closed weekend bars every week, so 5000 H1 rows span only ~30 weeks
  (~149 D1 bars once those are dropped), and the 100-bar z-score warm-up leaves **~50 D1 points** (~10 weeks), not 90.
  H4 (~830 blocks) yields the full 90. Measured on all 12 pairs' real history: scores from the production-sized
  window are **identical** to full-history scores on every overlapping bar (max difference 0.0), so the short window
  does not change the answer — it only shortens the D1 chart. Another cost of the weekend bars
  (`BUILD_STATUS.md` item 18): they use up ~29% of the fetched H1 depth. Points without every input are omitted,
  never faked.

**1.3 COT — a new data path, the largest new dependency.** The indicator uses CFTC's **Legacy
futures-only, Non-Commercial long/short** report (8 contracts: EUR GBP JPY CHF CAD AUD NZD, and the
ICE Dollar Index for USD). The app's existing COT job (`scan_cot.py`, `cot.py`) uses the **TFF**
report (leveraged funds) for Conviction — a different series; the two **coexist and must not be
unified** (same reasoning as the two %B reads). New: a committed store
`data/cot_legacy/legacy_nc.csv` (~8,600 rows, 2006→, ~400 KB) plus a weekly refresh (CFTC publishes
Fridays; append the newest report, keep the file on any fetch failure). Alignment is the tested,
unit-tested rule: **a bar in calendar week *k* reads the report dated the Tuesday of week *k−1*** —
never one dated in week *k* or later. Percentile: 156 weeks on a Mon–Fri calendar; the two legs of a
pair combine with the quote leg inverted and are averaged. **A pair whose COT cannot be resolved
still gets a score, with COT's weight left in the denominator (ceiling 75) and `cot_ok: false`** —
exactly the Pine's own graceful fallback. How the card shows that state: §2.2 footer ("No COT"; decided, §3 item 5).

**1.4 Contract** — a new block **inside the existing `pairs.<PAIR>`**, same shape family as
`bollinger_series`/`momentum_series`, **`schema_version` 11 → 12**:

```
pairs.<PAIR>.crowd_series = {
  "d1" | "h4": {
     dates:  [str, …],      # bar labels, oldest-first, ≤ 90 points, same date format as the sibling series
     top:    [float, …],    # 0-100, the top score per completed bar
     bottom: [float, …],    # 0-100, the bottom score per completed bar
     latest: { bar_date: str, top: float, bottom: float,
               top_on: [str, …], bottom_on: [str, …],     # which of the 8 conditions are on, by name
               cot_pctl: float | null, cot_ok: bool, cot_asof: str | null }
  }
}
```

`latest` carries the state Phase 2's alert and its message body need, so the contract does not
change again later. **A free forward record:** the scan bot already commits `data/signals.json`
every scan, so git history now accumulates real, dated, out-of-sample `latest` scores from the day
this ships — a script can rebuild an honest forward test later. No extra logging writer is added.

**1.5 Call-site:** one `attach_crowd_series(...)` call in `scan_h1.py` beside `attach_bollinger_series`
(pure additive call-site; no calculation or firing condition changes). `scan_m15.py` never touches
this key, and `scan_h1.py` rebuilds `pairs_out` fresh each run, so the **cross-cadence carry-forward
trap** (`INDEX.md`, 2026-09-18 3rd) does not apply — but the tests below assert it anyway.
A module failure leaves the key absent for that pair and the scan continues (same posture as every
other EXTEND module; the known observability gap is `BUILD_STATUS.md` outstanding item 13).

### 2. App (Kotlin/Compose) — a pure consumer: it draws the series and computes nothing

**2.1 Model** (`data/model/Signals.kt`): `CrowdSeries` + `PairSignals.crowdSeries`, tolerant of an
absent key (older cached JSON, or a pair whose module failed) — absent means no card.

**2.2 The card `CrowdScoreCard`** — added to `ChartSheet.kt` **below MACD**, "under the long-press
graphs". It uses the shared two-tone `IndicatorCard` shell (grey `surfaceRaised` header strip, black
`ground` plot area, grey footer strip) and the shared `ChartCommon.kt` helpers, so it reads as the
fifth card of one panel. The D1/H4/H1/M15 row already drives every card: **the card shows on D1 and
H4 and is hidden on H1 and M15** (no data, no evidence there) — **decided (Pieter): hide.** Hiding shifts the sheet's height when
the row changes; an empty card reading "not computed on this timeframe" was judged noisier.

- **Header:** `Crowd score` (white, Body) · `Top 42 · Bottom 0` (grey, Caption) — both latest values.
- **Plot:** y-axis fixed 0–100. **Two lines: top in `colors.bear` (red), bottom in `colors.bull`
  (green)** — TradingView's red/green, as requested — **agreed (Pieter)**. This is a deliberate exception to the
  "every line is white" rule (`DESIGN.md` §19.4b, 3rd restyle), justified the same way MACD's signal
  line is: two overlaid lines must be tellable apart. A **dashed reference line at 60** (the
  indicator's own flag line, the app's existing dashed-reference style). No other reference lines.
  Endpoint glow as on the sibling cards. A small dot on each bar where a side first reaches ≥ 60
  (TradingView's ▼ TOP / ▲ BOTTOM labels are too wide for 90 points; the value is in the header).
- **Footer state word** (right-aligned, nothing else — the 6th/7th restyle rule), answering "is the
  market crowded to one side right now?" using **only the 60 line the chart already draws — no new
  threshold**: **Crowded top** (`bear`) if top ≥ 60 · **Crowded bottom** (`bull`) if bottom ≥ 60 ·
  **Mixed** (`watch`) if both ≥ 60 · **Not crowded** (`neutral`) otherwise. When `cot_ok` is false the
  footer reads **No COT** (`textMuted`) **instead of** the state word (decided, Pieter): a score capped
  at 75 silently reads as "less crowded", so it says so.
- **Dates** via the shared `drawDateRow`. **Theming:** tokens only, no literal hex; verified in light
  and dark. **Haptics (§16):** the card has no tappable element in Phase 1, so no new haptic wiring;
  the existing TF row keeps its own.

**2.3 In-app Library** (`LibraryContent.kt`, prose per `ATOM_FX_LIBRARY_STYLE.md`, in the same change):
a "Crowd score" entry — what the eight conditions are, how to read top/bottom, the 60 line, the COT
input's weekly lag, and the evidence paragraph above in plain words.

### 3. Decisions (Pieter, 2026-09-19) and one finding

1. **Name:** Crowd score / Crowd top / Crowd bottom — agreed.
2. **H1 / M15:** hide the card — agreed.
3. **Line colours:** top in `bear` (red), bottom in `bull` (green), an accepted exception to the
   all-white rule on this card — agreed.
4. **Completed bars only** — agreed, on the condition that it fires on time. §1.2 defines that precisely:
   the first scan after each D1/H4 close (scans run every ~2 hours, so up to ~2 h later), not real-time.
5. **Missing COT:** the footer says "No COT" — agreed.
6. **H4 alignment: TradingView's** (New York-session blocks), verified against a TradingView screenshot
   — §1.2. Reversed from the earlier "UTC, the app's standard" proposal at Pieter's request, since
   comparing with TradingView is the point of this card. D1 was already NY-close.
7. **Weekend-bar follow-up:** confirmed, and **measured** (below) — recorded as
   `BUILD_STATUS.md` outstanding item 18; not part of this feature.

**Finding — market-closed weekend bars in the app's existing D1/H4 series (measured 2026-09-19).**
Since Twelvedata began emitting market-closed weekend bars on 2026-01-11, `main`'s D1 series (labelled by
the session's OPEN date, so real sessions read Sun–Thu and the closed-only ones Fri/Sat) and H4 series
include them; the closed-market filter (`DECISION-007`, `agg_nyclose.py`) exists only on the `research`
branch. On the same real H1 history with `main`'s own functions, live vs filtered, since 2026-01-12:
**28% of D1 rows are closed-market-only bars** (median range about half a real session's); the **BB touch
(12-period, the alert path) state differs on 17.3%** of comparable days and the daily-label sequence shows
**409 vs 270** none→touch transitions across the 12 pairs (about +50%, at the daily-label level — not a
count of pushes sent); the latest RSI(14) values differ by a mean 2.3 points (max 4.5) and %B(20) by a
mean 6.4 (max 14.7), enough to flip some "Stretched" footers. **This spec changes no shipped number** —
the Crowd score is built on filtered bars — and the fix is its own decision (it moves numbers an alert
fires on). Full figures and the proposed fix: `BUILD_STATUS.md` item 18.

### 4. Phase 2 — alerts (outline only; its own spec after Phase 1 has been watched live)

An edge-triggered `crowd_score` push type in `state_alerts.py` using the `latest` block, a Settings
toggle and a **minimum-level setting** (any / 20 / 40 / 60) per timeframe, and a message that always
states the level and which conditions are on. Measured volume on 2021–2026 data, filter off, rising
edge into each level, across all 12 pairs: **D1** ≈ 267 / 162 / 36 / 6 a year for >0 / ≥20 / ≥40 / ≥60;
**H4** ≈ 1,400 / 990 / 264 / 31 a year (H4 "any level" ≈ 5 pushes per trading day). Score > 0 is present
on ~40% of bars and mostly means a single condition is on. Default state, cooldown and level-escalation
rules are decided then.

### 5. Verification — Phase 1 is done when

- **Python:** the ported module's parity tests pass on `main` (Pine primitives, divergence latch,
  COT no-look-ahead alignment); the last-90 scores computed from a 5000-H1 window agree with the same
  bars computed from full history within tolerance (the short-warm-up check); series shape, absent-COT
  behaviour, and completed-bars-only are asserted; **`bb_d1`, `bollinger_series` and `momentum_series`
  are byte-identical before and after** (the `test_bollinger_series_does_not_disturb_bb_d1` pattern);
  the `prev`-carry-forward across a `scan_m15` run is asserted; `python -m tests.test_rule1_frozen`
  and `python -m tests.test_extend` green.
- **App:** `./gradlew assembleDebug` compiles; a unit test for the footer-state function; **on
  device**, a fixture that forces each footer state (Crowded top / Crowded bottom / Mixed / Not
  crowded / No COT) and the card appearing on D1/H4 and disappearing on H1/M15, in light and dark.
- **Docs, same session:** `ARCHITECTURE.md` §4.2 (contract, v12), `DESIGN.md` §19.4b (card), `GLOSSARY.md`,
  `BUILD_STATUS.md` (row), `LibraryContent.kt`, this section flipped from DRAFT to shipped.

### 6. Relative size

Backend: medium (port + tests are done and move over; the Legacy COT store and weekly refresh are the
new work). App: medium-small (one card on an existing shell). The alert phase is separate.

## 6. Suggested order

1. **Phase 1** (§2) — all six items plus the strip, one implementation session, ships together
   since they share the same detection mechanism and push schema.
2. **Phase 3** (§4, COT) — biggest standalone value-add, no frozen-file risk, but budget real
   time for it.
3. **Phase 4** (§5, Bollinger) — second signal class, rounds out the engine.
3b. **Crowd score** (§5c, added 2026-09-19, DRAFT) — Phase 1 (backend series + one chart card) after
   Pieter's review of §5c's ⚠ decisions; its alerts (Phase 2) only after Phase 1 has been watched live.
4. **Phase 2** (§3, Rate Differential) — last, and only after the FROZEN-TOUCH conversation in
   §3's callout has actually happened. Do not let "it's just 5% weight" make this feel smaller
   than it is — it is the only item here that can invalidate the Rule #1 golden test.

## 7. Cross-cutting engineering notes

- `UserPreferences.kt`'s `NotificationPrefs` data class grows one boolean per new toggle.
  **Corrected 2026-09-17** — this list was stale on every count: current toggles are Gold
  Signal, Level Alerts, Structure, Regime, Volatility, Alignment, Positioning, BB Touch,
  Recommendation (9 total, plus the master on/off switch). "Setup" was retired outright
  2026-09-17; "Reversal" never existed under that name (it's "BB Touch"); Gold Signal and
  Level Alerts predate this note and were always missing from it.
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
- Phase 5b → hourly `ranked.top` re-rank + `recommendation` alert type, documented in
  `ATOM_FX_ARCHITECTURE.md` §4.1/§7 and this section.
- §5c Crowd score → `crowd_series` contract + schema v12 in `ATOM_FX_ARCHITECTURE.md` §4.2; the
  card in `ATOM_FX_DESIGN.md` §19.4b; new terms in `GLOSSARY.md`; a `BUILD_STATUS.md` row; a
  `LibraryContent.kt` entry. If Phase 2 ships: the new alert type in `ARCHITECTURE.md` §7 and this
  doc's toggle list (§7).
- Each phase should also get a new entry in `LibraryContent.kt` (`app/src/main/java/.../ui/
  settings/`) — the in-app study library should stay in lockstep with what's actually shipped,
  not just what existed at the time it was first written.
