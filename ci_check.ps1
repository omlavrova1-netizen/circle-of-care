# Читает аннотации ошибок последнего CI-запуска (публичный API GitHub)
param(
    [string]$Repo = "omlavrova1-netizen/circle-of-care",
    [string]$Sha = ""
)
if (-not $Sha) { $Sha = (git rev-parse HEAD).Trim() }
$url = "https://api.github.com/repos/$Repo/commits/$Sha/check-runs"
$cr = Invoke-RestMethod -Uri $url -Headers @{ Accept = "application/vnd.github+json" }
foreach ($run in $cr.check_runs) {
    Write-Output ("RUN: " + $run.name + " -> " + $run.conclusion)
    if ($run.output.annotations_count -gt 0) {
        $ann = Invoke-RestMethod -Uri $run.output.annotations_url -Headers @{ Accept = "application/vnd.github+json" }
        foreach ($a in $ann) {
            Write-Output ("  [" + $a.annotation_level + "] " + $a.path + ":" + $a.start_line + " " + $a.message)
        }
    } else {
        Write-Output "  (no annotations)"
    }
}
