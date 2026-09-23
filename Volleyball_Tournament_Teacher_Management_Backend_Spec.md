# Volleyball Tournament — Teacher Management Backend Specification

## 1. Purpose

This document specifies the backend functionality used by teachers to create, update, and delete tournament data.

The teacher-management functionality is NOT a separate deployable microservice for the first version. It is a separated module/package inside the existing Kotlin + Spring Boot application that already provides the public tournament REST API and WebSocket connection.

There is one application and one MariaDB database.

## 2. Scope

Teachers must be able to manage these database entities:

- groups
- teams
- rounds
- fields/courts
- games

The teacher frontend is only a client. It must communicate with this backend through HTTP REST endpoints.

The backend owns all write operations and is responsible for validation, persistence, error handling, and realtime change notifications.

## 3. Architectural rule

Use this structure:

```text
Teacher UI
   |
   | REST
   v
Teacher Controller
   |
   v
Domain Service (@Transactional)
   |
   +--> Repository --> MariaDB
   |
   +--> publish TournamentChangeEvent
                 |
                 v
        AFTER_COMMIT listener
                 |
                 v
           WebSocket broadcast
```

Do not let the teacher UI access MariaDB directly.

Do not duplicate persistence logic in controllers.

Do not create a second Spring Boot application for teacher management.

## 4. API namespace

Place teacher write endpoints below:

`/api/teacher/...`

This makes the distinction between public read APIs and teacher write APIs explicit.

Existing public GET APIs remain unchanged.

## 5. Group management

### Create

`POST /api/teacher/groups`

Request:

```json
{
  "name": "A"
}
```

Behavior:

- validate non-blank name
- validate max length 10
- reject duplicate designation
- create the group
- return `201 Created`
- return the created DTO

Success example:

```json
{
  "groupId": 5,
  "name": "A"
}
```

Broadcast after commit:

```json
{
  "type": "TOURNAMENT_DATA_CHANGED",
  "entity": "GROUP",
  "operation": "CREATE",
  "entityId": 5
}
```

### Update

`PUT /api/teacher/groups/{id}`

Request:

```json
{
  "name": "B"
}
```

Rules:

- missing ID -> `404`
- invalid name -> `400`
- duplicate name -> `409`
- successful update -> `200`

Broadcast `GROUP / UPDATE` after commit.

### Delete

`DELETE /api/teacher/groups/{id}`

Rules:

- missing ID -> `404`
- group referenced by teams -> `409`
- success -> `204`

Broadcast `GROUP / DELETE` after commit.

## 6. Team management

### Create

`POST /api/teacher/teams`

Request:

```json
{
  "groupId": 1,
  "class": "U18 Boys",
  "name": "New School"
}
```

Rules:

- group must exist
- class must not be blank
- name must not be blank
- database length constraints must be respected
- success -> `201`

Broadcast `TEAM / CREATE` after commit.

### Update

`PUT /api/teacher/teams/{id}`

Request contains `groupId`, `class`, and `name`.

Rules:

- team must exist
- target group must exist
- fields must be valid
- success -> `200`

Broadcast `TEAM / UPDATE` after commit.

### Delete

`DELETE /api/teacher/teams/{id}`

Rules:

- missing team -> `404`
- if the team is referenced by a game as team A, team B, or referee -> `409`
- success -> `204`

Broadcast `TEAM / DELETE` after commit.

## 7. Round management

### Create

`POST /api/teacher/rounds`

Request:

```json
{
  "number": 6
}
```

Rules:

- number > 0
- round number must be unique
- success -> `201`

Broadcast `ROUND / CREATE` after commit.

### Update

`PUT /api/teacher/rounds/{id}`

Rules:

- round must exist
- number > 0
- number must remain unique
- success -> `200`

Broadcast `ROUND / UPDATE` after commit.

### Delete

`DELETE /api/teacher/rounds/{id}`

Rules:

- missing round -> `404`
- if games reference the round -> `409`
- success -> `204`

Broadcast `ROUND / DELETE` after commit.

## 8. Field/court management

### Create

`POST /api/teacher/fields`

Request:

```json
{
  "name": "Court 5"
}
```

Rules:

- non-blank
- unique
- success -> `201`

Broadcast `FIELD / CREATE` after commit.

### Update

`PUT /api/teacher/fields/{id}`

Request:

```json
{
  "name": "Court A"
}
```

Rules:

- field exists
- non-blank
- unique
- success -> `200`

Broadcast `FIELD / UPDATE` after commit.

### Delete

`DELETE /api/teacher/fields/{id}`

Rules:

- missing field -> `404`
- if games reference the field -> `409`
- success -> `204`

Broadcast `FIELD / DELETE` after commit.

## 9. Game management

Games are the most important write target because score updates are expected during the tournament.

### Create

`POST /api/teacher/games`

Request:

```json
{
  "roundId": 1,
  "fieldId": 1,
  "teamAId": 1,
  "teamBId": 2,
  "refereeTeamId": 3,
  "scoreA": 0,
  "scoreB": 0
}
```

