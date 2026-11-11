# Installation propre de TvHome, sans la greffe "Apply Changes" d'Android Studio
# Lancer avec : powershell -ExecutionPolicy Bypass -File C:\TvHome\deploy.ps1
# Garde tes reglages, tes photos et l'autorisation du filet de securite.
$ErrorActionPreference = "Stop"
$adb = "C:\platform-tools\adb.exe"
$tv  = "192.168.1.24:5555"
$apk = "C:\TvHome\app\build\outputs\apk\release\app-release.apk"

# Gradle refuse Java 25 : on prend le Java 21 qu'Android Studio a installe
$jdk = Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -ErrorAction SilentlyContinue |
  Where-Object { $_.Name -match '21' -and (Test-Path "$($_.FullName)\bin\java.exe") } | Select-Object -First 1
if ($jdk) { $env:JAVA_HOME = $jdk.FullName; Write-Host "Java : $($jdk.Name)" }

Write-Host "`n[1/4] Compilation de la version release..."
Push-Location C:\TvHome
& .\gradlew.bat assembleRelease --quiet
$ok = $LASTEXITCODE
Pop-Location
if ($ok -ne 0) { Write-Host "La compilation a echoue : envoie une capture a Claude."; exit 1 }

Write-Host "`n[2/4] Installation complete sur la TV (efface la greffe d'Android Studio)..."
& $adb connect $tv | Out-Null
& $adb -s $tv install -r $apk

Write-Host "`n[3/4] Precompilation de l'appli (1 a 2 minutes)..."
& $adb -s $tv shell cmd package compile -m speed -f fr.alban.tvhome
& $adb -s $tv shell "dumpsys package fr.alban.tvhome | grep status="

Write-Host "`n[4/4] Relance de l'accueil..."
& $adb -s $tv shell am start -n fr.alban.tvhome/.MainActivity | Out-Null
Write-Host "`nTermine."
