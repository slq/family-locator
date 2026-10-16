[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidateRange(1, 2100000000)][int]$VersionCode,
    [Parameter(Mandatory = $true)][ValidatePattern('^\d+\.\d+\.\d+$')][string]$VersionName
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    & .\gradlew.bat :app:assembleRelease "-PappVersionCode=$VersionCode" "-PappVersionName=$VersionName" --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8' --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
    $sdkLine = Get-Content -LiteralPath 'local.properties' | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
    if (-not $sdkLine) { throw 'Add sdk.dir to local.properties first.' }
    $sdkPath = ($sdkLine -replace '^sdk\.dir=', '').Replace('\\', '\').Replace('\:', ':')
    & python scripts/prepare_release.py --version-code $VersionCode --version-name $VersionName --sdk $sdkPath
    if ($LASTEXITCODE -ne 0) { throw 'Release packaging failed.' }
} finally { Pop-Location }
