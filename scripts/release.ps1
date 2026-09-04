[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$Version,
    [string]$NotesFile,
    [string]$Repository = 'gitzzang11/DataPeek',
    [string]$SigningPropertiesPath = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.datapeek\signing\signing.properties'),
    [switch]$SkipPush,
    [switch]$SkipGithubRelease,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot

function Invoke-Checked([string]$File, [string[]]$Arguments) {
    & $File @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $File $($Arguments -join ' ')"
    }
}

function Parse-Version([string]$Value) {
    $parts = $Value.Split('.') | ForEach-Object { [int]$_ }
    [pscustomobject]@{ Major = $parts[0]; Minor = $parts[1]; Patch = $parts[2] }
}

function Compare-Version($Left, $Right) {
    foreach ($property in @('Major', 'Minor', 'Patch')) {
        if ($Left.$property -ne $Right.$property) {
            return [Math]::Sign($Left.$property - $Right.$property)
        }
    }
    return 0
}

function Read-SigningProperties([string]$Path) {
    $result = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) {
            continue
        }
        $separator = $line.IndexOf('=')
        if ($separator -le 0) {
            continue
        }
        $name = $line.Substring(0, $separator).Trim()
        $value = $line.Substring($separator + 1)
        $result[$name] = $value
    }
    return $result
}

$status = @(git status --porcelain)
if ($status.Count -ne 0) {
    throw 'Release requires a clean working tree. Commit or stash changes first.'
}
$branch = (git branch --show-current).Trim()
if ($branch -ne 'main') {
    throw "Release must run from main; current branch is '$branch'."
}

$gradleFile = Join-Path $projectRoot 'app\build.gradle.kts'
$gradleText = Get-Content -Raw -LiteralPath $gradleFile
$currentNameMatch = [regex]::Match($gradleText, 'versionName\s*=\s*"([^"]+)"')
$currentCodeMatch = [regex]::Match($gradleText, 'versionCode\s*=\s*(\d+)')
if (-not $currentNameMatch.Success -or -not $currentCodeMatch.Success) {
    throw 'Android versionName/versionCode could not be found.'
}
$currentVersion = $currentNameMatch.Groups[1].Value
$currentVersionCode = [int]$currentCodeMatch.Groups[1].Value
$versionComparison = Compare-Version (Parse-Version $Version) (Parse-Version $currentVersion)
if ($versionComparison -le 0) {
    throw "Version $Version must be greater than current version $currentVersion."
}
$nextVersionCode = $currentVersionCode + 1

$keyPath = $null
if (Test-Path -LiteralPath $SigningPropertiesPath -PathType Leaf) {
    $signingProperties = Read-SigningProperties $SigningPropertiesPath
    $keyPath = $signingProperties['storeFile']
}
if (-not (Test-Path -LiteralPath $SigningPropertiesPath -PathType Leaf) -or
    [string]::IsNullOrWhiteSpace($keyPath) -or
    -not (Test-Path -LiteralPath $keyPath -PathType Leaf)) {
    throw "DataPeek release signing configuration missing: $SigningPropertiesPath"
}

$tag = "v$Version"
$apkPath = Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk'
$assetPath = Join-Path $projectRoot "release\DataPeek-$Version.apk"

if ($DryRun) {
    Write-Output "Current version: $currentVersion"
    Write-Output "Next version: $Version"
    Write-Output "Next versionCode: $nextVersionCode"
    Write-Output 'Signing key: FOUND'
    Write-Output "Repository: $Repository"
    Write-Output "Tag: $tag"
    Write-Output "Release asset: $assetPath"
    exit 0
}

if ((git tag --list $tag).Trim()) {
    throw "Local tag already exists: $tag"
}
if (-not $SkipPush) {
    $remoteTag = @(git ls-remote --tags origin "refs/tags/$tag")
    if ($remoteTag.Count -ne 0) {
        throw "Remote tag already exists: $tag"
    }
}
if (-not $SkipGithubRelease) {
    & gh release view $tag --repo $Repository *> $null
    if ($LASTEXITCODE -eq 0) {
        throw "GitHub Release already exists: $tag"
    }
}

$versionNameReplacement = 'versionName = "' + $Version + '"'
$updatedText = $gradleText -replace 'versionName\s*=\s*"[^"]+"', $versionNameReplacement
$updatedText = $updatedText -replace 'versionCode\s*=\s*\d+', "versionCode = $nextVersionCode"
Set-Content -LiteralPath $gradleFile -Value $updatedText -NoNewline -Encoding utf8

Invoke-Checked '.\gradlew.bat' @('test')
Invoke-Checked '.\gradlew.bat' @('lint')
Invoke-Checked '.\gradlew.bat' @('assembleRelease')

if (-not (Test-Path -LiteralPath $apkPath -PathType Leaf)) {
    throw "Release APK not found: $apkPath"
}
$apksignerPath = (Get-Command apksigner -ErrorAction SilentlyContinue).Source
if ([string]::IsNullOrWhiteSpace($apksignerPath)) {
    $apksignerPath = (Get-Command apksigner.bat -ErrorAction SilentlyContinue).Source
}
if ([string]::IsNullOrWhiteSpace($apksignerPath)) {
    $sdkRoots = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    $sdkRoots += Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'Android\Sdk'
    foreach ($sdkRoot in $sdkRoots) {
        $candidate = Get-ChildItem -LiteralPath (Join-Path $sdkRoot 'build-tools') -Filter apksigner.bat -Recurse -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1
        if ($candidate) { $apksignerPath = $candidate.FullName; break }
    }
}
if ([string]::IsNullOrWhiteSpace($apksignerPath)) {
    throw 'apksigner was not found.'
}
Invoke-Checked $apksignerPath @('verify', '--verbose', '--print-certs', $apkPath)

$hash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash
New-Item -ItemType Directory -Path (Split-Path -Parent $assetPath) -Force | Out-Null
Copy-Item -LiteralPath $apkPath -Destination $assetPath -Force

$notesPath = Join-Path ([IO.Path]::GetTempPath()) ("datapeek-release-notes-" + [Guid]::NewGuid().ToString('N') + '.md')
try {
    if ($NotesFile) {
        Copy-Item -LiteralPath $NotesFile -Destination $notesPath -Force
    } else {
        $template = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'release-notes-template.md')
        $template = $template.Replace('{VERSION}', $Version).Replace('{APK_SHA256}', $hash)
        Set-Content -LiteralPath $notesPath -Value $template -NoNewline -Encoding utf8
    }

    git add -- app/build.gradle.kts
    Invoke-Checked 'git' @('commit', '-m', "Release DataPeek $Version")
    Invoke-Checked 'git' @('tag', '-a', $tag, '-m', "DataPeek $Version")
    if (-not $SkipPush) {
        Invoke-Checked 'git' @('push', 'origin', 'main')
        Invoke-Checked 'git' @('push', 'origin', $tag)
    }
    if (-not $SkipGithubRelease) {
        Invoke-Checked 'gh' @('release', 'create', $tag, '--repo', $Repository, '--title', "DataPeek $Version", '--notes-file', $notesPath, $assetPath)
        Invoke-Checked 'gh' @('release', 'view', $tag, '--repo', $Repository)
    }
} finally {
    Remove-Item -LiteralPath $notesPath -Force -ErrorAction SilentlyContinue
}

Write-Output "Release version: $Version"
Write-Output "Release APK: $assetPath"
Write-Output "APK SHA-256: $hash"
