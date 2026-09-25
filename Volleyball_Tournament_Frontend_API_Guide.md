# Volleyball Tournament — Frontend API Guide

One backend, one database, **two ports**:

| Surface | Base URL | Content |
|---|---|---|
| Public / student | `http://localhost:8080` | Read-only REST + live WebSocket |
| Admin | `http://localhost:8081` | All writes (CRUD + scoring control) |

> The ports are fixed parts of the contract. Calling an endpoint on the wrong
> port returns `404` — e.g. `POST http://localhost:8080/api/admin/groups`
> is rejected, and `GET http://localhost:8081/api/groups` is rejected.
> The public frontend must use `:8080`; the admin frontend must use `:8081`.
> In production, replace `localhost` with the real host (ports stay the same).
> CORS allows `GET, POST, PUT, DELETE, OPTIONS` on `/api/**` from any origin.

Interactive docs (each port shows only its own endpoints):

- Public: `http://localhost:8080/swagger-ui.html`
- Admin: `http://localhost:8081/swagger-ui.html`


## Port cheat sheet

```text
http://localhost:8080                      PUBLIC
  GET  /
  GET  /api/groups  GET  /api/groups/{id}
  GET  /api/teams   GET  /api/teams/{id}
  GET  /api/rounds
  GET  /api/fields
  GET  /api/games   GET  /api/games/filter/{filter}   GET  /api/games/{id}
  WS   /ws/live

http://localhost:8081                      ADMIN
  POST   /api/admin/groups  PUT  /api/admin/groups/{id}  DELETE /api/admin/groups/{id}
  POST   /api/admin/teams   PUT  /api/admin/teams/{id}   DELETE /api/admin/teams/{id}
  POST   /api/admin/games   PUT  /api/admin/games/{id}   DELETE /api/admin/games/{id}
  POST   /api/admin/fields  PUT  /api/admin/fields/{id}  DELETE /api/admin/fields/{id}
  POST   /api/admin/rounds  PUT  /api/admin/rounds/{id}  DELETE /api/admin/rounds/{id}
  POST /api/games/{id}/start
  POST /api/games/{id}/end
  POST /api/admin/rounds/{id}/generate-games/round-robin
  POST /api/admin/rounds/{id}/generate-games/knockout
  POST /api/admin/rounds/{id}/generate-games/consolation
```


## 1. Conventions (both APIs)

### IDs

- In **request bodies**, IDs are numbers: `{ "groupId": 1 }`.
- In **responses**, IDs are strings: `{ "groupId": "1" }`.
- In URLs, use the numeric ID: `GET /api/groups/3`.

### Success envelopes

Every success body is wrapped in a `data` envelope:

```json
{ "data": { "groupId": "5", "name": "A" } }
```

| Operation | Status | Body |
|---|---|---|
| `GET` | `200 OK` | object or list in `data` envelope |
| Create (`POST`) | `201 Created` | created object in `data` envelope (+ `Location` header) |
| Update (`PUT`) | `200 OK` | updated object in `data` envelope |
| Delete (`DELETE`) | `204 No Content` | empty body |
| Scoring action (`POST`) | `200 OK` | updated game in `data` envelope, no extra `GET` needed |

### Error envelope

Every error looks like this (HTTP status carries the meaning, `code` is machine-readable):

```json
{ "error": { "code": "CONFLICT", "message": "Group 'A' already exists." } }
```

| HTTP status | `code` | Meaning |
|---|---|---|
| `400` | `BAD_REQUEST` | validation failed (blank field, bad number, unknown reference ID, illegal team combination, malformed JSON, non-numeric scoring ID) |
| `404` | `RESOURCE_NOT_FOUND` | the ID in the URL does not exist (or is not numeric); also returned when calling an endpoint on the wrong port |
| `409` | `CONFLICT` | duplicate unique value, delete blocked because other rows still reference this row, or illegal scoring action (see §4) |
| `500` | `INTERNAL_ERROR` | unexpected server error, retry or report |

Note: referenced IDs that don't exist inside a body (e.g. `groupId: 999`) give
`400`, not `404` — only the ID in the URL gives `404`.

### Updates are full replacements

`PUT` expects the **complete object**, not a patch. To change only a score via
CRUD, `GET` the game first, change the score, and `PUT` the whole object back.
(For live scoring during a match, prefer the button endpoints in §4.)

