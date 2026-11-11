# Bench final : 3 tours, chacun mesure dans l'ordre A (référence), F (version finale), Google, tvOS Launcher.
# Entrelacé pour que la dérive de la TV touche chaque version de la même façon. Plume : APK indisponible (voir rapport).
param([int]$Tours = 3)
$o = "C:\TvHome\bench\outils"
$A = "C:\TvHome\bench\apk\A-fin-phase1.apk"
$F = "C:\TvHome\bench\apk\F-final.apk"
$T = "C:\TvHome\ref-build\tvos-launcher\build\tvos-home.apk"
for ($n = 1; $n -le $Tours; $n++) {
  Write-Host "=== Tour $n : TvHome référence puis finale ==="
  powershell -ExecutionPolicy Bypass -File "$o\ab.ps1" -A $A -B $F -Tours $n -Depart $n -BExtras "--ez glass_band true" | Out-Host
  Write-Host "=== Tour $n : Google ==="
  powershell -ExecutionPolicy Bypass -File "$o\bench-launcher.ps1" -Cible google -Fois $n -Depart $n | Out-Host
  Write-Host "=== Tour $n : tvOS Launcher ==="
  powershell -ExecutionPolicy Bypass -File "$o\bench-launcher.ps1" -Cible tvos -Fois $n -Depart $n -Apk $T | Out-Host
}
# Fin : version finale installée, réglages par défaut
& "C:\platform-tools\adb.exe" connect 192.168.1.24:5555 | Out-Null
& "C:\platform-tools\adb.exe" -s 192.168.1.24:5555 install -r $F | Out-Null
& "C:\platform-tools\adb.exe" -s 192.168.1.24:5555 shell cmd package compile -m speed -f fr.alban.tvhome | Out-Null
& "C:\platform-tools\adb.exe" -s 192.168.1.24:5555 shell am start -n fr.alban.tvhome/.MainActivity --ez glass_band true --ez nav_sound false --ez scene false | Out-Null
Write-Host "Termine."
