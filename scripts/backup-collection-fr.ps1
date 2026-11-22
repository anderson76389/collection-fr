#requires -Version 5.1
<#
.SYNOPSIS
    Sauvegarde hors ligne de Collection FR, de ses deux branches et des APK/JAR publies.
.DESCRIPTION
    Necessite Git et GitHub CLI (gh) avec acces aux releases publiques.
    N'exporte JAMAIS la cle de signature Android ni les secrets GitHub.
    Copiez votre vrai keystore .jks separement sur un support chiffre.
.EXAMPLE
    .\scripts\backup-collection-fr.ps1 -Destination "E:\Sauvegardes\CollectionFR"
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $Destination
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$Repository = "anderson76389/collection-fr"

foreach ($Tool in @("git", "gh")) {
    if (-not (Get-Command $Tool -ErrorAction SilentlyContinue)) {
        throw "Commande introuvable : $Tool. Installer Git et GitHub CLI avant de continuer."
    }
}
if (Test-Path -LiteralPath $Destination) {
    throw "Le dossier cible existe deja. Choisissez un nouveau dossier vide : $Destination"
}
$Destination = [System.IO.Path]::GetFullPath($Destination)
New-Item -ItemType Directory -Path $Destination -Force | Out-Null
$Mirror = Join-Path $Destination "collection-fr.git"
$Bundle = Join-Path $Destination "collection-fr.bundle"
$Releases = Join-Path $Destination "releases"
New-Item -ItemType Directory -Path $Releases | Out-Null

Write-Host "1/5 - Clonage miroir (toutes les branches, tags et historique Git)..."
& git clone --mirror "https://github.com/$Repository.git" $Mirror
if ($LASTEXITCODE -ne 0) { throw "Echec du clonage miroir Git." }

Write-Host "2/5 - Export Git autonome..."
& git --git-dir=$Mirror bundle create $Bundle --all
if ($LASTEXITCODE -ne 0) { throw "Echec de creation du bundle Git." }
& git --git-dir=$Mirror bundle verify $Bundle
if ($LASTEXITCODE -ne 0) { throw "Bundle Git invalide." }

Write-Host "3/5 - Sauvegarde de l'index Mihon et des APK/JAR references..."
$IndexPath = Join-Path $Destination "index.json"
$RepoPath = Join-Path $Destination "repo.json"
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/$Repository/repo/index.json" -OutFile $IndexPath -UseBasicParsing
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/$Repository/repo/repo.json" -OutFile $RepoPath -UseBasicParsing
$Index = Get-Content -LiteralPath $IndexPath -Raw -Encoding UTF8 | ConvertFrom-Json
$Packages = @($Index.extensionList.extensions)
if ($Packages.Count -lt 1) { throw "Index Mihon vide ou illisible." }

$Tags = @($Packages | ForEach-Object {
    if ($_.resources.apkUrl -notmatch '/releases/download/([^/]+)/') {
        throw "URL APK invalide dans l'index pour $($_.name)"
    }
    $Matches[1]
} | Sort-Object -Unique)

foreach ($Tag in $Tags) {
    if ($Tag -notmatch '^[a-zA-Z0-9._-]+$') { throw "Tag de release dangereux : $Tag" }
    $Target = Join-Path $Releases $Tag
    New-Item -ItemType Directory -Path $Target | Out-Null
    & gh release download $Tag --repo $Repository --dir $Target
    if ($LASTEXITCODE -ne 0) { throw "Echec du telechargement de la release $Tag" }
}

foreach ($Package in $Packages) {
    foreach ($Field in @("apkUrl", "jarUrl")) {
        $Url = [string] $Package.resources.$Field
        $Parsed = [Uri] $Url
        if ($Parsed.Host -ne "github.com" -or
            $Parsed.AbsolutePath -notlike "/$Repository/releases/download/*") {
            throw "Adresse $Field inattendue : $Url"
        }
        $Tail = $Parsed.AbsolutePath.Substring(("/$Repository/releases/download/").Length)
        $Parts = $Tail.Split("/", 2)
        if ($Parts.Count -ne 2) { throw "URL de release incorrecte : $Url" }
        $Tag = $Parts[0]
        $Name = [Uri]::UnescapeDataString($Parts[1])
        $AssetPath = Join-Path (Join-Path $Releases $Tag) $Name
        if (-not (Test-Path -LiteralPath $AssetPath -PathType Leaf)) {
            throw "Fichier indispensable manquant dans la sauvegarde : $AssetPath"
        }
    }
}

$Readme = Join-Path $Destination "RESTAURATION.txt"
@"
Collection FR - Sauvegarde effectuee le $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
Repository: https://github.com/$Repository
Branches principales: main (code), repo (index Mihon)
Nombre de packages Mihon: $($Packages.Count)
Tags de releases references: $($Tags -join ", ")

Restaurer une copie locale :
  git clone collection-fr.bundle collection-fr-restaure
  cd collection-fr-restaure
  git checkout main
  git checkout repo

Pour verifier les fichiers, comparer leurs SHA-256 avec SHA256SUMS.txt.
Les APK et JAR se trouvent dans releases/<tag>.
Conserver cette sauvegarde en deux endroits physiques distincts ou sur un cloud prive chiffre.

IMPORTANT : la cle de signature Android d'origine (.jks) et les mots de passe
ne figurent PAS dans cette sauvegarde. Les sauvegarder SEPARAMENT,
dans un coffre-fort chiffre, hors de GitHub. Sans cette cle d'origine,
il peut etre impossible de mettre a jour les APK installes.
"@ | Set-Content -LiteralPath $Readme -Encoding UTF8

Write-Host "4/5 - Calcul et enregistrement des SHA-256..."
$Manifest = Join-Path $Destination "SHA256SUMS.txt"
$Lines = New-Object 'System.Collections.Generic.List[string]'
Get-ChildItem -LiteralPath $Destination -Recurse -File -Force |
    Where-Object { $_.FullName -ne $Manifest } |
    Sort-Object FullName |
    ForEach-Object {
        $Hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $Relative = $_.FullName.Substring($Destination.Length).TrimStart('\').Replace('\', '/')
        $Lines.Add("$Hash  $Relative")
    }
[System.IO.File]::WriteAllLines($Manifest, $Lines, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "5/5 - Terminé : $Destination"
Write-Host "Sauvegarde creee. N'oubliez pas de conserver la cle .jks separement et chiffree."
