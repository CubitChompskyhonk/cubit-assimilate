# Grok ↔ Assimilate Connection Protocol v1

## Doctrine
**Grok may request. Founder device executes.**  
No silent remote control. Biometric + system permissions still apply on device.

## Bus
Google Drive folder:
`Head Offices / 00 - Device Vault / founder-android-01 / connection/`

| File | Writer | Reader | Purpose |
|------|--------|--------|---------|
| `status.json` | Assimilate (via Share or future OAuth) | Grok | Device scopes, staged counts, version |
| `pending.json` | Grok (or Founder) | Assimilate | Requested actions |
| `ack.json` | Assimilate | Grok | Last command result |

## status.json (example)
```json
{
  "schema": 1,
  "version": "0.7.0-debug",
  "device_label": "founder-android-01",
  "ts": 0,
  "staged_count": 0,
  "scopes": {
    "media_images": false,
    "sms": false,
    "call_log": false,
    "contacts": false,
    "all_files": false,
    "device_admin": false
  },
  "last_a11y_pkg": "",
  "message": ""
}
```

## pending.json commands (allowed)
```json
{
  "schema": 1,
  "id": "cmd-001",
  "actions": [
    {"type": "stage", "scopes": ["photos", "packages"]},
    {"type": "clear_staging"},
    {"type": "status_only"}
  ]
}
```
Forbidden remote types: keylog, hidden_sms_upload, wipe, disable_lockscreen.

## Founder flow (v1 without OAuth)
1. Grok updates `pending.json` in Drive connection folder (or proposes actions in chat).
2. Founder opens Assimilate → Connection → **Pull pending** (paste/share) or **Run pending from staging**.
3. Founder authenticates if required → device stages/exports.
4. Founder **Share** `status.json` + exports to Drive connection/photos as needed.
5. Grok reads vault and continues.

## Future
OAuth auto-write of status.json and auto-read of pending.json under biometric policy.
