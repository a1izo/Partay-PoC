#Requires -Version 7
# Builds proto, data-server and app-server. Use -Test to also run the tests.
param([switch]$Test)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    if ($Test) { .\mvnw.cmd clean package } else { .\mvnw.cmd clean package -DskipTests }
    if ($LASTEXITCODE -ne 0) { throw "Build failed (exit $LASTEXITCODE)" }
    Write-Host 'Build OK: data-server/target and app-server/target contain the jars.' -ForegroundColor Green
} finally {
    Pop-Location
}
