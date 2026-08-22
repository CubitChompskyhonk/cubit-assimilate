# Cubit Suite Manifest v1

## Modules
| Module | Package / location | Role |
|--------|-------------------|------|
| **Cubit OS** | `inc.cubitsystems.os` | Suite launcher / home |
| **Library** | `inc.cubitsystems.library` | On-device file navigation |
| **Assimilate** | `inc.cubitsystems.assimilate` | Permission console + intake |
| **HQ** | Google Drive Head Offices | Company VRE / source of truth |
| **Connection bus** | Device Vault / connection/ | Grok status · pending · ack |

## Install order
1. Cubit OS  
2. Library  
3. Assimilate  
4. HQ via Google account (no APK)

## Deep links
- `cubit://os` — OS home  

## Data rules
- Assimilate: per-`install_id`, user-directed Share  
- HQ vault: company space (Founder)  
- No cross-user staging drift  

## Games
Optional Labs — not required for suite.
