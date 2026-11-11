# Banc d'essai launcher : AVANT (launcher Google + apps d'origine) vs APRES (TvHome + menage)
# Lancer avec : powershell -ExecutionPolicy Bypass -File C:\TvHome\bench.ps1
# Duree : environ 10 minutes. Ne pas toucher la telecommande pendant le test.

param([switch]$ApresSeulement)

$adb = "C:\platform-tools\adb.exe"
$tv  = "192.168.1.24:5555"
$out = "C:\TvHome\bench"
New-Item -ItemType Directory -Force $out | Out-Null

$menage = @(
 "com.vestel.vestelreporter","fusion.android.tv.demo","com.cltv.fast",
 "com.mediatek.tv.freeviewplay","com.google.android.play.games",
 "com.google.android.feedback","com.android.printspooler",
 "com.dsi.ant.service.socket","com.google.android.katniss",
 "com.google.android.tvrecommendations",
 "com.google.android.leanbacklauncher.partnercustomizer"
)
$google = "com.google.android.tvlauncher"

function A { & $adb -s $tv @args 2>&1 }

function Redemarrer {
  Write-Host "  Enregistrement des reglages (20 s)..."
  Start-Sleep 20
  Write-Host "  Redemarrage de la TV..."
  $sw = [Diagnostics.Stopwatch]::StartNew()
  A reboot | Out-Null
  Start-Sleep 15
  & $adb disconnect $tv 2>&1 | Out-Null
  for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep 5
    $r = & $adb connect $tv 2>&1
    if ("$r" -match "connected") {
      $b = "$(A shell getprop sys.boot_completed)".Trim()
      if ($b -eq "1") { $sw.Stop(); return [math]::Round($sw.Elapsed.TotalSeconds) }
    }
  }
  throw "La TV ne repond plus apres le redemarrage."
}

function Verifier($attendu) {
  $h = "$(A shell "cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME | grep -m1 packageName")".Trim()
  Write-Host "  Ecran d'accueil actif : $h"
  if ($h -notmatch [regex]::Escape($attendu)) { throw "Mauvais launcher actif (attendu : $attendu)" }
}

function Mesurer($label, $pkg, $activity, $boot) {
  $f = "$out\$label.txt"
  "=== $label ===" | Set-Content $f
  "## boot_seconds" | Add-Content $f
  "$boot" | Add-Content $f

  Write-Host "  Stabilisation (90 s)..."
  Start-Sleep 90

  A shell am start -n "$pkg/$activity" | Out-Null
  Start-Sleep 10
  "## foreground" | Add-Content $f
  A shell "dumpsys activity activities | grep -m1 mResumedActivity" | Add-Content $f
  Write-Host "  Memoire..."
  "## meminfo" | Add-Content $f
  A shell "cat /proc/meminfo | head -3" | Add-Content $f
  "## top_rss" | Add-Content $f
  A shell "dumpsys meminfo | head -45" | Add-Content $f
  "## launcher_pss" | Add-Content $f
  A shell "dumpsys meminfo $pkg | grep -E 'TOTAL'" | Add-Content $f

  Write-Host "  Demarrage a froid (x5)..."
  "## cold_start" | Add-Content $f
  for ($i = 1; $i -le 5; $i++) {
    A shell am start -a android.settings.SETTINGS | Out-Null
    Start-Sleep 3
    A shell am force-stop $pkg | Out-Null
    Start-Sleep 2
    A shell am start -W -n "$pkg/$activity" | Select-String "TotalTime" | ForEach-Object { $_.Line } | Add-Content $f
    Start-Sleep 5
  }

  Write-Host "  Retour accueil (x5)..."
  "## warm_home" | Add-Content $f
  for ($i = 1; $i -le 5; $i++) {
    A shell am start -a android.settings.SETTINGS | Out-Null
    Start-Sleep 3
    A shell am start -W -n "$pkg/$activity" | Select-String "TotalTime" | ForEach-Object { $_.Line } | Add-Content $f
    Start-Sleep 3
  }

  Write-Host "  Fluidite de navigation (60 appuis)..."
  A shell am start -n "$pkg/$activity" | Out-Null
  Start-Sleep 4
  A shell dumpsys gfxinfo $pkg reset | Out-Null
  A shell 'for r in 1 2 3; do for k in 22 22 22 22 20 21 21 21 21 20 22 22 22 22 19 19 21 21 21 21; do input keyevent $k; sleep 0.4; done; done'
  "## gfxinfo" | Add-Content $f
  A shell "dumpsys gfxinfo $pkg | grep -E 'Total frames|Janky|percentile|Number'" | Add-Content $f
  Write-Host "  OK -> $f"
}

try {
  & $adb connect $tv | Out-Null

  if (-not $ApresSeulement) {
  Write-Host "`n[1/2] AVANT : launcher Google et apps d'origine"
  A shell pm enable $google | ForEach-Object { Write-Host "    $_" }
  foreach ($p in $menage) { A shell pm enable $p | ForEach-Object { Write-Host "    $_" } }
  A shell pm disable-user --user 0 fr.alban.tvhome | ForEach-Object { Write-Host "    $_" }
  A shell cmd package set-home-activity "$google/com.google.android.tvlauncher.MainActivity" | ForEach-Object { Write-Host "    $_" }
  Verifier $google
  $boot = Redemarrer
  Verifier $google
  Mesurer "avant" $google "com.google.android.tvlauncher.MainActivity" $boot
  }

  Write-Host "`n[2/2] APRES : TvHome et menage"
  A shell pm enable fr.alban.tvhome | ForEach-Object { Write-Host "    $_" }
  A shell pm disable-user --user 0 $google | ForEach-Object { Write-Host "    $_" }
  foreach ($p in $menage) { A shell pm disable-user --user 0 $p | ForEach-Object { Write-Host "    $_" } }
  A shell cmd package set-home-activity "fr.alban.tvhome/fr.alban.tvhome.MainActivity" | ForEach-Object { Write-Host "    $_" }
  Verifier "fr.alban.tvhome"
  $boot = Redemarrer
  Verifier "fr.alban.tvhome"
  Mesurer "apres" "fr.alban.tvhome" "fr.alban.tvhome.MainActivity" $boot

  Write-Host "`nTermine. Resultats dans $out"
} catch {
  Write-Host "`nErreur : $_"
  Write-Host "Pour remettre la config APRES a la main :"
  Write-Host "  .\adb shell pm enable fr.alban.tvhome"
  Write-Host "  .\adb shell pm disable-user --user 0 $google"
}
