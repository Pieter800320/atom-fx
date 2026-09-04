"""
ATOM FX — shared alert-message helpers (NEW-tier, `push/`).

Moved out of `scanner/scan_h1.py` verbatim (2026-09-04, Signals Roadmap Phase 3) so
`scanner/scan_cot.py` — a second, independent orchestrator — can send push alerts
through the same `send_push_alert` drop-in without importing one entry-point script
from another. No behaviour change: same functions, same bodies, same call contract.

Rule #1 note: this is pure message formatting / delivery plumbing, not a trading
calculation or a firing condition — moving it does not touch anything
`tests/test_rule1_frozen.py` checks (that test calls `scanner/*.py` functions directly
via `tests/frozen_probe.py` and never imports `scan_h1.py`).
"""
import os
import re
import urllib.request
import urllib.error
import json

from scanner.config import PAIRS
from push.send_push import send_push

_HTML_TAG_RE = re.compile(r"<[^>]+>")
_NO_SLASH_PAIRS = [p.replace("/", "") for p in PAIRS]


def send_telegram(msg: str):
    token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
    chat  = os.environ.get("TELEGRAM_CHAT_ID",   "").strip()
    if not token or not chat:
        print("  Telegram: BOT_TOKEN or CHAT_ID not set in secrets")
        return
    print(f"  Telegram: sending to chat_id={chat!r} (token length={len(token)})")
    url     = f"https://api.telegram.org/bot{token}/sendMessage"
    payload = json.dumps({
        "chat_id":    chat,
        "text":       msg,
        "parse_mode": "HTML",
    }).encode("utf-8")
    req = urllib.request.Request(
        url, data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            body = r.read().decode()
            print(f"  Telegram: {r.status} — {body[:200]}")
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        print(f"  Telegram error {e.code}: {body}")
    except Exception as e:
        print(f"  Telegram error: {e}")


def _msg_to_title_body(msg: str) -> tuple[str, str]:
    """Split one Telegram-style HTML message into a push (title, body). Same content, no markup."""
    lines = [_HTML_TAG_RE.sub("", line).strip() for line in msg.strip().splitlines()]
    lines = [line for line in lines if line]
    title = lines[0] if lines else "ATOM FX"
    body  = "\n".join(lines[1:]) if len(lines) > 1 else title
    return title, body


def _extract_pair(msg: str) -> str | None:
    for pair in _NO_SLASH_PAIRS:
        if pair in msg:
            return pair
    return None


def send_push_alert(msg: str, msg_type: str, deeplink: str, direction: str | None = None, extra: dict | None = None) -> None:
    """§7 drop-in for send_telegram: push first (primary transport), Telegram as fallback.

    `extra` (added 2026-09-04) merges additional per-type fields into the push `data`
    payload — e.g. `regime_code` for `archetype_change`, so the client can assemble a
    contextual explanation without re-deriving the value from `signals.json` itself.
    """
    title, body = _msg_to_title_body(msg)
    data = {"type": msg_type, "deeplink": deeplink}
    if direction:
        data["direction"] = direction
    if extra:
        data.update(extra)
    if not send_push(title, body, data):
        send_telegram(msg)


def send_push_level_alert(msg: str) -> None:
    pair = _extract_pair(msg)
    direction = "above" if "↑" in msg else "below" if "↓" in msg else None
    title, body = _msg_to_title_body(msg)
    data = {"type": "level_alert"}
    if pair:
        data["pair"] = pair
        data["deeplink"] = f"atomfx://pair/{pair}"
    if direction:
        data["direction"] = direction
    if not send_push(title, body, data):
        send_telegram(msg)
