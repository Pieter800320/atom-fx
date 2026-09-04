"""
ATOM FX - push transport (FCM HTTP v1 via firebase-admin).

send_push(title, body, data) sends a notification to the 'atomfx-signals' topic.
The Android app subscribes to that topic to receive alerts.

Rule #1 note: this changes only the DELIVERY of notifications, not WHEN they fire.
It is a drop-in for the scanner's Telegram send - same message, native transport.

Requires the FCM_SERVICE_ACCOUNT env var (the service-account JSON). If it is not
set, send_push skips quietly (returns False) so local/dev runs never break.

Data-only message (2026-09-04, Notification History feature): title/body live in the
`data` dict, not a separate `notification:` block. A combined notification+data message
is auto-displayed by Android's system tray whenever the app is backgrounded or killed,
which skips the app's own onMessageReceived entirely — so a history/record feature built
on that hook would miss most real-world deliveries. Data-only messages always reach
onMessageReceived (short of a fully force-stopped app, an OS limit, not an FCM one), so
the app can record and build every notification itself in every state.
"""
import os
import json

TOPIC = "atomfx-signals"
_app = None
_init_failed = False


def _ensure_app():
    global _app, _init_failed
    if _app is not None:
        return True
    if _init_failed:
        return False
    raw = os.environ.get("FCM_SERVICE_ACCOUNT", "").strip()
    if not raw:
        print("  [push] FCM_SERVICE_ACCOUNT not set - skipping push")
        _init_failed = True
        return False
    try:
        import firebase_admin
        from firebase_admin import credentials
        info = json.loads(raw)
        cred = credentials.Certificate(info)
        _app = firebase_admin.initialize_app(cred)
        return True
    except Exception as e:
        print(f"  [push] init error: {e}")
        _init_failed = True
        return False


def send_push(title, body, data=None):
    """Send one notification to the atomfx-signals topic. Returns True on success."""
    if not _ensure_app():
        return False
    try:
        from firebase_admin import messaging
        payload = {k: str(v) for k, v in (data or {}).items()}
        payload["title"] = title
        payload["body"] = body
        msg = messaging.Message(
            data=payload,
            topic=TOPIC,
            android=messaging.AndroidConfig(priority="high"),
        )
        msg_id = messaging.send(msg)
        print(f"  [push] sent -> {msg_id}")
        return True
    except Exception as e:
        print(f"  [push] send error: {e}")
        return False


if __name__ == "__main__":
    ok = send_push("ATOM FX - test", "Your push pipeline is working.", {"type": "test"})
    print("OK" if ok else "FAILED")
    raise SystemExit(0 if ok else 1)