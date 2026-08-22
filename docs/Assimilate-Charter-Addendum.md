# Cubit Assimilate — Privacy Charter Addendum
**Status:** Founder-approved with Boardroom proposals  
**Parent law:** Android Privacy Charter  
**Doctrine:** Assimilation before operation · Explicit consent · Biometrics for high risk

## Purpose
Assimilate is the Founder-authorized **backup and company extremity agent** on the primary Android device. It is not spyware, not silent full-device control, and not a bypass of Android permissions.

## Definitions
- **Vault:** Google Drive folder tree under Head Offices used for device backups.
- **Scope:** A data class the Founder explicitly enables.
- **Founder key:** Device biometrics (or screen lock fallback) required for high-risk actions.

## Allowed outcomes
1. Backup of **Founder-selected** scopes to the Vault.
2. Clear permission UI before each new scope.
3. Company apps (Library, Head Offices, Assimilate) as the on-device Cubit layer.
4. Biometric gate before first wide backup, broad storage enable, or vault wipe.

## Forbidden
1. Covert collection of SMS, call logs, messengers, or mic/camera without a separate explicit project approval.
2. Hidden app icon, silent exfiltration, keylogging, or accessibility abuse.
3. Claiming “complete control” of a stock Android phone without enterprise Device Owner enrollment (out of scope unless later approved).
4. Bypassing Android runtime permission dialogs.

## Permission tiers
| Tier | Scope | Gate |
|------|--------|------|
| A | Network, notifications, biometric use | Install / first run |
| B | Photos/videos (MediaStore or picker) | Biometric + confirm |
| C | Documents tree via SAF (user-selected) | Biometric + confirm |
| D | Broad all-files access (optional) | Biometric + typed CONFIRM |
| E | SMS, call log, accessibility, device admin | Not in v1 |

## Retention & delete
- Vault copies live in Founder’s Google Drive until Founder deletes them.
- Uninstalling Assimilate stops new backups; it does not auto-delete Drive data.
- Founder may request a vault wipe job (biometric + confirm).

## Success criteria
Founder can restore chosen data from Drive and operate Cubit apps under biometric policy — without Grok reading personal life unprompted.
