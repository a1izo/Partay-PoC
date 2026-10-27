#Requires -Version 7
# T3 data-server (gRPC on DATA_SERVER_PORT, default 9090). Needs MySQL running (run-db.ps1).
. "$PSScriptRoot\lib.ps1"
Import-PartayEnv

$jar = Get-ChildItem (Join-Path $RepoRoot 'data-server\target') -Filter 'data-server-*.jar' -ErrorAction SilentlyContinue |
    Where-Object Name -notlike '*.original' | Select-Object -First 1
if (-not $jar) { throw 'data-server jar not found. Run scripts\build.ps1 first.' }

$javaArgs = @(Get-JavaArgs) + @('-jar', $jar.FullName)
& java @javaArgs
