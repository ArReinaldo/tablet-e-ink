# Etapa 2 - Remoção de apps para o usuário 0 (sem root, reversível)
# ATENÇÃO: lista feita para o Multilaser M8 4G (Android 13 Go). Em outro aparelho, revise a lista
# antes de rodar. Os apps continuam no sistema e voltam com -Desfazer.
# Uso:
#   .\etapa2-debloat.ps1            -> remove (pm uninstall --user 0)
#   .\etapa2-debloat.ps1 -Desfazer  -> restaura (pm install-existing --user 0)
param([switch]$Desfazer)
. "$PSScriptRoot\_comum.ps1"

$pacotes = @(
    # Multilaser / lojas
    "br.com.multilaser.lsitec.sellout",
    "br.com.multilaser.lsitec.uai",
    "br.com.multilaser.multifeedback",
    "penseavanti.com.br.app2shop_multilaser",
    "com.netflix.mediaclient",
    # Fábrica Weibu / MTK
    "com.weibu.factorytest",
    "com.weibu.getnumqr",
    "com.mediatek.factorymode",
    "com.mediatek.ygps",
    "com.mediatek.lbs.em2.ui",
    "com.debug.loggerui",
    # MTK extras (duraspeed fecha apps em segundo plano e pode matar o relógio)
    "com.mediatek.voicecommand",
    "com.mediatek.voiceunlock",
    "com.mediatek.omacp",
    "com.mediatek.duraspeed",
    # Google mídia
    "com.google.android.youtube",
    "com.google.android.apps.youtube.kids",
    "com.google.android.apps.youtube.music",
    "com.google.android.videos",
    "com.google.android.apps.photosgo",
    # Google outros
    "com.google.android.apps.assistant",
    "com.google.android.apps.searchlite",
    "com.google.android.apps.tachyon",
    "com.google.android.apps.kids.home",
    "com.google.android.apps.docs",
    "com.google.android.apps.maps",
    "com.google.android.apps.books",
    "com.google.android.gm",
    "com.google.android.calendar",
    "com.google.android.gms.location.history",
    "com.google.android.apps.safetyhub",
    "com.google.android.apps.restore",
    "com.google.android.feedback",
    "com.google.android.marvin.talkback",
    # AOSP extras
    "com.android.egg",
    "com.android.fmradio",
    "com.android.soundrecorder",
    "com.android.traceur",
    # Telefone/SMS (o chip continua funcionando para dados)
    "com.google.android.dialer",
    "com.google.android.contacts",
    "com.google.android.apps.messaging"
)

foreach ($p in $pacotes) {
    if ($Desfazer) { $r = sh "pm install-existing --user 0 $p" }
    else           { $r = sh "pm uninstall --user 0 $p" }
    "{0,-45} {1}" -f $p, ($r -join ' ')
}