Rules:

- round exists
- field exists
- team A exists
- team B exists
- referee team exists
- scoreA >= 0
- scoreB >= 0
- team A != team B
- referee team != team A
- referee team != team B
- success -> `201`

Broadcast `GAME / CREATE` after commit.

### Update

`PUT /api/teacher/games/{id}`

Request contains all editable game fields.

At minimum, the update endpoint must support score changes without requiring deletion/recreation of the game.

Example score update:

```json
{
  "roundId": 1,
  "fieldId": 1,
  "teamAId": 1,
  "teamBId": 2,
  "refereeTeamId": 3,
  "scoreA": 18,
  "scoreB": 21
}
```

Rules:

- game must exist
- all referenced IDs must exist
- score values must be >= 0
- participating teams must be different
- referee team must differ from both participants
- success -> `200`

Broadcast `GAME / UPDATE` after commit.

### Delete

`DELETE /api/teacher/games/{id}`

Rules:

- missing game -> `404`
- success -> `204`

Broadcast `GAME / DELETE` after commit.

## 10. Transaction requirements

Every create/update/delete method that changes the database must run in a transaction.

Example conceptually:

```kotlin
@Transactional
fun updateGame(id: Long, request: UpdateGameRequest): GameDto {
    // validate
    // load entity
    // modify
    // save
    // publish event
}
```

The event is published inside the transaction but delivered to WebSocket clients only after commit using:

```kotlin
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
```

Do not call the WebSocket broadcaster directly from the controller.

## 11. Concurrency and lost updates

The initial implementation should keep writes simple and transactional.

For score updates, avoid a sequence of `GET` + client-side calculation + `PUT` when the backend can update the score directly from the submitted current value.

At minimum:

- each update targets a specific game ID
- the backend re-reads the game inside the transaction
- the backend validates the new state before saving

Optimistic locking with a version column may be introduced later if simultaneous editors become a real requirement. It is not required for the first implementation unless the project already uses it.

## 12. Error response format

Use one consistent JSON error format across teacher endpoints.

Recommended format:

```json
{
  "status": 409,
  "code": "CONFLICT",
  "message": "Group cannot be deleted because teams still reference it."
}
```

The frontend should never have to parse raw SQL/MariaDB error text.

Use these status codes:

- `400` invalid request/validation
- `404` entity not found
- `409` uniqueness conflict or delete blocked by a foreign key
- `500` unexpected error

## 13. Realtime integration

Teacher management is the only intended writer in the current architecture.

Therefore every successful teacher CRUD method is also the source of the realtime notification.

Do not query MariaDB again to discover whether the write happened.

Event contract:

```json
{
  "type": "TOURNAMENT_DATA_CHANGED",
  "entity": "GAME",
  "operation": "UPDATE",
  "entityId": 123
}
```

The event must be created exactly once for each successful write.

No event is sent for:

- validation failure
- missing ID
- duplicate/unique constraint failure
- foreign key conflict
- transaction rollback

## 14. Recommended internal components

Suggested teacher package structure:

```text
teacher/
├── group/
│   ├── TeacherGroupController.kt
│   ├── CreateGroupRequest.kt
│   └── UpdateGroupRequest.kt
├── team/
│   ├── TeacherTeamController.kt
│   ├── CreateTeamRequest.kt
│   └── UpdateTeamRequest.kt
├── round/
│   ├── TeacherRoundController.kt
│   └── ...
├── field/
│   ├── TeacherFieldController.kt
│   └── ...
├── game/
│   ├── TeacherGameController.kt
│   ├── CreateGameRequest.kt
│   └── UpdateGameRequest.kt
└── common/
    └── ApiExceptionHandler.kt
```

The actual services/repositories may remain in the existing entity/domain packages if that avoids duplication. Package organization is secondary to keeping controllers thin and business rules in services.

## 15. Test tooling and test data

Use Kotlin/JUnit 5 with Spring Boot integration tests for HTTP behavior. Use MockMvc for REST endpoint tests. For database-dependent tests, prefer a real MariaDB instance managed by Testcontainers so foreign-key, unique-constraint, and transaction behavior is tested against the actual database engine. Seed the test database with the tournament SQL fixture before the integration tests.

Keep unit tests for pure validation/mapping logic where they are useful, but do not replace database integration tests with mocks for persistence behavior.

## 15. Automated tests — teacher API

Use Spring Boot integration tests for controller + service + repository + MariaDB behavior. A real MariaDB test container is preferred over mocking the database because foreign-key and uniqueness behavior are part of the requirements.

### Group tests

`TeacherGroupControllerTest`

1. `createGroup_returns201AndCreatedGroup`
2. `createGroup_rejectsBlankName`
3. `createGroup_rejectsDuplicateNameWith409`
4. `updateGroup_returns404WhenGroupDoesNotExist`
5. `updateGroup_returns200AndUpdatedGroup`
6. `updateGroup_rejectsDuplicateNameWith409`
7. `deleteGroup_returns204WhenUnused`
8. `deleteGroup_returns409WhenTeamsReferenceGroup`
9. `deleteGroup_returns404WhenMissing`

