# Volleyball Tournament Website — Backend Specification

## 1. Purpose

This document is the implementation specification for the backend of the public Volleyball Tournament Website.

The backend is a Kotlin + Spring Boot application connected to MariaDB. Its primary responsibility is to expose tournament data through REST endpoints and to notify connected frontend clients when tournament data changes.

The public website is read-only from the browser's point of view. Data is changed by the teacher-management part of the same Spring Boot application.

## 2. Updated architectural decision

The original project documentation describes the public application as read-only against an externally managed database and specifies a WebSocket interface for automatic updates.

This specification extends that design: the same Spring Boot application also receives teacher/admin write requests and is therefore responsible for database changes.

Important consequence:

- Do NOT poll MariaDB to detect changes.
- Do NOT add database triggers, binlog CDC, Debezium, or a second change-detection service for the first implementation.
- A successful write operation in the Spring Boot service layer creates an application event.
- The WebSocket notification is sent only after the database transaction commits successfully.
- REST remains the source of truth for reading the current data.

This design is appropriate because all intended database writes are controlled by this application.

## 3. Main goal

The public frontend must be able to:

1. Load the current tournament data using REST.
2. Open a WebSocket connection at `/ws/live`.
3. Wait for change notifications instead of polling.
4. Receive a small change event when a teacher creates, updates, or deletes tournament data.
5. Refresh the affected data through REST when necessary.
6. Continue to work correctly after a temporary WebSocket disconnect.

## 4. Database model used by this backend

The database contains these entities:

- `rounds`
- `groups`
- `teams`
- `fields`
- `games`

Important relationships:

- one group -> many teams
- one round -> many games
- one field -> many games
- one game -> team A, team B, referee team

`games.points_a` and `games.points_b` must be >= 0.

The database currently uses foreign keys with `ON DELETE RESTRICT`. The backend must return a meaningful conflict response when an entity cannot be deleted because another entity still references it.

## 5. Existing REST contract

Keep the following public endpoints compatible with the current frontend contract unless a coordinated API change is explicitly required.

### Groups

`GET /api/groups`

Returns all groups as:

```json
[
  {
    "groupId": 1,
    "name": "A"
  }
]
```

`GET /api/groups/{id}`

Returns one group. Non-numeric or missing IDs return `404 Not Found` according to the current contract.

### Teams

`GET /api/teams`

Returns:

```json
[
  {
    "teamId": 1,
    "class": "U18 Boys",
    "name": "St. Maria School",
    "groupId": 1
  }
]
```

`GET /api/teams/{id}`

Returns one team.

### Rounds

`GET /api/rounds`

Returns all rounds:

```json
[
  {
    "roundId": 1,
    "number": 1
  }
]
```

### Fields

`GET /api/fields`

Returns all courts/fields:

```json
[
  {
    "fieldId": 1,
    "name": "Court 1"
  }
]
```

### Games

`GET /api/games`

Returns all games:

```json
[
  {
    "gameId": 1,
    "roundId": 1,
    "fieldId": 1,
    "teamAId": 1,
    "teamBId": 2,
    "refereeTeamId": 3,
    "scoreA": 25,
    "scoreB": 21
  }
]
```

`GET /api/games/{id}`

Returns one game.

`GET /api/games/filter/{filter}`

Current meaning: filter by round ID represented as a string.

Required current behavior:

- blank/invalid filter -> `400 Bad Request` where already defined by the existing contract
- non-numeric values currently return an empty list

Do not change this behavior without coordinating with the frontend.

## 6. WebSocket contract

Endpoint:

`WS /ws/live`

The server broadcasts a JSON change notification to currently connected public clients.

Use a lightweight event. The event is a notification, not a replacement for REST.

### Event format

```json
{
  "type": "TOURNAMENT_DATA_CHANGED",
  "entity": "GAME",
  "operation": "UPDATE",
  "entityId": 17
}
```

Allowed `entity` values:

- `GROUP`
- `TEAM`
- `ROUND`
- `FIELD`
- `GAME`

Allowed `operation` values:

- `CREATE`
- `UPDATE`
- `DELETE`

Do not send the full database entity in the first implementation.

Reason: REST remains the authoritative representation and the event remains stable even if DTOs change.

### Frontend handling rule

On a valid event:

- `GAME` change -> refresh the relevant game data
- `TEAM`/`GROUP`/`ROUND`/`FIELD` change -> refresh the views that depend on that data
- `DELETE` -> remove the deleted object from local state or reload the affected collection

