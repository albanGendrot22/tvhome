# Capture une trace Perfetto pendant la navigation du bench (1 tour de 20 touches + attente Top Shelf)
param([string]$Label = "trace")
$adb = "C:\platform-tools\adb.exe"
$tv  = "192.168.1.24:5555"
$dir = "C:\TvHome\bench\traces"
New-Item -ItemType Directory -Force $dir | Out-Null
function A { & $adb -s $tv @args }

$cfg = @'
buffers: { size_kb: 32768 fill_policy: RING_BUFFER }
buffers: { size_kb: 2048 fill_policy: RING_BUFFER }
data_sources: {
  config {
    name: "linux.ftrace"
    target_buffer: 0
    ftrace_config {
      ftrace_events: "sched/sched_switch"
      ftrace_events: "sched/sched_wakeup"
      ftrace_events: "sched/sched_waking"
      ftrace_events: "power/cpu_frequency"
      ftrace_events: "power/cpu_idle"
      ftrace_events: "task/task_newtask"
      ftrace_events: "task/task_rename"
      atrace_categories: "gfx"
      atrace_categories: "view"
      atrace_categories: "input"
      atrace_categories: "am"
      atrace_categories: "wm"
      atrace_categories: "dalvik"
      atrace_categories: "res"
      atrace_categories: "hal"
      atrace_apps: "fr.alban.tvhome"
      buffer_size_kb: 8192
      drain_period_ms: 250
    }
  }
}
data_sources: { config { name: "linux.process_stats" target_buffer: 1 process_stats_config { scan_all_processes_on_start: true } } }
duration_ms: 32000
'@
$tmp = Join-Path $env:TEMP "tvhome_perfetto.txt"
[IO.File]::WriteAllText($tmp, ($cfg -replace "`r", ""))
A push $tmp /data/local/tmp/tvhome_perfetto.txt | Out-Null

A shell input keyevent KEYCODE_WAKEUP | Out-Null
A shell am start -n fr.alban.tvhome/.MainActivity | Out-Null
Start-Sleep 4
A shell input keyevent 3 | Out-Null   # HOME : remet le focus en haut
Start-Sleep 3
A shell "rm -f /data/misc/perfetto-traces/tvhome.pftrace"
# Perfetto en arrière-plan (config par stdin : obligatoire sur Android 11)
A shell "cat /data/local/tmp/tvhome_perfetto.txt | perfetto --txt -c - -o /data/misc/perfetto-traces/tvhome.pftrace --background" | Out-Null
Start-Sleep 2
# Même parcours que bench.ps1 (un tour), puis 2 s sur la première rangée pour voir le Top Shelf charger
A shell 'for k in 22 22 22 22 20 21 21 21 21 20 22 22 22 22 19 19 21 21 21 21; do input keyevent $k; sleep 0.4; done; for k in 22 22 22; do input keyevent $k; sleep 1.5; done'
Start-Sleep 22
A pull /data/misc/perfetto-traces/tvhome.pftrace "$dir\$Label.pftrace" | Out-Null
Get-Item "$dir\$Label.pftrace" | Select-Object Name, Length
