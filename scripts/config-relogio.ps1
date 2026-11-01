# Edita pelo PC a configuração e os lembretes do relógio de cabeceira.
# No tablet: /sdcard/Android/data/br.tabletink.cabeceira/files/   No PC: <projeto>\config\ (fora do git)
# Uso:
#   .\config-relogio.ps1 -Baixar   -> copia cabeceira.conf e lembretes.txt do tablet para o PC
#   .\config-relogio.ps1 -Enviar   -> envia os dois arquivos do PC para o tablet
#                                     (o relógio relê sozinho em até 1 minuto)
# Sem arquivos no PC, o -Enviar parte de config.example e lembretes.example.txt.
param([switch]$Baixar, [switch]$Enviar)
. "$PSScriptRoot\_comum.ps1"

if (-not ($Baixar -xor $Enviar)) { Write-Host "Use -Baixar ou -Enviar."; exit 1 }
$local = Join-Path $Raiz "config"
New-Item -ItemType Directory -Force $local | Out-Null

$pares = @{ "cabeceira.conf" = "config.example"; "lembretes.txt" = "lembretes.example.txt" }
foreach ($a in $pares.Keys) {
    if ($Baixar) {
        & $adb pull "$PastaApp/$a" (Join-Path $local $a)
    } else {
        $arquivo = Join-Path $local $a
        if (-not (Test-Path $arquivo)) { Copy-Item (Join-Path $Raiz $pares[$a]) $arquivo }
        & $adb push $arquivo "$PastaApp/$a"
    }
}
if ($Baixar) { Write-Host "Edite os arquivos em $local e depois rode: .\config-relogio.ps1 -Enviar" }
