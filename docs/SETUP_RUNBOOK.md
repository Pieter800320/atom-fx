# ATOM FX — Setup Runbook (step by step)

**Goal of this document:** get from *nothing* to *a repo with the frozen scanner, the
specs, the fixtures, and a passing Rule #1 test* — carefully, with a checkpoint after
every step so you can see it worked before moving on. Don't rush; each step is small.

You do **not** write any app code here. This is just the foundation. When it's done,
`ATOM_FX_SETUP_AND_KICKOFF.md` takes over for Firebase/secrets and driving Claude Code.

> **Legend:** lines in `code blocks` are commands to type. After most steps there's a
> **✅ You should see** line — if you don't, jump to **Troubleshooting** at the bottom
> and don't continue until it matches.

---

## What you'll end up with

```
atom-fx/                         ← your new GitHub repo, cloned locally
├── docs/                        ← all specs + this runbook + the mockup   (from the bundle)
│   ├── INDEX.md  GLOSSARY.md  SETUP_RUNBOOK.md
│   ├── ATOM_FX_ARCHITECTURE.md  ATOM_FX_DESIGN.md  ATOM_FX_FUNCTIONAL_SPEC.md
│   ├── ATOM_FX_SETUP_AND_KICKOFF.md
│   └── mockups/atom-fx-screen-kit.html
├── fixtures/                    ← 4 UI state fixtures                      (from the bundle)
├── tools/make_ui_fixtures.py    ← regenerates the fixtures                 (from the bundle)
├── tests/                       ← the Rule #1 guard                        (from the bundle)
│   ├── frozen_probe.py  make_golden.py  test_rule1_frozen.py  README.md
│   └── golden/                  ← you create golden/frozen_golden.json in Step 7
├── scanner/                     ← the FROZEN scanner, forked verbatim      (Step 5)
└── requirements.txt             ← Python deps                             (Step 6)
```

Time: about 30–45 minutes. Nothing here is irreversible.

---

## Step 0 — Prerequisites (check you have these)

Open a terminal (Android Studio has one at the bottom: **View → Tool Windows → Terminal**, or use any terminal app) and run each line. You're just checking they exist.

```
git --version
python3 --version
```

- **git** — if missing: install from https://git-scm.com (Windows/Mac) or your package manager.
- **python3** — you need **3.11+**. On Windows the command may be `python` instead of `python3`; if so, use `python` everywhere below.

✅ You should see a version number for each (e.g. `git version 2.4x`, `Python 3.11.x`). If a command is "not found", install it before continuing.

You also need your GitHub account (you have `Pieter800320`) and the two source things nearby:
- your existing **`fx-signal-board`** repo (we copy the scanner from it), and
- the **bundle** `atom-fx-starter.zip` (the file that came with this runbook).

---

## Step 1 — Create the new repo on GitHub (in the browser)

1. Go to https://github.com/new
2. **Repository name:** `atom-fx`
3. **Description:** *ATOM FX — native Android FX trading app* (optional)
4. Choose **Private** (you can make it public later for free CI minutes).
5. **Tick "Add a README file."**
6. Click **Create repository**.

✅ You should see the new empty repo at `https://github.com/Pieter800320/atom-fx` with one file (`README.md`).

---

## Step 2 — Clone the repo to your computer

Pick a folder where you keep projects, then:

```
git clone https://github.com/Pieter800320/atom-fx
cd atom-fx
```

✅ You should now be *inside* the `atom-fx` folder. Check with:

```
git status
```

It should say *"On branch main / nothing to commit, working tree clean."*

> If git asks you to log in, use your GitHub username and a **Personal Access Token** as
> the password (GitHub no longer accepts your account password here). See Troubleshooting.

---

## Step 3 — Unzip the starter bundle into the repo

Extract `atom-fx-starter.zip`. It contains these folders: `docs/`, `fixtures/`, `tools/`,
`tests/`, and a top-level `README.md`. **Copy those into your `atom-fx` folder** so they
sit at the repo root (next to the existing `README.md` — overwrite it when asked).