### Team tests

`TeacherTeamControllerTest`

1. `createTeam_returns201`
2. `createTeam_rejectsMissingGroup`
3. `createTeam_rejectsBlankClass`
4. `createTeam_rejectsBlankName`
5. `updateTeam_updatesGroupClassAndName`
6. `updateTeam_returns404WhenMissing`
7. `deleteTeam_returns409WhenUsedByGame`
8. `deleteTeam_returns204WhenUnused`

### Round tests

`TeacherRoundControllerTest`

1. `createRound_returns201`
2. `createRound_rejectsNonPositiveNumber`
3. `createRound_rejectsDuplicateNumber`
4. `updateRound_updatesNumber`
5. `deleteRound_returns409WhenGamesReferenceRound`
6. `deleteRound_returns204WhenUnused`

### Field tests

`TeacherFieldControllerTest`

1. `createField_returns201`
2. `createField_rejectsBlankName`
3. `createField_rejectsDuplicateName`
4. `updateField_updatesName`
5. `deleteField_returns409WhenGamesReferenceField`
6. `deleteField_returns204WhenUnused`

### Game tests

`TeacherGameControllerTest`

1. `createGame_returns201`
2. `createGame_rejectsMissingRound`
3. `createGame_rejectsMissingField`
4. `createGame_rejectsMissingTeamA`
5. `createGame_rejectsMissingTeamB`
6. `createGame_rejectsMissingReferee`
7. `createGame_rejectsNegativeScore`
8. `createGame_rejectsSameTeamForAAndB`
9. `createGame_rejectsRefereeEqualToTeamA`
10. `createGame_rejectsRefereeEqualToTeamB`
11. `updateGame_canChangeOnlyScoreValues`
12. `updateGame_returns404WhenMissing`
13. `updateGame_rejectsInvalidReferences`
14. `deleteGame_returns204`
15. `deleteGame_returns404WhenMissing`

## 16. Automated tests — realtime behavior

`TeacherRealtimeIntegrationTest`

### Create event

- Open a WebSocket client.
- POST a new game.
- Assert HTTP response `201`.
- Assert exactly one `CREATE` event for that game ID.
- Query the REST endpoint and verify the row exists.

### Update event

- Open a WebSocket client.
- PUT an existing game with a changed score.
- Assert HTTP response `200`.
- Assert exactly one `UPDATE` event.
- Query the game through REST and verify the new score.

### Delete event

- Open a WebSocket client.
- DELETE an existing game.
- Assert HTTP response `204`.
- Assert exactly one `DELETE` event.
- Query the game and verify `404`.

### Rollback/no event

- Arrange a write that fails inside the transaction.
- Assert the write did not persist.
- Assert the WebSocket client receives no change event.

### Broadcast to all clients

- Connect two WebSocket clients.
- Execute one successful teacher update.
- Assert both receive the same event.

## 17. Security boundary

Teacher endpoints are write-capable and must not be exposed as public read endpoints.

At minimum, keep them under `/api/teacher/...` so the API boundary is explicit.

Authentication/authorization should use the project's existing security mechanism if one already exists. If no security system exists yet, do not invent a large identity system as part of the realtime feature; make the teacher endpoint boundary easy to secure with Spring Security in the next step.

## 18. Non-goals

Do not implement in this iteration:

- a separate teacher microservice
- direct database access from the frontend
- database polling
- MariaDB triggers for change notification
- Kafka/RabbitMQ
- CDC/Debezium
- persistent WebSocket message queues
- a second database

## 19. Definition of done

Teacher management is complete when:

- all five entity types can be created, updated, and deleted as defined above
- validation prevents invalid domain states
- foreign-key restrictions are translated into `409 Conflict`
- controller, service, and repository responsibilities are separated
- all write operations are transactional
- successful writes generate exactly one AFTER_COMMIT change event
- unsuccessful writes generate no realtime event
- public WebSocket clients receive the event
- automated tests pass against MariaDB
- existing public GET endpoints still work
- no database polling is present

## 20. Implementation order for OpenCode

1. Inspect the existing entity, repository, service, controller, DTO, and test structure before changing it.
2. Preserve all existing public GET endpoint behavior.
3. Add teacher request DTOs and teacher controllers under `/api/teacher`.
4. Add/complete service-layer CRUD methods with `@Transactional`.
5. Add validation and exception handling.
6. Implement `TournamentChangeEvent`.
7. Publish one event from each successful create/update/delete method.
8. Implement the `AFTER_COMMIT` listener and WebSocket broadcaster from the public-backend specification.
9. Add all teacher CRUD integration tests.
10. Add transaction/event/WebSocket tests.
11. Run the complete test suite and fix regressions before changing the frontend.
