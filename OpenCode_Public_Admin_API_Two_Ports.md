# OpenCode Prompt — Separate Public and Admin APIs by Port

> **NOTE (score increment/decrement removed):** the four
> `POST /api/games/{id}/score/…` endpoints referenced in this document no
> longer exist (they return `404`). Scores are changed exclusively via
> `PUT /api/admin/games/{id}`. Only `POST /api/games/{id}/start` remains
> (admin port only, read-only no-op). The port-separation contract is
> otherwise unchanged.

## Task

Modify the existing Kotlin + Spring Boot Volleyball Tournament backend so that it remains **one application, one project/module, and one MariaDB database**, but exposes **two logically separated API surfaces on different HTTP ports**.

- **Port 8080** = Public / Student API
- **Port 8081** = Admin API

The separation must be enforced by the listening port, not only by URL prefixes.

Do **not** create a second Spring Boot application or a second project/module.

---

## 1. Target Architecture

Implement this architecture:

```text
                         ONE SPRING BOOT APPLICATION
                         ONE PROJECT / ONE MODULE
                                  │
                    ┌─────────────┴─────────────┐
                    │                           │
                 PORT 8080                  PORT 8081
                    │                           │
             PUBLIC/STUDENT API              ADMIN API
                    │                           │
             Read-only operations        Write operations
                    │                           │
                    └─────────────┬─────────────┘
                                  │
                               MariaDB
```

The same application, domain model, repositories, services, database, and realtime infrastructure should be reused where appropriate.

The two API surfaces must be logically separated.

---

# 2. Port 8080 — Public / Student API

Port `8080` must expose only the public tournament API and the public realtime WebSocket.

Expected endpoints:

```text
GET /
GET /api/groups
GET /api/groups/{id}
GET /api/teams
GET /api/teams/{id}
GET /api/rounds
GET /api/fields
GET /api/games
GET /api/games/filter/{filter}
GET /api/games/{id}

WS /ws/live
```

These endpoints are read-only.

There must be no admin CRUD or scoring endpoint available through port `8080`.

---

# 3. Port 8081 — Admin API

Port `8081` must expose the admin functionality.

## Admin CRUD endpoints

### Groups

```text
POST   /api/admin/groups
PUT    /api/admin/groups/{id}
DELETE /api/admin/groups/{id}
```

### Teams

```text
POST   /api/admin/teams
PUT    /api/admin/teams/{id}
DELETE /api/admin/teams/{id}
```

### Games

```text
POST   /api/admin/games
PUT    /api/admin/games/{id}
DELETE /api/admin/games/{id}
```

### Fields

```text
POST   /api/admin/fields
PUT    /api/admin/fields/{id}
DELETE /api/admin/fields/{id}
```

### Rounds

```text
POST   /api/admin/rounds
PUT    /api/admin/rounds/{id}
DELETE /api/admin/rounds/{id}
```

---

# 4. Admin Game Control / Scoring Endpoints

The following endpoints belong to the Admin API and must be available **only on port 8081**:

```text
POST /api/games/{id}/start

POST /api/games/{id}/score/team-a/increment
POST /api/games/{id}/score/team-a/decrement

POST /api/games/{id}/score/team-b/increment
POST /api/games/{id}/score/team-b/decrement
```

Important:

Keep these exact paths.

Do **not** change them to `/api/admin/games/{id}/start` or `/api/admin/games/{id}/score/...`.

The URL remains `/api/games/...`, but the routes must only exist on port `8081`.

---

# 5. Port Isolation Requirements

## Port 8080

The following must work:

```text
GET /api/groups
GET /api/groups/{id}
GET /api/teams
GET /api/teams/{id}
GET /api/rounds
GET /api/fields
GET /api/games
GET /api/games/filter/{filter}
GET /api/games/{id}
WS /ws/live
```

The following must NOT be available on port `8080`:

