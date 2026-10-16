[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$propertiesPath = Join-Path $projectRoot 'keystore.properties'
$signingRoot = Join-Path $projectRoot 'signing'
$keyPath = Join-Path $signingRoot 'rodzina-release.jks'
if ((Test-Path -LiteralPath $propertiesPath) -or (Test-Path -LiteralPath $keyPath)) {
    throw 'Signing configuration already exists. Keep the existing key for future updates.'
}
$keytool = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\keytool.exe' } else { 'keytool.exe' }
New-Item -ItemType Directory -Path $signingRoot -Force | Out-Null
$randomBytes = New-Object byte[] 32
$random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $random.GetBytes($randomBytes) } finally { $random.Dispose() }
$env:RODZINA_NEW_KEY_PASSWORD = -join ($randomBytes | ForEach-Object { $_.ToString('x2') })
try {
    & $keytool -genkeypair -keystore $keyPath -storetype PKCS12 -alias rodzina -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Rodzina' -storepass:env RODZINA_NEW_KEY_PASSWORD -keypass:env RODZINA_NEW_KEY_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Could not generate the signing key.' }
    $content = "storeFile=signing/rodzina-release.jks`nstorePassword=$env:RODZINA_NEW_KEY_PASSWORD`nkeyAlias=rodzina`nkeyPassword=$env:RODZINA_NEW_KEY_PASSWORD`n"
    [System.IO.File]::WriteAllText($propertiesPath, $content, [System.Text.UTF8Encoding]::new($false))
    Write-Output 'Signing key and keystore.properties created locally. Keep a secure backup of both files.'
    & $keytool -list -v -keystore $keyPath -alias rodzina -storepass:env RODZINA_NEW_KEY_PASSWORD | Select-String -Pattern 'SHA1:|SHA256:'
} finally { Remove-Item Env:\RODZINA_NEW_KEY_PASSWORD -ErrorAction SilentlyContinue }
