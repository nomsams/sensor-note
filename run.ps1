\ = (git credential fill <<< 'protocol=https
host=github.com

') | Where-Object { \ -like 'password=*' } | Select-Object -First 1
\ = \.Substring(9)
\ = @{Authorization='Bearer \';Accept='application/vnd.github+json'}
\ = Invoke-RestMethod -Uri 'https://api.github.com/repos/nomsams/sensor-note/actions/runs?per_page=3' -Headers \
if (\.workflow_runs) { \ = \.workflow_runs[0]; "\: \ / \ \" } else { 'no_runs_found' }