---

## 2. Public API — `http://localhost:8080` (read-only)

### Root

`GET /` — in a browser, redirects to the Swagger UI (works on both ports;
each port shows its own endpoints). Not used by frontend code.

### Groups

`GET /api/groups` → `200`:

```json
{ "data": [{ "groupId": "1", "name": "A" }] }
```

`GET /api/groups/{id}` → `200` with `{ "data": { "groupId": "1", "name": "A" } }`.
Missing or non-numeric ID → `404`.

### Teams

`GET /api/teams` → `200`:

```json
{ "data": [{ "teamId": "1", "class": "U18 Boys", "name": "St. Maria School", "groupId": "1" }] }
```

`GET /api/teams/{id}` → `200`, missing/non-numeric → `404`.

### Rounds

`GET /api/rounds` → `200`:

```json
{ "data": [{ "roundId": "1", "number": 1 }] }
```

### Fields (courts)

`GET /api/fields` → `200`:

```json
{ "data": [{ "fieldId": "1", "name": "Court 1" }] }
```

### Games

`GET /api/games` → `200`:

```json
{
  "data": [
    {
      "gameId": "1",
      "roundId": "1",
      "fieldId": "1",
      "teamAId": "1",
      "teamBId": "2",
      "refereeTeamId": "3",
      "scoreA": 25,
      "scoreB": 21,
      "status": "FINISHED"
    }
  ]
}
```

`status` is one of `SCHEDULED` (planned), `RUNNING` (being played),
`FINISHED` (ended).

`GET /api/games/{id}` → `200`, missing/non-numeric → `404`.

`GET /api/games/filter/{filter}` — games of one round, where `{filter}` is the
round ID:

- valid numeric ID → `200` with matching games (possibly an empty list);
- `"invalid"` or blank → `400`;
- other non-numeric values → `200` with an empty list.

### Score values

`scoreA` / `scoreB` are always numbers (`0` or greater) — the current score.
Scores are never `null`.

---

## 3. Admin CRUD API — `http://localhost:8081`

All endpoints below exist **only** on port `8081`.

### Groups — `/api/admin/groups`

Create — `POST /api/admin/groups`:

```json
{ "name": "A" }
```

- `name`: required, non-blank, max 10 characters, must be unique.
- → `201` + created group. Duplicate name → `409`. Blank/too-long → `400`.

Update — `PUT /api/admin/groups/{id}` with `{ "name": "B" }`:

- Same rules as create. Missing ID → `404`. Duplicate name → `409`. → `200`.

Delete — `DELETE /api/admin/groups/{id}`:

- Missing ID → `404`. Group still has teams → `409` (delete or move the teams first). → `204`.

### Teams — `/api/admin/teams`

Request bodies use the key **`"class"`** for the team class (the alias `"clazz"`
is also accepted, but prefer `"class"`).

Create — `POST /api/admin/teams`:

```json
{ "groupId": 1, "class": "U18 Boys", "name": "New School" }
```

- `groupId`: required, must reference an existing group (unknown group → `400`).
- `class`: required, non-blank, max 100 characters.
- `name`: required, non-blank, max 100 characters.
- → `201` + created team, e.g.
  `{ "data": { "teamId": "7", "class": "U18 Boys", "name": "New School", "groupId": "1" } }`.

Update — `PUT /api/admin/teams/{id}`: same body (all three fields required, full
replacement; can also move the team to another group). Missing team → `404`. → `200`.

Delete — `DELETE /api/admin/teams/{id}`:

- Missing team → `404`. Team is used in any game (as team A, team B, or
  referee) → `409` (delete or reassign those games first). → `204`.

### Rounds — `/api/admin/rounds`

Create — `POST /api/admin/rounds` with `{ "number": 6 }`:

- `number`: required, integer `> 0`, must be unique. Zero/negative → `400`.
  Duplicate → `409`. → `201`, e.g. `{ "data": { "roundId": "6", "number": 6 } }`.

Update — `PUT /api/admin/rounds/{id}` with `{ "number": 7 }`: same rules,
missing round → `404`. → `200`.

Delete — `DELETE /api/admin/rounds/{id}`: missing round → `404`, games still
reference the round → `409`. → `204`.

### Fields — `/api/admin/fields`

Create — `POST /api/admin/fields` with `{ "name": "Court 5" }`:

