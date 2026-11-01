# Root (opcional) — Multilaser M8 4G

> ## ⚠️ Leia antes
> - **Nada disto é necessário** para usar o relógio e o leitor. O root só serve para extras:
>   limitar a carga da bateria em 85%, cores suaves no sistema inteiro e bloquear atualizações.
> - **Desbloquear o bootloader APAGA TODOS OS DADOS do aparelho.**
> - Pode anular a garantia, quebrar atualizações oficiais e fazer apps de banco/streaming recusarem
>   rodar. Um erro de gravação pode deixar o aparelho sem iniciar.
> - **Tudo por sua conta e risco.** Os passos abaixo foram feitos num único aparelho
>   (Multilaser M8 4G, `ML_SO0V_M8_4G`, MT6762, Android 13 Go, build `V8_20250304`); em outro
>   modelo ou build os nomes de partição e o comportamento podem ser diferentes.

Os comandos usam `adb`/`fastboot` do Android platform-tools e devem ser rodados a partir da pasta do projeto.

## 0. O que foi verificado neste aparelho
- "Desbloqueio de OEM" aparece em Opções do desenvolvedor.
- **O Magisk corrige a `boot_a`**, não a `init_boot_a`: a `init_boot_a` deste aparelho é vazia
  (só zeros) e a `boot_a` tem header v2 com ramdisk.
- O `fastboot fetch` do fastbootd é recusado ("fetch not supported on user builds"): a imagem
  original só sai pelo modo BROM (mtkclient).
- O chip não tem proteção no BROM (SBC/SLA/DAA desligados), então o mtkclient lê sem autenticação.

## 1. Backup mínimo (somente leitura)
Guarda a `boot_a` original (que o Magisk precisa) e as partições insubstituíveis (IMEI/calibração).

1. Python 3 + Git. Clone o mtkclient em `tools\mtkclient` e instale as dependências num venv:
   ```powershell
   git clone https://github.com/bkerler/mtkclient tools\mtkclient
   cd tools\mtkclient; python -m venv .venv; .\.venv\Scripts\python.exe -m pip install -r requirements.txt
   ```
2. Driver: no Windows, o driver serial da MediaTek (certificado WHQL) “ganha” do WinUSB e o mtkclient
   não abre o BROM. Instale o WinUSB para `0E8D:0003` com o [Zadig](https://zadig.akeo.ie/)
   (Device → Create New Device) e, se necessário, remova o driver serial com
   [`scripts/driver-brom.ps1`](scripts/driver-brom.ps1) (como administrador; `-Restaurar` desfaz).
3. Rode [`scripts/backup-brom.ps1`](scripts/backup-brom.ps1). Para entrar em BROM: com o tablet
   ligado no cabo, `adb shell reboot -p`; quando apagar, desconecte, segure **Volume + e Volume −** e
   reconecte. O script primeiro lê o preloader (precisa dele para configurar a RAM) e numa segunda
   conexão lê as partições para `backup\<data>\`, com SHA256.
4. Guarde a pasta `backup\` em dois lugares. Ela é pessoal (IMEI): **nunca publique**.

## 2. Desbloquear o bootloader (APAGA O APARELHO)
1. Opções do desenvolvedor → **Desbloqueio de OEM**.
2. `adb reboot bootloader` e `fastboot flashing unlock`; confirme no tablet com **Volume +**.
3. `fastboot reboot`. O aparelho volta zerado e mostra o aviso "Orange State" ao ligar (normal).

## 3. Root com Magisk
1. Configuração inicial, Depuração USB de novo, autorize o PC.
2. Instale o [Magisk](https://github.com/topjohnwu/Magisk/releases) (`adb install Magisk-vXX.apk`).
3. `adb push backup\<data>\boot_a.bin /sdcard/Download/boot_a.img`
4. No Magisk: **Instalar → Patch firmware/image** → `boot_a.img`.
5. `adb pull /sdcard/Download/magisk_patched-XXXXX.img backup\`
6. `adb reboot bootloader` e `fastboot flash boot_a backup\magisk_patched-XXXXX.img`, depois `fastboot reboot`.
7. Para o adb ter root (Magisk 31): no app Magisk → **SuperUsuário → Grant Superuser → Shell**.
   (O Magisk 31 recusa o shell do adb sem mostrar aviso; o log diz `su: request rejected (2000)`.)

**Se não iniciar:** `fastboot flash boot_a backup\<data>\boot_a.bin` restaura o original.

## 4. Extras com root
| Script | O que faz | Desfazer |
|---|---|---|
| [`scripts/etapa4-root.ps1`](scripts/etapa4-root.ps1) | saturação do sistema 0,45 (estilo Colorsoft) e **limite de carga 50/85** como serviço do Magisk (`/data/adb/service.d`) | `-Desfazer` |
| [`scripts/bloquear-atualizacoes.ps1`](scripts/bloquear-atualizacoes.ps1) | desativa os componentes de atualização do sistema do Google Play Services (uma OTA apagaria o root) | `-Desfazer` |

Como funciona o limite de carga: escrever `0` em `/sys/devices/platform/charger/input_current`
corta a corrente de entrada (o aparelho passa a usar a bateria mesmo no cabo); `3000000` libera de
novo (o arquivo não aceita `-1` de volta; no reboot o kernel restaura). O serviço confere a cada
minuto: pausa em ≥ 85% e retoma em ≤ 50%. Alternativa pronta para outros aparelhos:
[ACC — Advanced Charging Controller](https://github.com/VR-25/acc).
