# Captures des menus de la phase 2 : réglages, centre de contrôle, menu d'appli → bench\captures\<Label>-*.png
param([Parameter(Mandatory)][string]$Label)
$adb = "C:\platform-tools\adb.exe"
$tv  = "192.168.1.24:5555"
$dir = "C:\TvHome\bench\captures"
New-Item -ItemType Directory -Force $dir | Out-Null
function A { & $adb -s $tv @args }
function Cap($name) {
  Start-Sleep 3
  A shell screencap -p /sdcard/tvhome_cap.png | Out-Null
  A pull /sdcard/tvhome_cap.png "$dir\$Label-$name.png" | Out-Null
}
function Key($k, $n = 1) { for ($i = 0; $i -lt $n; $i++) { A shell input keyevent $k | Out-Null; Start-Sleep -Milliseconds 400 } }

A shell input keyevent KEYCODE_WAKEUP | Out-Null
A shell am start -n fr.alban.tvhome/.MainActivity | Out-Null
Start-Sleep 2; Key 3; Start-Sleep 3

# Réglages
A shell am start -n fr.alban.tvhome/.MainActivity --ez settings true | Out-Null
Cap "reglages"
Key 23; Cap "reglages-apparence"        # OK sur « Apparence »
Key 4; Key 4; Start-Sleep 1

# Centre de contrôle (panneau de droite)
A shell am start -n fr.alban.tvhome/.MainActivity --ez lights true | Out-Null
Start-Sleep 4; Cap "centre-controle"
Key 20 8; Cap "centre-controle-bas"
Key 4; Start-Sleep 1

# Menu d'une appli (appui long sur OK)
Key 3; Start-Sleep 3
A shell input keyevent --longpress KEYCODE_DPAD_CENTER | Out-Null
Cap "menu-appli"
Key 4; Key 3
Get-ChildItem "$dir\$Label-*.png" | Select-Object Name, Length
