# ATOM FX — Design Document

**Version:** 1.1 (2026-09-10: §17/§19/§20 synced to the shipped Wheel v2 + Simplification Rework — see `docs/ATOM_FX_BUILD_STATUS.md`) · **Status:** Specification (hand to Claude Code as an anti-drift contract)
**Companion to:** `ATOM_FX_ARCHITECTURE.md` · **Platform:** Android (Jetpack Compose, Material 3)

---

## 0. How to read this document

This is the **binding visual & interaction contract** for ATOM FX. It defines *what the app looks like and how it behaves* down to tokens, geometry, motion curves, and the exact contents of every sheet. Claude Code builds against this; when a UI decision isn't covered here, ask rather than invent.

Terminology aligns with the Energy Wheel brief. The architecture doc owns *what the numbers are*; this doc owns *how they are seen and touched*. The six factors, their order, names, and colours are **identical** across both documents and `potential_config.py`.

**The one-line north star (spec §70):**
> *Look → identify outer-ring pairs → tap → understand why → decide whether to enter.*
> The market is the atom. Currencies are the forces. Regime is the environment. Breadth and momentum reveal whether the move is real. Structure confirms it. Entry location decides if it's actionable. Pairs move outward as evidence accumulates.

**Design intent (spec §54):** *Elegant institutional terminal + modern mobile finance app.* Restrained, precise, information-dense. The wheel is the hero. Never a neon arcade; colour is information, not decoration.

---

## 1. Design language

**Feel:** quiet, engineered, precise — in either theme. A deep, calm ground with subtle radial illumination (near-black by night, cool paper by day); thin technical lines; a small, disciplined status-colour palette; excellent spacing and precise alignment; high-quality typography. Depth comes from *light and blur* in dark and from *soft shadow and hairlines* in light — never from big cards, heavy borders, or rounded-rectangle soup. The two themes are the same instrument under different lighting, not two different designs.

**Avoid (spec §54):** excessive gradients, glassmorphism overkill, cartoon effects, giant cards, rounded-rectangle clutter, gratuitous icons, rainbow colour, dense tables, flashing, spinning, particles.

**Use:** thin strokes, subtle radial fields, controlled glow, one accent temperature per state, generous negative space, and motion that *only* communicates change.

---

## 2. Colour tokens

**ATOM FX ships two themes — dark and light — and follows the OS setting with a manual override in Settings.** Semantic first (spec §55): green = supportive/bullish/advancing; red = negative/bearish/failing; amber = watch/developing; grey = neutral/inactive. Colour encodes **market state**, never variety. The two themes share the *same semantics and the same layout*; only the token values change. Neutrals are deliberately **cool-biased** (a hint of blue), never a flat grey. These values match the reference mockups exactly — treat mockup and doc as one palette.

### 2.1 Theme strategy (Compose)

Define **one token set** (a `data class AtomColors`) and provide two instances, `DarkColors` and `LightColors`, via a `CompositionLocal`. Every component reads tokens (`AtomTheme.colors.bull`), **never a literal hex**. The wheel `Canvas` receives the resolved token set as a parameter so it repaints correctly on theme change. Theme source: `isSystemInDarkTheme()` by default, overridable by a stored user preference (`system | dark | light`). Animate theme switches with a ~200ms cross-fade.

### 2.2 Ground & surface

| Token | Dark | Light | Use |
|---|---|---|---|
| `ground` | `#0A0F16` | `#EDF1F6` | app background (dark: near-black cool; light: cool paper, **not** pure white) |
| `ground-radial` | `#121C29` | `#FFFFFF` | centre of the wheel's radial field, fading to `ground` |
| `surface` | `#0E141B` | `#FFFFFF` | bottom sheets, panels |
| `surface-raised` | `#161F29` | `#F2F6FA` | pressed/active rows, chips |
| `hairline` | `#1E2833` | `#DDE4EC` | 1px separators, ring base strokes |
| `hairline-strong` | `#2C3947` | `#C6D1DD` | active ring / selected outline |
| `control-surface` | `#212C38` | `#F2F6FA` | fill for a **control** — see below |
| `control-border` | `#2C3947` | `#DDE4EC` | border for a **control** — see below |
| `card-surface` | `#161F29` | `#FFFFFF` | fill for a **card** — see below |

**Controls vs. cards (added 2026-09-03, confirmed on-device — the precedent for every future card
or button).** Two kinds of surface, never a third:

- **A control is anything directly pressable** that isn't already its own pill/chip — the Summary
  button, a Cascade row, a Currency Flow ticker chip. Fill `control-surface`; always bordered with
  `control-border` (a plain grey, never a status colour) so a control reads as "this has an edge
  you can press." A control is always **lighter than `ground`, in both themes** — dark theme reads
  "brighter," light theme reads "lighter than the page," but it's the same direction: elevated =
  lighter. The border itself flips per theme: **lighter than the fill in dark** (a highlight
  catching the edge), **darker than the fill in light** (unchanged from the first pass — it was
  already right).