The backend does not need to know which frontend page is currently displayed.

## 7. Backend event flow

Every teacher write follows this sequence:

```text
HTTP request
   -> controller
   -> validation
   -> service @Transactional
   -> repository write
   -> publish application event
   -> transaction commit
   -> @TransactionalEventListener(AFTER_COMMIT)
   -> WebSocket broadcaster
   -> connected clients
```

The crucial rule is:

> Never send the WebSocket event before the database transaction has successfully committed.

If the transaction rolls back, no realtime notification is sent.

## 8. Recommended code structure

Keep one Spring Boot application and separate responsibilities through packages/modules inside it.

Suggested structure:

```text
src/main/kotlin/.../
├── group/
│   ├── GroupController.kt
│   ├── GroupService.kt
│   ├── GroupRepository.kt
│   ├── GroupEntity.kt
│   └── GroupDto.kt
├── team/
│   ├── TeamController.kt
│   ├── TeamService.kt
│   └── ...
├── round/
│   ├── RoundController.kt
│   ├── RoundService.kt
│   └── ...
├── field/
│   ├── FieldController.kt
│   ├── FieldService.kt
│   └── ...
├── game/
│   ├── GameController.kt
│   ├── GameService.kt
│   └── ...
├── teacher/
│   ├── TeacherGroupController.kt
│   ├── TeacherTeamController.kt
│   ├── TeacherRoundController.kt
│   ├── TeacherFieldController.kt
│   ├── TeacherGameController.kt
│   └── ...
└── realtime/
    ├── TournamentChangeEvent.kt
    ├── TournamentChangeEventListener.kt
    ├── WebSocketConfig.kt
    └── WebSocketBroadcaster.kt
```

Avoid duplicating database/business logic between public controllers and teacher controllers.

The teacher controller should call the same domain/service layer that owns the transaction and event publication.

## 9. Teacher writes and realtime notifications

Teacher CRUD endpoints are specified in the separate teacher-management document.

For every successful create/update/delete:

1. The service writes to MariaDB.
2. The service publishes `TournamentChangeEvent` inside the transaction.
3. Spring invokes the listener after commit.
4. The listener broadcasts the event to all connected WebSocket clients.

Example event object:

```kotlin
data class TournamentChangeEvent(
    val entity: EntityType,
    val operation: OperationType,
    val entityId: Long
)
```

## 10. Error handling

Use consistent HTTP responses:

- `200 OK` for successful reads/updates where a response body is returned
- `201 Created` for successful creates
- `204 No Content` for successful deletes where no body is returned
- `400 Bad Request` for invalid request data
- `404 Not Found` for non-existing IDs
- `409 Conflict` for uniqueness violations or foreign-key deletion conflicts
- `500 Internal Server Error` only for unexpected server errors

Do not expose SQL exceptions directly to the frontend.

## 11. Validation rules

At minimum validate:

### Group

- designation is not blank
- designation length is within database limit (`VARCHAR(10)`)
- designation is unique

### Team

- name is not blank
- team class is not blank
- group ID exists

### Round

- round number is > 0
- round number is unique

### Field

- name is not blank
- name is unique

### Game

- round exists
- field exists
- team A exists
- team B exists
- referee team exists
- points A >= 0
- points B >= 0
- team A != team B
- referee team != team A
- referee team != team B

The last three rules should be enforced by the service even though the current database schema does not explicitly contain these checks.

## 12. WebSocket connection behavior

On connection:

- accept the connection
- register the session
- do not send a fake change event

On disconnect:

- unregister the session
- do not fail the application

On malformed client messages:

- since the public client is only a listener, the server may ignore inbound messages or close the connection
- the backend does not need a client-to-server command protocol for the first version

## 13. No polling requirement

There must be no scheduled task that repeatedly asks MariaDB whether a row changed.

There must be no implementation such as:

```text
while true:
    SELECT ...
    compare previous result
    sleep
```

The application already knows when a teacher write occurs, so application events are the change signal.

## 14. Reconnection behavior

The WebSocket implementation must tolerate clients disconnecting and reconnecting.

The backend only guarantees delivery to clients that are connected at broadcast time.

The frontend should perform a normal REST reload after reconnecting to make sure no events were missed.

The backend does not need persistent event storage for the first version.

## 15. Test tooling and test data

Use Kotlin/JUnit 5. Use MockMvc (or the existing Spring Boot HTTP test facility) for REST contract tests. Use Spring WebSocket test support for `/ws/live`. For database integration tests, prefer Testcontainers with MariaDB so foreign-key, unique-constraint, and transaction behavior is tested against the same database engine used by the application. Seed the integration-test database with the supplied tournament SQL fixture.

