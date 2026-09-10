# ATOM FX — Library & Playbook Prose Style

**Version:** 1.0 (2026-09-10) · **Status:** binding — every `LibraryContent.kt` entry and every
Playbook entry (`RegimePlaybookContent.kt`, `TechnicalRegimePlaybookContent.kt`,
`AlertPlaybookContent.kt`) must follow this. Read it before writing or editing any of them.

---

## 1. Purpose (read this before writing anything)

Two surfaces, two jobs. Every rule below exists to serve one of these — if a sentence doesn't
serve either, cut it.

- **Library** (`ui/settings/LibraryContent.kt`, rendered by `LibraryScreen.kt`) — a reference
  glossary. Answers *"what is this, how is it computed, why does it matter"* for any term, on
  demand, independent of context. The reader arrived by searching or tapping a "Learn more" link;
  they want the fact, not the story of how it got here.
- **Playbook** (`RegimePlaybookContent.kt`, `TechnicalRegimePlaybookContent.kt`,
  `AlertPlaybookContent.kt`, rendered by `ReadingWindow.kt`) — contextual teaching for a *live
  instance*. Answers *"what does this actually mean right now"* for the specific alert or regime
  state the reader just tapped into.

**Neither one documents how the feature was built, changed, or debugged.** That belongs in code
comments and commit messages — a different audience, a different job. If you're tempted to write
"2026-09-06 (Pieter's own catch)..." here, that sentence belongs in the `.kt` file's own doc
comment above the string literal, not inside the string itself.

---

## 2. The rules

1. **No developer narration.** No names, no dates, no "was X, now Y," no bug-fix history, no
   reasoning trails ("first cut of this," "found live," "Pieter's own ask"). If it explains a
   trading or app concept, keep it. If it explains how the code changed, cut it entirely — every
   time, no exceptions. That history still lives in the `.kt` file's surrounding comments and in
   git; deleting it from the string loses nothing real.
2. **State the current fact, not its history.** Write what's true now. Don't say "used to be H4,
   now D1" — just say "D1."
3. **Lead with the answer.** First sentence is the plain-language definition or takeaway.
   Mechanics and detail follow, never before.
4. **Short sentences, plain words.** Prefer the common word over the technical one. When a
   technical term is genuinely necessary, it must already be a `GLOSSARY.md` term — never an
   invented synonym for something the Glossary already names.
5. **Lists for anything list-shaped.** Steps, criteria, thresholds, a checklist — one per line,
   never comma-chained into a paragraph. Compose's `Text` renders `\n` as a real line break
   already, so this needs no UI work — just write the string with line breaks in it.
6. **One idea per sentence.** Don't stack two or three clauses onto one sentence with em-dashes.
   If a sentence needs a second em-dash to fit everything in, it's two sentences.
7. **No meta-commentary.** No "worth noting," no hedging about a design debate, no editorializing
   about whether a decision was right. State the fact plainly and move on.
8. **Numbers stated precisely and consistently.** A threshold or band is always a range,
   low→high, with its unit if it has one (e.g. "25–39", "20–70").
9. **A soft length ceiling**, so conciseness isn't optional:
   - `summary`: ≤ 20 words
   - `howItWorks` (or its Playbook equivalent): ≤ 60 words, or a list of ≤ 6 lines
   - `whyItMatters`: ≤ 40 words
   These are guides, not hard limits — a genuinely irreducible explanation can run over. A
   paragraph that's over because it's still carrying old changelog prose is not that; cut it
   first, then reassess the length.

---

## 3. Before / after

Real example, from the Library's ADX entry, before this pass:

> "ADX answers a different question than direction — CSM, pills, and Overall already cover that.
> It answers whether there's an actual trend worth trading, or just noise with a directional
> lean. Two pairs can share the same CSM-implied bias and read completely differently on ADX;
> that gap is exactly what the wheel's own Trend wing (renamed 2026-09-05 — same field, clearer
> name) and the pair sheet's Overview TREND row both surface. 2026-09-06 (Pieter's own catch) — a
> flat "25+ = strong trend" label was treating ADX 32 and ADX 88 as the same thing, which they
> aren't: the Overview row now reads 25–39 as trending, 40–59 as very strong, 60+ as extreme and
> worth treating with caution (rare, often an overextended/exhaustion-prone move, not simply
> "more is better")."

After:

> "ADX measures trend strength, not direction — a pair can share another pair's bias and still
> read completely differently here.
> 0–24: no real trend
> 25–39: trending
> 40–59: very strong
> 60+: extreme — often exhaustion-prone, not simply "more is better""

---

## 4. Checklist for every entry you write or edit

- [ ] Would a first-time reader, with zero session context, understand this?
- [ ] Does any sentence name a person, a date, or a past state ("was," "used to")? Cut it.
- [ ] Is the first sentence the actual answer, not a run-up to it?
- [ ] Is anything list-shaped still written as a comma-chained paragraph? Break it into lines.
- [ ] Does every sentence carry exactly one idea?
- [ ] Is every technical term a real `GLOSSARY.md` term?
