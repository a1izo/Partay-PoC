# Partay Proof of Concept: Build Plan

Audience: Claude Code, running on Alex's Windows laptop inside `SEP3\Partay-PoC`.
Goal: a working demo for the supervisor meeting on 28 Oct 2026.
Instruction to Claude Code: build everything in this file, in the milestone order below. Verify each milestone before moving on. Do not add features outside the scope section. Ask Alex before any install that needs admin rights or a reboot.

---

## 1. What we are building

Two Spring Boot services that match the three-tier sketch from the supervisors (T1 presentation, T2 app/logic, T3 data):

```
 T1 (thin)                   T2 app-server                     T3 data-server            MySQL 8.4
 demo page (HTML)    REST    Spring Boot, port 8080    gRPC     Spring Boot, port 9090    JPA/Hibernate
 Swagger UI, curl   -------> @RestController           -------> gRPC service      -------> partay DB
 PowerShell scripts  JSON    Service (all rules)       proto    Repositories (@Repository)
                             Spotify client  ----REST/HTTPS----> Spotify Web API (search, get track)
```

- T1 is one plain HTML page (`index.html`, no CSS, vanilla JavaScript, no framework) that T2 serves at http://localhost:8080/. It prints the demo as a chronological text log. It is the main thing on the projector. Swagger UI, curl and the PowerShell scripts stay as backups.
- T2 owns every business rule. It exposes REST + Swagger UI, calls T3 over gRPC, and calls Spotify.
- T3 only stores and returns rows. No rules beyond database constraints.
- Two network technologies: REST (client to T2, T2 to Spotify) and gRPC (T2 to T3).

### Demo story

1. Host Mikkel creates a party and gets a 6-character join code.
2. Guests Sofia and Jonas join with nicknames and get a guestId each.
3. Guests search Spotify live, as they type, from the terminal.
4. Each guest adds a song by its Spotify track ID. T2 fetches the real title, artist and duration.
5. Guests vote up, down or clear. A new vote replaces the old one.
6. The queue reorders by score, then by time added.
7. The demo page prints each step in order as plain text: the request, the response, the ranked queue, and the hops (each gRPC call to T3, each Spotify call or cache hit, with status and time).
8. Both server terminals also log every hop, so the supervisors see the SQL in T3 as well.

### Scope

In scope:
- Create party, join party, live search, add song, vote, get ranked queue, health check through both tiers.
- Spotify search and track lookup with the client credentials flow.
- Logging of every REST call, gRPC call and SQL statement.
- A request trace in T2 (`GET /api/debug/traces`) that the demo page reads.
- One demo page (`index.html`) served by T2. This is the main demo client.
- `demo.ps1` (full scripted flow) and `search.ps1` (live search in the terminal) as backups.
- Unit tests for the rules in T2. One integration test for T3.

