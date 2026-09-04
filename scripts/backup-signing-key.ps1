[CmdletBinding()]
param(
    [string]$SigningRoot = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.datapeek\signing'),
    [string]$BackupRoot = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.datapeek\backup'),
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

$keyPath = Join-Path $SigningRoot 'datapeek-release.jks'
$propertiesPath = Join-Path $SigningRoot 'signing.properties'
if (-not (Test-Path -LiteralPath $keyPath -PathType Leaf)) {
    throw "Release signing key not found: $keyPath"
}
if (-not (Test-Path -LiteralPath $propertiesPath -PathType Leaf)) {
    throw "Release signing properties not found: $propertiesPath"
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

$properties = Read-SigningProperties $propertiesPath
foreach ($required in @('storePassword', 'keyAlias', 'keyPassword')) {
    if ([string]::IsNullOrWhiteSpace($properties[$required])) {
        throw "Missing signing property: $required"
    }
}

New-Item -ItemType Directory -Path $BackupRoot -Force | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupDirectory = Join-Path $BackupRoot "datapeek-release-$stamp"
if ((Test-Path -LiteralPath $backupDirectory) -and -not $Force) {
    throw "Backup destination already exists: $backupDirectory"
}
New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null

$backupKeyPath = Join-Path $backupDirectory 'datapeek-release.jks'
$backupPropertiesPath = Join-Path $backupDirectory 'signing.properties'
Copy-Item -LiteralPath $keyPath -Destination $backupKeyPath -Force:$Force

$backupPropertiesLines = @(
    "storeFile=$($backupKeyPath.Replace('\', '/'))",
    "storePassword=$($properties['storePassword'])",
    "keyAlias=$($properties['keyAlias'])",
    "keyPassword=$($properties['keyPassword'])"
)
Set-Content -LiteralPath $backupPropertiesPath -Value $backupPropertiesLines -Encoding utf8

$primaryHash = (Get-FileHash -LiteralPath $keyPath -Algorithm SHA256).Hash
$backupHash = (Get-FileHash -LiteralPath $backupKeyPath -Algorithm SHA256).Hash
if ($primaryHash -ne $backupHash) {
    throw 'Signing key backup hash mismatch.'
}

$keytool = Get-Command keytool -ErrorAction SilentlyContinue
if ($null -eq $keytool) {
    throw 'keytool was not found on PATH.'
}
$passwordFile = Join-Path ([IO.Path]::GetTempPath()) ("datapeek-keytool-" + [Guid]::NewGuid().ToString('N') + '.txt')
try {
    Set-Content -LiteralPath $passwordFile -Value $properties['storePassword'] -NoNewline -Encoding utf8
    $fingerprintOutput = & $keytool.Source -list -v -keystore $keyPath -storepass:file $passwordFile -alias $properties['keyAlias'] 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw 'Primary signing certificate verification failed.'
    }
    $backupFingerprintOutput = & $keytool.Source -list -v -keystore $backupKeyPath -storepass:file $passwordFile -alias $properties['keyAlias'] 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw 'Backup signing certificate verification failed.'
    }
    $primaryFingerprint = ($fingerprintOutput | Select-String -Pattern 'SHA256' | Select-Object -First 1).Line.Trim()
    $backupFingerprint = ($backupFingerprintOutput | Select-String -Pattern 'SHA256' | Select-Object -First 1).Line.Trim()
    if ([string]::IsNullOrWhiteSpace($primaryFingerprint) -or $primaryFingerprint -ne $backupFingerprint) {
        throw 'Signing certificate fingerprint mismatch.'
    }
} finally {
    Remove-Item -LiteralPath $passwordFile -Force -ErrorAction SilentlyContinue
}

$manifestPath = Join-Path $backupDirectory 'backup-info.txt'
@(
    'App: DataPeek',
    "Key alias: $($properties['keyAlias'])",
    "Primary key SHA-256: $primaryHash",
    "Backup key SHA-256: $backupHash",
    "Certificate fingerprint: MATCH",
    "Created: $(Get-Date -Format o)"
) | Set-Content -LiteralPath $manifestPath -Encoding utf8

Write-Output 'Signing backup created.'
Write-Output "Backup directory: $backupDirectory"
Write-Output 'Primary/backup key hash: MATCH'
Write-Output 'Certificate fingerprint: MATCH'
