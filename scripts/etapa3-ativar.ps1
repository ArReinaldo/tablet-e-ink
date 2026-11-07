# Etapa 3 - Instala o app Cabeceira e o ativa como tela inicial e protetor de tela
# Uso:
#   .\etapa3-ativar.ps1            -> aplica
#   .\etapa3-ativar.ps1 -Desfazer  -> volta ao launcher padrão e às opções originais
#
#   .\etapa3-ativar.ps1 -Apk C:\caminho\cabeceira-v0.3.0.apk  -> usa o APK baixado das Releases
#
# Sem -Apk, usa o APK compilado em app-relogio\app\build\outputs\apk\debug\
param([switch]$Desfazer, [string]$Apk)
. "$PSScriptRoot\_comum.ps1"
if ($Apk) { $ApkApp = $Apk }

if (-not $Desfazer) {
    if (-not (Test-Path $ApkApp)) { Write-Host "APK não encontrado: $ApkApp (baixe das Releases e use -Apk, ou compile o app)." -ForegroundColor Red; exit 1 }
    & $adb install -r $ApkApp

    # Visual de cores suaves: desliga o monocromático da Etapa 2
    sh "settings delete secure accessibility_display_daltonizer_enabled"
    sh "settings delete secure accessibility_display_daltonizer"

    # Tela inicial
    sh "cmd package set-home-activity $Pacote/.MainActivity"

    # Protetor de tela: relógio ao carregar. Substitui 'tela sempre ligada' da Etapa 2, que
    # impediria o protetor de iniciar; o próprio app mantém a tela ligada enquanto carrega.
    sh "settings put secure screensaver_enabled 1"
    sh "settings put secure screensaver_components $Pacote/.ClockDream"
    sh "settings put secure screensaver_activate_on_sleep 1"
    sh "settings put secure screensaver_activate_on_dock 1"
    sh "settings put global stay_on_while_plugged_in 0"

    # Sem tela de bloqueio de deslizar (só funciona se não houver PIN/senha):
    # tocar no protetor vai direto ao relógio
    sh "locksettings set-disabled true"
}
else {
    # Volta ao outro launcher instalado (o padrão do aparelho)
    $outro = sh "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME" |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ -match '^[\w.]+/[\w.$]+$' -and $_ -notlike "$Pacote/*" -and $_ -notlike "com.android.settings/*" } |
        Select-Object -First 1
    if ($outro) { sh "cmd package set-home-activity $outro" }
    sh "settings delete secure screensaver_components"
    sh "settings put secure screensaver_activate_on_sleep 0"
    sh "settings put global stay_on_while_plugged_in 7"
    sh "locksettings set-disabled false"
    Write-Host "Para remover o app também: adb uninstall $Pacote"
}

# Conferência
sh "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1"
sh "echo protetor=`$(settings get secure screensaver_components) ao_carregar=`$(settings get secure screensaver_activate_on_sleep) stay_on=`$(settings get global stay_on_while_plugged_in) mono=`$(settings get secure accessibility_display_daltonizer_enabled)"