- `name`: required, non-blank, max 100 characters, must be unique.
- Blank → `400`. Duplicate → `409`. → `201`.

Update — `PUT /api/admin/fields/{id}`: same rules, missing field → `404`. → `200`.

Delete — `DELETE /api/admin/fields/{id}`: missing field → `404`, games still
reference the field → `409`. → `204`.

### Games — `/api/admin/games`

Deleting a game is always allowed; nothing references games.

Create — `POST /api/admin/games`:

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

Rules (all violations → `400`):

- `roundId`, `fieldId`, `teamAId`, `teamBId`, `refereeTeamId` are required and
  must reference existing rows.
- `scoreA`, `scoreB` are required non-negative integers (omit them to default
  to `0`). Negative scores → `400`; explicit `null` → `400`.
- `teamAId` and `teamBId` must be different teams.
- `refereeTeamId` must differ from both `teamAId` and `teamBId`.

→ `201` + created game. Every new game starts with `"status": "SCHEDULED"`,
assigned by the backend (do not send `status` in the request).

Update — `PUT /api/admin/games/{id}` (incl. score updates): send the full
object; for a score change just resend everything with the new scores. Same
validation as create. The lifecycle `status` cannot be changed here (sending
`status` → `400`); use the start/end endpoints in §4. Missing game → `404`.
→ `200`.

Delete — `DELETE /api/admin/games/{id}`: missing game → `404`, otherwise → `204`.

---

## 4. Admin game lifecycle — `http://localhost:8081`

Games move through `SCHEDULED → RUNNING → FINISHED`. Scores are changed
exclusively through the admin game CRUD API: send the complete game object
with the new scores via `PUT /api/admin/games/{id}` (see §3).

Two button-style endpoints control the lifecycle. They live under
`/api/games/…` (not `/api/admin/…`) but are reachable **only** on port
`8081`. Both are empty `POST`s with no request body and return `200` + the
updated game in the `data` envelope. Neither modifies the scores:

```http
POST /api/games/{id}/start
POST /api/games/{id}/end
```

| Endpoint | Effect |
|---|---|
| `…/start` | `SCHEDULED → RUNNING`. Already `RUNNING`/`FINISHED` → `409`, nothing changes. |
| `…/end` | `RUNNING → FINISHED`. Still `SCHEDULED` or already `FINISHED` → `409`, nothing changes. |

There are no reverse transitions and no reset/reopen endpoint.

Error cases:

- Non-numeric `{id}` → `400`.
- Missing game → `404`.
- `409` responses carry a message, e.g. `"Game 15 is already running."` or
  `"Game 15 cannot be finished because it is still scheduled."` — show it to
  the admin user.

Example — starting a scheduled game:

```http
POST http://localhost:8081/api/games/15/start
```

```json
{
  "data": {
    "gameId": "15",
    "roundId": "1",
    "fieldId": "1",
    "teamAId": "3",
    "teamBId": "4",
    "refereeTeamId": "5",
    "scoreA": 0,
    "scoreB": 0,
    "status": "RUNNING"
  }
}
```

Example — changing a score during a match:

```http
PUT http://localhost:8081/api/admin/games/15
```

```json
{
  "roundId": 1,
  "fieldId": 1,
  "teamAId": 3,
  "teamBId": 4,
  "refereeTeamId": 5,
  "scoreA": 12,
  "scoreB": 9
}
```

→ `200` + the updated game in the `data` envelope. Negative scores → `400`.

---

## 4a. Admin game generation — `http://localhost:8081`

Three endpoints generate tournament games automatically. The frontend decides
*when* to call them; the backend validates *whether* generation is possible
and determines the participants itself — the requests carry no team IDs.

```http
POST /api/admin/rounds/{id}/generate-games/round-robin
POST /api/admin/rounds/{id}/generate-games/knockout
POST /api/admin/rounds/{id}/generate-games/consolation
```

All three exist **only** on port `8081` (`404` on port `8080`). `{id}` is the
target `round_id` (missing round → `404`, non-numeric → `404`). All are
transactional and idempotent: pairings already present in the target round
(`A vs B` == `B vs A`) are skipped untouched, so a repeat call reports
`gamesCreated: 0`. Every created game starts as `SCHEDULED` with `0/0` and
emits one `GAME / CREATE` live event; skipped/failed generation emits nothing.

Response (`201`):

