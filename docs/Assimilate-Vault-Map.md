# Assimilate — Google Drive Vault Map
**Root:** Cubit Systems Inc. - Head Offices  
**Vault:** `00 - Device Vault`

```
00 - Device Vault/
  [device-label]/          e.g. founder-android-01/
    manifest.json            what was backed up, when, scopes
    photos/                  Tier B
    download/                Tier C/D as selected
    documents/               Tier C
    cubit-apps/              APK copies / config exports
    logs/                    assimilate run logs (no message bodies)
```

## Naming
- device-label: stable id chosen on first run (not hardware serial in chat logs)
- each run folder optional: `run-YYYYMMDD-HHMM/`

## Rules
- No secrets (PATs, passwords) written into vault logs
- Manifest lists counts and scope names, not full personal file lists in Boardroom chat