Out of scope (list these as next steps in the meeting, do not build them):
- Mobile app (.NET MAUI, C#), WebSockets, Redis, Spotify login and playback, guest tokens or passwords, host moderation (skip, remove, lock, block), song limit per guest, anonymous adds, saving the playlist.

### Decisions already made (with reasons, for the exam)

| Decision | Choice | Reason |
|---|---|---|
| Tiers built | T2 and T3 | Matches the supervisors' sketch. T1 is a thin demo page plus tools. |
| Demo client | One plain HTML page served by T2, no CSS, chronological text log | Shows the output without building a UI. Same origin as the API, so no CORS setup. The real client stays the MAUI app. |
| Database | MySQL 8.4 | Matches the Project Description. Supervisor approved it. |
| Rules location | T2 only | T3 is a pure data tier. Clear separation for the exam. |
| Redis | Not in PoC | Parties have 2 to 15 people. Memory and MySQL are enough. Revisit in Sprint 3. |
| Spotify | Client credentials, search + get track | No user login, no Premium needed per call. Proves the integration. |
| Adding songs | Client sends only the track ID | The server never trusts song data from a client. |
| Guest identity | `X-Guest-Id` header with a UUID | No accounts, matches the survey finding "no account to join". |
| Live search | Terminal, debounced at 300 ms | Shows the concept with no UI. The same endpoint serves the MAUI app later. |
| gRPC library | Plain grpc-java, no Spring starter | Fewest version conflicts with Spring Boot. |

---

## 2. Tech stack and versions

Pick the latest patch of each line from Maven Central at build time and pin it in the parent `pom.xml`.

| Item | Version line | Notes |
|---|---|---|
| JDK | 21 (Temurin) | Required. |
| Spring Boot | 3.5.x | Use `spring-boot-starter-parent`. Do not use Boot 4 (Jackson 3 and module changes add risk). |
| springdoc-openapi | 2.8.x | `springdoc-openapi-starter-webmvc-ui`, matches Boot 3.5. |
| grpc-java | latest 1.x | Import `io.grpc:grpc-bom`. Use `grpc-netty-shaded`, `grpc-protobuf`, `grpc-stub`, `grpc-services` (reflection). |
| protobuf-java / protoc | same version as the protobuf-java that grpc-protobuf pulls in | Check with `.\mvnw dependency:tree -pl proto`. protoc and the runtime must match. |
| protobuf-maven-plugin | `org.xolstice.maven.plugins:protobuf-maven-plugin:0.6.1` | With `kr.motd.maven:os-maven-plugin:1.7.1` as a build extension. |
| annotations-api | `org.apache.tomcat:annotations-api:6.0.53`, scope provided | Needed by older generated gRPC code. Keep it, harmless. |
| MySQL driver | `com.mysql:mysql-connector-j` | Version managed by Spring Boot. |
| Caffeine | managed by Spring Boot | Search and track caches in T2. |
| Testcontainers | managed by Spring Boot | T3 integration test, skipped when Docker is missing. |
| MySQL server | Docker image `mysql:8.4` | Fallback: local MySQL 8 install. |
| PowerShell | 7.x (`pwsh`) | Scripts need PS 7. Windows PowerShell 5.1 mangles UTF-8 song titles and lacks `-SkipHttpErrorCheck`. |

Java packages:
- `dk.via.partay.grpc` for generated code
- `dk.via.partay.app` for T2
- `dk.via.partay.data` for T3

---

## 3. Repository layout

```
Partay-PoC/
  PLAN.md
  README.md
  pom.xml                      parent, packaging pom, modules: proto, data-server, app-server
  mvnw, mvnw.cmd, .mvn/        Maven wrapper
  .gitignore                   target/, .idea/, *.iml, .env, .vscode/
  .env.example                 committed template
  .env                         real values, never committed
  docker-compose.yml
  proto/
    pom.xml
    src/main/proto/partay_data.proto
  data-server/                 T3
    pom.xml
    src/main/java/dk/via/partay/data/
      DataServerApplication.java
      config/GrpcServerConfig.java
      grpc/PartayDataGrpcService.java
      grpc/LoggingServerInterceptor.java
      grpc/ProtoMapper.java
      entity/PartyEntity.java, GuestEntity.java, QueueItemEntity.java, VoteEntity.java, VoteId.java
      repository/PartyRepository.java, GuestRepository.java, QueueItemRepository.java, VoteRepository.java
    src/main/resources/application.properties
    src/main/resources/schema.sql
    src/test/java/... PartayDataGrpcServiceIT.java
  app-server/                  T2
    pom.xml
    src/main/java/dk/via/partay/app/
      AppServerApplication.java
      config/GrpcClientConfig.java, OpenApiConfig.java, CacheConfig.java
      grpc/DataClient.java, LoggingClientInterceptor.java
      spotify/SpotifyProperties.java, SpotifyAuthClient.java, SpotifyClient.java,
              LiveSpotifyClient.java, MockSpotifyClient.java, TrackInfo.java
      service/PartyService.java, QueueService.java, SearchService.java,
              QueueRanker.java, JoinCodeGenerator.java
      web/PartyController.java, QueueController.java, SearchController.java, HealthController.java,
          DebugController.java, ApiExceptionHandler.java, RequestLoggingFilter.java
      trace/TraceRecorder.java, RequestTrace.java, TraceHop.java
      web/dto/  (Java records for requests and responses)
      error/    (NotFoundException, ForbiddenException, ConflictException, BadRequestException,
                 UpstreamException)
    src/main/resources/application.properties
    src/main/resources/static/index.html      demo page (plain HTML and JS in one file, no CSS)
    src/test/java/... QueueRankerTest.java, JoinCodeGeneratorTest.java, QueueServiceTest.java,
                      TraceRecorderTest.java
  scripts/
    setup-check.ps1            check and install prerequisites
    create-db.sql              fallback for local MySQL
    build.ps1                  mvnw clean package
    run-db.ps1                 docker compose up -d, wait for healthy
    run-t3.ps1                 java -jar data-server
    run-t2.ps1                 load .env, java -jar app-server
    lib.ps1                    shared helpers for the two demo scripts
    demo.ps1                   full scripted flow
    search.ps1                 live search
```

---

## 4. Milestones (build in this order)

Each milestone ends with a check. Do not start the next one until the check passes.

### M0. Prerequisites (`scripts/setup-check.ps1`)

The script checks each tool, prints OK or MISSING with the version, and offers to install what is missing with `winget`. It asks before each install.

| Tool | Check | Install |
|---|---|---|
| PowerShell 7 | `pwsh -v` | `winget install --id Microsoft.PowerShell -e` |
| JDK 21 | `java -version` shows 21 or newer | `winget install --id EclipseAdoptium.Temurin.21.JDK -e` |
| Git | `git --version` | `winget install --id Git.Git -e` |
| Docker Desktop | `docker --version` and `docker info` succeeds | `winget install --id Docker.DockerDesktop -e` (needs WSL2, admin, often a reboot: ask Alex first) |
| MySQL fallback | only if Docker fails: `mysql --version` | run `winget search mysql`, pick the Oracle MySQL Server 8 package, install, then run `scripts/create-db.sql` as root |

Notes:
- After a JDK install, a new terminal is needed for `JAVA_HOME` and `PATH`.
- Maven itself is not required. The project uses the Maven wrapper. If `mvn` is missing when generating the wrapper, download any project from start.spring.io (Maven, Java 21, Boot 3.5) and copy `mvnw`, `mvnw.cmd` and `.mvn/` from it.
- Port check: if port 3306 is taken (a local MySQL is running), set `DB_PORT=3307` in `.env`. Compose and T3 both read it.

`scripts/create-db.sql`:
```sql
CREATE DATABASE IF NOT EXISTS partay CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'partay'@'localhost' IDENTIFIED BY 'partay';
CREATE USER IF NOT EXISTS 'partay'@'%' IDENTIFIED BY 'partay';
GRANT ALL PRIVILEGES ON partay.* TO 'partay'@'localhost';
GRANT ALL PRIVILEGES ON partay.* TO 'partay'@'%';
FLUSH PRIVILEGES;
```

Check: `setup-check.ps1` prints OK for pwsh, Java 21, Git, and either Docker or MySQL.

### M1. Project skeleton

- Parent `pom.xml`: parent `spring-boot-starter-parent` 3.5.x, `<packaging>pom</packaging>`, modules `proto`, `data-server`, `app-server`, `java.version` 21, properties for `grpc.version` and `protobuf.version`, `dependencyManagement` importing `grpc-bom`.
- Each service pom has `spring-boot-maven-plugin` and depends on the `proto` module.
- `.gitignore`, `.env.example`, `docker-compose.yml` (below), Maven wrapper.
- `git init`, first commit.

`docker-compose.yml`:
```yaml
services:
  mysql:
    image: mysql:8.4
    container_name: partay-mysql
    environment:
      MYSQL_DATABASE: partay
      MYSQL_USER: partay
      MYSQL_PASSWORD: partay
      MYSQL_ROOT_PASSWORD: root
    ports:
      - "${DB_PORT:-3306}:3306"
    volumes:
      - partay-mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-proot"]
      interval: 5s
      timeout: 3s
      retries: 30
volumes:
  partay-mysql-data:
```

`.env.example`:
```
# Spotify developer app (owner needs Spotify Premium, see section 7)
SPOTIFY_CLIENT_ID=
SPOTIFY_CLIENT_SECRET=
# live = call Spotify, mock = built-in track list (demo safety net)
SPOTIFY_MODE=live

DB_HOST=localhost
DB_PORT=3306
DB_USER=partay
DB_PASSWORD=partay

DATA_SERVER_HOST=localhost
DATA_SERVER_PORT=9090
```

Check: `.\mvnw -q clean package -DskipTests` succeeds (empty apps are fine at this point).

### M2. gRPC contract (`proto/src/main/proto/partay_data.proto`)

Use exactly this file. Timestamps are epoch milliseconds (`int64`) to avoid well-known-type imports.

```proto
syntax = "proto3";

package partay.data.v1;

option java_multiple_files = true;
option java_package = "dk.via.partay.grpc";
option java_outer_classname = "PartayDataProto";

// T3 data service. Stores and returns rows. No business rules.
service PartayDataService {
  rpc Ping (PingRequest) returns (PingResponse);

  rpc CreateParty (CreatePartyRequest) returns (CreatePartyResponse);
  rpc GetPartyByJoinCode (GetPartyByJoinCodeRequest) returns (Party);

  rpc CreateGuest (CreateGuestRequest) returns (Guest);
  rpc GetGuest (GetGuestRequest) returns (Guest);

  rpc AddQueueItem (AddQueueItemRequest) returns (QueueItem);
  rpc ListQueueItems (ListQueueItemsRequest) returns (ListQueueItemsResponse);

  rpc UpsertVote (UpsertVoteRequest) returns (Vote);
  rpc DeleteVote (DeleteVoteRequest) returns (DeleteVoteResponse);
  rpc ListVotes (ListVotesRequest) returns (ListVotesResponse);
}

enum GuestRole {
  GUEST_ROLE_UNSPECIFIED = 0;
  HOST = 1;
  GUEST = 2;
}

message Party {
  int64 id = 1;
  string join_code = 2;
  string name = 3;
  int64 created_at_ms = 4;
}

message Guest {
  string id = 1;            // UUID
  int64 party_id = 2;
  string nickname = 3;
  GuestRole role = 4;
  int64 joined_at_ms = 5;
}

message QueueItem {
  int64 id = 1;
  int64 party_id = 2;
  string spotify_track_id = 3;
  string title = 4;
  string artist = 5;
  string album = 6;
  int32 duration_ms = 7;
  string added_by_guest_id = 8;
  string added_by_nickname = 9;
  int64 added_at_ms = 10;
}

message Vote {
  int64 queue_item_id = 1;
  string guest_id = 2;
  int32 value = 3;          // -1 or 1
  int64 updated_at_ms = 4;
}

message PingRequest { string sender = 1; }
message PingResponse {
  string message = 1;
  int64 server_time_ms = 2;
  bool database_ok = 3;
}

// T3 creates the party and the host guest in one transaction.
message CreatePartyRequest {
  string join_code = 1;     // generated by T2
  string name = 2;
  string host_nickname = 3;
}
message CreatePartyResponse {
  Party party = 1;
  Guest host = 2;
}

message GetPartyByJoinCodeRequest { string join_code = 1; }

message CreateGuestRequest {
  int64 party_id = 1;
  string nickname = 2;
  GuestRole role = 3;
}
message GetGuestRequest { string guest_id = 1; }

message AddQueueItemRequest {
  int64 party_id = 1;
  string spotify_track_id = 2;
  string title = 3;
  string artist = 4;
  string album = 5;
  int32 duration_ms = 6;
  string added_by_guest_id = 7;
}
message ListQueueItemsRequest { int64 party_id = 1; }
message ListQueueItemsResponse { repeated QueueItem items = 1; }

message UpsertVoteRequest {
  int64 queue_item_id = 1;
  string guest_id = 2;
  int32 value = 3;          // -1 or 1. T2 sends 0 as DeleteVote instead.
}
message DeleteVoteRequest {
  int64 queue_item_id = 1;
  string guest_id = 2;
}
message DeleteVoteResponse { bool deleted = 1; }

message ListVotesRequest { int64 party_id = 1; }
message ListVotesResponse { repeated Vote votes = 1; }
```

gRPC status codes T3 returns:
- `NOT_FOUND`: party, guest or queue item does not exist.
- `ALREADY_EXISTS`: unique constraint hit (join code, nickname in party, track in party). Catch `DataIntegrityViolationException` and map it.
- `INVALID_ARGUMENT`: empty required field, vote value not -1 or 1, foreign key violation.
- `INTERNAL`: anything else, with the exception message in the description.

Check: `.\mvnw -q -pl proto install` generates `PartayDataServiceGrpc` and the message classes.

### M3. T3 data-server

`application.properties`:
```properties
spring.application.name=partay-data-server
spring.main.web-application-type=none

grpc.server.port=${DATA_SERVER_PORT:9090}

spring.datasource.url=jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/partay?serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false
spring.datasource.username=${DB_USER:partay}
spring.datasource.password=${DB_PASSWORD:partay}

spring.sql.init.mode=always
spring.jpa.hibernate.ddl-auto=none
spring.jpa.open-in-view=false
spring.jpa.properties.hibernate.jdbc.time_zone=UTC

logging.pattern.console=%d{HH:mm:ss.SSS} %-5level %msg%n
logging.level.root=WARN
logging.level.dk.via.partay=INFO
logging.level.org.hibernate.SQL=DEBUG
```
Note: `allowPublicKeyRetrieval=true` is required for MySQL 8.4 default auth without SSL.

`schema.sql` (idempotent, runs on every start):
```sql
CREATE TABLE IF NOT EXISTS party (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  join_code   CHAR(6)      NOT NULL,
  name        VARCHAR(100) NOT NULL,
  created_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT uq_party_join_code UNIQUE (join_code)
);

CREATE TABLE IF NOT EXISTS guest (
  id          CHAR(36)     PRIMARY KEY,
  party_id    BIGINT       NOT NULL,
  nickname    VARCHAR(30)  NOT NULL,
  role        VARCHAR(10)  NOT NULL,
  joined_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_guest_party FOREIGN KEY (party_id) REFERENCES party(id) ON DELETE CASCADE,
  CONSTRAINT uq_guest_nickname UNIQUE (party_id, nickname),
  CONSTRAINT ck_guest_role CHECK (role IN ('HOST', 'GUEST'))
);

CREATE TABLE IF NOT EXISTS queue_item (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  party_id          BIGINT       NOT NULL,
  spotify_track_id  VARCHAR(64)  NOT NULL,
  title             VARCHAR(300) NOT NULL,
  artist            VARCHAR(300) NOT NULL,
  album             VARCHAR(300) NOT NULL,
  duration_ms       INT          NOT NULL,
  added_by          CHAR(36)     NOT NULL,
  added_at          TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_item_party FOREIGN KEY (party_id) REFERENCES party(id) ON DELETE CASCADE,
  CONSTRAINT fk_item_guest FOREIGN KEY (added_by) REFERENCES guest(id),
  CONSTRAINT uq_item_track UNIQUE (party_id, spotify_track_id)
);

CREATE TABLE IF NOT EXISTS vote (
  queue_item_id  BIGINT       NOT NULL,
  guest_id       CHAR(36)     NOT NULL,
  vote_value     SMALLINT     NOT NULL,
  updated_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (queue_item_id, guest_id),
  CONSTRAINT fk_vote_item  FOREIGN KEY (queue_item_id) REFERENCES queue_item(id) ON DELETE CASCADE,
  CONSTRAINT fk_vote_guest FOREIGN KEY (guest_id) REFERENCES guest(id) ON DELETE CASCADE,
  CONSTRAINT ck_vote_value CHECK (vote_value IN (-1, 1))
);
```
The default collation `utf8mb4_0900_ai_ci` makes the nickname constraint case-insensitive ("sofia" and "Sofia" clash). That is intended.

Entities: map with `Instant` for timestamps, `String` for UUIDs, `short` for `vote_value`, `@Enumerated(EnumType.STRING)` for role. `VoteEntity` uses `@EmbeddedId VoteId(queueItemId, guestId)`. `QueueItemEntity` has `@ManyToOne(fetch = LAZY) GuestEntity addedBy` so T3 fills `added_by_nickname`. Use `@Generated`/database defaults or set timestamps in Java before save. Either works, stay consistent.

Repositories:
- `PartyRepository.findByJoinCode(String)`
- `GuestRepository` (default methods)
- `QueueItemRepository.findByPartyIdOrderByIdAsc(long)` with a fetch join on `addedBy` to avoid N+1
- `VoteRepository.findByPartyId(long)` via JPQL join on queue item
- `VoteRepository.upsert(itemId, guestId, value)`: native, `@Modifying`, race-safe:
  ```sql
  INSERT INTO vote (queue_item_id, guest_id, vote_value) VALUES (:itemId, :guestId, :value) AS new
  ON DUPLICATE KEY UPDATE vote_value = new.vote_value, updated_at = CURRENT_TIMESTAMP(3)
  ```
  Then read the row back and return it.

`PartayDataGrpcService extends PartayDataServiceGrpc.PartayDataServiceImplBase`:
- Each method is `@Transactional` (read-only where it only reads). Methods are on a Spring bean, so transactions apply.
- Each method maps entities to proto with `ProtoMapper`, calls `onNext` then `onCompleted`, and on error calls `onError(Status.X.withDescription(...).asRuntimeException())`.
- `Ping` runs `SELECT 1` through the `DataSource` and returns `database_ok`.
- `CreateParty` saves the party and the host guest (new UUID, role HOST) in one transaction.

`GrpcServerConfig`:
- A `SmartLifecycle` bean that builds `ServerBuilder.forPort(port).addService(ServerInterceptors.intercept(service, loggingInterceptor)).addService(ProtoReflectionService.newInstance()).build()`, starts it in `start()`, shuts it down in `stop()`.
- The app has no web server, so start a non-daemon thread that calls `server.awaitTermination()`. Without it the JVM exits right after startup.
- Reflection lets Postman (gRPC request) or grpcurl list and call the service. Mention this in the demo: it covers "BloomRPC" from the supervisors' sketch. BloomRPC itself is archived, Postman replaces it.

`LoggingServerInterceptor` output, one line per call:
```
[T3] gRPC <- CreateParty  OK  (8 ms)
[T3] gRPC <- GetGuest     NOT_FOUND guest 1f0c... not found  (3 ms)
```
Hibernate SQL lines appear under each one because of `org.hibernate.SQL=DEBUG`.

Check:
1. `scripts/run-db.ps1` then `scripts/run-t3.ps1`. Tables exist (`docker exec partay-mysql mysql -upartay -ppartay partay -e "SHOW TABLES"`).
2. If grpcurl is available: `grpcurl -plaintext localhost:9090 list` shows `partay.data.v1.PartayDataService`. Otherwise go to M4, the health endpoint checks it.

### M4. T2 app-server: gRPC client, health, party and guest

`application.properties`:
```properties
spring.application.name=partay-app-server
server.port=8080

data-server.host=${DATA_SERVER_HOST:localhost}
data-server.port=${DATA_SERVER_PORT:9090}
data-server.deadline-ms=3000

spotify.mode=${SPOTIFY_MODE:live}
spotify.client-id=${SPOTIFY_CLIENT_ID:}
spotify.client-secret=${SPOTIFY_CLIENT_SECRET:}
spotify.accounts-url=https://accounts.spotify.com
spotify.api-url=https://api.spotify.com/v1

springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.tryItOutEnabled=true

logging.pattern.console=%d{HH:mm:ss.SSS} %-5level %msg%n
logging.level.root=WARN
logging.level.dk.via.partay=INFO
```

`GrpcClientConfig`:
- One `ManagedChannel` bean: `ManagedChannelBuilder.forAddress(host, port).usePlaintext().intercept(loggingClientInterceptor).build()`. Shut it down on context close.
- One `PartayDataServiceBlockingStub` bean without a deadline.
- Pitfall: never set `withDeadlineAfter` on the singleton stub. The deadline is absolute and expires for every later call. `DataClient` calls `stub.withDeadlineAfter(deadlineMs, MILLISECONDS)` per call.

`DataClient`: one method per RPC. Converts proto messages into T2 records (`PartyView`, `GuestView`, `QueueItemView`, `VoteView`). Catches `StatusRuntimeException` and rethrows as T2 exceptions:

| gRPC status | T2 exception | HTTP |
|---|---|---|
| NOT_FOUND | NotFoundException | 404 |
| ALREADY_EXISTS | ConflictException | 409 |
| INVALID_ARGUMENT | BadRequestException | 400 |
| UNAVAILABLE, DEADLINE_EXCEEDED | UpstreamException("data-server unavailable") | 503 |
| other | UpstreamException | 502 |

`LoggingClientInterceptor` output:
```
[T2] gRPC -> CreateParty  OK  (11 ms)
```

`RequestLoggingFilter` (`OncePerRequestFilter`, skip `/swagger-ui`, `/v3/api-docs`, `/api/debug`, `/`, `/index.html` and other static paths):
```
[T2] REST <- POST /api/parties
[T2] REST -> 201 POST /api/parties  (37 ms)
```

`ApiExceptionHandler` (`@RestControllerAdvice`) returns `ProblemDetail` JSON:
```json
{ "type": "about:blank", "title": "Conflict", "status": 409,
  "detail": "Nickname 'Sofia' is already taken in party K7QX2M", "instance": "/api/parties/K7QX2M/guests" }
```
Also map `MethodArgumentNotValidException`, `MissingRequestHeaderException`, `HttpMessageNotReadableException` and `MethodArgumentTypeMismatchException` to 400.

`JoinCodeGenerator`: 6 characters from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (no 0, O, 1, I) with `SecureRandom`. `PartyService.createParty` retries up to 5 times when T3 returns ALREADY_EXISTS for the code.

Rules in T2 (validate with Bean Validation on request records plus explicit checks in services):
- Party name: trimmed, 1 to 100 chars.
- Nickname: trimmed, 1 to 30 chars, letters, digits, space, `_` and `-` only.
- Join codes are case-insensitive. Uppercase them on input.
- A guest acts only inside their own party. `X-Guest-Id` must be a valid UUID, the guest must exist, and `guest.partyId` must equal the party's id. Otherwise 403 "Guest is not part of this party".

Endpoints for this milestone:

`GET /api/health`
```json
200 { "appServer": "UP", "dataServer": "UP", "database": "UP", "spotifyMode": "live", "roundTripMs": 6 }
```
T2 calls `Ping` on T3. If T3 is down: 503 with `"dataServer": "DOWN"`.

`POST /api/parties`
```json
request  { "name": "Friday pre-drinks", "hostNickname": "Mikkel" }
201      { "partyId": 1, "joinCode": "K7QX2M", "name": "Friday pre-drinks",
           "createdAt": "2026-10-28T09:00:00.123Z",
           "host": { "guestId": "3c1d...", "nickname": "Mikkel", "role": "HOST" } }
```

`GET /api/parties/{joinCode}`
```json
200 { "partyId": 1, "joinCode": "K7QX2M", "name": "Friday pre-drinks", "createdAt": "..." }
404 unknown code
```

`POST /api/parties/{joinCode}/guests`
```json
request  { "nickname": "Sofia" }
201      { "guestId": "9a7e...", "nickname": "Sofia", "role": "GUEST", "partyId": 1, "joinCode": "K7QX2M" }
404 unknown code, 409 nickname taken, 400 invalid nickname
```

Swagger: `OpenApiConfig` sets title "Partay PoC - T2 API", a description naming the three tiers, and documents the `X-Guest-Id` header on each endpoint that uses it (`@Parameter(in = ParameterIn.HEADER)`).

Check: with T3 running, start T2. `curl http://localhost:8080/api/health` returns UP for all three. Create a party, join it, try a duplicate nickname (409) and a wrong code (404). Both terminals show the hop logs. Swagger UI opens at http://localhost:8080/swagger-ui.html.

### M5. Spotify integration

Interface `SpotifyClient`:
```java
List<TrackInfo> search(String query, int limit);
TrackInfo getTrack(String spotifyTrackId);
```
`TrackInfo` record: `spotifyTrackId, title, artist, album, durationMs, previewImageUrl` (image is optional, take the smallest album image or null).

`LiveSpotifyClient` (active when `spotify.mode=live` and both credentials are set):
- `SpotifyAuthClient` gets a token with the client credentials flow:
  `POST {accounts-url}/api/token`, header `Authorization: Basic base64(clientId:clientSecret)`, form body `grant_type=client_credentials`. Response has `access_token` and `expires_in` (3600). Cache the token in memory and refresh 60 seconds before expiry. Thread-safe.
- Search: `GET {api-url}/search?q={query}&type=track&limit={limit}` with `Authorization: Bearer {token}`. Map `tracks.items[]`: `id`, `name`, `artists[].name` joined with ", ", `album.name`, `duration_ms`, `album.images`.
- Get track: `GET {api-url}/tracks/{id}`. Same mapping.
- Use Spring `RestClient`. Encode the query parameter properly (use `uriBuilder.queryParam`, not string concatenation).
- On 401, drop the cached token, fetch a new one, retry once.
- On 429, read `Retry-After` and throw `UpstreamException` (503, "Spotify rate limit, retry in N s").
- On 404 from get track: NotFoundException "Track not found on Spotify". On other errors: UpstreamException (502).

Spotify rules as of 2026 (from the February 2026 Development Mode changes):
- The app owner needs an active Spotify Premium subscription, or the app stops working.
- Search `limit` max is 10, default 5. Never send more than 10.
- Batch `GET /tracks?ids=` is removed. Use single `GET /tracks/{id}` only.
- `popularity`, `available_markets` and `linked_from` are removed from track objects. Do not read them.

`MockSpotifyClient` (active when `spotify.mode=mock`, or when live mode lacks credentials: log a WARN line at startup saying "[T2] Spotify credentials missing, using mock tracks"):
- A fixed list of 20 well-known tracks with 22-character fake IDs (`mock` + 18 digits). Search does a case-insensitive substring match on title and artist. This keeps the demo alive if Spotify or the network fails on the day.

`SearchService` (T2 rules for search):
- Trim, collapse inner whitespace. Reject fewer than 2 or more than 100 characters with 400.
- `limit` 1 to 10, default 5.
- Caffeine cache `search`: key = lowercased normalized query + limit, TTL 60 s, max 500 entries.
- Caffeine cache `tracks`: key = track ID, TTL 30 min, max 5000. Every search result goes in here.
- Response includes `cached: true/false` and `tookMs` so the demo shows the cache working.

`GET /api/search?q=blinding&limit=5`
```json
200 { "query": "blinding", "cached": false, "tookMs": 182,
      "tracks": [ { "spotifyTrackId": "0VjIjW4GlUZAMYd2vXMi3b", "title": "Blinding Lights",
                    "artist": "The Weeknd", "album": "After Hours", "durationMs": 200040,
                    "imageUrl": "https://i.scdn.co/..." } ] }
400 query too short or too long, 502/503 Spotify problems
```

Check: with real credentials, search returns real tracks, a repeat search returns `cached: true` in a few ms, and `GET /tracks/{id}` works for an ID from the results. With `SPOTIFY_MODE=mock`, search works offline.

### M6. Queue and votes

`QueueService` rules:
- Add song: validate the track ID (`^[0-9A-Za-z]{22}$`). Check guest membership. Get track details from the `tracks` cache first, else from `SpotifyClient.getTrack`. Call `AddQueueItem`. ALREADY_EXISTS becomes 409 "This song is already in the queue".
- Vote: value must be -1, 0 or 1, else 400. The queue item must belong to this party (load `ListQueueItems` for the party and check the id, 404 if missing). Value 0 calls `DeleteVote` (clearing a vote that does not exist is fine, still 200). Value -1 or 1 calls `UpsertVote`, which replaces any earlier vote by the same guest.
- Get queue: call `ListQueueItems` and `ListVotes` for the party, then rank with `QueueRanker`.

`QueueRanker` (pure Java, no Spring, fully unit tested):
- score = sum of vote values, upvotes = count of +1, downvotes = count of -1.
- Sort by score descending, then `addedAt` ascending, then id ascending.
- `position` starts at 1.
- `myVote` = the requesting guest's vote on that item (-1, 0 or 1), 0 when no `X-Guest-Id` is sent.

`POST /api/parties/{joinCode}/queue` with header `X-Guest-Id`
```json
request  { "spotifyTrackId": "0VjIjW4GlUZAMYd2vXMi3b" }
201      { "itemId": 1, "spotifyTrackId": "0VjIjW4GlUZAMYd2vXMi3b", "title": "Blinding Lights",
           "artist": "The Weeknd", "album": "After Hours", "durationMs": 200040,
           "addedBy": "Sofia", "addedAt": "..." }
400 bad ID, 403 not in party, 404 party or track missing, 409 duplicate
```

`GET /api/parties/{joinCode}/queue` with optional header `X-Guest-Id`
```json
200 { "joinCode": "K7QX2M", "partyName": "Friday pre-drinks",
      "items": [ { "position": 1, "itemId": 3, "title": "September", "artist": "Earth, Wind & Fire",
                   "addedBy": "Jonas", "score": 2, "upvotes": 2, "downvotes": 0, "myVote": 1,
                   "durationMs": 215000, "addedAt": "..." } ] }
```

`PUT /api/parties/{joinCode}/queue/{itemId}/vote` with header `X-Guest-Id`
```json
request  { "value": 1 }
200      the full ranked queue, same shape as GET queue, so the reorder shows at once
400 bad value, 403 not in party, 404 party or item missing
```

Check: add three songs as three guests, vote, and confirm the order changes. Vote twice with the same value (idempotent), switch a vote from +1 to -1 (score drops by 2), clear with 0.

### M7. Logging pass

Run the full flow once and read both terminals. Every REST call in T2 shows REST in, each gRPC call out with status and time, and REST out with status and time. Every call in T3 shows gRPC in and the SQL. Spotify calls in T2 log `[T2] HTTP -> Spotify GET /search 200 (180 ms)` or `[T2] cache hit search "blinding"`. Remove noisy framework logs (root level WARN). Keep the startup banner lines that print the port.

#### M7b. Request trace for the demo page

The same hops that go to the T2 log also go into an in-memory trace, so the browser shows them.

- `TraceHop` record: `type` (`GRPC`, `SPOTIFY`, `CACHE`), `name` (for example `UpsertVote`, `GET /search`, `search "blinding"`), `status` (`OK`, `NOT_FOUND`, `200`, `HIT`), `ms`.
- `RequestTrace` record: `id` (increasing long), `startedAt`, `method`, `path`, `status`, `totalMs`, `actingAs` (nickname from `X-Guest-Id` if known, else null), `hops`.
- `TraceRecorder` bean: a `ThreadLocal` holds the trace of the current request. `RequestLoggingFilter` starts it and finishes it. `LoggingClientInterceptor`, `LiveSpotifyClient` and `SearchService` (cache hits) append hops. This works because blocking gRPC stubs and the Spotify client run on the request thread. Finished traces go into a ring buffer of the last 100 (synchronized `ArrayDeque`). Always clear the `ThreadLocal` in a `finally` block.
- Requests with the header `X-Partay-Poll: 1` and paths under `/api/debug` are not traced, so page refreshes do not flood the panel.
- `DebugController`:
  - `GET /api/debug/traces?after={id}` returns traces with id greater than `after`, oldest first.
  - `DELETE /api/debug/traces` clears the buffer (the page calls it on "Reset").
- Unit test `TraceRecorderTest`: hops attach to the right trace, the buffer keeps only 100, `after` filters correctly.

Example response:
```json
[ { "id": 42, "startedAt": "2026-10-28T09:14:02.311Z", "method": "PUT",
    "path": "/api/parties/K7QX2M/queue/3/vote", "status": 200, "totalMs": 16, "actingAs": "Sofia",
    "hops": [ { "type": "GRPC", "name": "GetGuest", "status": "OK", "ms": 3 },
              { "type": "GRPC", "name": "ListQueueItems", "status": "OK", "ms": 4 },
              { "type": "GRPC", "name": "UpsertVote", "status": "OK", "ms": 4 },
              { "type": "GRPC", "name": "ListVotes", "status": "OK", "ms": 3 } ] } ]
```

### M8. Scripts

All scripts target PowerShell 7 and start with:
```powershell
#Requires -Version 7
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
```

`build.ps1`: `.\mvnw clean package -DskipTests` from the repo root.

`run-db.ps1`: `docker compose up -d`, then poll `docker inspect -f '{{.State.Health.Status}}' partay-mysql` until `healthy` (timeout 90 s). If Docker is missing, print the local MySQL fallback steps.

`run-t3.ps1` and `run-t2.ps1`: load `.env` from the repo root into `$env:` (skip blank lines and `#` comments), then `java -jar data-server/target/data-server-*.jar` or `java -jar app-server/target/app-server-*.jar`. Do not run `spring-boot:run` from the parent: in a multi-module build it also runs on the `proto` module and fails.

`lib.ps1` (dot-sourced by the two demo scripts):
- `$Base = $env:PARTAY_URL ?? 'http://localhost:8080'`
- `Invoke-Partay -Method -Path -Body -GuestId`: wraps `Invoke-RestMethod` with `-SkipHttpErrorCheck -StatusCodeVariable`, JSON body via `ConvertTo-Json -Depth 10`, sets `X-Guest-Id` when given. Returns `[pscustomobject]@{ Status; Body }`. Never throws on 4xx so the demo shows errors on purpose.
- `Show-Step` prints a numbered cyan header. `Show-Queue` prints the queue as a table: Pos, Title, Artist, AddedBy, Score, Up, Down.
- `Show-Result` prints the HTTP status in green (2xx) or yellow (4xx) and the body as indented JSON.

`demo.ps1` (switch `-Pause` waits for Enter between steps, for live presenting):
1. Health check through T2 to T3 to MySQL.
2. Mikkel creates "Friday pre-drinks". Print the join code.
3. Sofia and Jonas join. Then "sofia" tries to join: 409.
4. Search "blinding lights", "mr brightside", "september earth wind". Print the top 3 for each. Repeat one search to show `cached: true`.
5. Sofia adds Blinding Lights, Mikkel adds Mr. Brightside, Jonas adds September. Print the queue (all score 0, ordered by time added).
6. Votes: Mikkel and Sofia upvote September, Jonas downvotes Mr. Brightside. Print the queue: September first.
7. Sofia switches her vote on September to -1. Print the queue and point out the reorder.
8. Error cases, each prints its status: wrong join code (404), vote value 5 (400), missing `X-Guest-Id` (400), guest from a second party votes here (403), Sofia adds Blinding Lights again (409).
9. Final queue and the join code to use with `search.ps1`.
10. Optional `-Stress`: 30 parallel votes (`ForEach-Object -Parallel -ThrottleLimit 10`) from 3 guests on one item with random values. Then fetch the queue and assert the score equals the sum of each guest's last vote, and that each guest has at most one vote row. Prints PASS or FAIL. This shows the race-safe upsert.

In mock mode, use queries that match the mock list (pick mock tracks named the same as the live ones, so the script works in both modes).

`search.ps1 -JoinCode K7QX2M -GuestId <uuid> [-Simple]`:
- If `-GuestId` is missing, the script asks for a nickname and joins the party first.
- Live mode loop:
  - Poll `[Console]::KeyAvailable` every 25 ms. Read keys with `[Console]::ReadKey($true)`.
  - Printable character: append to the query. Backspace: remove last character. Up/Down: move the selection. Enter: add the selected track with `POST /queue` and show the result on the status line. Esc: quit.
  - Debounce: search only when 300 ms have passed since the last keystroke, the query has at least 2 characters, and it differs from the last searched query.
  - Redraw the screen after each search or selection change: header (party, nickname), the query line with a cursor, up to 5 results with a `>` marker on the selected one, `tookMs` and `cached`, a status line, and a footer "Type to search | Up/Down select | Enter add | Esc quit".
  - Use `[Console]::SetCursorPosition(0,0)` and pad lines to the window width to avoid flicker. Fall back to `Clear-Host` if that fails.
- `-Simple` mode: `Read-Host` a query, print numbered results, `Read-Host` a number to add, repeat until an empty query. Use it if key reading misbehaves in the demo terminal (for example inside an IDE terminal).

Check: run `demo.ps1` end to end with no unexpected status codes. Run `search.ps1` in Windows Terminal, type "blind", see results appear without pressing Enter, add one, and see it in `GET /queue`.

### M8b. Demo page (`app-server/src/main/resources/static/index.html`)

A plain HTML page that prints the demo output in chronological order, like a terminal log in a browser. Spring Boot serves it at http://localhost:8080/. It calls the same REST endpoints as every other client and holds no rules.

Look: no styling. No CSS file, no `<style>` block, no cards, no colors, no badges, no layout grid, no framework, no CDN. Default browser rendering only. Everything prints into one `<pre>` element, top to bottom, oldest first, newest at the bottom. The page scrolls to the bottom after each new line.

The whole page, top to bottom:

```
Partay PoC
[Next step] [Run all] [Reset]
Live search as: [Sofia v]  [text input]
<pre> ... the log ... </pre>
```

Controls (default unstyled HTML elements only):
- "Next step" runs the next step of the guided demo. "Run all" runs every remaining step with a 1.5 s pause between them. "Reset" clears the log and the page memory, and the next step starts a new party.
- Live search: a `<select>` with the guests the page created (empty until the join step) and one `<input type="text">`. On each keystroke, wait 300 ms after the last key, then call `GET /api/search?q=...` if the text has at least 2 characters. Cancel the previous request with `AbortController`. Results print into the log as numbered lines. Typing a number from 1 to 5 followed by Enter in the same input adds that track as the selected guest. Print the result and the new queue.

Log format. Each step prints a header line, then each HTTP call, then the hops from the trace, then the queue when it changed:

```
09:14:00  STEP 1  Health check
09:14:00  GET  /api/health
          200  {"appServer":"UP","dataServer":"UP","database":"UP","spotifyMode":"live","roundTripMs":6}
          hops: gRPC Ping OK 4 ms

09:14:02  STEP 2  Mikkel creates a party
09:14:02  POST /api/parties  {"name":"Friday pre-drinks","hostNickname":"Mikkel"}
          201  {"partyId":1,"joinCode":"K7QX2M","name":"Friday pre-drinks","host":{"guestId":"3c1d...","nickname":"Mikkel","role":"HOST"}}
          hops: gRPC CreateParty OK 11 ms

09:14:20  STEP 6  Mikkel and Sofia upvote September, Jonas downvotes Mr. Brightside
09:14:20  PUT  /api/parties/K7QX2M/queue/3/vote  as Sofia  {"value":1}
          200  (queue below)
          hops: gRPC GetGuest OK 3 ms, gRPC ListQueueItems OK 4 ms, gRPC UpsertVote OK 4 ms, gRPC ListVotes OK 3 ms
          queue:
            1  September        Earth, Wind & Fire  Jonas    score  2  (+2 -0)
            2  Blinding Lights  The Weeknd          Sofia    score  0  (+0 -0)
            3  Mr. Brightside   The Killers         Mikkel   score -1  (+0 -1)

09:15:01  SEARCH as Sofia  "dancing que"
          200  182 ms  live from Spotify
            1  Dancing Queen        ABBA     3:50
            2  Dancing On My Own    Robyn    4:41
          hops: Spotify GET /search 200 182 ms
```

Rules for the log:
- Print the full JSON response on one line for small bodies. For queue responses print the queue table instead of the JSON.
- After each HTTP call, fetch `GET /api/debug/traces?after={lastId}` once and print the hops of the new traces on one `hops:` line.
- Print 4xx and 5xx responses the same way as 2xx. The status code at the start of the line is enough. No colors.
- Pad columns with spaces so the queue lines up in the monospace `<pre>`.

Guided demo steps (same as `demo.ps1`): health, create party as Mikkel, join Sofia and Jonas, duplicate nickname "sofia" (409), search and add the three songs as their three guests, the three votes, Sofia switches her vote, then the error cases (wrong join code 404, vote value 5 400, missing `X-Guest-Id` 400, guest from a second party 403, duplicate song 409), then the final queue. In mock mode, use search terms that exist in the mock list.

Rules for the JavaScript:
- One `api(method, path, body, guestId)` helper using `fetch`. It never throws on 4xx or 5xx. It returns `{ status, body }` and prints the request and response lines.
- Append text with `textContent` or text nodes only. Never use `innerHTML` with API data. Song titles come from Spotify and are untrusted.
- Page memory is a plain JS object (party, guests). A browser refresh starts over.
- No requests to any origin other than the page's own.

Check:
1. Open http://localhost:8080/ with T2 and T3 running. The page shows only the title, three buttons, the search controls and an empty log.
2. Click "Next step" through the whole demo. Every step prints in order with the expected status codes and its hops.
3. Type a song in the search input. Results print without pressing Enter. Type "1" and Enter to add the first one. The new queue prints.
4. Stop T3 and click "Next step". The response line shows 503. Start T3 again and continue.

### M9. Tests

app-server (JUnit 5, Mockito):
- `QueueRankerTest`: ordering by score, ties by `addedAt`, ties by id, upvote and downvote counts, `myVote` with and without a guest, empty queue.
- `JoinCodeGeneratorTest`: length 6, only allowed characters, 1000 codes contain no 0, O, 1 or I.
- `QueueServiceTest` with a mocked `DataClient` and `SpotifyClient`: vote 0 calls `DeleteVote`, vote 1 calls `UpsertVote`, vote 5 throws BadRequest, guest from another party throws Forbidden, add song uses the cache before calling Spotify.

data-server:
- `PartayDataGrpcServiceIT` with Testcontainers MySQL (`@Testcontainers(disabledWithoutDocker = true)`) and an in-process gRPC server (`grpc-inprocess`, `grpc-testing`): create party plus host, duplicate join code gives ALREADY_EXISTS, upsert vote twice keeps one row with the latest value, delete vote.

Check: `.\mvnw test` passes.

### M10. README and commit

`README.md`, short:
- What this is (one paragraph, the three tiers).
- Prerequisites and `scripts/setup-check.ps1`.
- Spotify setup (section 7 below).
- Start order: `build.ps1`, `run-db.ps1`, `run-t3.ps1` (terminal 1), `run-t2.ps1` (terminal 2), `demo.ps1` and `search.ps1` (terminal 3).
- URLs: demo page http://localhost:8080/, Swagger UI http://localhost:8080/swagger-ui.html, gRPC localhost:9090 with reflection.
- Endpoint table.
- Troubleshooting (section 8).

Commit with a clear message. Alex pushes to a new GitHub repo for the team.

---

## 5. Acceptance checklist (Claude Code ticks each one before saying it is done)

- [ ] `setup-check.ps1` reports all prerequisites OK.
- [ ] `.\mvnw clean package` builds all three modules and runs the tests green.
- [ ] MySQL runs and T3 creates the four tables on start.
- [ ] T2 starts, `GET /api/health` returns UP for app server, data server and database.
- [ ] Swagger UI loads and every endpoint runs from it.
- [ ] Live Spotify search returns real tracks. A repeat search shows `cached: true`.
- [ ] Mock mode works with no network and no credentials.
- [ ] Add song stores the title and artist from Spotify, not from the client.
- [ ] Votes replace earlier votes. Value 0 clears. The queue reorders correctly.
- [ ] Every error case in `demo.ps1` step 8 returns the documented status.
- [ ] `demo.ps1 -Stress` prints PASS.
- [ ] `search.ps1` updates results while typing and adds the selected track.
- [ ] The demo page at http://localhost:8080/ passes every check in M8b.
- [ ] The page has no CSS and prints everything in one chronological log.
- [ ] Every logged call has a `hops:` line with its gRPC calls, Spotify calls or cache hits.
- [ ] T2 and T3 terminals show REST, gRPC and SQL hops for every call.
- [ ] `.env` is in `.gitignore` and no secret appears in any committed file.
- [ ] README explains how to run it in under 5 minutes.

---

## 6. Running the demo (for Alex, meeting day)

Before the meeting:
1. Open Docker Desktop and wait until it says running.
2. `scripts\build.ps1` (only if code changed).
3. `scripts\run-db.ps1`.
4. Terminal 1: `scripts\run-t3.ps1`. Terminal 2: `scripts\run-t2.ps1`.
5. Open http://localhost:8080/ and click "Next step" once to check the health line says UP three times. Click "Reset".
6. Keep `SPOTIFY_MODE=mock` ready in `.env` as a backup. Switching needs only a T2 restart.

Screen layout: the demo page in the browser on most of the screen, the T3 terminal in a strip at the bottom so the SQL lines are visible.

In the meeting (about 10 minutes):
1. Show the architecture sketch and map it: T1 = demo page (plus Swagger and scripts), T2 = app-server, T3 = data-server.
2. Show `partay_data.proto` as the contract between T2 and T3.
3. Click "Next step" through the guided demo. For each step, point at the `hops:` line (gRPC calls from T2 to T3) and the T3 terminal (SQL).
4. Type a song in the search input, then add it by number.
5. Stop T3 for a moment and click "Next step" to show the 503, then start it again.
6. Open Swagger UI and run one call by hand.
7. Optional: call T3 directly from Postman over gRPC with reflection.
8. Backup if the browser misbehaves: `demo.ps1 -Pause` and `search.ps1` in the terminal.

Next steps to present:
- .NET MAUI client in C# replaces T1 tools. This meets the Java plus C# requirement.
- WebSocket push from T2 so every phone sees queue changes live.
- Host Spotify login (Authorization Code with PKCE) and playback. Partay keeps its own queue and sends one track at a time to the host's active Spotify device. The host needs Premium and an open Spotify app.
- Host controls: skip, remove, lock queue, block guest, 3-song limit per guest.
- Redis decision in Sprint 3.
- Guest tokens instead of a raw guestId header.

Questions to ask the supervisors:
- Is C# only in the client enough for the heterogeneity requirement, or do you expect C# on one server too?
- Is the guest identity without accounts acceptable for the exam, given the survey result that people want to join without an account?
- Is calling Spotify from T2 acceptable as the external system, with T3 never talking to the outside?

---

## 7. Spotify developer app setup (Alex does this once)

1. Log in at https://developer.spotify.com/dashboard with Alex's account. The account needs an active Premium subscription (required for Development Mode apps since February 2026).
2. Create app. Name "Partay PoC". Description "VIA SEP3 proof of concept". Redirect URI `http://127.0.0.1:8080/callback` (required by the form, not used by the PoC, Spotify rejects `localhost`). Tick "Web API".
3. Open Settings, copy the Client ID and Client Secret into `.env`.
4. Never paste the secret into chat, code, commits or screenshots. If it leaks, rotate it in the dashboard.

---

## 8. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| T3 exits right after start | No non-daemon thread | Make sure the awaitTermination thread exists (M3). |
| `Public Key Retrieval is not allowed` | MySQL 8.4 auth | Keep `allowPublicKeyRetrieval=true&useSSL=false` in the JDBC URL. |
| `Communications link failure` | MySQL not ready or wrong port | Wait for `healthy`, check `DB_PORT`. |
| Port 3306 in use | Local MySQL running | Set `DB_PORT=3307` in `.env`, rerun `run-db.ps1`. |
| T2 returns 503 data-server unavailable | T3 not running or wrong port | Start T3 first, check `DATA_SERVER_PORT`. |
| All gRPC calls fail with DEADLINE_EXCEEDED after the first minute | Deadline set on the singleton stub | Set the deadline per call in `DataClient`. |
| Spotify 400 `invalid_client` | Wrong ID or secret | Recopy from the dashboard, restart T2. |
| Spotify 403 | Owner has no Premium or app restricted | Check the subscription, or switch to `SPOTIFY_MODE=mock`. |
| Spotify 400 on search | `limit` above 10 | Keep `limit` at 10 or less. |
| Song titles show broken characters | Windows PowerShell 5.1 | Run the scripts with `pwsh` (PowerShell 7). |
| `search.ps1` does not react to keys | Terminal without key events (some IDE terminals) | Use Windows Terminal, or run with `-Simple`. |
| `protoc` version error at build | protoc and protobuf-java differ | Set `protobuf.version` to the runtime version from `dependency:tree`. |
