# Instala o projeto inteiro num tablet (sem root): ajustes, limpeza de apps, KOReader e o relógio.
# Antes: Depuração USB ligada e PC autorizado no tablet (README > Instalação).
# Uso:
#   .\instalar-tudo.ps1 -Apk C:\caminho\cabeceira-v0.2.0.apk   -> com o APK baixado das Releases
#   .\instalar-tudo.ps1                                         -> com o APK compilado localmente
#   -SemDebloat   -> pula a remoção de apps (recomendado fora do Multilaser M8)
param([switch]$SemDebloat, [string]$Apk)
. "$PSScriptRoot\_comum.ps1"

Write-Host "Aguardando o tablet autorizado no adb..."
while ((& $adb get-state 2>$null) -ne "device") { Start-Sleep 3 }

Write-Host "`n== Etapa 2: ajustes de sistema =="
& "$PSScriptRoot\etapa2-config.ps1"

if (-not $SemDebloat) {
    Write-Host "`n== Etapa 2: remoção de apps =="
    & "$PSScriptRoot\etapa2-debloat.ps1"
}

Write-Host "`n== KOReader, leitor alternativo (release oficial do GitHub) =="
$abi = (& $adb shell getprop ro.product.cpu.abi).Trim()
$arq = switch -Wildcard ($abi) { "arm64*" { "arm64" } "armeabi*" { "arm" } "x86_64" { "x86_64" } "x86" { "x86" } default { "arm64" } }
$pasta = Join-Path $Raiz "downloads"
New-Item -ItemType Directory -Force $pasta | Out-Null
$apk = Get-ChildItem $pasta -Filter "koreader-android-$arq-*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $apk) {
    $rel = Invoke-RestMethod "https://api.github.com/repos/koreader/koreader/releases/latest"
    $asset = $rel.assets | Where-Object { $_.name -match "^koreader-android-$arq-.*\.apk$" } | Select-Object -First 1
    if (-not $asset) { Write-Host "Não achei o APK do KOReader para $abi." -ForegroundColor Red; exit 1 }
    $destino = Join-Path $pasta $asset.name
    $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest $asset.browser_download_url -OutFile $destino
    $apk = Get-Item $destino
}
& $adb install -r $apk.FullName

Write-Host "`n== Etapa 3: relógio de cabeceira (instala o app e ativa) =="
if ($Apk) { & "$PSScriptRoot\etapa3-ativar.ps1" -Apk $Apk } else { & "$PSScriptRoot\etapa3-ativar.ps1" }

Write-Host "`nPronto. Configure cidade e agenda com: .\config-relogio.ps1 -Baixar (README > Configuração)"
Write-Host "Leitor: instale o ReadEra pela Play Store (README > Instalação). Sem ele, o botão abre o KOReader."
