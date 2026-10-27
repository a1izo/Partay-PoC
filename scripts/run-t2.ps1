#Requires -Version 7
# T2 app-server (REST on 8080). Start T3 first.
. "$PSScriptRoot\lib.ps1"
Import-PartayEnv

$jar = Get-ChildItem (Join-Path $RepoRoot 'app-server\target') -Filter 'app-server-*.jar' -ErrorAction SilentlyContinue |
    Where-Object Name -notlike '*.original' | Select-Object -First 1
if (-not $jar) { throw 'app-server jar not found. Run scripts\build.ps1 first.' }

Write-Host "Demo page   http://localhost:8080/"
Write-Host "Swagger UI  http://localhost:8080/swagger-ui.html"
$javaArgs = @(Get-JavaArgs) + @('-jar', $jar.FullName)
& java @javaArgs
