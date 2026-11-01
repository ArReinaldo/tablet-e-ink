# Bloqueia atualizacoes de sistema (OTA). Uma OTA substituiria a boot_a e removeria o root
# e os servicos do Magisk. Neste tablet a OTA vem do Google Play Services (GOTA), nao de app
# da Multilaser; desativa so os componentes de atualizacao dele (a Play Store continua normal).
# Requer root (Magisk). Uso:
#   .\bloquear-atualizacoes.ps1            -> bloqueia
#   .\bloquear-atualizacoes.ps1 -Desfazer  -> libera de novo
param([switch]$Desfazer)

. "$PSScriptRoot\..\..\..\scripts\_comum.ps1"

$componentes = @(
    "com.google.android.gms/.update.SystemUpdateActivity",
    "com.google.android.gms/.update.SystemUpdateService",
    "com.google.android.gms/.update.SystemUpdateGcmTaskService",
    "com.google.android.gms/.update.SystemUpdatePersistentListenerService",
    "com.google.android.gms/.update.OtaSuggestionActivity",
    "com.google.android.gms/.update.OtaSuggestionSummaryProvider",
    "com.google.android.gms/.update.UpdateFromSdCardActivity",
    "com.google.android.gms/.update.phone.PopupDialog"
)

if (-not ((su "id") -match "uid=0")) { Write-Host "Sem root (su negado ou Magisk ausente)."; exit 1 }

$acao = if ($Desfazer) { "enable" } else { "disable" }
foreach ($c in $componentes) {
    $r = su "pm $acao --user 0 $c"
    "{0,-72} {1}" -f $c, ($r -join " ")
}

# Opcao do Android "Atualizacoes automaticas do sistema" (Opcoes do desenvolvedor)
if ($Desfazer) { su "settings delete global ota_disable_automatic_update" | Out-Null }
else           { su "settings put global ota_disable_automatic_update 1" }
Write-Host ("ota_disable_automatic_update=" + (su "settings get global ota_disable_automatic_update"))
