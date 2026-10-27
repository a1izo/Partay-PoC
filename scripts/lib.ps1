#Requires -Version 7
# Shared helpers. Dot-source it: . "$PSScriptRoot\lib.ps1"
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$RepoRoot = Split-Path -Parent $PSScriptRoot
$Base = $env:PARTAY_URL ?? 'http://localhost:8080'
$ToolsDir = $env:PARTAY_TOOLS ?? (Join-Path $env:USERPROFILE 'tools')

# Loads .env from the repo root into $env: (skips blank lines and # comments).
function Import-PartayEnv {
    $file = Join-Path $RepoRoot '.env'
    if (-not (Test-Path $file)) {
        Write-Host "No .env found, using defaults (copy .env.example to .env)." -ForegroundColor Yellow
        return
    }
    foreach ($line in Get-Content $file -Encoding utf8) {
        $t = $line.Trim()
        if ($t -eq '' -or $t.StartsWith('#')) { continue }
        $i = $t.IndexOf('=')
        if ($i -lt 1) { continue }
        $name = $t.Substring(0, $i).Trim()
        $value = $t.Substring($i + 1).Trim().Trim('"')
        Set-Item -Path "env:$name" -Value $value
    }
}

# JVM flags. On Java 24+ protobuf's use of sun.misc.Unsafe prints warnings; allow it quietly.
function Get-JavaArgs {
    $v = (& java -version 2>&1 | Select-Object -First 1) -as [string]
    if ($v -match 'version "(\d+)') {
        if ([int]$Matches[1] -ge 24) { return @('--sun-misc-unsafe-memory-access=allow') }
    }
    return @()
}

# Calls T2. Never throws on 4xx/5xx, so the demo can show errors on purpose.
function Invoke-Partay {
    param(
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        $Body,
        [string]$GuestId
    )
    $headers = @{}
    if ($GuestId) { $headers['X-Guest-Id'] = $GuestId }
    $params = @{
        Method             = $Method
        Uri                = "$Base$Path"
        Headers            = $headers
        SkipHttpErrorCheck = $true
        StatusCodeVariable = 'status'
    }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 10 -Compress
        $params['Body'] = [System.Text.Encoding]::UTF8.GetBytes($json)
        $params['ContentType'] = 'application/json; charset=utf-8'
    }
    try {
        $result = Invoke-RestMethod @params
    } catch {
        # Connection refused etc.: T2 itself is down
        return [pscustomobject]@{ Status = 0; Body = [pscustomobject]@{ detail = $_.Exception.Message } }
    }
    [pscustomobject]@{ Status = [int]$status; Body = $result }
}

$script:StepNo = 0
function Show-Step([string]$Title) {
    $script:StepNo++
    Write-Host ''
    Write-Host ("== STEP {0}  {1}" -f $script:StepNo, $Title) -ForegroundColor Cyan
}

function Show-Result($Response, [string]$Label) {
    $color = if ($Response.Status -ge 200 -and $Response.Status -lt 300) { 'Green' } else { 'Yellow' }
    $prefix = if ($Label) { "$Label  " } else { '' }
    Write-Host ("{0}HTTP {1}" -f $prefix, $Response.Status) -ForegroundColor $color
    if ($null -ne $Response.Body -and "$($Response.Body)" -ne '') {
        Write-Host ($Response.Body | ConvertTo-Json -Depth 10)
    }
}

function Show-Queue($Queue) {
    if (-not $Queue.items -or $Queue.items.Count -eq 0) {
        Write-Host '(queue is empty)'
        return
    }
    $Queue.items | ForEach-Object {
        [pscustomobject]@{
            Pos     = $_.position
            Title   = $_.title
            Artist  = $_.artist
            AddedBy = $_.addedBy
            Score   = $_.score
            Up      = $_.upvotes
            Down    = $_.downvotes
        }
    } | Format-Table -AutoSize | Out-String | Write-Host
}

# Asserts a status code and prints the outcome on one line.
function Assert-Status($Response, [int]$Expected, [string]$What) {
    $ok = $Response.Status -eq $Expected
    $color = if ($ok) { 'Green' } else { 'Red' }
    $detail = $Response.Body.detail ?? ''
    Write-Host ("{0,-48} expected {1}, got {2}  {3}" -f $What, $Expected, $Response.Status, $detail) -ForegroundColor $color
    if (-not $ok) { $script:Unexpected++ }
}
$script:Unexpected = 0
