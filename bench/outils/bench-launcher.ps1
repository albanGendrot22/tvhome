# Bench d'un autre accueil (plafond) : google ou plume, N fois, puis remet TvHome comme accueil.
# Reutilise les fonctions de bench.ps1 (A, Redemarrer, Verifier, Mesurer).
param([Parameter(Mandatory)][ValidateSet("google","plume","tvos")][string]$Cible, [int]$Fois = 3, [string]$Apk = "", [int]$Depart = 1)

$src = Get-Content "C:\TvHome\bench.ps1" -Encoding UTF8 -Raw
$src = $src.Substring(0, $src.IndexOf("try {"))
$src = $src -replace '(?m)^param\(.*\)\s*$', ''
Invoke-Expression $src   # definit $adb $tv $out $menage $google et les fonctions

$date = Get-Date -Format yyyy-MM-dd

function RestaurerTvHome {
  Write-Host "`nRestauration de TvHome..."
  A shell pm enable fr.alban.tvhome | Out-Null
  A shell pm disable-user --user 0 $google | Out-Null
  foreach ($p in $menage) { A shell pm disable-user --user 0 $p | Out-Null }
  A shell cmd package set-home-activity "fr.alban.tvhome/fr.alban.tvhome.MainActivity" | Out-Null
  if ($Cible -eq "plume") { A uninstall dev.plume | Out-Null }
  if ($Cible -eq "tvos") { A uninstall com.techgenius.tvoslauncher | Out-Null }
}

try {
  & $adb connect $tv | Out-Null
  if ($Cible -eq "google") {
    A shell pm enable $google | Out-Null
    foreach ($p in $menage) { A shell pm enable $p | Out-Null }
    A shell pm disable-user --user 0 fr.alban.tvhome | Out-Null
    $pkg = $google; $act = "com.google.android.tvlauncher.MainActivity"
  } elseif ($Cible -eq "tvos") {
    A install -r $Apk | ForEach-Object { Write-Host "    $_" }
    # saute l'assistant de premiere installation (sinon il bloque l'accueil pendant le bench)
    A shell am start -n com.techgenius.tvoslauncher/.SetupActivity --ez skip true | Out-Null
    Start-Sleep 3
    A shell pm disable-user --user 0 fr.alban.tvhome | Out-Null
    A shell pm disable-user --user 0 $google | Out-Null
    foreach ($p in $menage) { A shell pm disable-user --user 0 $p | Out-Null }
    $pkg = "com.techgenius.tvoslauncher"; $act = "com.techgenius.tvoslauncher.MainActivity"
  } else {
    A install -r $Apk | ForEach-Object { Write-Host "    $_" }
    A shell pm disable-user --user 0 fr.alban.tvhome | Out-Null
    A shell pm disable-user --user 0 $google | Out-Null
    foreach ($p in $menage) { A shell pm disable-user --user 0 $p | Out-Null }
    $pkg = "dev.plume"; $act = "dev.plume.MainActivity"
  }
  A shell cmd package set-home-activity "$pkg/$act" | ForEach-Object { Write-Host "    $_" }
  Verifier $pkg
  for ($n = $Depart; $n -le $Fois; $n++) {
    Write-Host "`n=== $Cible $n/$Fois ==="
    $boot = Redemarrer
    Verifier $pkg
    Mesurer "$Cible-$date-$n" $pkg $act $boot
  }
} catch {
  Write-Host "`nErreur : $_"
} finally {
  RestaurerTvHome
  Verifier "fr.alban.tvhome"
}
