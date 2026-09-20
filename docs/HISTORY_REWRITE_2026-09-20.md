# History rewrite of 2026-09-20 (vendor price data removed)

**Why.** Twelve Data's terms (https://twelvedata.com/terms, last updated 2026-01-01; sections 2.2(e), 2.3(b), 2.3(g)) do not allow redistributing its raw data without a Redistribution Rights add-on or written agreement. Raw hourly candles
(`data/h1_history/`) and aggregated daily/4-hour price stores (`data/d1_utc_daily`, `d1_nyclose`, `d1_nyclose_long`, `h4_ny_long`, `h4_utc_long`) had been committed to this PUBLIC repository. Derived data (signals, scores, experiment results) is allowed
(2.2(c)) and stays.

**What was done (Pieter approved: "Yes, go ahead with the purge ... on both branches").** `git filter-repo --invert-paths` on a fresh mirror clone removed those six paths from EVERY commit of `main`, `research` and the tag `archive/trend-pullback-research`, then the three refs
were force-pushed (with a lease, so a concurrent scan-bot push could not be overwritten). 625 -> 623 commits (two became empty). No commit content other than those paths changed; **author/committer dates and messages are preserved**, so the pre-registration timestamps are intact,
but **every commit hash changed**. Verified afterwards: no blob under those paths in any ref, on GitHub's refs or in the local repo.

**What this cannot do.** GitHub may keep serving the old commits by their old SHA (and via caches or forks) until it garbage-collects them. To remove those too, ask GitHub Support to purge cached views / dangling commits ("Removing sensitive data from a repository").

**Translating an old hash.** `docs/history-rewrite-2026-09-20-commit-map.txt` lists `old-full-hash new-full-hash` for every commit. The commits cited in the docs:

| cited (old) | now |
|---|---|
| `2bf1785` | `84031d5` |
| `34a4d73` | `3299444` |
| `3aa1097` | `4b69cbd` |
| `63e91d0` | `8afc659` |
| `7b8c9f2` | `ba40df4` |
| `8a5a634` | `ba5b173` |
| `94f2466` | `8e9a7b5` |
| `95481ec` | `bf5fb41` |
| `ac18ee0` | `7244d15` |
| `e4f6464` | `8a806be` |
| `ebf0bee` | `8ef27d9` |
| `f8b1624` | `aa58bb2` |

Going forward: raw and aggregated candles are never committed (`.gitignore`; see `docs/DATA_NOT_IN_GIT.md` on the research branch and BUILD_STATUS item 20 on main).
