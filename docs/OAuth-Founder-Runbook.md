# Assimilate OAuth — Founder runbook (Drive upload)

Assimilate needs a **Google Cloud OAuth client** under the Founder’s Google account so the APK can upload staged files into Device Vault.

## Steps (Founder in browser)

1. Open [Google Cloud Console](https://console.cloud.google.com/)
2. Create or select a project (e.g. `cubit-assimilate`)
3. **APIs & Services → Enable APIs** → enable **Google Drive API**
4. **APIs & Services → OAuth consent screen**
   - User type: External (or Internal if Workspace)
   - App name: Cubit Assimilate
   - Support email: your email
   - Scopes: add `.../auth/drive.file` (files created/opened by the app only — preferred)
5. **Credentials → Create credentials → OAuth client ID**
   - Application type: **Android**
   - Package name: `inc.cubitsystems.assimilate`
   - SHA-1: from debug keystore (CI/debug):
     ```
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```
6. Also create an **Web application** client if using Google Sign-In helper, or use Android client per current Play services docs
7. Download `google-services.json` if using Firebase/Google Services plugin — **do not paste secrets into chat**
8. Put Client ID into app config (local properties / CI secret) — Engineering wires `DEFAULT_WEB_CLIENT_ID` or Android client

## Vault targets (already live)

| Path | Folder ID |
|------|-----------|
| founder-android-01 | `1OPZA65RyCorgJTFEnaOA05W6SzpB6h2U` |
| photos | `1_LhWLwzJw0X24N6jJFsPlLUOZHv85afZ` |
| documents | `1gmtf2KyYeouLdrs0hP8IHU-ULfmO7rWK` |
| logs | `12tVtMzd1l6VAMvKym7KcAGCfwpJm5OC9` |

## Scope policy

- Prefer `drive.file` (only app-created/selected files)
- Avoid full `drive` scope unless Founder later demands it
