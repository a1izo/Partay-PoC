#Requires -Version 7
# Full scripted demo against T2. -Pause waits for Enter between steps. -Stress adds the parallel vote test.
param([switch]$Pause, [switch]$Stress)
. "$PSScriptRoot\lib.ps1"

function Wait-Step { if ($Pause) { Read-Host 'Enter for next step' | Out-Null } }

function Add-FromSearch([string]$Query, [string]$GuestId, [string]$Nickname) {
    $s = Invoke-Partay GET "/api/search?q=$([uri]::EscapeDataString($Query))&limit=3"
    $track = $s.Body.tracks | Select-Object -First 1
    if (-not $track) { throw "No search result for '$Query'" }
    $r = Invoke-Partay POST "/api/parties/$code/queue" @{ spotifyTrackId = $track.spotifyTrackId } $GuestId
    Write-Host ("{0} adds {1} - {2}: HTTP {3}" -f $Nickname, $track.title, $track.artist, $r.Status) -ForegroundColor Green
    return $r.Body.itemId
}

function Vote([string]$Who, [string]$GuestId, $ItemId, [int]$Value) {
    $r = Invoke-Partay PUT "/api/parties/$code/queue/$ItemId/vote" @{ value = $Value } $GuestId
    Write-Host ("{0} votes {1,2} on item {2}: HTTP {3}" -f $Who, $Value, $ItemId, $r.Status)
    return $r
}

# 1
Show-Step 'Health check: T2 -> gRPC -> T3 -> MySQL'
$h = Invoke-Partay GET '/api/health'
Show-Result $h
if ($h.Status -ne 200) { throw 'Health check failed. Start run-db.ps1, run-t3.ps1 and run-t2.ps1 first.' }
$mock = $h.Body.spotifyMode -eq 'mock'
Wait-Step

# 2
Show-Step 'Mikkel creates "Friday pre-drinks"'
$p = Invoke-Partay POST '/api/parties' @{ name = 'Friday pre-drinks'; hostNickname = 'Mikkel' }
Show-Result $p
$code = $p.Body.joinCode
$mikkel = $p.Body.host.guestId
Write-Host "Join code: $code" -ForegroundColor Magenta
Wait-Step

# 3
Show-Step 'Sofia and Jonas join, then "sofia" tries to join again'
$s = Invoke-Partay POST "/api/parties/$code/guests" @{ nickname = 'Sofia' }
Show-Result $s 'Sofia'
$sofia = $s.Body.guestId
$j = Invoke-Partay POST "/api/parties/$code/guests" @{ nickname = 'Jonas' }
Show-Result $j 'Jonas'
$jonas = $j.Body.guestId
Assert-Status (Invoke-Partay POST "/api/parties/$code/guests" @{ nickname = 'sofia' }) 409 'Duplicate nickname "sofia"'
Wait-Step