```text
POST   /api/admin/groups
PUT    /api/admin/groups/{id}
DELETE /api/admin/groups/{id}

POST   /api/admin/teams
PUT    /api/admin/teams/{id}
DELETE /api/admin/teams/{id}

POST   /api/admin/games
PUT    /api/admin/games/{id}
DELETE /api/admin/games/{id}

POST   /api/admin/fields
PUT    /api/admin/fields/{id}
DELETE /api/admin/fields/{id}

POST   /api/admin/rounds
PUT    /api/admin/rounds/{id}
DELETE /api/admin/rounds/{id}

POST /api/games/{id}/start
POST /api/games/{id}/score/team-a/increment
POST /api/games/{id}/score/team-a/decrement
POST /api/games/{id}/score/team-b/increment
POST /api/games/{id}/score/team-b/decrement
```

## Port 8081

The admin endpoints must work.

The public GET API must not be exposed through port `8081`.

The public WebSocket endpoint must also remain a public-side endpoint on port `8080`.

---

# 6. Controller Organization

These controllers are admin functionality:

```text
game-scoring-controller
teacher-field-controller
teacher-game-controller
teacher-group-controller
teacher-round-controller
teacher-team-controller
```

Rename/restructure the teacher terminology where appropriate so the backend consistently uses `admin`.

Recommended names:

```text
GameScoringController
AdminFieldController
AdminGameController
AdminGroupController
AdminRoundController
AdminTeamController
```

Use `admin` rather than `teacher` in the new controller/package naming where practical.

Do not use `/api/teacher/...` in the new external API.

---

# 7. API Naming Rule

Admin CRUD URLs must use:

```text
/api/admin/...
```

Examples:

```text
/api/admin/groups
/api/admin/teams
/api/admin/games
/api/admin/fields
/api/admin/rounds
```

Do not use:

```text
/api/teacher/groups
/api/teacher/teams
/api/teacher/games
/api/teacher/fields
/api/teacher/rounds
```

The scoring URLs remain:

```text
/api/games/{id}/start
/api/games/{id}/score/...
```

but are only accessible on port `8081`.

---

# 8. Do Not Duplicate the Business/Data Layer

There should still be one underlying domain/data layer.

Prefer:

```text
Public Controller
      │
      ▼
   Service
      │
      ▼
 Repository
      │
      ▼
 MariaDB


Admin Controller
      │
      ▼
   Service
      │
      ▼
 Repository
      │
      ▼
 MariaDB
```

Do not create duplicate repositories, entities, database configurations, or business logic unless the existing architecture requires it.

The public side is read-only.

The admin side performs the writes.

---

# 9. Nullable Game Scores

The `games` table now allows:

```sql
points_a INTEGER NULL,
points_b INTEGER NULL
```

Interpretation:

```text
NULL = game has not started
0 or greater = game has started
```

The public `GameDto` must preserve nullable values.

Example before starting:

```json
{
  "gameId": 15,
  "roundId": 1,
  "fieldId": 1,
  "teamAId": 3,
  "teamBId": 4,
  "refereeTeamId": 5,
  "scoreA": null,
  "scoreB": null
}
```

After starting:

```json
{
  "gameId": 15,
  "roundId": 1,
  "fieldId": 1,
  "teamAId": 3,
  "teamBId": 4,
  "refereeTeamId": 5,
  "scoreA": 0,
  "scoreB": 0
}
```

Do not map `NULL` to `0`.

---

# 10. Game Start Behavior

Endpoint:

```text
POST /api/games/{id}/start
```

This endpoint is available only on port `8081`.

Behavior:

```text
NULL / NULL
    ↓
0 / 0
```

If the game is already started, do not reset the score.

Example:

```text
5 / 3
  ↓
POST /start
  ↓
5 / 3
```

Required behavior:

- invalid ID → `400 BAD_REQUEST`
- game not found → `404 NOT_FOUND`
- unstarted game → set both scores to `0`
- already started game → leave current scores unchanged
- successful request → return updated `GameDto`

---

