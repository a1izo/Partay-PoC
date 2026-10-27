#Requires -Version 7
# Live search in the terminal. Results update while you type (debounced at 300 ms).
#   pwsh scripts\search.ps1 -JoinCode K7QX2M [-GuestId <uuid>] [-Simple]
# Without -GuestId the script asks for a nickname and joins the party first.
# -Simple uses Read-Host prompts, for terminals without key events (some IDE terminals).
param(
    [Parameter(Mandatory)][string]$JoinCode,
    [string]$GuestId,
    [switch]$Simple
)
. "$PSScriptRoot\lib.ps1"

$JoinCode = $JoinCode.Trim().ToUpperInvariant()
$party = Invoke-Partay GET "/api/parties/$JoinCode"
if ($party.Status -ne 200) { throw "Party $JoinCode not found (HTTP $($party.Status))" }

$nickname = '(guest)'
if (-not $GuestId) {
    while (-not $GuestId) {
        $name = Read-Host 'Nickname'
        $j = Invoke-Partay POST "/api/parties/$JoinCode/guests" @{ nickname = $name }
        if ($j.Status -eq 201) { $GuestId = $j.Body.guestId; $nickname = $j.Body.nickname }
        else { Write-Host "HTTP $($j.Status): $($j.Body.detail)" -ForegroundColor Yellow }
    }
}

function Find-Tracks([string]$Query) {
    Invoke-Partay GET "/api/search?q=$([uri]::EscapeDataString($Query))&limit=5"
}

function Add-Track($Track) {
    $r = Invoke-Partay POST "/api/parties/$JoinCode/queue" @{ spotifyTrackId = $Track.spotifyTrackId } $GuestId
    if ($r.Status -eq 201) { return "Added: $($r.Body.title) - $($r.Body.artist) (HTTP 201)" }
    return "HTTP $($r.Status): $($r.Body.detail)"
}

function Format-Duration([int]$Ms) {
    $s = [int][math]::Floor($Ms / 1000)
    '{0}:{1:D2}' -f [int][math]::Floor($s / 60), ($s % 60)
}

# --- Simple mode ---
if ($Simple) {
    while ($true) {
        $q = Read-Host "`nSearch (empty to quit)"
        if (-not $q) { break }
        $r = Find-Tracks $q
        if ($r.Status -ne 200) { Write-Host "HTTP $($r.Status): $($r.Body.detail)" -ForegroundColor Yellow; continue }
        Write-Host ("{0} ms  cached={1}" -f $r.Body.tookMs, $r.Body.cached)
        $i = 0
        foreach ($t in $r.Body.tracks) { $i++; Write-Host ("  {0}  {1} - {2}  {3}" -f $i, $t.title, $t.artist, (Format-Duration $t.durationMs)) }
        $n = Read-Host 'Number to add (empty to skip)'
        if ($n -match '^\d+$' -and [int]$n -ge 1 -and [int]$n -le $i) { Write-Host (Add-Track $r.Body.tracks[[int]$n - 1]) -ForegroundColor Green }
    }
    exit 0
}

# --- Live mode ---
$query = ''
$results = @()
$selected = 0
$meta = ''
$status = 'Start typing.'
$lastKeyAt = [datetime]::MinValue
$lastSearched = ''
$useCursor = $true

function Show-Screen {
    $width = [Math]::Max(40, [Console]::WindowWidth - 1)
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add("Partay live search   party $JoinCode ($($party.Body.name))   as $nickname")
    $lines.Add('')
    $lines.Add("Search: $query" + '_')
    $lines.Add('')
    for ($i = 0; $i -lt 5; $i++) {
        if ($i -lt $results.Count) {
            $t = $results[$i]
            $marker = if ($i -eq $selected) { '>' } else { ' ' }
            $lines.Add(("{0} {1}  {2} - {3}  {4}" -f $marker, ($i + 1), $t.title, $t.artist, (Format-Duration $t.durationMs)))
        } else { $lines.Add('') }
    }
    $lines.Add('')
    $lines.Add($meta)
    $lines.Add("Status: $status")
    $lines.Add('')
    $lines.Add('Type to search | Up/Down select | Enter add | Esc quit')
    $text = ($lines | ForEach-Object { $s = $_; if ($s.Length -gt $width) { $s = $s.Substring(0, $width) }; $s.PadRight($width) }) -join "`n"
    if ($script:useCursor) {
        try { [Console]::SetCursorPosition(0, 0) } catch { $script:useCursor = $false }
    }
    if (-not $script:useCursor) { Clear-Host }
    [Console]::Write($text)
}

Clear-Host
try { [Console]::CursorVisible = $false } catch { }
Show-Screen
try {
    while ($true) {
        if ([Console]::KeyAvailable) {
            $key = [Console]::ReadKey($true)
            switch ($key.Key) {
                'Escape' { return }
                'Backspace' { if ($query.Length -gt 0) { $query = $query.Substring(0, $query.Length - 1) }; $lastKeyAt = Get-Date }
                'UpArrow' { if ($selected -gt 0) { $selected-- } }
                'DownArrow' { if ($selected -lt $results.Count - 1) { $selected++ } }
                'Enter' {
                    if ($results.Count -gt 0) { $status = Add-Track $results[$selected] } else { $status = 'Nothing to add.' }
                }
                default {
                    if (-not [char]::IsControl($key.KeyChar)) { $query += $key.KeyChar; $lastKeyAt = Get-Date }
                }
            }
            Show-Screen
            continue
        }

        $trimmed = $query.Trim()
        $due = ((Get-Date) - $lastKeyAt).TotalMilliseconds -ge 300
        if ($due -and $trimmed.Length -ge 2 -and $trimmed -ne $lastSearched) {
            $lastSearched = $trimmed
            $r = Find-Tracks $trimmed
            if ($r.Status -eq 200) {
                $results = @($r.Body.tracks)
                $selected = 0
                $source = if ($r.Body.cached) { 'cache' } else { 'Spotify' }
                $meta = "{0} results  {1} ms  cached={2} ({3})" -f $results.Count, $r.Body.tookMs, $r.Body.cached, $source
            } else {
                $results = @()
                $meta = "HTTP $($r.Status): $($r.Body.detail)"
            }
            Show-Screen
        }
        Start-Sleep -Milliseconds 25
    }
} finally {
    try { [Console]::CursorVisible = $true } catch { }
    Write-Host ''
}
