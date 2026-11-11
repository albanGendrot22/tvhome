# Bench alterné A / B : installe chaque APK à tour de rôle (même précompilation que deploy.ps1) puis mesure.
# Le banc est bruité (une même version fait de 17 à 83 images ratées) : on alterne pour répartir la dérive.
# Usage : ab.ps1 -A <apk A> -B <apk B> -Tours 2 -BExtras "--ez glass_band true"
param([string]$A, [string]$B, [int]$Tours = 2, [string]$AExtras = "", [string]$BExtras = "", [int]$Depart = 1)
# Les réglages propres à une version (capsule, scène…) se passent par -AExtras / -BExtras ; le script ne force que le son.
$adb = "C:\platform-tools\adb.exe"
$tv  = "192.168.1.24:5555"
function T { & $adb -s $tv @args }

function Installer($apk, $extras) {
  & $adb connect $tv | Out-Null
  T install -r $apk | Out-Null
  T shell cmd package compile -m speed -f fr.alban.tvhome | Out-Null
  T shell am start -n fr.alban.tvhome/.MainActivity --ez nav_sound false | Out-Null
  Start-Sleep 3
  if ($extras) { T shell "am start -n fr.alban.tvhome/.MainActivity $extras" | Out-Null; Start-Sleep 3 }
}

for ($i = $Depart; $i -le $Tours; $i++) {
  foreach ($v in @(@("A", $A, $AExtras), @("B", $B, $BExtras))) {
    Write-Host "=== Tour $i : version $($v[0]) ==="
    Installer $v[1] $v[2]
    T logcat -c | Out-Null
    powershell -ExecutionPolicy Bypass -File C:\TvHome\bench\outils\essai.ps1 -Nom "ab-$($v[0])-$i" -SansDeploy | Out-Null
    T logcat -d -s TvHome:I | Select-Object -Last 1 | Add-Content "C:\TvHome\bench\preuve-ab.txt"
  }
}
Write-Host "Termine."
