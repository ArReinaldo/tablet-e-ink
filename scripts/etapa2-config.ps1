# Etapa 2 - Ajustes de sistema via adb, sem root (tudo reversível)
# Uso:
#   .\etapa2-config.ps1            -> aplica
#   .\etapa2-config.ps1 -Desfazer  -> volta aos padrões do Android
param([switch]$Desfazer)
. "$PSScriptRoot\_comum.ps1"

if (-not $Desfazer) {
    # 1. Manter a tela ligada enquanto carrega (7 = AC + USB + sem fio)
    #    (a Etapa 3 troca isto pelo protetor de tela ao carregar)
    sh "settings put global stay_on_while_plugged_in 7"

    # 2. Desligar as três escalas de animação
    sh "settings put global window_animation_scale 0"
    sh "settings put global transition_animation_scale 0"
    sh "settings put global animator_duration_scale 0"

    # 3. Modo monocromático (correção de cor: 0 = monocromacia)
    #    (a Etapa 3 desliga de novo, para o visual com cores suaves)
    sh "settings put secure accessibility_display_daltonizer 0"
    sh "settings put secure accessibility_display_daltonizer_enabled 1"
}
else {
    sh "settings put global stay_on_while_plugged_in 0"
    sh "settings put global window_animation_scale 1.0"
    sh "settings put global transition_animation_scale 1.0"
    sh "settings delete global animator_duration_scale"
    sh "settings delete secure accessibility_display_daltonizer_enabled"
    sh "settings delete secure accessibility_display_daltonizer"
}

# Conferência
sh "echo stay_on=`$(settings get global stay_on_while_plugged_in) win=`$(settings get global window_animation_scale) trans=`$(settings get global transition_animation_scale) anim=`$(settings get global animator_duration_scale) mono=`$(settings get secure accessibility_display_daltonizer_enabled)/`$(settings get secure accessibility_display_daltonizer)"