## 15. Automated tests

Use a combination of unit tests and integration tests.

### Test class: GroupControllerTest

1. `GET /api/groups returns all groups`
   - Given groups A-D exist.
   - When GET `/api/groups` is called.
   - Then response is `200`.
   - Then all groups are returned with correct IDs and names.

2. `GET /api/groups/{id} returns existing group`
   - Then `200` and correct DTO.

3. `GET /api/groups/{id} returns 404 for missing id`
   - Then `404`.

### Test class: TeamControllerTest

1. List teams returns `200` and expected DTO fields.
2. Existing team by ID returns `200`.
3. Missing team by ID returns `404`.

### Test class: RoundControllerTest

1. `GET /api/rounds` returns all rounds in the expected DTO format.

### Test class: FieldControllerTest

1. `GET /api/fields` returns all fields in the expected DTO format.

### Test class: GameControllerTest

1. List games returns `200` and correct score/team/reference IDs.
2. Existing game by ID returns `200`.
3. Missing game by ID returns `404`.
4. Round filter with valid numeric ID returns matching games only.
5. Invalid/blank filter follows the existing `400` behavior.
6. Non-numeric filter follows the current empty-list behavior.

### Test class: RealtimeWebSocketIntegrationTest

1. `websocket connection can be established`
   - Connect to `/ws/live`.
   - Expect successful handshake.

2. `successful teacher update broadcasts one update event`
   - Connect client A.
   - Update one game through the teacher service/API.
   - Wait for one event.
   - Assert type, entity, operation, and ID.

3. `successful teacher create broadcasts create event`
   - Create a record.
   - Assert `CREATE` event.

4. `successful teacher delete broadcasts delete event`
   - Delete a record.
   - Assert `DELETE` event.

5. `failed transaction does not broadcast event`
   - Trigger a validation/database error.
   - Assert no WebSocket event is sent.
   - Assert database state is unchanged.

6. `multiple clients receive the same event`
   - Connect clients A and B.
   - Execute one successful write.
   - Assert both clients receive the corresponding event.

7. `disconnecting one client does not affect other clients`
   - Connect A and B.
   - Disconnect A.
   - Perform a write.
   - Assert B receives the event and the application remains healthy.

8. `reconnecting client can connect again`
   - Connect, disconnect, reconnect.
   - Assert the second connection succeeds.

9. `no polling task is required`
   - Test by changing data only through the teacher write path.
   - The event must be produced by the write flow, not a scheduled database check.

### Test class: TransactionEventTest

1. `event is published after commit`
2. `event is not broadcast on rollback`
3. `one successful write produces one event`

## 16. Acceptance criteria

The public backend is complete when all of the following are true:

- Existing GET endpoints remain compatible with the current frontend.
- `/ws/live` accepts WebSocket connections.
- Teacher CRUD operations can modify the tournament data.
- Every successful teacher write causes exactly one corresponding realtime event.
- Failed or rolled-back writes produce no realtime event.
- Multiple connected clients receive the event.
- No database polling/change-detection loop exists.
- REST remains the authoritative way to retrieve current data.
- Automated tests cover REST behavior, validation, database constraints, WebSocket delivery, and transaction rollback.

## 17. Implementation order for OpenCode

Implement in this order and do not skip tests:

1. Preserve and test all existing public GET endpoints.
2. Refactor shared domain/service logic so teacher writes are handled by services rather than controllers.
3. Implement teacher CRUD endpoints from the teacher specification.
4. Add service-level validation and consistent error handling.
5. Add `TournamentChangeEvent`.
6. Publish the event from each successful teacher write inside the transaction.
7. Implement `@TransactionalEventListener(AFTER_COMMIT)`.
8. Implement `/ws/live` connection management and broadcasting.
9. Add the WebSocket integration tests.
10. Run the complete backend test suite.
11. Only after all tests pass, connect the React frontend to the WebSocket event contract.

## 18. Explicit non-goals for this iteration

Do not implement:

- separate microservice deployment for teacher management
- Kafka/RabbitMQ
- Debezium/CDC
- database polling
- persistent WebSocket event history
- WebSocket commands that modify database data
- full authentication/authorization redesign unless the project already requires it

The first version should remain a single Spring Boot application with clear internal separation of public read functionality, teacher write functionality, and realtime notification functionality.
