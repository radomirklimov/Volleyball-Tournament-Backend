# Game Scoring Endpoints – OpenCode Specification

> **SUPERSEDED (score increment/decrement removed):** the four
> `POST /api/games/{id}/score/…` endpoints described in this document no
> longer exist (they return `404`). Scores are changed exclusively via
> `PUT /api/admin/games/{id}` with the complete game object. Only
> `POST /api/games/{id}/start` remains, as a read-only no-op returning the
> game unchanged. This document is kept as a historical record.

## 1. Purpose

Implement backend endpoints that allow a teacher to control the score of an individual volleyball game with simple button actions.

The database fields `games.points_a` and `games.points_b` are nullable.

A `NULL` score means that the game has not been started yet.

A started game always has numeric scores, initially `0 : 0`.

The scoring endpoints must integrate with the existing realtime update mechanism:

```text
Teacher request
    ↓
Spring Boot
    ↓
MariaDB transaction
    ↓
transaction committed successfully
    ↓
GameChangedEvent
    ↓
WebSocket /ws/live
    ↓
connected public frontend clients
```

Do not poll MariaDB to detect these changes. The backend already knows that a change occurred because the backend performed the write operation.

---

## 2. Database State

The `games` table contains:

```sql
points_a INTEGER NULL,
points_b INTEGER NULL
```

Interpret the values as follows:

| `points_a` / `points_b` | Meaning |
|---|---|
| `NULL` | Game has not been started |
| `0` | Game has started and the team has zero points |
| `1` or greater | Current score |

A game should normally transition from:

```text
NULL / NULL
```

to:

```text
0 / 0
```

when it is started.

---

# 3. Endpoint: Start Game

## HTTP

```http
POST /api/games/{id}/start
```

## Purpose

Start an existing game.

Starting a game changes both nullable score fields to `0`.

### Example

Before:

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

After:

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

## Rules

1. The game ID must be numeric.
2. If the game does not exist, return `404 NOT_FOUND`.
3. If the game exists and either score is `NULL`, set both scores to `0`.
4. If the game is already started, the endpoint must be idempotent: keep both scores unchanged.
5. Examples:
   - `NULL / NULL` → `0 / 0`
   - `3 / 2` → `3 / 2`
   - `0 / 0` → `0 / 0`
6. Persist the change in a transaction.
7. After a successful transaction commit, publish a game-changed realtime event when the game state actually changes.
8. Return the updated `GameDto`.

Starting an already running game must **not** reset the score.

---

# 4. Endpoint: Increment Team A

## HTTP

```http
POST /api/games/{id}/score/team-a/increment
```

## Purpose

Increase Team A's score by exactly `1`.

### Example

```text
0 → 1
1 → 2
2 → 3
```

## Rules

1. The game ID must be numeric.
2. If the game does not exist, return `404 NOT_FOUND`.
3. The game must already be started.
4. If `scoreA == NULL`, return `409 CONFLICT`.
5. Increase only `scoreA`.
6. Do not change `scoreB`.
7. Increase the value by exactly `1`.
8. Persist the change in a transaction.
9. After successful commit, publish a realtime game-changed event.
10. Return the updated `GameDto`.

---

# 5. Endpoint: Decrement Team A

## HTTP

```http
POST /api/games/{id}/score/team-a/decrement
```

## Purpose

Decrease Team A's score by exactly `1`.

## Rules

1. The game ID must be numeric.
2. If the game does not exist, return `404 NOT_FOUND`.
3. The game must already be started.
4. If `scoreA == NULL`, return `409 CONFLICT`.
5. If `scoreA == 0`, return `409 CONFLICT`.
6. Modify only Team A's score.
7. The score must never become negative.
8. Persist the change in a transaction.
9. After successful commit, publish a realtime game-changed event.
10. Return the updated `GameDto`.

---

# 6. Endpoint: Increment Team B

## HTTP

```http
POST /api/games/{id}/score/team-b/increment
```