```json
{
  "data": {
    "roundId": "6",
    "gamesCreated": 6,
    "gamesSkipped": 0,
    "games": [
      {
        "gameId": "101",
        "roundId": "6",
        "fieldId": "1",
        "teamAId": "1",
        "teamBId": "2",
        "refereeTeamId": "3",
        "scoreA": 0,
        "scoreB": 0,
        "status": "SCHEDULED"
      }
    ]
  }
}
```

| Endpoint | Rule |
|---|---|
| `…/round-robin` | Every team plays every other team inside its own group exactly once (`n·(n−1)/2` per group). Never cross-group. |
| `…/knockout` | First stage (no previous knockout games): top team of each group leaderboard (`round_number = 1`) qualifies; count must be a power of two (`400` otherwise). Later stages: winners of the previous knockout stage qualify — it must be fully `FINISHED` (`409` otherwise) with exactly one winner per game (draw → `400`). Deterministic bracket pairing (first vs last). |
| `…/consolation` | Lowest team of each group leaderboard (`round_number = 1`) participates. Even count required (`400` if odd, no byes); each team plays once, never against its own group. |

Shared rules: each game is played on its group's field — groups ordered by
`group_id` map onto fields ordered by `field_id` (group 1 → field 1, group 2
→ field 2, …), wrapping around when the counts don't match. A game's group is
Team A's group, so all of one group's round-robin games share a field.
No fields → `400`;
referee is the lowest-ID team not participating (none available → `400`, e.g.
only two teams exist); existing games are never modified.

---

## 5. Live updates (public WebSocket)

Every successful admin write (CRUD) broadcasts exactly one event;
failed writes broadcast nothing. This is how the public page learns about
changes without polling.

- Connect: `ws://localhost:8080/ws/live` (port `8080` only — port `8081` has no WebSocket). No handshake message is sent on connect.
- You only listen — the server ignores incoming messages.
- If the connection drops, reconnect and do a normal REST reload (events sent while disconnected are not replayed).

Event format:

```json
{ "type": "TOURNAMENT_DATA_CHANGED", "entity": "GAME", "operation": "UPDATE", "entityId": 17 }
```

- `entity`: `GROUP`, `TEAM`, `ROUND`, `FIELD`, or `GAME`.
- `operation`: `CREATE`, `UPDATE`, or `DELETE`.
- `entityId`: numeric ID of the changed row.

Frontend handling rule:

- `GAME` event → reload that game (`GET /api/games/{entityId}`) or the game list.
- `TEAM` / `GROUP` / `ROUND` / `FIELD` event → reload the views depending on that data.
- `DELETE` → drop the object from local state or reload the affected collection.

---

## 6. Typical frontend flows

**Public page startup:**

1. `GET http://localhost:8080/api/groups`, `/api/teams`, `/api/rounds`, `/api/fields`, `/api/games` → render.
2. Open `ws://localhost:8080/ws/live`.
3. On each event → re-fetch the affected data via the public GET endpoints.

**Score update during a match (admin):**

1. `PUT http://localhost:8081/api/admin/games/{id}` with the full game object
   and new `scoreA`/`scoreB` → `200`, use `response.data` directly.
2. All connected public clients receive `GAME / UPDATE` and refresh via REST.

**Starting a match (admin):**

1. `POST http://localhost:8081/api/games/{id}/start` → `200`, status is now `RUNNING`, scores unchanged.

**Ending a match (admin):**

1. `POST http://localhost:8081/api/games/{id}/end` → `200`, status is now `FINISHED`, scores unchanged.

**Adding a new team (admin):**

1. `POST http://localhost:8081/api/admin/teams` → `201`. If the group doesn't exist yet, create it first via `POST http://localhost:8081/api/admin/groups`.

**Deleting a group that is still in use (admin):**

1. `DELETE http://localhost:8081/api/admin/groups/{id}` → `409`.
2. Show the user which teams block it (e.g. via `GET http://localhost:8080/api/teams`), move/delete them, retry.

## 7. Quick error-handling recipe

```text
201/200 → use response.data, update local state
204       → remove item from local state
400       → show response.error.message next to the form (user input problem)
404       → item is gone, reload the list (or: wrong port — check the base URL)
409       → show response.error.message (duplicate name or item still in use)
500       → generic "server error, try again" message
+ WS event → re-fetch affected data via the public GET endpoints
```
