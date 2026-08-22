# Assimilate native build notes (v0.2 path)

## Vault (live)
- Head Offices → `00 - Device Vault` → `founder-android-01`
- Subfolders: photos, documents, download, cubit-apps, logs
- Device folder id: `1OPZA65RyCorgJTFEnaOA05W6SzpB6h2U`

## Next native modules
1. **BiometricPrompt** (AndroidX) before first backup and before Tier D
2. **Activity Result** contracts for READ_MEDIA_IMAGES / READ_MEDIA_VIDEO
3. **SAF** `OpenDocumentTree` for Tier C
4. **Drive REST** upload using Founder Google account (Android AccountPicker / Drive API)
5. Foreground service + notification while uploading

## Not in v0.2
- SMS, call log, accessibility, device owner
- Background silent sync without notification
