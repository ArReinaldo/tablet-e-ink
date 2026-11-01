# Etapa 4 - Extras que exigem root (Magisk). Rode depois de restaurar-tudo.ps1.
# Pre-requisito (Magisk 31.1): app Magisk > SuperUsuario > "Grant Superuser" > tocar em Shell.
# Uso:
#   .\etapa4-root.ps1                     -> aplica saturacao Colorsoft 0.45 + limite de carga 50/85
#   .\etapa4-root.ps1 -Saturacao 0.5      -> outra intensidade (0 = cinza, 1 = cores normais)
#   .\etapa4-root.ps1 -Liga 40 -Pausa 80  -> outra faixa de carga
#   .\etapa4-root.ps1 -Sondar             -> so lista os controles de carga do kernel (leitura)
#   .\etapa4-root.ps1 -Desfazer           -> remove os scripts de boot, cores e carga normais
param([double]$Saturacao = 0.45, [int]$Liga = 50, [int]$Pausa = 85, [switch]$Sondar, [switch]$Desfazer)

. "$PSScriptRoot\..\..\..\scripts\_comum.ps1"

$dir        = "/data/adb/service.d"
$colorsoft  = "$dir/colorsoft.sh"
$carga      = "$dir/limite-carga.sh"
# input_current: 0 pausa a carga; o arquivo nao aceita -1 de volta, entao 3 A (acima do
# que o carregador entrega) e o valor de "sem limite". No reboot o kernel volta a -1.
$inputCur   = "/sys/devices/platform/charger/input_current"
$semLimite  = 3000000
# "[a]" impede o pkill de casar com a propria linha de comando do su que o executa;
# -9 porque o sh parado no "sleep 60" ignora o SIGTERM
$matarCarga = "pkill -9 -f /service.d/limite-carg[a]"

if (-not ((su "id") -match "uid=0")) { Write-Host "Sem root (su negado ou Magisk ausente)."; exit 1 }

if ($Sondar) {
    # Candidatos para limitar a carga sem tomada inteligente (somente leitura)
    su "ls -l /sys/devices/platform/charger/ /sys/class/power_supply/battery/ 2>/dev/null"
    su "for f in /sys/devices/platform/charger/* /sys/class/power_supply/*/charge* /sys/class/power_supply/*/input_suspend; do [ -f `$f ] && echo `$f=`$(cat `$f 2>/dev/null | head -1); done"
    exit 0
}

if ($Desfazer) {
    su $matarCarga
    su "rm -f $colorsoft $carga; echo $semLimite > $inputCur"
    su "service call SurfaceFlinger 1022 f 1.0"
    Write-Host "Cores normais, carga sem limite e scripts de boot removidos."
    exit 0
}

if ($Liga -ge $Pausa) { Write-Host "-Liga ($Liga) precisa ser menor que -Pausa ($Pausa)."; exit 1 }

function enviar([string]$nome, [string]$conteudo, [string]$destino) {
    $tmp = Join-Path $env:TEMP $nome
    [IO.File]::WriteAllText($tmp, $conteudo)
    & $adb push $tmp "/data/local/tmp/$nome" | Out-Null
    su "mkdir -p $dir && cp /data/local/tmp/$nome $destino && chmod 755 $destino && rm /data/local/tmp/$nome"
}

# Saturacao global (estilo Kindle Colorsoft), reaplicada a cada boot pelo Magisk
$sat = $Saturacao.ToString([Globalization.CultureInfo]::InvariantCulture)
enviar "colorsoft.sh" ("#!/system/bin/sh`n" +
    "# tablet-e-ink: cores suaves estilo Kindle Colorsoft`n" +
    "until [ `"`$(getprop sys.boot_completed)`" = 1 ]; do sleep 2; done`n" +
    "sleep 5`n" +
    "service call SurfaceFlinger 1022 f $sat`n") $colorsoft
su "service call SurfaceFlinger 1022 f $sat" | Out-Null
Write-Host "Saturacao $sat aplicada agora e a cada boot ($colorsoft)."

# Limite de carga: pausa em >= Pausa%, retoma em <= Liga%, confere a cada minuto
enviar "limite-carga.sh" ("#!/system/bin/sh`n" +
    "# tablet-e-ink: limite de carga $Liga/$Pausa sem tomada inteligente`n" +
    "LIGA=$Liga; PAUSA=$Pausa`n" +
    "C=$inputCur`n" +
    "B=/sys/class/power_supply/battery/capacity`n" +
    "until [ `"`$(getprop sys.boot_completed)`" = 1 ]; do sleep 5; done`n" +
    "while true; do`n" +
    "  n=`$(cat `$B)`n" +
    "  if [ `"`$n`" -ge `"`$PAUSA`" ]; then echo 0 > `$C`n" +
    "  elif [ `"`$n`" -le `"`$LIGA`" ]; then echo $semLimite > `$C`n" +
    "  fi`n" +
    "  sleep 60`n" +
    "done`n") $carga
# Inicia agora (sem esperar reboot), substituindo uma instancia anterior
su $matarCarga
su "setsid sh $carga </dev/null >/dev/null 2>&1 &"
Write-Host "Limite de carga $Liga/$Pausa ativo agora e a cada boot ($carga)."
