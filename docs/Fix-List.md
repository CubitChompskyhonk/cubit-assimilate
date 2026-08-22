# Assimilate Fix List

## Resolved
1. **Fingerprint mismatch** — retry + PIN messaging (v0.2.1+)
2. **Download showed v0.2.1** — root cause: release APK packaged stale `assets/index.html` while tag said 0.3.0.
   - **Fix:** rebuild from main as **v0.3.1**, verify UI string inside APK before Founder link (`VERIFY_PASS`).

## Open
1. Drive OAuth client ID under Founder Google Cloud (runbook ready)
2. SAF polish / larger batch staging
3. Real Drive multipart upload once OAuth configured