After copying, from inside `atom-fx` run:

```
ls
```

(On Windows: `dir`)

✅ You should see: `docs  fixtures  tests  tools  README.md`

Confirm the specs landed:

```
ls docs
```

✅ You should see `INDEX.md`, `GLOSSARY.md`, `SETUP_RUNBOOK.md`, the four `ATOM_FX_*.md` files, and a `mockups` folder.

---

## Step 4 — Fork the frozen scanner in (Rule #1 by construction)

This copies your proven scanner **verbatim** so the calculations are identical from day one.
Adjust the path if your `fx-signal-board` clone is elsewhere; this assumes it sits next to `atom-fx`.

**Mac / Linux:**
```
cp -R ../fx-signal-board/scanner ./scanner
rm -rf ./scanner/__pycache__
```

**Windows (PowerShell):**
```
Copy-Item -Recurse ..\fx-signal-board\scanner .\scanner
Remove-Item -Recurse -Force .\scanner\__pycache__ -ErrorAction SilentlyContinue
```

Then add a one-line reminder that these files are frozen:

```
echo "FROZEN — verbatim fork of fx-signal-board. Rule #1: never edit. New analytics go in scanner/extend/." > scanner/FROZEN.md
```

✅ Check the scanner is there:
```
ls scanner
```
You should see `csm.py  mom1212.py  regime.py  score.py  cont_score.py  rank.py  structure.py  …` and your new `FROZEN.md`.

> Do **not** copy the old `dashboard/`, the old workflows, or `data/` yet — we only need the scanner right now.

---

## Step 5 — Python environment

