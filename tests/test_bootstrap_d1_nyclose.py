"""
ATOM FX — bootstrap_d1_nyclose URL-encoding regression test.

Pure test of _build_url() — no network call, no mocking needed. Locks the bug fix: a raw
space/colon in `end_date` (Twelvedata's own "YYYY-MM-DD HH:MM:SS" datetime format) must never
reach the built URL unescaped — urllib.request.urlopen rejects a URL containing a raw,
unescaped space with "URL can't contain control characters".
"""
from tools.bootstrap_d1_nyclose import _build_url


def test_build_url_encodes_end_date_space_as_percent20_not_plus():
    url = _build_url("time_series", {
        "symbol": "EUR/USD", "interval": "1h", "outputsize": 5000,
        "order": "ASC", "type": "price", "end_date": "2024-01-01 00:00:00",
    })
    assert " " not in url                                    # the actual bug
    end_date_value = url.split("end_date=")[1].split("&")[0]
    assert end_date_value == "2024-01-01%2000%3A00%3A00"      # %20, not '+' (quote, not quote_plus)
    assert "+" not in end_date_value
    assert "symbol=EUR%2FUSD" in url                          # urlencode's own safe='' also encodes '/' -- harmless


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