## Purpose

Increase Team B's score by exactly `1`.

## Rules

Apply the same rules as Team A increment, but modify only `scoreB`.

If `scoreB == NULL`, return `409 CONFLICT`.

---

# 7. Endpoint: Decrement Team B

## HTTP

```http
POST /api/games/{id}/score/team-b/decrement
```

## Purpose

Decrease Team B's score by exactly `1`.

## Rules

Apply the same rules as Team A decrement, but modify only `scoreB`.

The score must never become negative.

If `scoreB == NULL`, return `409 CONFLICT`.

If `scoreB == 0`, return `409 CONFLICT`.

---

# 8. Complete Endpoint List

```http
POST /api/games/{id}/start
POST /api/games/{id}/score/team-a/increment
POST /api/games/{id}/score/team-a/decrement
POST /api/games/{id}/score/team-b/increment
POST /api/games/{id}/score/team-b/decrement
```

Do not introduce request bodies for these operations. The action is completely described by the endpoint.

---

# 9. Response Contract

All successful scoring operations should return the updated `GameDto`.

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

The nullable state must also be represented correctly. Do not convert `NULL` to `0` in DTO mapping.

---

# 10. Error Handling

Use the existing API error-handling conventions of the project.

| Situation | HTTP status |
|---|---:|
| Game ID is invalid | `400 BAD_REQUEST` |
| Game does not exist | `404 NOT_FOUND` |
| Game has not been started | `409 CONFLICT` |
| Decrement would make score negative | `409 CONFLICT` |
| Valid operation | `200 OK` |

Conflict responses should include a clear error message, for example:

```text
Game 15 has not been started.
```

```text
Team A score cannot be decreased below 0.
```

---

# 11. Transaction and Realtime Requirements

Every successful score modification must be executed inside a transaction.

The application must publish the realtime event **only after the transaction has committed successfully**.

Required behavior:

```text
HTTP request
    ↓
validate game
    ↓
modify entity
    ↓
save/update database
    ↓
commit transaction
    ↓
publish GameChangedEvent
    ↓
WebSocket notifier broadcasts event
```

Do not broadcast before the transaction commits.

If the database transaction fails or is rolled back, no WebSocket event must be sent.

---

# 12. Realtime Event

Every successful state-changing operation is a game change:

- game started
- Team A incremented
- Team A decremented
- Team B incremented
- Team B decremented

Every successful change must therefore result in a realtime event.

Recommended event payload:

```json
{
  "type": "GAME_CHANGED",
  "gameId": 15
}
```

The public frontend can use the event to update its state or retrieve the latest game data.

The scoring endpoints themselves return the updated `GameDto`, so the teacher frontend does not need another GET request after clicking a score button.

---

# 13. Concurrency Requirement

Score operations can be triggered by multiple users or very quickly in succession.

Do not implement the operation as an unsafe read-modify-write sequence that can lose updates.

The implementation must ensure that concurrent increments do not accidentally overwrite each other.

Example:

```text
Initial scoreA = 10

Two valid Team A increment requests occur concurrently.

Required final result:
scoreA = 12
```

not:

```text
scoreA = 11
```

Choose an implementation compatible with Spring Data JPA/Hibernate and MariaDB, such as an appropriate pessimistic lock or atomic update strategy, and document the chosen strategy in the implementation.

---

# 14. Suggested Service API

Keep controller logic thin. The controller should delegate to a game service.

Conceptually:

```kotlin
fun startGame(gameId: Long): GameDto

fun incrementTeamAScore(gameId: Long): GameDto

fun decrementTeamAScore(gameId: Long): GameDto

fun incrementTeamBScore(gameId: Long): GameDto

fun decrementTeamBScore(gameId: Long): GameDto
```

The service is responsible for:

- loading/locking the game as required
- validating the current state
- changing the score
- saving the entity
- publishing the application event
- returning the updated DTO

---

# 15. Tests