# 11. Score Increment Behavior

## Team A

```text
POST /api/games/{id}/score/team-a/increment
```

Increase Team A by exactly `1`.

Example:

```text
7 → 8
```

Do not modify Team B.

If Team A score is `NULL`, return:

```text
409 CONFLICT
```

Do not automatically start the game.

## Team B

```text
POST /api/games/{id}/score/team-b/increment
```

Apply the same behavior to Team B.

---

# 12. Score Decrement Behavior

## Team A

```text
POST /api/games/{id}/score/team-a/decrement
```

Decrease Team A by exactly `1`.

The score must never become negative.

Examples:

```text
3 → 2
2 → 1
1 → 0
```

Invalid:

```text
0 → -1
```

If score is already `0`, return:

```text
409 CONFLICT
```

If score is `NULL`, return:

```text
409 CONFLICT
```

## Team B

```text
POST /api/games/{id}/score/team-b/decrement
```

Apply the same behavior to Team B.

---

# 13. Response Contract

All successful game start and score operations should return the updated `GameDto`.

Example:

```json
{
  "gameId": 15,
  "roundId": 1,
  "fieldId": 1,
  "teamAId": 3,
  "teamBId": 4,
  "refereeTeamId": 5,
  "scoreA": 12,
  "scoreB": 9
}
```

This allows the admin frontend to update immediately without making another GET request.

---

# 14. Error Handling

Use the project's existing error-handling conventions.

At minimum:

| Situation | Status |
|---|---:|
| invalid game ID | `400 BAD_REQUEST` |
| game not found | `404 NOT_FOUND` |
| game not started | `409 CONFLICT` |
| decrement would make score negative | `409 CONFLICT` |
| valid operation | `200 OK` |

Conflict responses should contain a clear error message.

Examples:

```text
Game 15 has not been started.
```

```text
Team A score cannot be decreased below 0.
```

```text
Team B score cannot be decreased below 0.
```

---

# 15. Realtime WebSocket Architecture

The public WebSocket remains:

```text
WS /ws/live
```

and must be exposed on port `8080`.

Admin operations happen on port `8081`.

After a successful admin database change, notify connected public clients.

Required flow:

```text
Admin request :8081
       ↓
Admin Controller
       ↓
Service
       ↓
MariaDB transaction
       ↓
COMMIT
       ↓
publish application event
       ↓
WebSocket notifier
       ↓
WS /ws/live :8080
       ↓
Public frontend
```

Do not poll MariaDB to detect changes.

The backend already knows about the change because the admin operation performed it.

---

# 16. Transaction Requirement

All admin writes must be transactional:

```text
POST
PUT
DELETE
```

and all game score operations:

```text
start
increment
decrement
```

must be transactional.

Realtime events must be sent only after the transaction has committed successfully.

Correct:

```text
Database change
    ↓
COMMIT
    ↓
publish event
    ↓
WebSocket
```

Incorrect:

```text
Database change
    ↓
WebSocket
    ↓
transaction fails / rollback
```

If the transaction rolls back, no realtime notification must be sent.

---

# 17. Realtime Event

Every successful admin change that affects public tournament data must produce the appropriate realtime event.

At minimum, game changes from:

```text
start
team-a increment
team-a decrement
team-b increment
team-b decrement
```

must generate a game-changed event.

Recommended event:

```json
{
  "type": "GAME_CHANGED",
  "gameId": 15
}
```

The public frontend can then fetch or otherwise update the changed data.

The exact event structure should follow the existing WebSocket implementation if one already exists. Do not unnecessarily replace an existing event contract.

---

# 18. Concurrency

Score changes can happen very quickly or concurrently.

The implementation must prevent lost updates.

Example:

```text
Initial scoreA = 10

Request A: increment
Request B: increment
```

Expected final result:

```text
scoreA = 12
```

It must not accidentally end at:

```text
scoreA = 11
```

because both requests read the same old value before saving.

