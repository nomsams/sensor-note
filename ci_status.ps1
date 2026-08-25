\$cred = (git credential fill <<< 'protocol=https`nhost=github.com`n`n') | Where-Object { \$_ -like 'password=*' } | Select-Object -First 1
\$tok = \$cred.Substring(9)
\$h = @{Authorization='Bearer \$tok';Accept='application/vnd.github+json'}
\$runs = Invoke-RestMethod -Uri 'https://api.github.com/repos/nomsams/sensor-note/actions/runs?per_page=3' -Headers \$h
if (\$runs.workflow_runs) { \$last = \$runs.workflow_runs[0]; "\$($last.name): \$($last.status) / \$($last.conclusion) \$($last.html_url)" } else { 'no_runs_found' }