Create `requirements.txt` at the repo root (same two deps as the reference; pinning is
recommended so the Rule #1 golden stays reproducible):

```
printf "pandas>=2.0.0\nnumpy>=1.24.0\npytest>=7.0\n" > requirements.txt
```

Make an isolated environment and install:

**Mac / Linux:**
```
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

**Windows (PowerShell):**
```
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

✅ You should see pip install pandas, numpy, pytest without errors, and your prompt now
starts with `(.venv)`. Add the venv to git-ignore so it isn't committed:

```
printf ".venv/\n__pycache__/\n*.pyc\n" > .gitignore
```

---

## Step 6 — Bootstrap the Rule #1 golden (your safety net)

This snapshots the *current* frozen calculations as the baseline. Because you forked the
scanner verbatim, this snapshot is by definition correct. Run it **once**:

```
python -m tests.make_golden
```

✅ You should see:
```
✓ wrote …/tests/golden/frozen_golden.json
  csm currencies: ['GBP', 'EUR', 'AUD', 'NZD', 'CAD', 'JPY', 'CHF', 'USD']
  pairs snapshotted: 12
  ranked setups: 0
```

(`ranked setups: 0` is expected — the test fixture is synthetic, not real market data. The
test still guards CSM, momentum, regime, continuation, structure, correlations, and rank.)

Now run the test:

```
python -m tests.test_rule1_frozen
```

✅ You should see:
```
✓ Rule #1 holds — frozen calculations unchanged.
```

**Optional — see the guard actually work.** Open `scanner/csm.py`, change `D1_WEIGHT = 0.7`
to `0.71`, save, and run the test again — it should FAIL and name the exact number that
changed. Then change it back to `0.7` and confirm it passes again. (This proves the net
is live. Don't commit the change.)

---

## Step 7 — Generate the UI fixtures

```
python tools/make_ui_fixtures.py
```

✅ You should see four lines, one per state:
```
✓ state_risk_on.json  (32 KB, 12 pairs, 3 tradeable)
✓ state_risk_off.json  (…, 4 tradeable)
✓ state_ranging_no_setups.json  (…, 0 tradeable)
✓ state_liquidity_shock.json  (…, 1 tradeable)
```

(They're already in the bundle, so this just confirms the generator runs; the outputs
are identical.)

---

## Step 8 — First commit & push

```
git add .
git commit -m "chore: scaffold atom-fx — frozen scanner, specs, fixtures, Rule #1 test"
git push
```

✅ Refresh `https://github.com/Pieter800320/atom-fx` in your browser — you should see
`scanner/`, `docs/`, `tests/`, `fixtures/`, `tools/`, and `requirements.txt`.

---

## Step 9 — Add the Rule #1 test to CI (so it runs on every push)

Create `.github/workflows/rule1.yml` with this content (this is safe to add now; it needs
no secrets):

```yaml
name: Rule #1 — frozen calc guard
on: [push, pull_request, workflow_dispatch]
jobs:
  rule1:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with: { python-version: '3.11' }
      - run: pip install -r requirements.txt
      - run: python -m tests.test_rule1_frozen
```

Commit and push it:
```
git add .github/workflows/rule1.yml
git commit -m "ci: run Rule #1 guard on every push"
git push
```

✅ On the repo's **Actions** tab you should see the workflow run and go green.

> If it goes red on this first CI run, it's almost always a numpy/pandas version
> difference between your machine and the runner. Fix: pin the versions in
> `requirements.txt` to what CI installed (see the run log), re-run
> `python -m tests.make_golden` locally, and commit the new golden. The 6-dp rounding
> usually prevents this, but pinning removes all doubt.

---

## You're set. What's next

The foundation is done and protected. From here, follow **`ATOM_FX_SETUP_AND_KICKOFF.md`**:

- Step 3–4 there: Firebase/FCM + GitHub secrets (for push notifications).
- Step 5 there: adapt the hourly/news workflows from `fx-signal-board`.
- Step 6 there: open `atom-fx` in Android Studio, paste the **Golden Rule** preamble to
  Claude Code, then drive it **one build phase at a time** (Architecture §9).

Give Claude Code `docs/INDEX.md` first — it points at everything else.

---

## Verification checklist

- [ ] `atom-fx` repo exists on GitHub and is cloned locally
- [ ] `docs/`, `fixtures/`, `tools/`, `tests/` are in place (Step 3)
- [ ] `scanner/` is the verbatim fork with `FROZEN.md` (Step 4)
- [ ] `.venv` active, deps installed (Step 5)
- [ ] `tests/golden/frozen_golden.json` created; **Rule #1 test passes** (Step 6)
- [ ] Fixtures generate (Step 7)
- [ ] Everything committed and pushed (Step 8)
- [ ] Rule #1 CI workflow is green (Step 9)

---

## Troubleshooting

**`python3: command not found`** — On Windows use `python` instead of `python3` everywhere. Verify with `python --version` (must be 3.11+).

**`ModuleNotFoundError: No module named 'scanner'`** — You must run the `python -m tests.…` commands from the **repo root** (`atom-fx`), not from inside `tests/`. Check with `ls` that you can see the `scanner` and `tests` folders.

**`ModuleNotFoundError: No module named 'pandas'`** — Your venv isn't active or deps aren't installed. Re-activate (`source .venv/bin/activate` or `.\.venv\Scripts\Activate.ps1`) and run `pip install -r requirements.txt`.

**Rule #1 test fails on the very first run (before you changed anything)** — Almost always a numpy/pandas version mismatch. Re-run `python -m tests.make_golden` to snapshot in your current environment, then run the test again. For CI, pin versions in `requirements.txt` and commit the golden generated in that same environment.

**Git asks for a password and rejects it** — GitHub needs a **Personal Access Token**, not your password. Create one at GitHub → Settings → Developer settings → Personal access tokens → *Tokens (classic)* → Generate, with the `repo` scope. Use it as the password when git prompts. (Or set up SSH keys.)

**`__pycache__` keeps reappearing** — Harmless; `.gitignore` (Step 5) keeps it out of commits.

**I edited a frozen file by accident** — Restore it from your `fx-signal-board` copy: `cp ../fx-signal-board/scanner/<file>.py scanner/<file>.py`, then re-run the Rule #1 test to confirm green.

If something else looks wrong, stop and describe exactly what you typed and what you saw — don't push past a failing checkpoint.
