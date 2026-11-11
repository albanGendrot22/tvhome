# Resume des fichiers de bench : images ratees, 99e, saccades, demarrage, memoire
param([Parameter(Mandatory)][string[]]$Fichiers)
foreach ($f in $Fichiers) {
  $t = Get-Content $f -Encoding UTF8
  function V($re) { ($t | Select-String $re | Select-Object -First 1 | ForEach-Object { $_.Line.Trim() }) }
  $rat  = [regex]::Match((V 'Frame deadline missed'), '\d+').Value
  $p99  = [regex]::Match((V '99th percentile'), '\d+(?=ms)').Value
  $p50  = [regex]::Match((V '50th percentile'), '\d+(?=ms)').Value
  $jank = [regex]::Match((V 'Janky frames:'), '\(([\d.]+)%\)').Groups[1].Value
  $tot  = [regex]::Match((V 'Total frames'), '\d+').Value
  $slow = [regex]::Match((V 'Slow UI thread'), '\d+').Value
  $cold = ($t | Select-String 'TotalTime' | Select-Object -First 5 | ForEach-Object { [int]([regex]::Match($_.Line,'\d+').Value) } | Measure-Object -Average).Average
  $pss  = [regex]::Match((V 'TOTAL'), '\d+').Value
  $pct = if ([int]$tot -gt 0) { [math]::Round(100 * [int]$rat / [int]$tot, 1) } else { 0 }
  "{0,-48} ratees={1,4} ({9}% des img) p99={2,3}ms med={3,3}ms jank={4}% img={5} slowUI={6} froid={7:N0}ms" -f (Split-Path $f -Leaf), $rat, $p99, $p50, $jank, $tot, $slow, $cold, $pss, $pct
}
