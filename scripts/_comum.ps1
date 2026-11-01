# Configuração comum dos scripts (carregada com: . "$PSScriptRoot\_comum.ps1")
#
# adb: usa a variável de ambiente ADB se existir; senão, "adb" do PATH.
#   Ex.: $env:ADB = "C:\platform-tools\adb.exe"
# Aparelho: com mais de um conectado, defina ANDROID_SERIAL (o próprio adb lê essa variável).
# Ajustes só da sua máquina podem ficar em scripts\local.ps1 (fora do git).

$Raiz = Split-Path $PSScriptRoot -Parent
$local = Join-Path $PSScriptRoot "local.ps1"
if (Test-Path $local) { . $local }

$adb = if ($env:ADB) { $env:ADB } else { "adb" }
if (-not (Get-Command $adb -ErrorAction SilentlyContinue)) {
    Write-Host "adb não encontrado. Instale o Android platform-tools e coloque no PATH, ou defina `$env:ADB." -ForegroundColor Red
    exit 1
}

function sh([string]$cmd) { & $adb shell $cmd }
function su([string]$cmd) { & $adb shell "su -c '$cmd'" }

$Pacote   = "br.tabletink.cabeceira"
$ApkApp   = Join-Path $Raiz "app-relogio\app\build\outputs\apk\debug\app-debug.apk"
$PastaApp = "/sdcard/Android/data/$Pacote/files"