Implement automated backend tests for all new behavior.

## Start game

### Test 1 – start unstarted game

Given:

```text
scoreA = NULL
scoreB = NULL
```

When:

```http
POST /api/games/{id}/start
```

Then:

```text
HTTP 200
scoreA = 0
scoreB = 0
```

### Test 2 – start already started game

Given:

```text
scoreA = 12
scoreB = 8
```

When the start endpoint is called:

Then:

```text
HTTP 200
scoreA = 12
scoreB = 8
```

### Test 3 – start nonexistent game

Expected:

```text
HTTP 404
```

and no database change.

---

## Team A increment

### Test 4 – increment Team A

Given:

```text
scoreA = 5
scoreB = 3
```

Expected:

```text
scoreA = 6
scoreB = 3
HTTP 200
```

### Test 5 – increment Team A on unstarted game

Given:

```text
scoreA = NULL
scoreB = NULL
```

Expected:

```text
HTTP 409
```

and the values remain `NULL`.

### Test 6 – increment does not modify Team B

Verify that Team B's score is unchanged.

---

## Team A decrement

### Test 7 – decrement Team A

Given:

```text
scoreA = 5
scoreB = 3
```

Expected:

```text
scoreA = 4
scoreB = 3
HTTP 200
```

### Test 8 – decrement Team A from zero

Given:

```text
scoreA = 0
```

Expected:

```text
HTTP 409
```

and score remains `0`.

### Test 9 – decrement Team A when game has not started

Expected:

```text
HTTP 409
```

---

## Team B increment/decrement

Create equivalent tests for Team B:

- increment from a positive score
- increment on unstarted game
- decrement from a positive score
- decrement from zero
- decrement on unstarted game
- verify Team A is unchanged

---

# 16. Realtime Tests

Verify that every successful scoring operation produces exactly one game-changed event.

Test at least:

```text
start
team-a increment
team-a decrement
team-b increment
team-b decrement
```

Verify:

```text
successful DB change
        ↓
one GameChangedEvent
        ↓
WebSocket notification
```

Also verify:

```text
failed request
        ↓
no database modification
        ↓
no GameChangedEvent
```

Example:

```text
scoreA = 0
POST team-a/decrement
        ↓
409 CONFLICT
        ↓
scoreA remains 0
        ↓
no realtime event
```

---

# 17. Acceptance Criteria

The implementation is complete when all of the following are true:

- `POST /api/games/{id}/start` exists and starts an unstarted game with `0 : 0`.
- Starting an already started game does not reset its score.
- Team A can be incremented by one click/request.
- Team A can be decremented by one click/request.
- Team B can be incremented by one click/request.
- Team B can be decremented by one click/request.
- Scores can never become negative.
- Score operations on unstarted games are rejected with `409 CONFLICT`.
- Nonexistent games return `404 NOT_FOUND`.
- Successful operations return the updated `GameDto`.
- `NULL` scores remain `NULL` in API responses until the game is started.
- Every successful modification is transactional.
- Realtime notifications are sent only after successful transaction commit.
- Failed operations do not generate realtime notifications.
- Concurrent score updates are handled without lost updates.
- Automated tests cover all endpoints and important error cases.
- Existing GET endpoints continue to work without regression.

---

# 18. Implementation Order for OpenCode

Implement the feature in this order:

1. Update the `Game` entity/model so `points_a` and `points_b` support `NULL`.
2. Update DTO mapping so nullable scores are preserved.
3. Add the game-start service operation.
4. Add the five controller endpoints.
5. Implement validation and conflict handling.
6. Implement transactional score updates.
7. Implement the concurrency-safe update strategy.
8. Connect the operations to the existing application-event/realtime mechanism.
9. Add service/controller/repository tests.
10. Add realtime event tests.
11. Run the complete existing backend test suite and verify no regression.

Do not modify unrelated existing endpoints unless required to support nullable scores or the new scoring functionality.
