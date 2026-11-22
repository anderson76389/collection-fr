#requires -Version 5.1
<#
.SYNOPSIS
  Verifie l'integrite d'une sauvegarde Collection FR apres transfert ou restauration.
.EXAMPLE
  .\scripts\verify-collection-fr-backup.ps1 -BackupDirectory "E:\Sauvegardes\CollectionFR-2026"
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $BackupDirectory
)
$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$BackupDirectory = [System.IO.Path]::GetFullPath($BackupDirectory).TrimEnd('\')
$Manifest = Join-Path $BackupDirectory "SHA256SUMS.txt"
$Bundle = Join-Path $BackupDirectory "collection-fr.bundle"
$Mirror = Join-Path $BackupDirectory "collection-fr.git"
if (-not (Test-Path -LiteralPath $Manifest -PathType Leaf)) {
    throw "Manifeste SHA-256 introuvable : $Manifest"
}
if (-not (Test-Path -LiteralPath $Bundle -PathType Leaf)) {
    throw "Bundle Git introuvable : $Bundle"
}
$Count = 0
foreach ($Line in Get-Content -LiteralPath $Manifest -Encoding UTF8) {
    if ($Line -notmatch '^([0-9a-fA-F]{64})  (.+)$') {
        throw "Ligne SHA-256 invalide : $Line"
    }
    $Expected = $Matches[1].ToLowerInvariant()
    $Relative = $Matches[2].Replace('/', '\')
    $File = [System.IO.Path]::GetFullPath((Join-Path $BackupDirectory $Relative))
    if (-not $File.StartsWith($BackupDirectory + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Chemin hors sauvegarde refuse : $Relative"
    }
    if (-not (Test-Path -LiteralPath $File -PathType Leaf)) {
        throw "Fichier disparu : $Relative"
    }
    $Actual = (Get-FileHash -LiteralPath $File -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($Actual -ne $Expected) {
        throw "Corruption detectee : $Relative"
    }
    $Count++
}
if ($Count -eq 0) { throw "Le manifeste SHA-256 est vide." }
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw "Git est necessaire pour verifier le bundle."
}
if (-not (Test-Path -LiteralPath $Mirror -PathType Container)) {
    throw "Depot miroir absent : $Mirror"
}
& git --git-dir=$Mirror bundle verify $Bundle
if ($LASTEXITCODE -ne 0) {
    throw "Bundle Git incomplet ou invalide."
}
Write-Host "OK : $Count fichiers verifies (SHA-256), bundle Git valide."
Write-Host "La cle .jks doit toujours etre sauvegardee separement, sous chiffrement."
