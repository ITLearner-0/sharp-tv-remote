# Provenance des codes infrarouges

## Flipper-IRDB (CC0 1.0)

Les fichiers de `flipper-cc0/` proviennent de [Flipper-IRDB](https://github.com/Lucaslhm/Flipper-IRDB),
placé sous licence [CC0 1.0](https://github.com/Lucaslhm/Flipper-IRDB/blob/main/LICENSE) à partir du
commit `2319685` (7 août 2025). Le projet précise que les contributions **antérieures** à ce commit ne
sont pas couvertes : seuls les fichiers de TV **créés après** ce commit ont donc été retenus.

| Fichier | Chemin d'origine | Commit d'ajout |
|---|---|---|
| `Bush__BUSH_TV_DLED32287HDCNTDFVP.ir` | `TVs/Bush/BUSH_TV_DLED32287HDCNTDFVP.ir` | 268673ef |
| `ContinentalEdison__ContinentalEdison_CELD55SQLDV24B6.ir` | `TVs/ContinentalEdison/ContinentalEdison_CELD55SQLDV24B6.ir` | c7d00dc8 |
| `Crown__Crown_RC647340.ir` | `TVs/Crown/Crown_RC647340.ir` | f4c0304f |
| `DuraBrand__DuraBrand_DU-1301.ir` | `TVs/DuraBrand DU-1301` | 55b542f1 |
| `Grundig__Grundig_TP750C.ir` | `TVs/Grundig/Grundig_TP750C.ir` | 8bb4d424 |
| `Hitachi__Hitachi_CLE-1031.ir` | `TVs/Hitachi/Hitachi_CLE-1031.ir` | c5023f06 |
| `Hotels__Hilton_Connected_Room_Remote.ir` | `TVs/Hotels/Hilton_Connected_Room_Remote.ir` | 75edf01c |
| `JVC__JVC_LT-49HW97U.ir` | `TVs/JVC/JVC_LT-49HW97U.ir` | 5778c3e0 |
| `Magnavox__MAGNAVOX_CD130MW8.ir` | `TVs/Magnavox/MAGNAVOX_CD130MW8.ir` | 62e6db45 |
| `Philips__Philips_40PFL6533_F7D.ir` | `TVs/Philips/Philips_40PFL6533_F7D.ir` | b87ec7c6 |
| `SEG__SEG_MALTA.ir` | `TVs/SEG/SEG_MALTA.ir` | d573ea7d |
| `Samsung__Samsung_AA59-00484A.ir` | `TVs/Samsung/Samsung_AA59-00484A.ir` | de67cf30 |
| `Samsung__Samsung_BN59-01330C.ir` | `TVs/Samsung/Samsung_BN59-01330C.ir` | 1cfc8bbe |
| `Sanyo__Sanyo_UR77EC2703-3.ir` | `TVs/Sanyo/Sanyo UR77EC2703-3.ir` | ca152b65 |
| `Sanyo__Sanyo_ds32224.ir` | `TVs/Sanyo/Sanyo_ds32224.ir` | adcecc7c |
| `Technika__TECHNIKA_22880.ir` | `TVs/Technika/TECHNIKA_22880.ir` | 298feb37 |
| `Tevion__Tevion_3221TS.ir` | `TVs/Tevion/Tevion_3221TS.ir` | 875b6330 |
| `Toshiba__Toshiba_CT_8560.ir` | `TVs/Toshiba/Toshiba CT 8560.ir` | 0e4aaee7 |
| `Vizio__Vizio_VX32L.ir` | `TVs/Vizio/Vizio_VX32L.ir` | 9d272326 |

## Codes écrits à la main

`handwritten.json` contient des jeux de codes écrits d'après les protocoles infrarouges publics
(NEC, Samsung32, SIRC, RC5, RC6, Panasonic/Kaseikyo, Sharp). Les jeux `verified: true` ont été
recoupés avec une source indépendante (fichiers CC0 ci-dessus) ; les autres restent à confirmer
sur une vraie TV. L'assistant de l'application permet d'essayer plusieurs jeux de codes.

## Régénérer la base

```
python3 tools/ir/build_ir_db.py
```
