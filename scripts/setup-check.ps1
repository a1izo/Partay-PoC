# Checks the prerequisites and offers to install what is missing. Asks before every install.
# Runs in Windows PowerShell 5.1 on purpose (no #Requires -Version 7): it is what installs PowerShell 7.
#   powershell -ExecutionPolicy Bypass -File scripts\setup-check.ps1
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$RepoRoot = Split-Path -Parent $PSScriptRoot
$ToolsDir = if ($env:PARTAY_TOOLS) { $env:PARTAY_TOOLS } else { Join-Path $env:USERPROFILE 'tools' }
$PwshVersion = '7.6.9'
$MysqlVersion = '8.4.11'

function Write-Check([string]$Name, [bool]$Ok, [string]$Detail) {
    if ($Ok) { Write-Host ("OK       {0,-16} {1}" -f $Name, $Detail) -ForegroundColor Green }
    else { Write-Host ("MISSING  {0,-16} {1}" -f $Name, $Detail) -ForegroundColor Yellow }
}

function Confirm-Step([string]$Question) {
    $answer = Read-Host "$Question [y/N]"
    return $answer -match '^(y|yes|j|ja)$'
}

function Invoke-Winget([string]$Id) {
    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) { Write-Host 'winget not found.' -ForegroundColor Red; return }
    winget install --id $Id -e --accept-package-agreements
    Write-Host 'Open a new terminal afterwards so PATH and JAVA_HOME are updated.' -ForegroundColor Cyan
}

function Get-FirstLine([scriptblock]$Command) {
    try { return ((& $Command 2>&1) | Select-Object -First 1) -as [string] } catch { return $null }
}

function Test-Port([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try { return $client.ConnectAsync('127.0.0.1', $Port).Wait(500) -and $client.Connected } catch { return $false } finally { $client.Dispose() }
}

# Downloads and unzips into $ToolsDir. No admin needed.
function Install-Portable([string]$Url, [string]$ZipName, [string]$Target) {
    New-Item -ItemType Directory -Force -Path (Join-Path $ToolsDir 'dl') | Out-Null
    $zip = Join-Path $ToolsDir "dl\$ZipName"
    Write-Host "Downloading $Url ..."
    $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest -Uri $Url -OutFile $zip -UseBasicParsing
    Expand-Archive -Path $zip -DestinationPath $Target -Force
}

$missing = 0

# --- PowerShell 7 ---
$portablePwsh = Join-Path $ToolsDir 'pwsh\pwsh.exe'
$pwsh = Get-FirstLine { pwsh -v }
if (-not $pwsh -and (Test-Path $portablePwsh)) { $pwsh = (Get-FirstLine { & $portablePwsh -v }) + "  ($portablePwsh)" }
Write-Check 'PowerShell 7' ([bool]$pwsh) $pwsh
if (-not $pwsh) {
    $missing++
    if (Confirm-Step 'Install PowerShell 7 with winget (UAC prompt)?') { Invoke-Winget 'Microsoft.PowerShell' }
    elseif (Confirm-Step "Install portable PowerShell $PwshVersion into $ToolsDir\pwsh instead (no admin)?") {
        Install-Portable "https://github.com/PowerShell/PowerShell/releases/download/v$PwshVersion/PowerShell-$PwshVersion-win-x64.zip" 'pwsh.zip' (Join-Path $ToolsDir 'pwsh')
        Write-Host "Run the scripts with: $portablePwsh -File scripts\demo.ps1" -ForegroundColor Cyan
    }
}

# --- JDK 21+ ---
$java = Get-FirstLine { java -version }
$javaOk = $false
if ($java -match 'version "(\d+)') { $javaOk = [int]$Matches[1] -ge 21 }
Write-Check 'JDK 21+' $javaOk $java
if (-not $javaOk) {
    $missing++
    if (Confirm-Step 'Install Temurin JDK 21 with winget?') { Invoke-Winget 'EclipseAdoptium.Temurin.21.JDK' }
}

# --- Git ---
$git = Get-FirstLine { git --version }
Write-Check 'Git' ([bool]$git) $git
if (-not $git) {
    $missing++
    if (Confirm-Step 'Install Git with winget?') { Invoke-Winget 'Git.Git' }
}

# --- Docker or MySQL ---
$docker = Get-FirstLine { docker --version }
$dockerRunning = $false
if ($docker) { docker info *> $null; $dockerRunning = $LASTEXITCODE -eq 0 }
$dockerDetail = if ($docker -and -not $dockerRunning) { "$docker (not running: start Docker Desktop)" } else { $docker }
Write-Check 'Docker' $dockerRunning $dockerDetail

if (-not $dockerRunning) {
    $portableMysql = Get-ChildItem $ToolsDir -Directory -Filter 'mysql-8.4*' -ErrorAction SilentlyContinue | Select-Object -First 1
    $mysql = Get-FirstLine { mysql --version }
    if (-not $mysql -and $portableMysql) { $mysql = "portable $($portableMysql.Name) in $ToolsDir" }
    Write-Check 'MySQL fallback' ([bool]$mysql) $mysql

    if (-not $mysql) {
        $missing++
        Write-Host 'Docker Desktop needs WSL2, admin rights and often a reboot.' -ForegroundColor Cyan
        if (Confirm-Step "Install portable MySQL $MysqlVersion into $ToolsDir (no admin, about 280 MB)?") {
            Install-Portable "https://cdn.mysql.com/Downloads/MySQL-8.4/mysql-$MysqlVersion-winx64.zip" 'mysql.zip' $ToolsDir
            $home84 = Join-Path $ToolsDir "mysql-$MysqlVersion-winx64"
            $dataDir = Join-Path $ToolsDir 'mysql-data'
            & "$home84\bin\mysqld.exe" --initialize-insecure "--basedir=$home84" "--datadir=$dataDir" --console
            $p = Start-Process "$home84\bin\mysqld.exe" -ArgumentList "--basedir=`"$home84`"", "--datadir=`"$dataDir`"", '--port=3306' -WindowStyle Hidden -PassThru
            $deadline = (Get-Date).AddSeconds(60)
            while (-not (Test-Port 3306) -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 500 }
            Get-Content (Join-Path $RepoRoot 'scripts\create-db.sql') -Raw | & "$home84\bin\mysql.exe" -uroot -h127.0.0.1
            Write-Host 'Portable MySQL is initialized and running. run-db.ps1 starts it next time.' -ForegroundColor Green
        } elseif (Confirm-Step 'Install Docker Desktop with winget instead (admin, may need a reboot)?') {
            Invoke-Winget 'Docker.DockerDesktop'
        }
    }
}

# --- Ports ---
foreach ($port in 8080, 9090) {
    if (Test-Port $port) { Write-Host "NOTE     port $port is in use (fine if T2/T3 are already running)" -ForegroundColor Cyan }
}

Write-Host ''
if ($missing -eq 0) { Write-Host 'All prerequisites OK.' -ForegroundColor Green }
else { Write-Host "$missing prerequisite(s) were missing. Rerun this script after installing." -ForegroundColor Yellow }