Use an appropriate strategy compatible with Spring Data JPA/Hibernate and MariaDB, such as locking or an atomic database update.

Document the chosen approach.

---

# 19. Swagger / OpenAPI

The project currently uses Springdoc.

Review the existing Swagger/OpenAPI setup.

The goal is to logically document the two APIs separately.

Prefer:

```text
Public API
    → port 8080

Admin API
    → port 8081
```

Admin endpoints should not appear as public endpoints in the public API documentation when the chosen architecture permits this cleanly.

Do not let Swagger configuration weaken the port separation.

---

# 20. Automated Tests

Add automated tests that prove both behavior and port separation.

## Public API tests

Verify on `8080`:

```text
GET /api/groups              -> available
GET /api/groups/{id}         -> available
GET /api/teams               -> available
GET /api/teams/{id}          -> available
GET /api/rounds              -> available
GET /api/fields              -> available
GET /api/games               -> available
GET /api/games/{id}          -> available
GET /api/games/filter/{id}   -> available
```

Verify that admin endpoints are not exposed on `8080`.

For example:

```text
POST /api/admin/groups       -> rejected
POST /api/admin/teams        -> rejected
POST /api/admin/games        -> rejected
POST /api/admin/fields       -> rejected
POST /api/admin/rounds       -> rejected

POST /api/games/{id}/start   -> rejected
POST /api/games/{id}/score/... -> rejected
```

Also verify:

```text
WS /ws/live -> available on 8080
```

---

## Admin API tests

Verify on `8081`:

```text
POST   /api/admin/groups
PUT    /api/admin/groups/{id}
DELETE /api/admin/groups/{id}

POST   /api/admin/teams
PUT    /api/admin/teams/{id}
DELETE /api/admin/teams/{id}

POST   /api/admin/games
PUT    /api/admin/games/{id}
DELETE /api/admin/games/{id}

POST   /api/admin/fields
PUT    /api/admin/fields/{id}
DELETE /api/admin/fields/{id}

POST   /api/admin/rounds
PUT    /api/admin/rounds/{id}
DELETE /api/admin/rounds/{id}
```

Verify scoring:

```text
POST /api/games/{id}/start
POST /api/games/{id}/score/team-a/increment
POST /api/games/{id}/score/team-a/decrement
POST /api/games/{id}/score/team-b/increment
POST /api/games/{id}/score/team-b/decrement
```

Verify that public GET routes are not exposed on `8081`.

---

# 21. Realtime Tests

Verify that a successful admin operation produces the expected event.

Example:

```text
POST :8081/api/games/15/start
        ↓
database updated
        ↓
transaction committed
        ↓
GameChangedEvent
        ↓
public WebSocket :8080 receives event
```

Repeat for:

```text
POST :8081/api/games/15/score/team-a/increment
POST :8081/api/games/15/score/team-a/decrement
POST :8081/api/games/15/score/team-b/increment
POST :8081/api/games/15/score/team-b/decrement
```

Also verify failed operations produce no realtime event.

Example:

```text
scoreA = 0
    ↓
POST team-a/decrement
    ↓
409 CONFLICT
    ↓
scoreA stays 0
    ↓
no realtime event
```

---

# 22. Controller and Package Refactoring

Inspect the existing code before changing it.

Identify:

- public controllers
- teacher/admin controllers
- existing services
- repositories
- WebSocket configuration
- application event infrastructure
- Spring Boot server configuration
- Swagger configuration

Then refactor only what is necessary.

The public side should remain clearly read-only.

The admin side should contain all write and score-control operations.

Do not duplicate domain/data logic.

---

# 23. Critical Implementation Requirement: Two Ports in One Application

The most important technical requirement is:

```text
ONE Spring Boot process/application
    ├── HTTP port 8080 → public controllers + public WebSocket
    └── HTTP port 8081 → admin controllers
```

Do not solve this by simply using:

```text
server.port=8080
```

and adding `/api/admin/...`.

That would still expose the admin controllers through `8080`.