- **A card is a non-pressable grouping container** — Tradeable Now, Watch. Fill `card-surface`;
  **never a border**, in either theme — a card just groups content, it doesn't invite a tap itself
  (whatever's tappable inside it, e.g. a pill, carries its own border). Light theme's plain white
  card was already right and is untouched; dark theme's plain `surface` sat almost on top of
  `ground`, so cards get their own, lighter fill (`= surface-raised`).

### 2.3 Text

| Token | Dark | Light | Use |
|---|---|---|---|
| `text-primary` | `#EAF0F6` | `#0E141B` | primary values, pair names |
| `text-secondary` | `#9DB0C0` | `#4B5A6B` | labels, secondary lines |
| `text-muted` | `#61707E` | `#8A98A8` | inactive, captions, "stale" |

### 2.4 Status (the only chromatic colours)

| Token | Dark | Light | Meaning |
|---|---|---|---|
| `bull` | `#2FBF71` | `#159E5B` | bullish / supportive / advancing |
| `bull-soft` | `#2FBF71` @ 13% | `#159E5B` @ 9% | soft fill for bull pills / ring tint |
| `bear` | `#E5484D` | `#D0383D` | bearish / failing |
| `bear-soft` | `#E5484D` @ 13% | `#D0383D` @ 8% | soft fill for bear/blocked |
| `watch` | `#E7AE3A` | `#B27A16` | developing / watch / neutral-positive |
| `watch-soft` | `#E7AE3A` @ 13% | `#B27A16` @ 8% | soft fill for watch |
| `neutral` | `#61707E` | `#93A0AD` | no thesis / inactive |

USD carries no fixed hue — its framing follows the regime (risk-off ⇒ bear-tinted, risk-on ⇒ neutral).

> **Glow vs. shadow (theme-dependent).**
> **Dark:** glow is the status colour at 8–18% alpha, blurred, radius scaled by potential — high-potential nodes get "strong but controlled" glow (spec §14), low-potential nodes get none.
> **Light:** additive glow reads as haze on paper, so replace it with a **soft coloured shadow / low-alpha halo ring** (status colour at ~12%) plus a slightly heavier node stroke. Same *information* (high potential = more presence), different physical means. Never exceed the alphas in §7.4.

### 2.5 Regime accent (nucleus + status strip border)

`Risk-On → bull` · `Risk-Off → bear` · `Ranging → neutral` · `Mixed → watch`, resolved per theme. Confidence modulates the accent's alpha (High 100%, Medium 70%, Low 45%), never its hue.

### 2.6 Visual reference

The reference mockups (`ATOM FX Energy Wheel` artifact) render the landing screen in **both themes**, plus the pair sheet, currency-flow sheet, and recommendation panel, using exactly these tokens and the canonical Risk-On example state. Build to match them.

---

## 3. Typography

One family, four sizes, tight discipline (mirrors Pieter's "unified 4-level type scale" convention).

- **Family:** a technical grotesque — `Inter` (or `IBM Plex Sans`) for UI; **tabular figures on** for all numbers so they don't jitter when they update. A monospace (`JetBrains Mono` / `Roboto Mono`) only for raw price/level readouts.
- **Scale (sp):** `Display 28/600` (nucleus score, pair sheet header) · `Title 17/600` (sheet titles, section heads) · `Body 13/500` (values, rows) · `Caption 10/600 tracking+0.06em uppercase` (ring labels, pill captions, "UPDATED").
- Numbers are **always** tabular; deltas always carry a sign and an arrow (`+8 ↑`, `−10 ↓`).

---

## 4. Spacing, shape, elevation

- **Grid:** 4px base. Standard insets 16px; dense rows 12px; pill gaps 8px.
- **Radii:** sheets 20px (top corners only), chips/pills 999px (fully round), cards (rare) 14px. No nested rounded rectangles.
- **Elevation:** expressed with a 1px `hairline` + a soft shadow (`#000` 30–40% alpha, y+8, blur 24) — not with Material's default grey overlays.
- **Safe areas (spec §43):** honour status bar, nav bar, and display cutouts. The wheel uses the full available height (`fillMaxSize` minus insets); nothing critical sits under a system bar.

---

## 5. Screen architecture (information architecture)

The landing screen **is** the wheel (spec §61). Vertical composition, top→bottom:

```
┌───────────────────────────────────────────────┐
│  HEADER  ATOM FX   RISK ON ↑    Updated 09:42  │  ← §9
│          EUR leading · USD weakening    ● Fresh │
├───────────────────────────────────────────────┤
│  STATUS STRIP  REGIME · LEADER · LAGGARD ·     │  ← §10 (no scroll to see it)
│                BREADTH · TOP PAIR               │
├───────────────────────────────────────────────┤
│                                                 │
│                                                 │
│              ◜  ENERGY  WHEEL  ◝                │  ← §6, the hero (Canvas)
│                (6 rings, 12 nodes,              │
│                 nucleus = market state)         │
│                                                 │
│                                                 │
├───────────────────────────────────────────────┤
│  TRADEABLE NOW   EURUSD ↑86  EURJPY ↑79  …      │  ← §11 (or "NO A+ SETUPS")
└───────────────────────────────────────────────┘
   ▬ bottom nav (4 tabs): Wheel · Currency · Macro · Insights   ← §5.1, swipeable
   ▲ bottom sheets: factor sheets + pair sheet + currency detail  ← §13–14
```

### 5.1 Top-level navigation — bottom nav + swipe

Four peer destinations in a **Material 3 bottom navigation bar**, and the body is a **`HorizontalPager` you can swipe between**: `Wheel · Currency · Macro · Insights`. The active tab is the accent colour; swiping moves the pager and the nav highlight in sync. Settings is reached from the header gear (not a tab). This replaces the earlier edge-panel scheme — **Recommendation, News, and Calendar now live in the Insights tab** — which is cleaner and more conventional. The nav bar sits on `surface` with a hairline top border; icons are simple line glyphs, labels in Caption.

- **Bottom sheets** (§13) remain the detail mechanism, rising above whichever tab is active: tap a ring → factor sheet; tap a node → pair sheet; tap nucleus → regime sheet; tap a currency node → currency detail sheet.
- **Scrolling pills** (§15) appear in Tradeable Now, the pair-sheet tabs, the TF toggles, and the currency rows.

---

## 6. The Energy Wheel (the hero)

**Superseded 2026-09-05/06/09 (the Simplification Rework) — corrected in this doc 2026-09-17.**
Everything in §6.1–§6.9 and §6A below describes the *original* six-concentric-ring wheel plus a
separate Currency Strength Wheel. Neither exists any more: there is one wheel, pairs-only, with
4 selectable wings (Setup/Trend/Momentum/Volatility via the corner buttons) instead of six rings,
and currency strength lives in the always-on `CsmBarStrip` below the wheel, not a second wheel or
an on-dial mode (`ATOM_FX_WHEEL_V2_SPEC.md` §12 has the full rework spec; §19's component
checklist below is current truth). Kept here, unedited, as a historical record of the original
design rather than silently deleted — but do not build against anything in §6.1–§6A.

Drawn on a Compose `Canvas`. Clean geometry, minimal text inside (spec §27, §133): only ring labels, pair nodes, pair names, potential numbers, subtle directional marks, and the central state. Everything else lives in sheets.

### 6.1 Geometry (deterministic — spec §62–§64)

- Square coordinate space, logical `viewBox 0..1000`, centre `(500,500)`, scaled to fit the available square.
- **Six concentric levels** + nucleus. Reference radii (scale to viewport):
  `R0=100 (nucleus) · R1=170 · R2=240 · R3=310 · R4=380 · R5=450 · R6=500 (outer edge)`.
- **Pair angle is permanent:** `angle(index) = index·30° − 90°`, so EURUSD sits at 12 o'clock, and the sequence runs clockwise in the §2.1-architecture pair order. **Angle = identity, radius = potential** (spec §4, §156). Never reorder by rank.
- Node centre: `x = 500 + R(level)·cos(angle)`, `y = 500 + R(level)·sin(angle)`. Store *level*, compute pixels (spec §64 — no stored pixel coordinates).

### 6.2 Layer draw order (Canvas z-stack)

```
1. radial background field   (ground-radial → ground)
2. rings                     (6 thin arcs/circles, §6.3)
3. radial paths              (per pair, centre → node, §6.6)
4. factor markers            (6 dots along each path, §6.7)
5. pair nodes                (§6.4)
6. nucleus                   (§6.5)
7. top-pair halo             (§6.8, above nodes but below nucleus label)
```

### 6.3 Rings (spec §16, §66)

Thin strokes, subtle gradients, controlled glow — never solid fills (spec §16). Base ring stroke = `hairline`, 1px. A ring brightens toward the outer edge and takes a faint status tint only when meaningful:

| Ring | Label (Caption, uppercase) | Base look | Active tint |
|---|---|---|---|
| 1 | `1 · REGIME` | subtle | regime accent at low alpha |
| 2 | `2 · CURRENCY FLOW` | slightly brighter | flow leader colour, low alpha |
| 3 | `3 · BREADTH` | more visible | breadth band colour |
| 4 | `4 · MOMENTUM` | green/amber by aggregate | bull/watch |
| 5 | `5 · STRUCTURE` | stronger | structure direction colour |
| 6 | `6 · ENTRY SETUP` | outer luminous boundary | soft bull glow if any pair reaches it |

Ring labels sit just inside their ring on the vertical (12 o'clock gap is reserved for EURUSD's node — place labels at ~7–8 o'clock along the ring or as a short radial legend on the left; keep them from colliding with nodes). Labels are quiet until the ring is hovered/tapped, then the whole ring highlights (spec §46).

**Ring hit target (spec §18, §45):** the entire ring circumference has a **generous invisible touch band** (≥ 24dp thick) so the user never has to hit a thin stroke. Tap anywhere on a ring band → that factor's bottom sheet.

### 6.4 Pair nodes (spec §13, §45)

- A circular node, **min 44×44dp touch target** (visual disc smaller, hit area padded).
- Inside/under the node: pair code (Caption) + **Potential number** (Body, tabular) — e.g. `EURUSD` / `81`. Potential is the primary visual number (spec §13); Setup Rank lives in the pair sheet.
- **Direction encoded redundantly** (never colour alone — spec §15, §36): a tiny up-tick glyph for bull, down-tick for bear, dash for neutral, *plus* colour (`bull`/`bear`/`neutral`) *plus* glow intensity by potential.
- **Three visual states (spec §14):**
  - *Low potential* (inner rings): muted node, `text-muted`/`*-dim`, little/no glow, nearly static.
  - *Medium* (middle rings): visible, moderate glow.
  - *High* (outer rings): bright node, strong-but-controlled glow.
- Nodes never overlap ring labels; if a node sits at level 0 it rests just outside the nucleus at R0's edge on its fixed angle (clustered but distinguishable).

### 6.5 The nucleus (spec §17)

The centre answers *"What is the market doing?"* (the pairs answer *"What can I trade?"*). Contents, stacked:
```
        RISK ON            ← regime, Title, regime-accent colour
         Strong            ← strength word (from score/confidence)
         +7.4              ← regime score, Display, tabular
      High confidence      ← Caption, text-secondary
  EUR leading · USD weakening   ← Caption, currency_flow line
```
The nucleus is a filled soft radial (`ground-radial`) with a 1px `hairline-strong` rim tinted by regime accent. **Tapping the nucleus opens the Regime bottom sheet** (spec §17). If a `recommendation.headline` exists, show it as a single quiet line beneath the flow line (truncated), tappable → Recommendation panel.

### 6.6 Radial paths (spec §47)

Each pair has a subtle radial line from centre through its node — "this pair has travelled this far." Path stroke `hairline` at low alpha; the *travelled* segment (centre → node) tinted by direction at low alpha; the *untravelled* segment (node → edge) barely visible. Small dots at each passed level along the path.

### 6.7 Factor markers (spec §48) — one of the most important details

Along each pair's radial path, six tiny markers in fixed order:
```
R  F  B  M  S  E     (Regime · Flow · Breadth · Momentum · Structure · Entry)
```
- **Passed** marker: bright (status colour by direction).
- **Failed / not-yet** marker: dark (`text-muted` at low alpha).
- Rendered compactly so that, *without opening any sheet*, `● ● ● ● ● ○` reads as "5/6, blocked at Entry." This is the at-a-glance "why it stopped."

### 6.8 Top-pair halo (spec §50)

The current #1 setup gets a **thin animated halo** ring around its node (not a flash). On change of top pair, the old halo fades (~300ms) and the new node gains it. One halo at a time.

### 6.9 What the wheel must NOT contain

No tables, no long text, no more than the elements in §6.2. Detail belongs in sheets (spec §27). The wheel is geometrically stable — it never rotates or spins (spec §56).

---

## 6A. The Currency Strength Wheel (the `Currency` tab)

A second `Canvas` wheel, visually a sibling of the pair wheel but semantically distinct: **radius = currency strength**, not potential.

- **8 currency nodes** at fixed angles, split into two arcs: **risk bloc** (EUR, GBP, AUD, NZD, CAD) across the **top**, **defensive bloc** (USD, JPY, CHF) across the **bottom**, with a faint dashed divider and quiet `RISK BLOC` / `DEFENSIVE` labels. The point of the split is the *gestalt*: a top-heavy bloom reads risk-on, bottom-heavy reads risk-off.
- **Radius** maps CSM 0–100 from centre (weak) to edge (strong); faint concentric guide rings at 25/50/75/100.
- **Node fill = strength tier** (`bull` ≥60 · `watch` 40–60 · `bear` ≤40); a **Δ chevron** just outside each node shows CSM-delta (flow) in `bull`/`bear`. Strong nodes carry the same glow (dark) / soft-shadow (light) treatment as the pair wheel.
- **Centre** shows the flow leader and laggard (`EUR +8` / `USD −10`).
- **Breadth-gates the glow:** a broadly-supported currency looks more solid than a narrowly-strong one (§2.4 alphas).
- **Interaction:** tap a node → Currency detail sheet; long-press or a "show on wheel" control cross-highlights that currency's pairs on the pair wheel. TF toggle (D1/H4/H1) via pills.

It shares the pair wheel's restraint, geometry discipline, motion vocabulary (radial drift when strength changes), and both themes. Keep the two wheels visually distinguishable — the currency wheel has no six-ring confluence structure, so its guide rings are quieter and evenly spaced.

## 7. Motion (spec §28, §29, §56)

Animation exists **only to communicate a state change.** Geometry stays stable.

### 7.1 Radial advance / retreat

When a pair changes level between data loads, animate its node (and path/markers) along the radius:
- **Duration:** 400–700ms. **Easing:** a smooth ease-in-out (e.g. `FastOutSlowIn`); **no bounce, no overshoot** (spec §28).
- Advance (gaining energy) and retreat (losing a factor) use the same curve; the marker for the changed factor flips its brightness in sync.

### 7.2 Ambient

- High-potential (level 5–6) nodes may have a **very subtle breathing glow** (alpha oscillation ±4%, ~3s period). Low-potential nodes are static (spec §29).
- If multiple pairs move at once, **stagger** starts by 40–80ms (spec §29).

### 7.3 Regime change

A gentle cross-fade of the nucleus accent and ring tints (~500ms). No hard cuts, no flashing.

### 7.4 Motion & glow limits

No spinning, bouncing, flashing, or particle effects (spec §56). Glow alpha ceilings (dark): node core ≤ 90%, glow field ≤ 18%, halo ≤ 60%. **In light theme, glow is replaced by a soft coloured shadow / halo ring (≤ 14%) per §2.4** — same "presence scales with potential" behaviour, no additive haze. Respect the system "reduce motion" setting — when on, disable breathing and shorten transitions to a cross-fade.

---

## 8. Loading, stale & empty states (spec §57–§59)

- **Cold load:** wheel **skeleton** (rings + faint node placeholders), then populate in order: regime → currency flow → pair states → details. Never show fake data.
- **`signals.json` unavailable:** nucleus reads `DATA UNAVAILABLE`; nodes rest at nucleus, muted.
- **Stale (`updated` older than scan interval):** keep the last-good visual but stamp `DATA STALE` in the header and dim the freshness dot to `bear`. Never present old data as current (spec §58).
- **No qualifying setups:** Tradeable Now shows `NO A+ SETUPS` + `Closest: EURUSD — Level 5/6` (spec §32). The system is comfortable saying **NO TRADE**; never populate the outer ring artificially.
- **Empty lists** (Watchlist, Notification History, Library search, a chart with no history yet) —
  **terse, plain, no instructional copy**: state what's missing and stop. `"Watchlist empty"`,
  `"No notifications yet"`, `"Not available yet"` — not a sentence explaining what the screen is
  for or how to fill it. Standard empty-state UX guidance (an illustration, an explanation, a
  call-to-action button) is written for consumer *onboarding* empty states — first launch, nothing
  set up yet. This app doesn't have that kind; every empty state here is a *utility* one (a list
  that currently has zero items in an otherwise fully-populated, data-dense tool), and the terse
  form already matches the app's own restrained/no-arcade voice (§20's acceptance test language).
  Pieter's rule, 2026-09-10, after `WatchlistScreen.kt`'s own empty state ("Nothing watched yet —
  tap the bookmark on a pair sheet to add one, usually right after a BB touch alert.") was the one
  place in the app that had drifted from what every other empty string already did correctly.

---

## 9. Header (spec §30)

Compact, single band:
```
ATOM FX        RISK ON ↑            Updated 09:42
               EUR leading · USD weakening      ● Fresh   [Volatility Normal]
```
- Left: wordmark `ATOM FX` (Caption, tracking). Centre/near-left: regime + arrow (regime accent). Sub-line: currency-flow one-liner.
- Right: `Updated HH:MM` (device-local or UTC label like `London`), a freshness dot (`bull` fresh / `bear` stale), optional volatility word. Keep it to two lines max.

---

## 10. Status strip (spec §31)

A compact, non-scrolling strip directly under the header — visible without scrolling. Five micro-cells, hairline-separated (not big cards — spec §31):
```
REGIME      LEADER     LAGGARD    BREADTH    TOP PAIR
Risk-On     EUR +8     USD −10    Strong     EURUSD
```
Each cell: Caption label + Body value; leader/laggard carry signed deltas and arrows; breadth shows the band word coloured by band. Tapping a cell opens the matching sheet (regime/flow/flow/breadth/pair).

**Superseded 2026-09-03 (commit `658d2a2`) — the strip is now a single "SUMMARY" button.**
Collapsed, only the button shows; tapping it grows a vertical list of cards in place ("the
Cascade" — Item Library #03's grow-from-the-icon mechanic, staggered open/close) covering the
same five values above plus four more the strip never had room for. Same tap-a-row → matching
sheet behaviour, same Caption label / Body value per row. Kept here as the smallest strip layout
the design ever specified; `StatusStrip.kt` is current truth over the five-cell mock above where
they disagree.

---

## 11. Tradeable Now / Watch (spec §32–§34)

**Retired 2026-09-04, corrected in this doc 2026-09-17.** There is no standalone pill band any
more. That job is now the status-strip glyph row (§10's own "SUMMARY"/glyph-row supersession
note, above) plus the wheel's own Setup wing — see §19's component checklist for current truth.
Kept below as a historical record only.

Bottom band above the nav. A horizontal **scrolling pill row**:
- **Tradeable** (level 6 only): `EURUSD ↑ 86` pills, `bull`/`bear` tinted, ranked left→right by setup rank (ranking is separate from wheel position — spec §49). `A+` pills get a subtle brighter rim.
- If none reach level 6: `NO A+ SETUPS` + a single muted `Closest: <pair> — Level 5/6` pill.
- A secondary **Watch** row (levels 3–5) may follow, labelled `WATCH`, amber-tinted, e.g. `EURJPY · 5/6 · waiting for entry reset` (spec §33). Tapping any pill opens that pair's sheet.

---

## 12. Edge panels (spec §44 style, Pieter's request)

**Superseded — corrected in this doc 2026-09-17.** Neither panel is reached by an edge swipe any
more. RECOMMENDATION content moved into the status-strip glyph row (§10) — there's no separate
recommendation panel. CALENDAR is now `CalendarSheet.kt`, a regular bottom sheet opened from a
header calendar icon (with an unread-event dot, same treatment as the gear/watchlist icons — see
`ATOM_FX_BUILD_STATUS.md` §B "Header green dots"). Settings and Watchlist are reached the same
icon-triggered way, as slide-in panels, not edge-swipe gestures. **A fifth header icon, SESSIONS
(added 2026-09-17, `SessionsSheet.kt`), joins this same group** — a clock glyph next to Calendar,
opening the four FX trading sessions' current status/countdown as the same kind of slide-in panel;
its dot lights on a session OVERLAP specifically (London-NY, Tokyo-London), not merely "any session
open," since some session is open most of the day regardless and a dot lit almost constantly would
mean nothing. Kept below as a historical record.

- **Left — RECOMMENDATION** (the AI nucleus, §ai): headline, action chip (`TRADE`/`WATCH`/`STAND ASIDE`), primary pair + direction, confidence, rationale (40–60 words), invalidation line, and next catalyst. Sourced from `recommendation` (architecture §6); falls back to `deep_analysis` if absent.
- **Right — CALENDAR / EVENTS**: high-impact events (currency chip, name, time, forecast vs previous, one-line note). Pairs whose currency has an event in the next 24h are flagged here and get a subtle rim on their node (spec §44/§60 calendar behaviour).

Edge panels use `surface`, 20px inner corner, a drag handle, swipe-to-dismiss, tap-scrim-to-close.

---

## 13. Bottom sheets — mechanics (spec §44)

**Superseded 2026-09-03 (Pieter, direct in-session ask) — the three-detent model below is
replaced by a single-rise model.** `skipPartiallyExpanded = true`: a sheet rises straight to fit
its content, no intermediate `collapsed`/`half` stop to drag through and none to get stuck at on
the way back down — the old two-detent dismiss (drag to `half`, drag again to close) needed two
flicks; skipping the stop means one drag-down closes it. Capped at **80% of the screen height**
(`MAX_SHEET_HEIGHT_FRACTION`, `BottomSheetHost.kt`) so the scrim above it always shows enough of
the screen behind to read as an overlay, not a takeover; content taller than that scrolls inside
the sheet instead of clipping (the bug that prompted this: the Pair sheet's last WHY card was cut
off with no way to reach it — there was no scroll container at all under the old three-detent
setup). The wheel-visible-behind-at-half spatial-context idea is gone along with the half detent.

~~A single reusable draggable bottom sheet with three detents: `collapsed` (peek ~12%), `half`
(~48%, wheel remains visible behind), `expanded` (~92%).~~ Still current: drag handle, swipe-down
to dismiss, tap-scrim to dismiss, smooth spring, `surface`, 20px top corners, drag handle, a
Title, then content. Numbers tabular. No dense tables — use aligned rows and small bar meters.

### 13.1 Ring → factor sheet routing

**Superseded — corrected in this doc 2026-09-17.** There are no rings to tap any more (§6's own
notice). Current routing: tap nucleus/hub → Regime sheet (still current). Tap a pair node → Pair
sheet (§14.7, rewritten below). Tap a currency in the `CsmBarStrip` → `CurrencyDetailSheet`. The
six individual factor sheets in §14.1–§14.6 below no longer exist as separate tap targets —
`BreadthSheet.kt` in particular is confirmed unreachable dead code
(`ATOM_FX_BUILD_STATUS.md` outstanding item 11) since nothing can produce the `Ring` sheet target
that used to route to it. Momentum/Structure content moved into the Pair sheet's own Breakdown
tab (§14.7); Entry/Flow content was retired outright. Kept below as a historical record.

---

## 14. Bottom sheet contents (exact)

**§14.1–§14.6 below are historical record, superseded — see §13.1's own notice.** Only §14.7
(Pair sheet, rewritten 2026-09-17) reflects current behavior.

Each factor sheet is a **teaching surface**: it explains that analytical layer so the user need not inspect individual pairs (spec §18). Contents below are canonical.

### 14.1 Regime sheet (spec §19)

```
MARKET REGIME
RISK ON            Confidence: HIGH
H4: Risk-On   D1: Risk-On   H1: Risk-On        ← if they disagree, show clearly (amber divergence note)
──
Safe Haven vs Risk Basket   Risk 74 · Safe 31
USD Proxy                   USD 28 · Non-USD 67
Pair Balance                Bull 8 · Bear 2 · Neutral 2
──
Regime stability: Stable for 4 scans
```
Values map to `regime_h4/d1/h1` and the frozen vote inputs. Divergence between D1/H4/H1 gets an amber one-liner (Pieter's dual-regime convention).

### 14.2 Currency Flow sheet (spec §20)

Eight currencies ranked, each row: `CCY · Strength(0–100) · Δ · Flow arrows`.
```
CURRENCY FLOW
NZD  81  +3   ↑
EUR  72  +8   ↑↑
AUD  76  +4   ↑
GBP  63  +2   ↑
CAD  54  +1   →
CHF  38  −5   ↓
JPY  31  −7   ↓↓
USD  28  −10  ↓↓↓
──
CURRENT LEADER: EUR      CURRENT LAGGARD: USD
```
Arrow count encodes delta magnitude. Strength from `csm.h4`, Δ from `csm_delta.h4`, summary from `currency_flow`. **2026-09-02:** the sheet shows flow leader/laggard only — Pieter dropped the absolute-leader/laggard row to keep one clear number per currency (the data still exists in `signals.json`; see Glossary). Also reachable live from the wheel's Currency Flow ticker (§6A) in Currencies mode, not just this sheet.

### 14.3 Breadth sheet (spec §21)

Per currency, a small bar meter + `support/total` + band:
```
CURRENCY BREADTH
EUR  ███████░  7/8   Strong
USD  ███████   7/7   Strong
AUD  █████░░░  5/8   Moderate
…
──
For EURUSD:  EUR strength 7/8 · USD weakness 7/7 · Broad agreement: YES
```
Bands: Strong ≥70% · Moderate 50–69% · Weak <50%. Data from `breadth.h4`.

### 14.4 Momentum sheet (spec §22)

`D1 / H4 / H1` **scrolling pill tabs**; per tab MOM1212 + delta; then CMP + status. Optional sparkline only if history exists (spec §22 — none invented).
```
MOMENTUM        [D1] H4  H1
D1:  68  ↑ +12
H4:  72  ↑ +8
H1:  65  ↑ +5
──
CMP 70          BULLISH MOMENTUM
```
Values from `pairs.<PAIR>.mom`. No unrelated indicators here.

### 14.5 Structure sheet (spec §23)

```
MARKET STRUCTURE
D1: Bullish   H4: Bullish   H1: —
Last event: BOS ↑           Strength: 0.78
Higher Highs · Higher Lows
```
If **CHoCH** detected → a prominent amber/red warning line (spec §23). Data from `pairs.<PAIR>.structure` (architecture §5.3).

### 14.6 Entry Setup sheet (spec §24)

```
ENTRY SETUP
Setup Score 8.4 / 10        Continuation 84%
ADX 27    Reset 38    ATR percentile 42%
Trend alignment: D1 ↑  H4 ↑  H1 ↑
EMA200: Price above
──
Entry state: GOOD LOCATION      (or EXTENDED / WAIT FOR RESET)
```
Answers "even though attractive, should I enter now?" Setup Score = frozen `rank`, continuation/reset/ATR/ADX from frozen fields.

### 14.7 Pair sheet (spec §25–§26) — the most important surface

**Rewritten 2026-09-17 to match what's actually shipped, reworked 2026-09-05/09 from the
6-factor pass/fail WHY checklist below to 3 tabs** (`ATOM_FX_BUILD_STATUS.md` §B): **Overview**
(5 informational consensus rows — Regime D1 / Trend H4 / Momentum H4 / Volatility D1 / Structure
H4, real bull/bear/watch tints, no pass/fail gate glyphs any more), **Breakdown** (Momentum,
Structure, Alignment sub-sections), **Correlation** (unchanged in spirit from the original spec
below — correlated pairs so duplicate exposure is visible). `FlowSheet`/`BreadthSheet`/
`EntrySheet`/`RecommendationSheet`/`CurrencyFlowSheet` are gone — deleted outright or orphaned.
The 3-TF alignment strip and D1/H4/H1 sparkline row described below are still current. Everything
from "Then, immediately" through the old tab list is historical record of the original spec, not
current behavior.

Header:
```
EURUSD   EUR / USD
HIGH POTENTIAL · LONG
Potential 86 · Rank #1 / 12
```
**3-TF alignment strip (Signals Roadmap §2.7, added 2026-09-04):** immediately below the header,
above the D1/H4/H1 sparkline row — three small squares, one per timeframe, each an abbreviated
read of `pills.{d1,h4,h1}` (SB/B/N/S/SS for Strong Buy…Strong Sell) on a squircle tinted by the
same 5-step ramp the pill colours already use (`bull` → `bullSoft` → `neutral` → `bearSoft` →
`bear`). Same "label above, tinted square, centred value" recipe as the Momentum tab's bars —
reused, not reinvented. Absent entirely (no placeholder row) when the pair has no pills data.
Pairs with all three at Strong Buy/Strong Sell also fire the `tf_alignment` push alert (§2.6).

Then, immediately (Overview default — never hide the "why" behind tabs, spec §26):
```
WHY?
✓ REGIME     Risk-On supports EURUSD
✓ FLOW       EUR +8 / USD −10
✓ BREADTH    EUR 7/8 · USD 7/7
✓ MOMENTUM   CMP 70 · D1 delta +12
✓ STRUCTURE  Bullish BOS
✗ ENTRY      Extended · wait for reset      ← the blocking factor is unmistakable
```
Each row: status glyph (✓ passed / ✗ failed, coloured), factor name, one-line explanation, current value. The **blocked factor** is visually distinct so the user instantly sees *what is preventing further advancement* (spec §25, acceptance Q10).

Compact **scrolling pill tabs** (spec §26): `Overview · Momentum · Structure · Flow · Entry · Macro · Correlation`. Overview is default and already contains the six-factor WHY. Macro tab shows cross-asset support lines (spec §36: `DXY ↓ → USD offered`, `SPX ↑ → risk appetite`). Correlation tab shows correlated pairs so duplicate exposure is visible (spec §37: `GBPUSD +0.85 · NZDUSD +0.78`). Cross-asset and correlation get **no ring** — they're supporting evidence only (spec §36–§37).

---

## 15. Scrolling pills (component)

A reusable horizontally-scrollable, snap-friendly pill row used for: Tradeable Now / Watch, pair-sheet tabs, momentum TF tabs, and currency chips. Pills are fully round (`999px`), Caption text, 8px gaps, `surface-raised` when active with a status-tinted rim; inactive `surface` with `text-secondary`. Momentum/scroll on overflow; the active pill scrolls into view.

---

## 16. Touch & desktop behaviour

- **Touch (spec §45):** node ≥44dp; ring = wide invisible band; tap nucleus → regime; tap background → close/deselect. No precision tapping ever required.
- **Haptics (added 2026-09-02, Pieter's rule):** every interactive control gives haptic feedback on tap — wheel wedges/nucleus, the mode toggle, status-strip cells, pills, sheet tabs, everything tappable. Use the lightest appropriate `HapticFeedbackType` (e.g. `TextHandleMove` for a plain selection tap) via `LocalHapticFeedback`; reserve stronger feedback for consequential actions. This is non-negotiable, same tier as §17/§20 — check it on every new interactive element, not just the wheel.
- **Long-press a node →** the simplified line chart (native, no candles — Functional Spec §8), showing D1/H4/H1 close-price lines so up/down reads instantly.
- **Hover (large screens / desktop, spec §46):** node enlarges slightly, its radial path highlights, a tiny tooltip shows `PAIR · LONG · Level 6/6 · Potential 86`; ring hover highlights the whole ring. Hover augments, never replaces tapping.

---

## 17. Responsive & Android specifics (spec §43)

**Corrected 2026-09-17 — the "wheel sized to `min(width, height − chrome)`, screen never
scrolls" rule below was superseded 2026-09-03 (`WheelScreen.kt`, Pieter, flagged in-code as a
deliberate exception, never previously synced to this doc).** What actually ships: the wheel is
sized purely from **width** (`aspectRatio(1f)`, not `min(width, height-chrome)`), staying the
same size whether or not a taller panel is open, and the whole landing column sits in a
`verticalScroll` as a safety net — not a designed everyday behavior. Verified on-device that this
never actually scrolls in normal use (header, status strip, wheel, CSM strip, and D1/H4/H1 row
all still fit in one screen); the scroll capability exists so nothing ever gets cut off or forces
the wheel to shrink if content ever runs long, and it's also load-bearing for drag-down-to-refresh
(2026-09-09, `PullToRefreshBox` nests inside this same scroll container). **The still-binding
part of the original rule stands: never make the wheel itself shrink to accommodate other
content, and the landing screen should still never *need* to scroll in practice** — if a change
ever makes it visibly scroll on a real device, treat that as a regression to fix, not a new
normal.

- **Phone (primary), top to bottom:** header → status strip (the ranked-pairs recommendation glyph row — see below) → the wheel dial → the always-on Currency Strength Meter strip (Strength/Flow toggle) → D1/H4/H1 timeframe buttons. Settings/Watchlist/Calendar/Sessions are now icon-triggered slide-in panels (§12's own notice), not edge-summoned. Wheel fits with **no horizontal scroll**; uses full dynamic viewport height minus insets; safe-area respected.
- **There is no separate "Tradeable Now" card** (2026-09-04, Pieter's call — the standalone Tradeable Now/Watch card, and later the Strength/Potential ticker that replaced it, are both gone for good). That job is now served by two always-visible reads together: the status strip's ranked-pairs glyph row (`signals.ranked.top`, no longer count-capped since the 2026-09-17 score-floor change — see `ATOM_FX_BUILD_STATUS.md` §A — tap one to reflow open its Regime/Trend/Momentum/Volatility/Structure consensus + rank), and the wheel's own Setup wing (labelled "SETUP" on the wheel since 2026-09-09, internally still `WheelMode.OVERALL`; all 12 pairs at once, wedge size/colour = Continuation Score / Setup Band — see §20 and `ATOM_FX_WHEEL_V2_SPEC.md` §11).
- **Tablet / landscape:** wheel ~60–65% width; a compact market summary on one side; factor summary on the other; sheets still available.

---

## 18. Accessibility (spec §65)

- Every node has a content description: e.g. *"EURUSD, long, high potential, six of six factors passed."* Ring bands: *"Currency Flow ring, tap for detail."* Nucleus: *"Market regime Risk-On, high confidence, tap for detail."*
- Never rely on colour alone — arrows/glyphs/labels always accompany hue (spec §15, §36).
- Meet contrast on text against `surface`/`ground`; status colours chosen to remain distinguishable for common colour-vision deficiencies (bull green / bear red pair reinforced by shape).
- Respect system font scaling (layout reflows; the wheel keeps geometry, text truncates gracefully) and reduce-motion.

---

## 19. Component checklist (what Claude Code builds)

Updated 2026-09-10 to match what's actually shipped post Wheel v2 + the Simplification Rework
(2026-09-05/06/09) — the version below superseded the original brief's list, which described a
4-tab nav, a separate currency wheel, and a six-factor WHY checklist that no longer exist.

```
AppScaffold         bottom nav (3 tabs: Wheel · Macro · Insights) + HorizontalPager (swipe between tabs)
WheelCanvas         pair wheel, pairs-only (12 pairs, 4 corner buttons select the wing: Setup/
                     Trend/Momentum/Volatility, labelled "SETUP" since 2026-09-09, was "OVERALL"),
                     nucleus/hub. No on-dial Currencies mode, currency wedges, or Currency Flow
                     ticker any more — all retired 2026-09-04, see `ATOM_FX_WHEEL_V2_SPEC.md` §12
StatusStrip         ranked-pairs recommendation glyph row (signals.ranked.top, no count cap since
                     the 2026-09-17 score-floor change — was ≤3, corrected here same day), tap-to-
                     reflow panel
HeaderBar           wordmark, freshness, updated, calendar/sessions/watchlist/gear icons (regime
                     name lives on the wheel's hub; its strength-word/flow-line lines were
                     deliberately removed as clutter — see §20 item 2/3-4)
CsmBarStrip         always-on Currency Strength Meter below the wheel, Strength/Flow toggle
TimeframeButtons    D1/H4/H1 row below the CSM strip (drives the CSM strip only, not the wheel wings)
MacroScreen         archetype banner + bias baskets + evidence axes + cross-asset dashboard
InsightsScreen      recommendation card + theme-tagged news + calendar + brief
BottomSheetHost     draggable sheet host (rises above any tab)
CurrencyDetailSheet CSM 3-TF + breadth + drivers + expressing pairs (a CSM-strip bar tap), then
                     that currency's own %B chart last (§19.4a, moved here 2026-09-18)
RegimeSheet         hub tap — D1 regime detail
PairSheet           header (Setup Band + Continuation Score + rank) + tabs: Overview (5 consensus
                     rows) · Breakdown (Momentum, Structure, Alignment) · Correlation
ScrollingPills      shared pill row (recommendation glyphs, calendar chips, sheet tabs, TF toggles)
LineChart           native Compose close-price sparkline, D1/H4/H1 (no candles)
ChartSheet          long-press a wheel node: the complete 4-indicator glance panel (§19.4b) and
                     nothing else — a bare full-width D1/H4/H1 row driving four cards in order,
                     %B (20) · BandWidth · RSI · MACD. The old 12-period D1 %B card is deferred and
                     the Currency %B cards moved to CurrencyDetailSheet (both 2026-09-18, §19.4a)
PercentBOscillator  shared %B + signal-line chart, period-agnostic (§19.4a) — draws ChartSheet's
                     20-period pair %B and CurrencyDetailSheet's 12-period Currency %B
BandWidthChart      §19.4b BandWidth line + squeeze marks (ui/chart/BandWidthChart.kt)
RsiOscillator/
MacdOscillator      §19.4b momentum charts (ui/chart/MomentumOscillators.kt), ChartSheet only
ChartCommon.kt      the glance panel's shared chart primitives (heights, endpoint glow, date row) —
                     extracted 2026-09-18 when a third and fourth chart needed them
SettingsScreen      theme · notifications (+ send-test, history) · data source · optional PAT
                     (price-level alerts, disabled placeholder) · about/legend
SessionsSheet       Sydney/Tokyo/London/New York — 24h rolling timeline + per-session open/closed,
                     exact device-local times, live countdown (2026-09-17). Pure clock feature, no
                     signals.json involvement — each session's hours are computed in its own city's
                     real IANA timezone (not a fixed UTC offset), so DST is handled by the platform's
                     own timezone database, never hand-rolled
FreshnessBadge      fresh / stale / unavailable
Skeletons           wheel + sheet skeletons
```

### 19.1 Line chart (no candles)

A restrained close-price line: 1.5px stroke in the pair's direction colour, a soft area fill (direction colour at ~8%), a faint baseline, and an **emphasised endpoint dot**. No axes, no candles, no grid clutter — the point is "up or down, at a glance." Three of them (D1 · H4 · H1) stack in the pair-sheet header, or share one frame with `D1 H4 H1` pill tabs. Colour by net change over the window (endpoint vs window-start). Follows the dataviz conventions (area fill + emphasized endpoint) and both themes.

### 19.2 Macro screen

**Rebuilt 2026-09-10 (Pieter's ask)**: "the page reads 'Oil supply shock'... but there is no oil
supply shock. The name should accurately reflect reality, and the conclusion should be
actionable." The classifier (`macro_regime.py`) only ever sees 5 axes of price direction — it
can name the observed cross-asset PATTERN, but it cannot verify a specific real-world CAUSE.
The old layout led with the archetype name at Title weight, which read as a confident, settled
claim about reality that a 5-axis price read can't actually support. Four changes, described in
full in `macro_regime.py`'s own doc comments:

- **Evidence now leads the page**, not the archetype. The evidence axes (Risk · Rates · USD ·
  Commodity · Safe-haven) are purely descriptive — literal instrument moves, true by
  construction — so the page states what it knows before what it's inferring. Each axis is a
  standard card (not the old plain divided list): a lit/muted `EvidenceDot`, the axis label, its
  literal read (e.g. "SPX↓ VIX↑"), and — since the 2026-09-06 two-clock rework — a
  `confirmsToday` line ("Today confirms this trend" / "is fighting" / "No fresh move today").
- **The archetype card follows**, demoted from a Title-weight hero to `"Best match: {name}"` at
  Body weight — an inferred label, not a headline. Same confidence/USD/gold pill row as before.
  Its own display `name` deliberately diverges from the FX Macro Flow Handbook's own A-J
  vocabulary (`REGIME_LIB`'s doc comment, Functional Spec §6's regime table for the full
  cross-reference) — describes the pattern, not an inferred cause.
- **An explicit ambiguity callout** when `primary.confidence` reads Low because it tied
  `secondary` on distinct axes (not just a weak absolute count): names the actual runner-up
  story the data couldn't separate it from, rather than showing a bare "Low" pill and leaving the
  reader to guess why.
- **A news-corroboration line** — "Confirmed by recent headlines" or "Price pattern only — no
  recent headline confirms this" (`macro_regime.newsCorroboration`, from `scan_news.py`'s own
  `tag_theme()` axis-tagged headlines, now actually fed back into the classifier instead of
  sitting unused). Silent when there's nothing to check against ("unknown").
- **The currency-bias baskets are now accountable to live CSM**, not shown as flat, unconditional
  fact: each currency in the STRONG/WEAK boxes is checked against its own H4 CSM value (CSM=50
  as the confirm/not split — the same midpoint Rotation's own quadrant axes use, not a new
  threshold) and reads muted with "— CSM disagrees" (or "— CSM unavailable") when the live
  technical read doesn't back the macro story. This is the actionability test: a macro story the
  technicals don't support yet is a very different thing to trade than one they do.

The **cross-asset dashboard** (Appendix-A table: instrument, value, direction/Δ, a `confirms`
tag against the primary regime's own supporting axes) sits last, sorted moving-instruments-first.
No `✓/✗` glyphs anywhere — support is a tinted card fill (`EVIDENCE_LIT_AMOUNT` lerp toward
`bull`), consistent with the rest of the app's "colour encodes state" convention (§2).

### 19.3 Settings

Quiet, utilitarian, grouped rows on `surface`. A prominent **theme** segmented control (System / Dark / Light); a **notifications** group with a "Send test" button; a **data** group (source URL, refresh cadence, last-updated, force-refresh); an **optional** collapsed "Price-level alerts" group that reveals a GitHub-PAT field only when enabled; and an **About / legend** entry that opens the ⓘ guide. No API-key fields for market data or AI — state plainly that those live server-side.

### 19.4 Whole Market Indicators — retired 2026-09-10

Built same-day as a card on `InsightsScreen`: four concept indicators (Relative Rotation,
Confidence Index, Breadth Thrust, and %B/Currency %B) behind a "+ &lt;current&gt;" fan-out picker,
"tried live so Pieter could compare them before any one earns a permanent home." Once Currency %B
proved itself the useful one of the five (praised on-device: "The graphs look great... I think
this might be a very useful indicator"), Pieter's own call was to remove the whole experimental
card rather than trim it — Currency %B already had a real permanent home by then (§19.4a, below),
and Relative Rotation/Confidence Index/Breadth Thrust/Board %B had no other surface, so they left
the app entirely with it. `MarketIndicatorsCard.kt` and its four dedicated chart files
(`RotationChart.kt`/`PulseChart.kt`/`ThrustChart.kt`/`PercentBBoardChart.kt`/
`CurrencyPercentBChart.kt`) were deleted outright rather than left dead; the `Signals.kt` fields
that fed only this card (`rotation`/`pulse`/`breadthThrust`/`percentBBoard`, plus the
`RotationBlock`/`PulseBlock`/`ThrustBlock` types) were removed the same way. The backend still
computes and publishes `rotation`/`pulse`/`breadth_thrust`/`percent_b_board` in `signals.json`
(Architecture §4.2) — nothing server-side was touched — the app simply no longer reads them.

### 19.4a %B (Bollinger) — the 12-period reads (moved again 2026-09-18)

> **Superseded in part, 2026-09-18.** Everything below describes the **12-period** %B charts,
> and it was accurate until the 4-indicator glance panel was completed. Two changes, both
> Pieter's call:
>
> - **The pair's own 12-period D1 %B card is deferred, not deleted.** ChartSheet now shows the
>   stock-standard **20-period** %B instead (§19.4b), because a glance panel should show the
>   textbook read a trader can compare against any platform — and two %B charts on one sheet,
>   on different periods, invite a comparison that means nothing. The 12-period read is the BB
>   *touch alert's* own band math and stays exactly as specced here; `bb_touch.py`,
>   `pairs.<PAIR>.bb_d1` and `PercentBChart.kt` are all untouched and still in the repo,
>   pending a BB-touch-alert rework Pieter has flagged to discuss. Do not delete them as dead
>   code — that decision has already been taken the other way.
> - **The Currency %B cards moved to `CurrencyDetailSheet`**, one card at the bottom of each
>   currency's own sheet, still on 12-period bands and still `PercentBOscillator`. The
>   base/quote pairing described below existed so the currency read sat beside the pair's own
>   %B; with that card deferred the comparison isn't on this sheet any more, and a
>   currency-level read belongs on the currency's own surface. Still Currency %B's only UI
>   surface.

Lives on `ChartSheet.kt`, opened by a long-press on a wheel node — not a `PairSheet.kt` Breakdown
section any more (it started there earlier the same day, then Pieter's own follow-up ask moved
it: "the best place for the pair %B is actually under the long press function on the wheel...
put the %B in its place" of the sheet's old D1/H4/H1 close-price `LineChart`). That price chart
was a genuine duplicate of the pair-sheet header's own always-visible `Spark3Row` sparklines, so
nothing was lost removing it; %B was three taps deep (tap pair → Breakdown tab → scroll) and is a
chart-shaped read, a better fit for "long-press for the technical view."

No D1/H4/H1 switcher — %B is D1-only by design (`bb_touch.py`'s 12-period config), unlike price,
and a switcher pointing at nothing would be worse than none. In its place, a small header row
above the chart: `%B <value>` (left) and, when `bb_d1.touching` is `"upper"`/`"lower"`, "Still
touching upper/lower" (right, `bear`/`bull` token — upper reads bear since it signals a stretched-
up reversion risk, lower bull for the opposite) — blank when not touching either band. Same
`ui/chart/PercentBOscillator.kt` primitive every %B chart in this app shares, reading
`pairs.<PAIR>.bb_d1.pctb`/`.pctb_sma`/`.pctbDates` directly — no on-device Bollinger/SMA math or
date derivation (Architecture §8.3),
the series and its dates are both backend-computed (see Architecture §4.2's `pctb_dates` entry
for how the backend recovers dates the frozen aggregator itself discards).

Below the pair's own %B card, two more (2026-09-10, 2nd, Pieter's ask, talked through first) —
the pair's own base and quote currencies' `percent_b_currency` reads, one small card each
(`CurrencyPercentBCard`, `ChartSheet.kt`), same `PercentBOscillator` drawing again — **this is
Currency %B's only UI surface** now that §19.4's Insights-tab picker is retired. The workflow
behind this: a currency-level "USD looks fragile" read (Currency %B rolling over + CSM weakening)
only becomes an actual pair-level candidate once you check whether the OTHER leg of a specific
pair agrees — so both currencies sit right on the pair sheet
next to the pair's own %B, where that comparison happens. Deliberately three separate small
charts, not one merged one — pair %B (price position within THIS pair's own bands) and currency
%B (each currency's own stretch across ALL its pairs) are related but different reads; overlaying
up to 6 lines onto one chart would cost the "did it cross its own signal" readability that makes
any of this useful. `base`/`quote` come from the plain 6-char pair code (`pair.take(3)`/
`.takeLast(3)`) — no new parsing.

### 19.4b Momentum — RSI/MACD, per-pair (long-press → ChartSheet, added 2026-09-17, restyled 2026-09-18)

**Completed 2026-09-18.** This section now covers the whole 4-indicator glance panel Pieter
asked for ("a set of standard indicators... each gives one a different view on a certain market
dimension"). RSI and MACD shipped first (2026-09-17) — pure exposure of values `score.py` already
computes — deliberately ahead of the other two, "ship RSI and MACD first, then I can see what they
look like." %B-20 and BandWidth followed once their genuinely new backend calculation existed
(`scanner/extend/bollinger_series.py`).

`ChartSheet.kt` holds the panel and nothing else: a bare full-width D1/H4/H1 row driving four
cards in a fixed order — **%B (20)** (where price sits in its bands) · **BandWidth** (how wide
those bands are) · **RSI** (momentum extremity) · **MACD** (momentum direction/turn). The two
Bollinger reads lead because they share a band calculation and read as a pair; the two momentum
reads follow for the same reason. Every card is the same shell (`IndicatorCard`) so the four read
as one panel rather than four separately-styled charts.

**Restyled 2026-09-18** (Pieter's ask, comparing the first cut against LiteFinance's own panel:
"they look somewhat different... very flat"). RSI and MACD now each get their own card — was one
shared card under a "Momentum" heading — and the D1/H4/H1 selector moved out to a bare full-width
row above both, equal thirds, no title (same look HOME's own D1/H4/H1 row has below the wheel),
still driving both charts from one choice. `ui/chart/MomentumOscillators.kt`:

- **RSI (14)** — plain 0–100 line, dashed 30/70 reference lines, solid 50 centreline, the band
  between 30/70 shaded (`colors.scrim`) so a stretched reading stands out at a glance, no signal
  line (plain RSI doesn't have one). Line tints `bear`/`bull` when the latest value is ≥70/≤30 —
  the same "stretched, due to revert" convention %B's own band-touch colouring uses, so the two
  charts agree on what a stretched reading means.
- **MACD (12, 26, 9)** — histogram (MACD line − signal line) as bars tinted by sign
  (`bull`/`bear`), MACD line (`textPrimary`) and signal line (`watch`) overlaid, a legend row
  underneath. The histogram gets its OWN vertical scale, independent of the MACD/signal lines'
  own scale, both symmetric around the same shared zero line — a histogram is a difference of two
  similarly-sized series, so it's mathematically much smaller than either line on its own; one
  shared scale (the first cut's approach) flattened it to a barely-visible sliver.

Both charts are also now taller, finer-stroked, show three sparse date labels along the bottom
(same convention `PercentBOscillator` already uses), and add a soft glow behind the current-value
endpoint dot — the same `BlurMaskFilter` technique the wheel's own hub glow already uses
(`WheelCanvas.kt::glowFillCircle`), not a new visual language.

Both read `pairs.<PAIR>.momentum_series.<tf>` directly (`scanner/extend/momentum_series.py`,
Architecture §4.2) — a 90-point tail (up from 50 — a display cap only, not a fetch limit) of the
same frozen `_rsi`/`_macd` `score.py` already runs for every pair/timeframe as part of its own
scoring, kept as history instead of discarded after the latest value is read, plus its own `dates`
(D1 via the same NY-session close %B uses; H4/H1 via `scanner/extend/tf_dates.py`, which recovers
just the timestamps the frozen aggregator discards, the same "recover what the frozen aggregator
throws away" pattern `pctb_dates` already established). No on-device RSI/MACD math
(Architecture §8.3).

**%B (20) and BandWidth (added 2026-09-18)** — `scanner/extend/bollinger_series.py`, a new EXTEND
module deliberately separate from `bb_touch.py`. Both read
`pairs.<PAIR>.bollinger_series.<d1|h4|h1>`; no on-device band math (Architecture §8.3). Same bar
conventions `momentum_series` already established: D1 on the 17:00-New-York session close, H4/H1 on
the frozen aggregator's own bars, dates via `tf_dates.py`.

- **%B (20)** — standard 20-period ±2σ bands, plus a 20-period SMA of %B itself as the signal line.
  Drawn with the same shared `PercentBOscillator` every %B chart in the app uses (10/90 dashed
  reference lines, 50 centreline, 0–100 clamped for display only). The card header splits
  "EURUSD" (identity, Body) from "%B (20) 21" (reading, Caption), the same identity-primary
  pairing the Currency %B cards use. **Why 20 when `bb_d1` is 12:** the alert's period is the
  alert's own parameter (Pieter specified 12 explicitly); a glance panel's job is the textbook
  read that matches any charting platform. Neither is "the right one" — they answer different
  questions, and `bollinger_series.py` exists so that neither has to move for the other.
- **BandWidth** — (upper − lower) ÷ middle × 100, the same formula `bb_d1.width_pct` already
  reports as a single number, kept as a series instead. **No fixed Y domain** — BandWidth's
  typical range differs by pair and by timeframe, so the axis is scaled to the visible window's
  own min/max (padded 8%) rather than to invented "wide"/"narrow" levels this project has no data
  to place. The shape is the read.
  - **Squeeze marks** are Bollinger's own published definition — BandWidth at its lowest in the
    trailing 125 bars — flagged per bar by the backend, never recomputed on device. Pieter's
    explicit call (2026-09-18) over inventing a percentile cut-off, the same caution
    `bb_touch.py` already flags about its own untuned `width_trend` numbers. Drawn as a soft
    full-height column (`watchSoft`) behind the line plus a dot on it (`watch`), with an
    "In squeeze" header word when the current bar qualifies. `watch` is deliberate: a squeeze is
    "worth attention, no verdict yet, no direction implied," which is exactly that token's job.
  - Known and accepted: 5000 H1 bars is only ~208 D1 bars, so a 125-bar lookback leaves ~84 D1
    bars able to carry a verdict. The oldest few points of a 90-point D1 window always read
    not-a-squeeze — fail-quiet, not a miss.

`ui/chart/ChartCommon.kt` (2026-09-18) holds the primitives all four charts share — base height,
date-row height/format, the endpoint glow dot, the three-label date row. These began as private
helpers in `MomentumOscillators.kt`; a third and fourth chart needing them made a shared home the
alternative to a second copy drifting out of step. Nothing about the drawing changed in the move,
and `PercentBOscillator.kt` was pointed at the shared date row at the same time (it had its own
identical inline copy).

---

## 20. Acceptance test (spec §69) — the design is done when…

> Rewritten 2026-09-10 against the Simplification Rework's current vocabulary (Setup Bands, the
> fixed D1/H4 wing consensus) — the previous wording used the retired Level/Potential/six-factor
> vocabulary and a "tap a ring" gesture that's been dead code since before that rework. Same
> twelve questions, same intent (spec §69); only what answers them changed.

**Landing view, no sheet open, answers:**
1. What is the current regime? — the wheel hub's regime label (D1 Regime).
2. Strong or weak? — no on-screen proxy (Pieter's deliberate call: the hub's old strength-word line read as clutter, removed). `WheelMapper.mapNucleus` still computes `strengthWord`/`confidence` onto `NucleusState` — harmless dead output, not a bug — but `WheelCanvas.drawHub` only draws "REGIME" + the regime name by design.
3. Which currency is leading? 4. Which is weakening? — answered visually, not by a sentence: compare bar heights on the always-on CSM strip (fixed currency order, not sorted by strength). The old `flowLine` text ("X leading · Y weakening") was removed for the same declutter reason as #2 — `NucleusState.flowLine` is still computed, just unused.
5. Which pairs have the greatest potential? — the A+ SETUP / STRONG SETUP band: the biggest, most saturated wedges on the wheel's Setup wing (labelled "SETUP," was "OVERALL"), and (for whichever pairs clear the ranking gate, at most 3) their glyphs in the status strip above the wheel.
6. Which are merely developing? — the DEVELOPING band: mid-size Overall wedges.
7. Which should be ignored? — the LOW SETUP band: the smallest, most muted Overall wedges.

**One tap on a pair answers:**
8. Why is this pair attractive? — the pair sheet header (Setup Band word, Continuation Score, rank).
9. Which factors support it? — the Overview tab's Regime/Trend/Momentum/Volatility/Structure rows, each bull/bear-tinted on its own real read.
10. Which factor blocks further advancement? — whichever Overview row reads watch/bear (or a live Structure CHoCH) against an otherwise-supportive picture.
11. Is the entry location good? — the Volatility row (ATR percentile sane band) together with the Structure row (a recent BOS/CHoCH), the two Overview rows that speak to entry timing since the old dedicated Entry tab was folded into them.

**One tap on the hub, a currency, or a pair node answers:**
12. What exactly is happening at this analytical layer? — the hub opens RegimeSheet, a currency wedge opens CurrencyDetailSheet (CSM 3-TF, breadth, drivers, expressing pairs), a pair node opens its PairSheet — each a full breakdown of that one layer.

If the UI answers all twelve elegantly — restrained, precise, information-dense, no arcade — the redesign succeeds. Do not sacrifice analytical accuracy for visual simplicity; the wheel makes the existing system *easier to understand*, not simpler (spec §70).

*End of Design Document.*
