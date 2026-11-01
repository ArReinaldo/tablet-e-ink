# Troca o driver do tablet em modo BROM (0E8D:0003) para WinUSB, removendo o driver serial
# da MediaTek (oem146.inf = cdc-acm.inf). RODAR COMO ADMINISTRADOR.
# Copia do driver original: <projeto>\tools\drivers-backup\mediatek-vcom
#   (exporte antes, como administrador: pnputil /export-driver oemNN.inf <projeto>\tools\drivers-backup\mediatek-vcom)
#
# Uso:
#   .\driver-brom.ps1             -> remove o driver serial MediaTek (WinUSB passa a valer)
#   .\driver-brom.ps1 -Restaurar  -> reinstala o driver serial MediaTek
param([switch]$Restaurar)

$copia = (Join-Path (Resolve-Path "$PSScriptRoot\..\..\..").Path "tools\drivers-backup\mediatek-vcom\cdc-acm.inf")

$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) { Write-Host "Abra o PowerShell como administrador." -ForegroundColor Red; exit 1 }

if ($Restaurar) {
    pnputil /add-driver $copia /install
}
else {
    if (-not (Test-Path $copia)) { Write-Host "Copia do driver nao encontrada; abortando." -ForegroundColor Red; exit 1 }
    # Localiza o pacote pelo nome original (o oemNN.inf pode mudar)
    $pub = (pnputil /enum-drivers | Out-String) -split "(\r?\n){2,}" |
        Where-Object { $_ -match "cdc-acm\.inf" -and $_ -match "MediaTek" } |
        ForEach-Object { if ($_ -match "(oem\d+\.inf)") { $matches[1] } }
    if (-not $pub) { Write-Host "Driver serial MediaTek ja nao esta instalado." -ForegroundColor Green; exit 0 }
    foreach ($p in $pub) { pnputil /delete-driver $p /uninstall /force }
}

Write-Host "`nPacotes que atendem 0E8D:0003 agora:"
Get-ChildItem C:\Windows\INF\oem*.inf | Where-Object { (Get-Content $_.FullName -Raw) -match "VID_0E8D&PID_0003" } |
    ForEach-Object { "  " + $_.Name + "  " + ((Select-String -Path $_.FullName -Pattern "^\s*Provider\s*=|libwdi|MediaTek" | Select-Object -First 1).Line) }
