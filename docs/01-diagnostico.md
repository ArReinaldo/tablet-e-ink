# Aparelho testado — diagnóstico (somente leitura)

Levantado só com `adb shell getprop` e afins, sem alterar nada no aparelho.

## Aparelho
| Item | Valor |
|---|---|
| Fabricante / modelo | Multilaser M8 4G (`ML_SO0V_M8_4G`) |
| SoC | MediaTek **MT6762** (Helio P22), plataforma `mt6765`, hardware `mt8768`, 8 núcleos, arm64 |
| RAM | ~1,8 GB (`ro.config.low_ram=true` → **Android Go**) |
| Armazenamento | eMMC 32 GB (≈23 GB em /data) |
| Tela | 800×1280, 240 dpi |
| Android | 13 (API 33), patch de segurança 2025-03-05 |
| Build | `ML_SO0V_M8_4G.V8_20250304` — `user/release-keys` |
| Vendor | Android 12 (API 31), BSP MediaTek `alps-mp-s0.mp1.rc` (ODM Weibu) |
| Kernel | 4.19.191 |

## Partições / boot
- A/B (`ro.build.ab_update=true`), Virtual A/B, partições dinâmicas (`super`), Treble ativo.
- Tem `boot`, `init_boot`, `vendor_boot`, `dtbo`, `vbmeta*`, `lk`, `preloader` (em _a/_b).
  Na prática a `init_boot_a` é vazia: o ramdisk está na `boot_a` (ver [root-opcional](root-opcional/)).
- Partições exclusivas (sem cópia _a/_b): `nvram`, `nvdata`, `nvcfg`, `protect1`, `protect2`,
  `persist`, `proinfo`, `seccfg`, `md_udc`, `frp`, `otp`.
- Criptografia: FBE (por arquivo) + metadata.

## Bootloader / segurança (de fábrica)
| Propriedade | Valor | Significado |
|---|---|---|
| `ro.boot.flash.locked` | 1 | bootloader bloqueado |
| `ro.boot.verifiedbootstate` | green | AVB íntegro, imagens oficiais |
| `ro.secure` / `ro.debuggable` | 1 / 0 | build de produção, sem root |
| `sys.oem_unlock_allowed` | 0 | "Desbloqueio OEM" desligado (o toggle existe) |

## O que não funciona sem root (testado)
- Saturação global de cor (`SurfaceFlinger 1022`, `color_display`): permissão negada ao shell.
- MiraVision (ajuste de imagem da MediaTek): exige permissão de assinatura.
- Ler partições pelo adb ou `fastboot fetch` (recusado em builds `user`).
