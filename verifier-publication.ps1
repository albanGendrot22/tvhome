# Cherche des secrets ou des données personnelles dans ce qui va être publié (hors dossiers ignorés par .gitignore).
# Lancer : powershell -ExecutionPolicy Bypass -File verifier-publication.ps1
$racine = $PSScriptRoot
$exclus = '\\(\.git|\.gradle|\.kotlin|\.idea|\.claude|build|backup|ref|ref-build|photos|photos-tv|traces|captures|apk|tp)\\'
$motifs = @(
  @{ N = "jeton (JWT)";           P = 'eyJ[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}' },
  @{ N = "clé privée";            P = 'BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY' },
  @{ N = "jeton GitHub";          P = 'gh[pousr]_[A-Za-z0-9]{20,}' },
  @{ N = "clé API générique";     P = '(?i)(api[_-]?key|secret|password|passwd)\s*[=:]\s*["''][^"'']{8,}' },
  @{ N = "adresse e-mail";        P = '[A-Za-z0-9._%+-]+@(gmail|outlook|hotmail|yahoo|icloud)\.[a-z]+' }
)
$fichiers = Get-ChildItem $racine -Recurse -File -Force |
  Where-Object { $_.FullName -notmatch $exclus -and $_.Name -notin @('local.properties', 'CLAUDE.md') -and $_.Extension -notin @('.png', '.jpg', '.jpeg', '.webp', '.apk', '.jar', '.zip', '.class') -and $_.Length -lt 2MB }
$trouve = 0
foreach ($f in $fichiers) {
  foreach ($m in $motifs) {
    $hits = Select-String -Path $f.FullName -Pattern $m.P -ErrorAction SilentlyContinue
    foreach ($h in $hits) { $trouve++; Write-Host ("[{0}] {1}:{2}" -f $m.N, $f.FullName.Substring($racine.Length + 1), $h.LineNumber) -ForegroundColor Yellow }
  }
}
if ($trouve -eq 0) { Write-Host "Rien de suspect dans $($fichiers.Count) fichiers." -ForegroundColor Green }
else { Write-Host "$trouve élément(s) à vérifier avant de publier." -ForegroundColor Red }
