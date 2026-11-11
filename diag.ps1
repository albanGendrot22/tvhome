# Diagnostic de performance TvHome -> C:\TvHome\bench\diag.txt
# Lancer avec : powershell -ExecutionPolicy Bypass -File C:\TvHome\diag.ps1
$adb = "C:\platform-tools\adb.exe"
$tv  = "192.168.1.24:5555"
$f   = "C:\TvHome\bench\diag.txt"
function A { & $adb -s $tv @args 2>&1 }
function S($t) { "`n######## $t" | Add-Content $f }

& $adb connect $tv | Out-Null
"=== diag $(Get-Date) ===" | Set-Content $f

Write-Host "Etat de compilation de l'appli..."
S "compilation"
A shell "dumpsys package fr.alban.tvhome | grep -iE 'compilation|status=|versionName'" | Add-Content $f

Write-Host "Processeur : qui consomme quoi..."
S "top"
A shell "top -b -n 1 -o PID,USER,%CPU,%MEM,RES,NAME | head -30" | Add-Content $f
S "cpuinfo"
A shell "dumpsys cpuinfo | head -30" | Add-Content $f

Write-Host "Demarrage a froid avec journal..."
A logcat -c | Out-Null
A shell am force-stop fr.alban.tvhome | Out-Null
Start-Sleep 2
S "am start"
A shell am start -W -n fr.alban.tvhome/.MainActivity | Add-Content $f
Start-Sleep 8
S "logcat (demarrage)"
A shell "logcat -d -v time | grep -iE 'tvhome|ActivityTaskManager: Displayed|Choreographer|Skipped|AndroidRuntime|FATAL|StrictMode|GC freed|art  '" | Select-Object -Last 120 | Add-Content $f

Write-Host "Memoire detaillee..."
S "meminfo"
A shell dumpsys meminfo fr.alban.tvhome | Add-Content $f

Write-Host "Reglages ecran de veille..."
S "settings"
A shell "settings get secure screensaver_components; settings get secure screensaver_enabled; settings get system screen_off_timeout" | Add-Content $f

S "plantages enregistres"
A shell "logcat -d -b crash | tail -40" | Add-Content $f

Write-Host "`nTermine -> $f"