# 4
Show-Step ("Search Spotify ({0})" -f ($mock ? 'mock tracks' : 'live'))
foreach ($q in 'blinding lights', 'mr brightside', 'september earth wind') {
    $r = Invoke-Partay GET "/api/search?q=$([uri]::EscapeDataString($q))&limit=3"
    Write-Host ("""{0}""  HTTP {1}  {2} ms  cached={3}" -f $q, $r.Status, $r.Body.tookMs, $r.Body.cached) -ForegroundColor Green
    $r.Body.tracks | ForEach-Object { Write-Host ("   {0} - {1}  ({2})" -f $_.title, $_.artist, $_.spotifyTrackId) }
}
$r = Invoke-Partay GET "/api/search?q=blinding%20lights&limit=3"
Write-Host ("Repeat ""blinding lights""  HTTP {0}  {1} ms  cached={2}" -f $r.Status, $r.Body.tookMs, $r.Body.cached) -ForegroundColor Green
Wait-Step

# 5
Show-Step 'Each guest adds a song by track ID only (T2 fetches the details)'
$blinding = Add-FromSearch 'blinding lights' $sofia 'Sofia'
$brightside = Add-FromSearch 'mr brightside' $mikkel 'Mikkel'
$september = Add-FromSearch 'september earth wind' $jonas 'Jonas'
$q = Invoke-Partay GET "/api/parties/$code/queue"
Write-Host 'All score 0, ordered by time added:'
Show-Queue $q.Body
Wait-Step

# 6
Show-Step 'Mikkel and Sofia upvote September, Jonas downvotes Mr. Brightside'
Vote 'Mikkel' $mikkel $september 1 | Out-Null
Vote 'Sofia' $sofia $september 1 | Out-Null
$r = Vote 'Jonas' $jonas $brightside -1
Show-Queue $r.Body
$r = Vote 'Mikkel (again, same vote)' $mikkel $september 1
$sep = $r.Body.items | Where-Object itemId -eq $september
Write-Host ("Voting twice is idempotent: September score is still {0}" -f $sep.score)
Wait-Step

# 7
Show-Step 'Sofia switches her vote on September to -1'
$r = Vote 'Sofia' $sofia $september -1
Show-Queue $r.Body
Write-Host 'September dropped by 2 (+1 -> -1) and falls behind Blinding Lights (same score, added earlier).' -ForegroundColor Magenta
$r = Vote 'Jonas (clears)' $jonas $brightside 0
$bs = $r.Body.items | Where-Object itemId -eq $brightside
Write-Host ("Jonas cleared his vote with 0: Mr. Brightside score is now {0}" -f $bs.score)
Wait-Step

# 8
Show-Step 'Error cases'
Assert-Status (Invoke-Partay GET '/api/parties/ZZZZZZ') 404 'Wrong join code'
Assert-Status (Invoke-Partay PUT "/api/parties/$code/queue/$september/vote" @{ value = 5 } $sofia) 400 'Vote value 5'
Assert-Status (Invoke-Partay PUT "/api/parties/$code/queue/$september/vote" @{ value = 1 }) 400 'Missing X-Guest-Id'
$other = Invoke-Partay POST '/api/parties' @{ name = 'Other party'; hostNickname = 'Lars' }
Assert-Status (Invoke-Partay PUT "/api/parties/$code/queue/$september/vote" @{ value = 1 } $other.Body.host.guestId) 403 'Guest from a second party votes here'
$blindingTrack = ((Invoke-Partay GET "/api/parties/$code/queue").Body.items | Where-Object itemId -eq $blinding).spotifyTrackId
Assert-Status (Invoke-Partay POST "/api/parties/$code/queue" @{ spotifyTrackId = $blindingTrack } $sofia) 409 'Sofia adds Blinding Lights again'
Wait-Step

# 9
Show-Step 'Final queue'
Show-Queue (Invoke-Partay GET "/api/parties/$code/queue").Body
Write-Host "Live search in another terminal:"
Write-Host "  pwsh scripts\search.ps1 -JoinCode $code -GuestId $sofia" -ForegroundColor Magenta

# 10
if ($Stress) {
    Show-Step 'Stress: 30 parallel votes from 3 guests on one item'
    $guests = @($mikkel, $sofia, $jonas)
    $url = "$Base/api/parties/$code/queue/$blinding/vote"
    1..30 | ForEach-Object -ThrottleLimit 10 -Parallel {
        $g = ($using:guests)[$_ % 3]
        $v = Get-Random -InputObject @(-1, 0, 1)
        Invoke-RestMethod -Method Put -Uri $using:url -Headers @{ 'X-Guest-Id' = $g } `
            -ContentType 'application/json' -Body (@{ value = $v } | ConvertTo-Json) -SkipHttpErrorCheck | Out-Null
    }
    $mine = foreach ($g in $guests) {
        ((Invoke-Partay GET "/api/parties/$code/queue" -GuestId $g).Body.items | Where-Object itemId -eq $blinding).myVote
    }
    $item = (Invoke-Partay GET "/api/parties/$code/queue").Body.items | Where-Object itemId -eq $blinding
    $sum = ($mine | Measure-Object -Sum).Sum
    $rows = $item.upvotes + $item.downvotes
    Write-Host ("last votes per guest: {0}  sum={1}  score={2}  vote rows={3}" -f ($mine -join ', '), $sum, $item.score, $rows)
    if ($item.score -eq $sum -and $rows -le 3) { Write-Host 'PASS' -ForegroundColor Green }
    else { Write-Host 'FAIL' -ForegroundColor Red; $script:Unexpected++ }
}

Write-Host ''
if ($script:Unexpected -eq 0) { Write-Host 'Demo finished, no unexpected status codes.' -ForegroundColor Green }
else { Write-Host "Demo finished with $($script:Unexpected) unexpected result(s)." -ForegroundColor Red; exit 1 }
