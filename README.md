# Partay PoC

A proof of concept for Partay, a shared party queue. Guests join with a code, search Spotify, add songs and vote,
and the queue reorders by score. It follows the supervisors' three-tier sketch:

| Tier | What | Tech |
|---|---|---|
| T1 | Demo page at http://localhost:8080/, Swagger UI, PowerShell scripts | plain HTML/JS, pwsh 7 |
| T2 | `app-server`: REST API, **all business rules**, Spotify client | Spring Boot 3.5, port 8080 |
| T3 | `data-server`: stores and returns rows, no rules | Spring Boot 3.5 + grpc-java, port 9090 |
| DB | MySQL 8.4 | Docker, or a portable install |

Network technologies: REST (T1 to T2, T2 to Spotify) and gRPC (T2 to T3, contract in
[`proto/src/main/proto/partay_data.proto`](proto/src/main/proto/partay_data.proto)). The full plan and the decisions
behind it are in [PLAN.md](PLAN.md).

## Prerequisites

```
powershell -ExecutionPolicy Bypass -File scripts\setup-check.ps1
```

It checks PowerShell 7, JDK 21+, Git, and Docker or MySQL, and offers to install what is missing (it asks first).
Without admin rights it can install **portable** PowerShell 7 and MySQL 8.4 into `%USERPROFILE%\tools`. In that case,
run the scripts with `%USERPROFILE%\tools\pwsh\pwsh.exe` wherever this README says `pwsh`. Maven is not needed
because the project uses the Maven wrapper.

## Spotify setup (once)

1. Log in at https://developer.spotify.com/dashboard. The app owner needs **Spotify Premium** (required for
   Development Mode apps since February 2026).
2. Create an app: name "Partay PoC", redirect URI `http://127.0.0.1:8080/callback` (required by the form, not used),
   tick "Web API".
3. `copy .env.example .env` and paste the Client ID and Client Secret into `.env`. `.env` is git-ignored. Never commit
   or screenshot the secret, and rotate it in the dashboard if it leaks.

Without credentials, or with `SPOTIFY_MODE=mock`, T2 uses 20 built-in tracks, so the demo also works offline.

## Run it (under 5 minutes)

```
pwsh scripts\build.ps1          # add -Test to run the tests too
pwsh scripts\run-db.ps1         # Docker if available, else the portable MySQL
pwsh scripts\run-t3.ps1         # terminal 1: T3 data-server (shows gRPC calls and SQL)
pwsh scripts\run-t2.ps1         # terminal 2: T2 app-server (shows REST, gRPC and Spotify hops)
```

Then open http://localhost:8080/ and click **Next step** or **Run all**. Terminal 3 has the backups:

```
pwsh scripts\demo.ps1 -Pause            # the same story, step by step in the terminal
pwsh scripts\demo.ps1 -Stress           # adds 30 parallel votes, prints PASS/FAIL
pwsh scripts\search.ps1 -JoinCode K7QX2M -GuestId <uuid>   # live search while typing
pwsh scripts\search.ps1 -JoinCode K7QX2M -Simple           # prompt-based, for IDE terminals
```

| URL | |
|---|---|
| http://localhost:8080/ | Demo page (T1) |
| http://localhost:8080/swagger-ui.html | Swagger UI, every endpoint can be run from it |
| `localhost:9090` | T3 gRPC with server reflection: Postman (gRPC request) or `grpcurl -plaintext localhost:9090 list` |

## Endpoints (T2)

Guests identify themselves with the `X-Guest-Id` header (the `guestId` from create or join). Errors are
`ProblemDetail` JSON.

| Method | Path | Notes |
|---|---|---|
| GET | `/api/health` | T2 → gRPC Ping → T3 → `SELECT 1`. 503 if a tier is down |
| POST | `/api/parties` | `{name, hostNickname}` → 201 with `joinCode` and the host's `guestId` |
| GET | `/api/parties/{joinCode}` | 404 for an unknown code. Codes are case-insensitive |
| POST | `/api/parties/{joinCode}/guests` | `{nickname}` → 201 with `guestId`. 409 if the nickname is taken (case-insensitive) |
| GET | `/api/search?q=&limit=` | q 2–100 chars, limit 1–10 (default 5). Cached 60 s, response says `cached` and `tookMs` |
| POST | `/api/parties/{joinCode}/queue` | `X-Guest-Id`, `{spotifyTrackId}`. T2 fetches title/artist/duration. 409 for a duplicate |
| GET | `/api/parties/{joinCode}/queue` | Ranked by score, then time added. Optional `X-Guest-Id` fills `myVote` |
| PUT | `/api/parties/{joinCode}/queue/{itemId}/vote` | `X-Guest-Id`, `{value: 1, -1 or 0}`. Replaces the earlier vote; 0 clears. Returns the queue |
| GET | `/api/debug/traces?after=` | Hops of recent requests (read by the demo page) |
| DELETE | `/api/debug/traces` | Clears the trace buffer |

Status codes: 400 invalid input or missing `X-Guest-Id`, 403 guest not in this party, 404 not found, 409 conflict,
502/503 T3 or Spotify problems.

## Tests

`.\mvnw test` runs the T2 unit tests (`QueueRankerTest`, `JoinCodeGeneratorTest`, `QueueServiceTest`,
`TraceRecorderTest`) and the T3 integration test `PartayDataGrpcServiceIT` (Testcontainers MySQL 8.4 with an
in-process gRPC channel). The integration test skips itself when Docker is not running.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| T3 exits right after start | No non-daemon thread | `GrpcServerConfig` starts an `awaitTermination` thread; check it is there |
| `Public Key Retrieval is not allowed` | MySQL 8.4 auth | Keep `allowPublicKeyRetrieval=true&useSSL=false` in the JDBC URL |
| `Communications link failure` | MySQL not ready or wrong port | Wait for `run-db.ps1` to report it is up, check `DB_PORT` |
| Port 3306 in use | Another local MySQL | Set `DB_PORT=3307` in `.env`, rerun `run-db.ps1` |
| T2 returns 503 data-server unavailable | T3 not running or wrong port | Start T3 first, check `DATA_SERVER_PORT` |
| All gRPC calls fail with DEADLINE_EXCEEDED after a minute | Deadline set on the shared stub | Deadlines are set per call in `DataClient`; keep it that way |
| Spotify 400 `invalid_client` | Wrong ID or secret | Copy them from the dashboard again, restart T2 |
| Spotify 403 | Owner has no Premium or app is restricted | Check the subscription, or set `SPOTIFY_MODE=mock` |
| Spotify 400 on search | `limit` above 10 | T2 caps it at 10 |
| Song titles show broken characters | Windows PowerShell 5.1 | Run the scripts with `pwsh` (PowerShell 7) |
| `search.ps1` does not react to keys | Terminal without key events (some IDE terminals) | Use Windows Terminal, or `-Simple` |
| `protoc` version error at build | protoc and protobuf-java differ | Set `protobuf.version` in `pom.xml` to the runtime version from `.\mvnw dependency:tree -pl proto` |
| `.ps1 cannot be loaded because running scripts is disabled` | Execution policy | `pwsh -ExecutionPolicy Bypass -File scripts\...` |
