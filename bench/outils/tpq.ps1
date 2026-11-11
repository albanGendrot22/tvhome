# Exécute chaque requête d'un fichier .sql (séparées par une ligne ";;") sur une trace
param([string]$Trace, [string]$SqlFile)
$sp = Split-Path $MyInvocation.MyCommand.Path
$tp = "$sp\tp\tp.exe"
$parts = (Get-Content $SqlFile -Raw) -split "(?m)^;;\s*$"
$i = 0
foreach ($q in $parts) {
  if ($q.Trim() -eq "") { continue }
  $i++
  $f = "$sp\_q$i.sql"
  [IO.File]::WriteAllText($f, $q)
  "----- requete $i"
  $out = & $tp -q $f $Trace 2>&1
  $out | ForEach-Object { "$_" } | Where-Object { $_ -notmatch 'Loading trace|RemoteException|Trace loaded' }
}