The implementation must genuinely distinguish the incoming request by port.

Use a robust Spring-compatible approach that can support multiple connectors/ports in one application.

If the existing application architecture makes this non-trivial, inspect the project first and choose the smallest maintainable solution.

Do not introduce a second application.

Do not introduce a second project/module.

Do not create duplicate business logic.

---

# 24. Definition of Done

The implementation is complete only when all of the following are true:

1. There is still exactly one Spring Boot application.
2. There is still one project/module.
3. There is still one MariaDB database.
4. Port `8080` exposes only the public/student API and `/ws/live`.
5. Port `8081` exposes only the admin API.
6. Admin CRUD URLs use `/api/admin/...`.
7. No new `/api/teacher/...` API paths exist.
8. Game scoring remains under `/api/games/...`.
9. Game scoring is available only through port `8081`.
10. Public GET endpoints are not available through port `8081`.
11. Admin endpoints are not available through port `8080`.
12. Existing public GET endpoint behavior remains intact.
13. Nullable scores are handled correctly.
14. Starting a game changes `NULL / NULL` to `0 / 0`.
15. Starting an already-started game does not reset its score.
16. Score increment/decrement changes exactly one point.
17. Scores can never become negative.
18. Scoring an unstarted game returns `409 CONFLICT`.
19. Successful write operations return the updated `GameDto`.
20. All writes are transactional.
21. Realtime events are published only after successful transaction commit.
22. Failed operations do not generate realtime events.
23. Concurrent score updates do not lose increments/decrements.
24. Automated tests prove both endpoint behavior and port isolation.
25. Existing backend tests pass without regressions.

---

# 25. Implementation Order

Follow this order:

1. Inspect the current project structure and existing controllers.
2. Inspect the existing Spring Boot HTTP/WebSocket configuration.
3. Inspect the existing test setup.
4. Identify the cleanest single-application/two-port architecture.
5. Separate public and admin controller groups.
6. Rename teacher-specific controller/package names to admin where appropriate.
7. Change admin CRUD paths to `/api/admin/...`.
8. Keep scoring paths exactly as `/api/games/...`.
9. Configure port `8080` for public API and WebSocket.
10. Configure port `8081` for admin API.
11. Ensure cross-port route isolation.
12. Verify nullable score handling.
13. Verify game start and scoring behavior.
14. Verify transaction-aware realtime events.
15. Add/adjust unit and integration tests.
16. Test both ports explicitly.
17. Run the complete existing test suite.
18. Fix regressions without changing unrelated functionality.

---

# Final Target

The final backend must behave like this:

```text
http://localhost:8080
    PUBLIC / STUDENT API

    GET /
    GET /api/groups
    GET /api/groups/{id}
    GET /api/teams
    GET /api/teams/{id}
    GET /api/rounds
    GET /api/fields
    GET /api/games
    GET /api/games/filter/{filter}
    GET /api/games/{id}

    WS /ws/live
```

and:

```text
http://localhost:8081
    ADMIN API

    POST   /api/admin/groups
    PUT    /api/admin/groups/{id}
    DELETE /api/admin/groups/{id}

    POST   /api/admin/teams
    PUT    /api/admin/teams/{id}
    DELETE /api/admin/teams/{id}

    POST   /api/admin/games
    PUT    /api/admin/games/{id}
    DELETE /api/admin/games/{id}

    POST   /api/admin/fields
    PUT    /api/admin/fields/{id}
    DELETE /api/admin/fields/{id}

    POST   /api/admin/rounds
    PUT    /api/admin/rounds/{id}
    DELETE /api/admin/rounds/{id}

    POST /api/games/{id}/start

    POST /api/games/{id}/score/team-a/increment
    POST /api/games/{id}/score/team-a/decrement

    POST /api/games/{id}/score/team-b/increment
    POST /api/games/{id}/score/team-b/decrement
```

This is one backend application with two clearly separated API surfaces, not two separate backend applications.
