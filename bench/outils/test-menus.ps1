# Essai à la télécommande (touches ADB) des menus de la phase 2, avec captures → bench\captures\<Label>-*.png
param([Parameter(Mandatory)][string]$Label)
$adb = "C:\platform-tools\adb.exe"
$tv  = "192.168.1.24:5555"
$dir = "C:\TvHome\bench\captures"
function A { & $adb -s $tv @args }
function Key($k, $n = 1) { for ($i = 0; $i -lt $n; $i++) { A shell input keyevent $k | Out-Null; Start-Sleep -Milliseconds 450 } }
function Cap($name) { Start-Sleep 2; A shell screencap -p /sdcard/tvhome_cap.png | Out-Null; A pull /sdcard/tvhome_cap.png "$dir\$Label-$name.png" | Out-Null }
function Long { A shell input keyevent --longpress KEYCODE_DPAD_CENTER | Out-Null; Start-Sleep 1 }

A shell input keyevent KEYCODE_WAKEUP | Out-Null
A shell am start -n fr.alban.tvhome/.MainActivity | Out-Null; Start-Sleep 2; Key 3; Start-Sleep 3

# 1. Ranger la 2e appli dans « Autres applis » : appui long, 3e ligne
Key 22; Long; Key 20 2; Cap "1-menu-ligne-autres"; Key 23; Start-Sleep 2
Cap "2-accueil-avec-dossier"
# 2. Ouvrir le dossier (dernière tuile de la grille)
A shell input keyevent 3 | Out-Null; Start-Sleep 2
Key 20 3; Key 22 5; Key 21 5; Cap "3-position-dossier"
