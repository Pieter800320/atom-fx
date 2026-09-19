"""
ATOM FX research — historical CFTC Legacy (futures-only) COT downloader.

Builds data/cot_legacy/legacy_nc.csv: one row per (CFTC code, weekly as-of date) with the
Non-Commercial (large speculator) Long and Short contract counts. This is the SAME series
the Crowded Market Pine indicator reads on TradingView (COT:<code>_F_NCP_L / _F_NCP_S) —
verified 2026-09-19 to match CFTC's own Legacy futures-only report exactly.

Source: https://www.cftc.gov/files/dea/history/deacot<YYYY>.zip  (annual.txt inside)
Codes:  the 8 contracts the indicator maps (EUR GBP JPY CHF CAD AUD NZD + USD = ICE Dollar Index).
The as-of date is CFTC's own report date (a Tuesday); the report is published the following
Friday. Alignment to price bars — and the no-look-ahead rule — lives in
scanner/extend/crowded_reversal.py, not here: this file only fetches and normalises.

Usage:  py -m tools.fetch_cot_legacy [first_year]      (default 2006; last year = current)
"""
import csv
import datetime
import io
import sys
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).parent.parent
OUT_PATH = ROOT / "data" / "cot_legacy" / "legacy_nc.csv"
URL = "https://www.cftc.gov/files/dea/history/deacot{year}.zip"

# currency -> CFTC contract market code (identical to the Pine script's f_cftcCode)
CODES = {
    "EUR": "099741", "GBP": "096742", "JPY": "097741", "CHF": "092741",
    "CAD": "090741", "AUD": "232741", "NZD": "112741", "USD": "098662",
}
COL_DATE = "As of Date in Form YYYY-MM-DD"
COL_CODE = "CFTC Contract Market Code"
COL_LONG = "Noncommercial Positions-Long (All)"
COL_SHORT = "Noncommercial Positions-Short (All)"


def parse_year_zip(raw: bytes) -> list:
    """Rows [(code, date_iso, long, short)] for the mapped contracts in one annual zip."""
    wanted = set(CODES.values())
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        reader = csv.reader(io.TextIOWrapper(z.open(z.namelist()[0]), encoding="latin-1"))
        header = next(reader)
        i_date, i_code = header.index(COL_DATE), header.index(COL_CODE)
        i_long, i_short = header.index(COL_LONG), header.index(COL_SHORT)
        rows = []
        for r in reader:
            code = r[i_code].strip()
            if code in wanted:
                rows.append((code, r[i_date].strip(), int(r[i_long]), int(r[i_short])))
    return rows


def fetch_year(year: int) -> list:
    req = urllib.request.Request(URL.format(year=year), headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return parse_year_zip(resp.read())


def main(first_year: int = 2006) -> None:
    last_year = datetime.date.today().year
    all_rows = []
    for year in range(first_year, last_year + 1):
        rows = fetch_year(year)
        print(f"{year}: {len(rows)} rows")
        all_rows.extend(rows)

    all_rows = sorted(set(all_rows), key=lambda r: (r[0], r[1]))
    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    with open(OUT_PATH, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["code", "date", "nc_long", "nc_short"])
        w.writerows(all_rows)
    print(f"wrote {len(all_rows)} rows -> {OUT_PATH.relative_to(ROOT)} "
          f"({all_rows[0][1]} .. {all_rows[-1][1]})")


if __name__ == "__main__":
    main(int(sys.argv[1]) if len(sys.argv) > 1 else 2006)
