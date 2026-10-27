#Requires -Version 7
# Starts MySQL 8.4 and waits until it accepts connections.
#   1. Docker: docker compose up -d, wait for the healthcheck.
#   2. No Docker: a portable MySQL in $env:USERPROFILE\tools (installed by setup-check.ps1), started in the background.
#   3. Neither: prints the local MySQL fallback steps.
. "$PSScriptRoot\lib.ps1"
Import-PartayEnv
$port = [int]($env:DB_PORT ?? 3306)

function Test-Port([int]$p) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try { $client.ConnectAsync('127.0.0.1', $p).Wait(500) -and $client.Connected } catch { $false } finally { $client.Dispose() }
}

$dockerOk = $false
if (Get-Command docker -ErrorAction SilentlyContinue) {
    docker info *> $null
    $dockerOk = $LASTEXITCODE -eq 0
}

if ($dockerOk) {
    Push-Location $RepoRoot
    try { docker compose up -d; if ($LASTEXITCODE -ne 0) { throw 'docker compose up failed' } } finally { Pop-Location }
    $deadline = (Get-Date).AddSeconds(90)
    do {
        $health = docker inspect -f '{{.State.Health.Status}}' partay-mysql 2>$null
        if ($health -eq 'healthy') { Write-Host "MySQL (Docker) is healthy on port $port" -ForegroundColor Green; exit 0 }
        Write-Host "waiting for MySQL... ($health)"
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw 'MySQL did not become healthy within 90 s. Check: docker logs partay-mysql'
}

$mysqlHome = Get-ChildItem $ToolsDir -Directory -Filter 'mysql-8.4*' -ErrorAction SilentlyContinue | Select-Object -First 1
$dataDir = Join-Path $ToolsDir 'mysql-data'
if ($mysqlHome -and (Test-Path $dataDir)) {
    if (Test-Port $port) { Write-Host "MySQL already running on port $port" -ForegroundColor Green; exit 0 }
    $mysqld = Join-Path $mysqlHome.FullName 'bin\mysqld.exe'
    $log = Join-Path $ToolsDir 'mysql.log'
    Start-Process -FilePath $mysqld -WindowStyle Hidden -RedirectStandardError $log -ArgumentList @(
        "--basedir=`"$($mysqlHome.FullName)`"", "--datadir=`"$dataDir`"", "--port=$port", '--console')
    $deadline = (Get-Date).AddSeconds(60)
    while ((Get-Date) -lt $deadline) {
        if (Test-Port $port) { Write-Host "MySQL (portable, $($mysqlHome.Name)) is up on port $port" -ForegroundColor Green; exit 0 }
        Start-Sleep -Milliseconds 500
    }
    throw "MySQL did not start within 60 s. See $log"
}

if (Test-Port $port) { Write-Host "Something is listening on port $port, assuming it is a local MySQL." -ForegroundColor Yellow; exit 0 }

Write-Host @"
Docker is not available and no portable MySQL was found.
Local MySQL fallback:
  1. Run scripts\setup-check.ps1 and accept the portable MySQL install (no admin needed), then rerun this script.
  or
  1. winget search mysql, install the Oracle MySQL Server 8 package.
  2. mysql -uroot -p < scripts\create-db.sql
  3. If it does not listen on 3306, set DB_PORT in .env.
"@ -ForegroundColor Yellow
exit 1
