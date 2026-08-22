# Assimilate Fix List

## Open
1. **Fingerprint did not match (Founder report)** — BiometricPrompt failed match; improve retry, device-credential fallback, and messaging so a bad read is not a dead end.
2. Drive API OAuth client for automatic upload into `founder-android-01` (requires Google Cloud OAuth client ID under Founder account).
3. SAF Tier C folder picker write path.
4. Foreground service notification during long transfers.

## Done this cycle
- Biometric retry + explicit PIN/pattern/password fallback messaging
- MediaStore query + local staging folder after Tier B grant
- Upload pipeline scaffold (pending queue + vault deep link)
