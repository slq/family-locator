param([Parameter(Mandatory = $true)][string]$SdkPath, [string]$Serial = 'emulator-5554')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$adbPath = Join-Path $SdkPath 'platform-tools/adb.exe'
if ((& $adbPath -s $Serial shell getprop ro.kernel.qemu).Trim() -ne '1') {
    throw 'This isolated integration test requires an Android emulator.'
}
foreach ($apk in @('app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
    & $adbPath -s $Serial install -r (Join-Path $projectRoot $apk)
    if ($LASTEXITCODE -ne 0) { throw "Installation failed: $apk" }
}
# RTDB advertises 127.0.0.1:9000 in its WebSocket handshake, including after reconnect.
# Route that device loopback endpoint to the local host emulator as well.
$existingReverse = @(& $adbPath -s $Serial reverse --list | ForEach-Object {
    $parts = $_.Trim() -split '\s+'
    if ($parts.Length -ge 3 -and $parts[1] -eq 'tcp:9000') { $parts[2] }
})
if ($existingReverse.Count -gt 0 -and $existingReverse -notcontains 'tcp:9000') { throw 'Device port 9000 already has a different reverse mapping.' }
$addedReverse = $existingReverse.Count -eq 0
if ($addedReverse) {
    & $adbPath -s $Serial reverse tcp:9000 tcp:9000
    if ($LASTEXITCODE -ne 0) { throw 'Could not forward the Firebase database emulator.' }
}
try {
    $result = & $adbPath -s $Serial shell am instrument -w -r -e firebaseEmulators true -e class pl.slqpl.rodzina.location.TrackingControlTest,pl.slqpl.rodzina.location.HistoryIntegrationTest pl.slqpl.rodzina.test/androidx.test.runner.AndroidJUnitRunner
    $result | Write-Output
    if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'OK \(3 tests\)') { throw 'Tracking/history integration tests failed.' }
} finally {
    if ($addedReverse) { & $adbPath -s $Serial reverse --remove tcp:9000 }
}
