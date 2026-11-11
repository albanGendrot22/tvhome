# Un essai : deploy.ps1, vérif APK neuf, réveil TV, bench, copie datée, résumé
param([Parameter(Mandatory)][string]$Nom, [switch]$SansDeploy)
$adb = "C:\platform-tools\adb.exe"
$tv  = "192.168.1.24:5555"
$apk = "C:\TvHome\app\build\outputs\apk\release\app-release.apk"

# La TV répond-elle ? 3 essais à 5 min d'intervalle
$ok = $false
for ($i = 1; $i -le 4; $i++) {
  & $adb connect $tv | Out-Null
  $r = "$(& $adb -s $tv shell echo ok 2>&1)".Trim()
  if ($r -eq "ok") { $ok = $true; break }
  if ($i -lt 4) { Write-Host "TV muette (tentative $i), nouvel essai dans 5 min"; Start-Sleep 300 }
}
if (-not $ok) { Write-Host "ECHEC_TV"; exit 2 }

if (-not $SansDeploy) {
  $t0 = Get-Date
  # Sortie dans un fichier, pas dans un tuyau : le démon Gradle lancé par deploy.ps1 garderait le tuyau ouvert
  # et le script attendrait indéfiniment
  # Nom unique à chaque essai : le démon Gradle ou l'antivirus peut garder le précédent ouvert, et cmd refuserait de le rouvrir
  $log = "C:\TvHome\bench\outils\deploy-$(Get-Date -Format HHmmss).log"
  $jdk = Get-ChildItem "$env:USERPROFILE\.jdks" -Directory | Where-Object { $_.Name -match '21' } | Select-Object -First 1
  if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
  & C:\TvHome\gradlew.bat --stop | Out-Null
  cmd /c "powershell -ExecutionPolicy Bypass -File C:\TvHome\deploy.ps1 > `"$log`" 2>&1"
  if (-not (Test-Path $log)) { Write-Host "ECHEC_DEPLOY : journal absent, deploy.ps1 n'a pas tourne"; exit 5 }
  Get-Content $log -Tail 4
  $w = (Get-Item $apk).LastWriteTime
  if ($w -lt $t0) { Write-Host "ECHEC_APK : APK pas neuf ($w)"; exit 3 }
  Write-Host "APK neuf : $w"
}

& $adb -s $tv shell input keyevent KEYCODE_WAKEUP | Out-Null
$tb = Get-Date
$blog = "C:\TvHome\bench\outils\bench-$(Get-Date -Format HHmmss).log"
cmd /c "powershell -ExecutionPolicy Bypass -File C:\TvHome\bench.ps1 -ApresSeulement > `"$blog`" 2>&1"
Get-Content $blog -Tail 3
if ((Get-Item C:\TvHome\bench\apres.txt).LastWriteTime -lt $tb) { Write-Host "ECHEC_BENCH : apres.txt pas mis a jour"; exit 4 }
$dst = "C:\TvHome\bench\apres-$(Get-Date -Format yyyy-MM-dd-HHmm)-$Nom.txt"
Copy-Item C:\TvHome\bench\apres.txt $dst
Write-Host "Resultat : $dst"
Get-Content $dst | Select-String -Pattern 'TotalTime|TOTAL PSS|Janky|50th percentile|90th percentile|99th percentile|Slow UI|deadline|Total frames' | Where-Object { $_.Line -notmatch 'gpu' } | ForEach-Object { $_.Line.Trim() }
