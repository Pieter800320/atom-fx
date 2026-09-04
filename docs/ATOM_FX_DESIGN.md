# ATOM FX — Design Document

**Version:** 1.0 · **Status:** Specification (hand to Claude Code as an anti-drift contract)
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

Bottom band above the nav. A horizontal **scrolling pill row**:
- **Tradeable** (level 6 only): `EURUSD ↑ 86` pills, `bull`/`bear` tinted, ranked left→right by setup rank (ranking is separate from wheel position — spec §49). `A+` pills get a subtle brighter rim.
- If none reach level 6: `NO A+ SETUPS` + a single muted `Closest: <pair> — Level 5/6` pill.
- A secondary **Watch** row (levels 3–5) may follow, labelled `WATCH`, amber-tinted, e.g. `EURJPY · 5/6 · waiting for entry reset` (spec §33). Tapping any pill opens that pair's sheet.

---

## 12. Edge panels (spec §44 style, Pieter's request)

Two edge-drawn panels, summoned by an edge swipe or a header affordance; they overlay from the screen edge and dim the wheel behind.

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

Tap ring *N* → the corresponding factor sheet (§14). Tap nucleus → Regime sheet. Tap node → Pair sheet (§14.7).

---

## 14. Bottom sheet contents (exact)

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

- **Phone (primary):** wheel dominates; status strip above; Tradeable Now below; no permanent side panels (edge panels are summoned). Wheel fits with **no horizontal scroll**; uses full dynamic viewport height minus insets; safe-area respected.
- **Tablet / landscape:** wheel ~60–65% width; a compact market summary on one side; factor summary on the other; sheets still available.
- The wheel is always a centred square sized to `min(width, height − chrome)`.

---

## 18. Accessibility (spec §65)

- Every node has a content description: e.g. *"EURUSD, long, high potential, six of six factors passed."* Ring bands: *"Currency Flow ring, tap for detail."* Nucleus: *"Market regime Risk-On, high confidence, tap for detail."*
- Never rely on colour alone — arrows/glyphs/labels always accompany hue (spec §15, §36).
- Meet contrast on text against `surface`/`ground`; status colours chosen to remain distinguishable for common colour-vision deficiencies (bull green / bear red pair reinforced by shape).
- Respect system font scaling (layout reflows; the wheel keeps geometry, text truncates gracefully) and reduce-motion.

---

## 19. Component checklist (what Claude Code builds)

```
AppScaffold         bottom nav (4 tabs) + HorizontalPager (swipe between tabs)
WheelCanvas         pair wheel: rings, nodes, radial paths, factor markers, nucleus, halo, animation
CurrencyWheel       8-currency strength wheel: two blocs, radius=strength, Δ chevrons
NucleusView         regime state + flow line + recommendation line (in-canvas or overlay)
StatusStrip         5 micro-cells
HeaderBar           wordmark, regime, flow line, freshness, updated, gear
TradeableNow        scrolling pills + NO-A+ empty state + Watch row
MacroScreen         archetype banner + bias baskets + evidence axes + cross-asset dashboard
InsightsScreen      recommendation card + theme-tagged news + calendar + brief
BottomSheetHost     draggable sheet host (rises above any tab)
CurrencyDetailSheet CSM 3-TF + breadth + drivers + expressing pairs
FactorSheets        Regime, Flow, Breadth, Momentum, Structure, Entry
PairSheet           header + WHY checklist + pill tabs (Overview…Correlation)
ScrollingPills      shared pill row
LineChart           native Compose close-price sparkline, D1/H4/H1 (no candles)
MacroScreen         archetype banner + currency-bias baskets + evidence axes + cross-asset dashboard
SettingsScreen      theme · notifications · data source · optional PAT · about/legend
FreshnessBadge      fresh / stale / unavailable
Skeletons           wheel + sheet skeletons
```

### 19.1 Line chart (no candles)

A restrained close-price line: 1.5px stroke in the pair's direction colour, a soft area fill (direction colour at ~8%), a faint baseline, and an **emphasised endpoint dot**. No axes, no candles, no grid clutter — the point is "up or down, at a glance." Three of them (D1 · H4 · H1) stack in the pair-sheet header, or share one frame with `D1 H4 H1` pill tabs. Colour by net change over the window (endpoint vs window-start). Follows the dataviz conventions (area fill + emphasized endpoint) and both themes.

### 19.2 Macro screen

The archetype **banner** is the hero: the regime name in Title weight, a confidence chip, and a one-line narrative. Below it, the two **currency-bias baskets** (STRONG green / WEAK red chip rows), then the **evidence axes** as a short list of labelled rows (Risk · Rates · USD · Commodity · Safe-haven) each with its read and a ✓/✗ support glyph — this visualises the handbook's anti-double-counting discipline. The **cross-asset dashboard** is the Appendix-A table (Factor · Variable · Value · Dir · Δ · Zone · Impact) in an `overflow-x:auto` container; tap a row for its zone context. Gold-overlay and USD-regime are chips near the banner.

### 19.3 Settings

Quiet, utilitarian, grouped rows on `surface`. A prominent **theme** segmented control (System / Dark / Light); a **notifications** group with a "Send test" button; a **data** group (source URL, refresh cadence, last-updated, force-refresh); an **optional** collapsed "Price-level alerts" group that reveals a GitHub-PAT field only when enabled; and an **About / legend** entry that opens the ⓘ guide. No API-key fields for market data or AI — state plainly that those live server-side.

---

## 20. Acceptance test (spec §69) — the design is done when…

**Landing view, no panel open, answers:**
1. What is the current regime? 2. Strong or weak? 3. Which currency is leading? 4. Which is weakening? 5. Which pairs have the greatest potential? 6. Which are merely developing? 7. Which should be ignored?

**One tap on a pair answers:**
8. Why is this pair attractive? 9. Which factors support it? 10. Which factor blocks further advancement? 11. Is the entry location good?

**One tap on a ring answers:**
12. What exactly is happening at this analytical layer?

If the UI answers all twelve elegantly — restrained, precise, information-dense, no arcade — the redesign succeeds. Do not sacrifice analytical accuracy for visual simplicity; the wheel makes the existing system *easier to understand*, not simpler (spec §70).

*End of Design Document.*
