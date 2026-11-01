# Backup minimo via mtkclient em modo BROM (SOMENTE LEITURA - nao grava nada no eMMC).
# Uma unica conexao: le a init_boot_a (necessaria para o Magisk) e as particoes
# insubstituiveis (IMEI/calibracao), e gera SHA256.
#
# Como entrar em BROM quando aparecer "Waiting for device":
#   1. Tablet TOTALMENTE desligado e SEM cabo (se mostrar carga, segure Power ~10 s).
#   2. Segure Volume + e Volume - juntos e, segurando, conecte o cabo USB.
#   3. Mantenha as teclas ate o mtkclient comecar a ler (tela do tablet fica preta).
#
# Uso: .\backup-brom.ps1           -> via libusb/WinUSB
#      .\backup-brom.ps1 -Serial   -> via porta COM do driver MediaTek (sem exploit)
param([switch]$Serial)

$Raiz   = (Resolve-Path "$PSScriptRoot\..\..\..").Path
$mtkDir = "$Raiz\tools\mtkclient"   # git clone https://github.com/bkerler/mtkclient tools\mtkclient
$py     = "$mtkDir\.venv\Scripts\python.exe"
$stamp  = Get-Date -Format "yyyy-MM-dd_HHmm"
$dest   = "$Raiz\backup\$stamp"

$particoes = "init_boot_a","boot_a","vbmeta_a","nvram","nvdata","nvcfg",
             "protect1","protect2","persist","proinfo","seccfg"

New-Item -ItemType Directory -Force $dest | Out-Null
Start-Transcript -Path "$dest\backup.log" | Out-Null

$extra = if ($Serial) { @("--serialport", "DETECT") } else { @() }

# O DA precisa da configuracao de RAM do proprio tablet, que vem do preloader.
# Passo 1 (so na primeira vez): ler o preloader via payload no BROM (SRAM, somente leitura).
$plDir = "$Raiz\backup\preloader"
$pl    = "$plDir\preloader_m8_4g.bin"
if (-not (Test-Path $pl) -or (Get-Item $pl).Length -eq 0) {
    New-Item -ItemType Directory -Force $plDir | Out-Null
    Write-Host "== Passo 1/2: lendo o preloader (conexao BROM 1) ==" -ForegroundColor Cyan
    Push-Location $mtkDir
    & $py mtk.py dumppreloader --filename $pl @extra
    $code = $LASTEXITCODE
    Pop-Location
    if ($code -ne 0 -or -not (Test-Path $pl)) { Write-Host "`nFALHOU ao ler o preloader (codigo $code)" -ForegroundColor Red; Stop-Transcript | Out-Null; exit 1 }
    Write-Host "Preloader salvo: $pl ($((Get-Item $pl).Length) bytes)" -ForegroundColor Green
    Write-Host "`nDESCONECTE o cabo, segure Power ~10 s, e reconecte segurando Volume + e Volume -." -ForegroundColor Yellow
}

$arquivos = $particoes | ForEach-Object { "$dest\$_.bin" }
Write-Host "== Passo 2/2: lendo $($particoes.Count) particoes (conexao BROM 2) ==" -ForegroundColor Cyan
Push-Location $mtkDir
& $py mtk.py r ($particoes -join ",") ($arquivos -join ",") --preloader $pl @extra
$code = $LASTEXITCODE
Pop-Location
if ($code -ne 0) { Write-Host "`nFALHOU (codigo $code)" -ForegroundColor Red; Stop-Transcript | Out-Null; exit $code }

Get-ChildItem $dest -Filter *.bin |
    Get-FileHash -Algorithm SHA256 |
    ForEach-Object { "{0}  {1}" -f $_.Hash.ToLower(), (Split-Path $_.Path -Leaf) } |
    Set-Content -Encoding utf8 "$dest\SHA256SUMS.txt"

$faltando = $particoes | Where-Object { -not (Test-Path "$dest\$_.bin") -or (Get-Item "$dest\$_.bin").Length -eq 0 }
if ($faltando) { Write-Host "ATENCAO, faltando ou vazias: $($faltando -join ', ')" -ForegroundColor Yellow }
else { Write-Host "Todas as $($particoes.Count) particoes salvas." -ForegroundColor Green }

Get-ChildItem $dest -Filter *.bin | ForEach-Object { "  {0,-16} {1,8:N1} MB" -f $_.Name, ($_.Length / 1MB) }
Write-Host "Backup em $dest. Copie esta pasta para um segundo local."
Stop-Transcript | Out-Null
