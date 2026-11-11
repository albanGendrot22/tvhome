# Captures d'écran de l'accueil : Top Shelf (rangée du haut) et grille → bench\captures\<Label>-*.png
param([Parameter(Mandatory)][string]$Label)
$adb = "C:\platform-tools\adb.exe"
$tv  = "192.168.1.24:5555"
$dir = "C:\TvHome\bench\captures"
New-Item -ItemType Directory -Force $dir | Out-Null
function A { & $adb -s $tv @args }

A shell input keyevent KEYCODE_WAKEUP | Out-Null
A shell am start -n fr.alban.tvhome/.MainActivity | Out-Null
Start-Sleep 3
A shell input keyevent 3 | Out-Null   # HOME : retour en haut, focus sur la 1re appli
Start-Sleep 5                           # le Top Shelf charge son image après 0,7 s, puis 0,6 s de fondu
A shell screencap -p /sdcard/tvhome_cap.png
A pull /sdcard/tvhome_cap.png "$dir\$Label-topshelf.png" | Out-Null
A shell input keyevent 20 | Out-Null   # descente dans la grille
Start-Sleep 3
A shell screencap -p /sdcard/tvhome_cap.png
A pull /sdcard/tvhome_cap.png "$dir\$Label-grille.png" | Out-Null
A shell input keyevent 3 | Out-Null
Get-ChildItem "$dir\$Label-*.png" | Select-Object Name, Length
