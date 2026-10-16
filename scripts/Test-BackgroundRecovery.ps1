param([Parameter(Mandatory = $true)][string]$SdkPath, [string]$Serial = 'emulator-5554')
$ErrorActionPreference = 'Stop'
$adbPath = Join-Path $SdkPath 'platform-tools/adb.exe'
$projectRoot = Split-Path -Parent $PSScriptRoot
$package = 'pl.slqpl.rodzina'
$component = "$package/.location.LocationSharingService"
if ((& $adbPath -s $Serial shell getprop ro.kernel.qemu).Trim() -ne '1') {
    throw 'This process-kill test requires an unsigned-in Android emulator.'
}
function Adb([string[]]$Arguments) {
    $output = & $adbPath -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($Arguments[0])" }
    return $output
}
function Probe([string]$Phase) {
    $output = Adb @('shell', 'am', 'instrument', '-w', '-r', '-e', 'backgroundRecoveryProbe', 'true',
        '-e', 'phase', $Phase, '-e', 'class', 'pl.slqpl.rodzina.location.BackgroundRecoveryProbeTest',
        "$package.test/androidx.test.runner.AndroidJUnitRunner")
    if (($output -join "`n") -notmatch 'OK \(1 test\)') { throw "Probe $Phase failed: $output" }
}
function Session {
    $raw = Adb @('shell', 'run-as', $package, 'cat', 'shared_prefs/tracking_session.xml')
    $xml = [xml]($raw -join "`n")
    $entry = $xml.map.string | Where-Object { $_.name -eq 'session' }
    if ($null -eq $entry) { return $null }
    return $entry.InnerText | ConvertFrom-Json
}
function Wait-Until([scriptblock]$Check, [string]$Description, [int]$Seconds = 90) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        if (& $Check) { return }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "Timed out: $Description"
}
function Start-Mode([string]$Mode) {
    Adb @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') | Out-Null
    Adb @('shell', 'wm', 'dismiss-keyguard') | Out-Null
    Adb @('shell', 'am', 'start', '-W', '-n', "$package/.MainActivity") | Out-Null
    # A new explicit session resets Android's exponential crash-restart backoff between scenarios.
    Adb @('shell', 'run-as', $package, '/system/bin/am', 'startservice', '--user', '0', '-n', $component,
        '-a', "$package.STOP") | Out-Null
    Wait-Until { ((Adb @('shell', 'dumpsys', 'activity', 'services', $package)) -join "`n") -notmatch 'ServiceRecord' } 'stop previous scenario'
    Adb @('shell', 'run-as', $package, '/system/bin/am', 'start-foreground-service', '--user', '0', '-n', $component,
        '-a', "$package.$Mode") | Out-Null
    $expected = if ($Mode -eq 'PAUSE') { 'STOPPED' } else { $Mode }
    Wait-Until { $state = Session; $state -and $state.mode -eq $expected } "start $Mode"
    Adb @('shell', 'input', 'keyevent', 'KEYCODE_HOME') | Out-Null
}
function Kill-And-Check([string]$Expected, [switch]$ExpireLive, [switch]$MismatchedAccount) {
    $before = Session
    if (-not $before.local) { throw 'Only a local test session may be killed by this script.' }
    $processId = (Adb @('shell', 'pidof', $package)).Trim()
    if ($processId -notmatch '^\d+$') { throw 'Expected one running debug process.' }
    if ($ExpireLive -or $MismatchedAccount) {
        # Freeze the test process before changing its persisted fixture deadline, then kill it.
        # savedElapsed < liveUntil remains valid, but the deadline has expired by recovery.
        if ($ExpireLive) { $before.liveUntil = [long]$before.elapsed + 1 }
        if ($MismatchedAccount) {
            $probe = [xml]((Adb @('shell', 'run-as', $package, 'cat', 'shared_prefs/recovery_probe.xml')) -join "`n")
            $before.uid = ($probe.map.string | Where-Object { $_.name -eq 'uid' }).InnerText
            if ($before.uid -notlike 'recovery-probe-*') { throw 'Missing isolated probe owner.' }
            $before.local = $false
        }
        $payload = [System.Security.SecurityElement]::Escape(($before | ConvertTo-Json -Compress))
        $xml = '<?xml version="1.0" encoding="utf-8" standalone="yes" ?><map><string name="session">' + $payload + '</string></map>'
        $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($xml))
        Adb @('shell', "run-as $package sh -c 'kill -STOP $processId; echo $encoded | base64 -d > shared_prefs/tracking_session.xml; kill -9 $processId'") | Out-Null
    } else {
        Adb @('shell', 'run-as', $package, 'kill', '-9', $processId) | Out-Null
    }
    Wait-Until {
        $newProcess = "$( & $adbPath -s $Serial shell pidof $package )".Trim()
        $services = Adb @('shell', 'dumpsys', 'activity', 'services', $package)
        $state = Session
        if ($Expected -eq 'REJECT') {
            $newProcess -match '^\d+$' -and $newProcess -ne $processId -and $null -eq $state -and
                ($services -join "`n") -notmatch 'ServiceRecord'
        } else {
            $newProcess -match '^\d+$' -and $newProcess -ne $processId -and
                ($services -join "`n") -match 'isForeground=true' -and $state.mode -eq $Expected -and
                [long]$state.elapsed -gt [long]$before.elapsed
        }
    } "sticky recovery as $Expected"
    if ($Expected -eq 'REJECT') {
        Write-Output 'PASS: mismatched account disabled recovery without discarding the offline queue'
        return
    }
    $after = Session
    if ($Expected -eq 'LIVE' -and [long]$after.liveUntil -ne [long]$before.liveUntil) {
        throw 'Process recovery extended the live deadline.'
    }
    $notifications = Adb @('shell', 'dumpsys', 'notification')
    if (($notifications -join "`n") -notmatch "NotificationRecord.*pkg=$package ") { throw 'Missing recovery notification.' }
    Write-Output "PASS: real process death restored $Expected with a foreground notification"
}
foreach ($apk in @('app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
    Adb @('install', '-r', (Join-Path $projectRoot $apk)) | Out-Null
}
Probe 'seed'
try {
    foreach ($permission in @('ACCESS_COARSE_LOCATION', 'ACCESS_FINE_LOCATION', 'POST_NOTIFICATIONS')) {
        Adb @('shell', 'pm', 'grant', $package, "android.permission.$permission") | Out-Null
    }
    Start-Mode 'PERIODIC'
    Kill-And-Check 'PERIODIC'
    Start-Mode 'LIVE'
    Kill-And-Check 'LIVE'
    Kill-And-Check 'LIVE'
    Kill-And-Check 'PERIODIC' -ExpireLive
    Start-Mode 'PAUSE'
    Kill-And-Check 'STOPPED'
    Start-Mode 'PERIODIC'
    Kill-And-Check 'REJECT' -MismatchedAccount
    Start-Mode 'PERIODIC'
    Adb @('shell', 'am', 'start', '-W', '-n', "$package/.MainActivity") | Out-Null
    Adb @('shell', 'run-as', $package, '/system/bin/am', 'startservice', '--user', '0', '-n', $component,
        '-a', "$package.STOP") | Out-Null
    Wait-Until { $null -eq (Session) -and ((Adb @('shell', 'dumpsys', 'activity', 'services', $package)) -join "`n") -notmatch 'isForeground=true' } 'explicit stop'
    Probe 'check'
    Write-Output 'PASS: offline Room queue survived all deaths; explicit stop disabled recovery'
} finally { Probe 'cleanup' }
