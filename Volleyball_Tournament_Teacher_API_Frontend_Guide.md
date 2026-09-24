# Volleyball Tournament — Teacher API (Frontend Guide)

Base URL: same host as the public API (e.g. `http://localhost:8080`).

- All teacher endpoints live under `/api/teacher/...`. Public read endpoints under `/api/...` are unchanged.
- Requests and responses use JSON. Send `Content-Type: application/json`.
- **No authentication yet.** Anyone who can reach the API can write. Auth will be added later in front of `/api/teacher/...`, so already keep teacher calls in a separate API client module.
- CORS allows `GET, POST, PUT, DELETE, OPTIONS` on `/api/**`.

## 1. Conventions

### IDs

- In **request bodies**, IDs are numbers: `{ "groupId": 1 }`.
- In **responses**, IDs are strings: `{ "groupId": "1" }` (same style as the public API).
- In URLs, use the numeric ID as string: `PUT /api/teacher/groups/3`. Non-numeric or missing IDs return `404`.

### Success envelopes

Every success body is wrapped in a `data` envelope:

```json
{ "data": { "groupId": "5", "name": "A" } }
```

| Operation | Status | Body |
|---|---|---|
| Create (`POST`) | `201 Created` | created object in `data` envelope (+ `Location` header) |
| Update (`PUT`) | `200 OK` | updated object in `data` envelope |
| Delete (`DELETE`) | `204 No Content` | empty body |

### Error envelope

Every error looks like this (HTTP status carries the meaning, `code` is machine-readable):

```json
{ "error": { "code": "CONFLICT", "message": "Group 'A' already exists." } }
```

| HTTP status | `code` | Meaning |
|---|---|---|
| `400` | `BAD_REQUEST` | validation failed (blank field, bad number, unknown reference ID, illegal team combination, malformed JSON) |
| `404` | `RESOURCE_NOT_FOUND` | the ID in the URL does not exist (or is not numeric) |
| `409` | `CONFLICT` | duplicate unique value, or delete blocked because other rows still reference this row |
| `500` | `INTERNAL_ERROR` | unexpected server error, retry or report |

Note: referenced IDs that don't exist (e.g. `groupId: 999`) give `400`, not `404` — only the ID in the URL gives `404`.

### Updates are full replacements

`PUT` expects the **complete object**, not a patch. To change only a score, `GET` the game first (or reuse the data you already have), change the score, and `PUT` the whole object back.

## 2. Groups

### Create group — `POST /api/teacher/groups`

```json
{ "name": "A" }
```

- `name`: required, non-blank, max 10 characters, must be unique.
- → `201` + created group. Duplicate name → `409`. Blank/too-long → `400`.

```json
{ "data": { "groupId": "5", "name": "A" } }
```

### Update group — `PUT /api/teacher/groups/{id}`

```json
{ "name": "B" }
```

- Same rules as create. Missing ID → `404`. Duplicate name → `409`. → `200` + updated group.

### Delete group — `DELETE /api/teacher/groups/{id}`

- Missing ID → `404`.
- Group still has teams → `409` (delete or move the teams first).
- → `204`, empty body.

## 3. Teams

Request bodies use the key **`"class"`** for the team class (the alias `"clazz"` is also accepted, but prefer `"class"`).

### Create team — `POST /api/teacher/teams`

```json
{ "groupId": 1, "class": "U18 Boys", "name": "New School" }
```

- `groupId`: required, must reference an existing group (unknown group → `400`).
- `class`: required, non-blank, max 100 characters.
- `name`: required, non-blank, max 100 characters.
- → `201` + created team.

```json
{ "data": { "teamId": "7", "class": "U18 Boys", "name": "New School", "groupId": "1" } }
```

### Update team — `PUT /api/teacher/teams/{id}`

Same body as create (all three fields required, full replacement; can also move the team to another group). Missing team → `404`. → `200` + updated team.

### Delete team — `DELETE /api/teacher/teams/{id}`

- Missing team → `404`.
- Team is used in any game (as team A, team B, or referee) → `409` (delete or reassign those games first).
- → `204`, empty body.

## 4. Rounds

### Create round — `POST /api/teacher/rounds`

```json
{ "number": 6 }
```

- `number`: required, integer `> 0`, must be unique.
- → `201` + created round (`{ "data": { "roundId": "6", "number": 6 } }`). Zero/negative → `400`. Duplicate → `409`.

### Update round — `PUT /api/teacher/rounds/{id}`

```json
{ "number": 7 }
```

- Same rules. Missing round → `404`. → `200` + updated round.

### Delete round — `DELETE /api/teacher/rounds/{id}`

- Missing round → `404`.
- Games still reference the round → `409`.
- → `204`, empty body.

## 5. Fields (courts)

### Create field — `POST /api/teacher/fields`

```json
{ "name": "Court 5" }
```

- `name`: required, non-blank, max 100 characters, must be unique.
- → `201` + created field. Blank → `400`. Duplicate → `409`.

```json
{ "data": { "fieldId": "5", "name": "Court 5" } }
```

### Update field — `PUT /api/teacher/fields/{id}`

```json
{ "name": "Court A" }
```

- Same rules. Missing field → `404`. → `200` + updated field.

### Delete field — `DELETE /api/teacher/fields/{id}`

- Missing field → `404`.
- Games still reference the field → `409`.
- → `204`, empty body.

## 6. Games

Games are the hottest resource (live score updates). Deleting a game is always allowed; nothing references games.

### Create game — `POST /api/teacher/games`

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

- `roundId`, `fieldId`, `teamAId`, `teamBId`, `refereeTeamId` are required and must reference existing rows.
- `scoreA`, `scoreB` are optional: either a non-negative integer or `null` (omit them or send `null` for games not yet played — e.g. `"scoreA": null`).
- Negative scores → `400`. `null` is always accepted.
- `teamAId` and `teamBId` must be different teams.
- `refereeTeamId` must differ from both `teamAId` and `teamBId`.

Game responses carry `scoreA`/`scoreB` as number or `null`:

```json
{ "data": { "gameId": "17", "roundId": "1", "fieldId": "1", "teamAId": "1", "teamBId": "2", "refereeTeamId": "3", "scoreA": null, "scoreB": null } }

→ `201` + created game:

```json
{
  "data": {
    "gameId": "17",
    "roundId": "1",
    "fieldId": "1",
    "teamAId": "1",
    "teamBId": "2",
    "refereeTeamId": "3",
    "scoreA": 0,
    "scoreB": 0
  }
}
```

### Update game — `PUT /api/teacher/games/{id}` (incl. score updates)

Send the full object; for a score change just resend everything with the new scores. Sending `"scoreA": null` clears a score back to "not played":

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

- Same validation as create. Missing game → `404`. → `200` + updated game.

### Delete game — `DELETE /api/teacher/games/{id}`

- Missing game → `404`. Otherwise always allowed → `204`, empty body.

## 6a. Game scoring (button actions, no request body)

A `null` score means "not started yet". Five endpoints control the score with single clicks — send an empty `POST`, no body:

```http
POST /api/games/{id}/start
POST /api/games/{id}/score/team-a/increment
POST /api/games/{id}/score/team-a/decrement
POST /api/games/{id}/score/team-b/increment
POST /api/games/{id}/score/team-b/decrement
```

- Non-numeric `{id}` → `400`. Missing game → `404`.
- All return `200` + the updated game in the `data` envelope, so no extra `GET` is needed after a click.
- Every state-changing call broadcasts one `GAME / UPDATE` event on `/ws/live` (see section 7). Failed calls broadcast nothing.

| Endpoint | Effect |
|---|---|
| `…/start` | `null/null` → `0/0`. Idempotent: an already started game keeps its scores (and then sends no event). |
| `…/team-a/increment` | `scoreA + 1`. Unstarted game → `409`. |
| `…/team-a/decrement` | `scoreA − 1`, never below `0`. Unstarted game or `scoreA == 0` → `409`. |
| `…/team-b/increment` | `scoreB + 1`. Unstarted game → `409`. |
| `…/team-b/decrement` | `scoreB − 1`, never below `0`. Unstarted game or `scoreB == 0` → `409`. |

Only the addressed team's score changes; the other one is untouched. Rapid clicking is safe — concurrent increments are serialized server-side and none get lost.

## 7. Live updates (WebSocket)

Every successful teacher write broadcasts exactly one event; failed writes broadcast nothing. This is how the public page learns about your changes without polling.

- Connect: `WS /ws/live` (e.g. `ws://localhost:8080/ws/live`). No handshake message is sent on connect.
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

## 8. Typical frontend flows

**Score update during a match:**
1. `PUT /api/teacher/games/{id}` with the full object and new `scoreA`/`scoreB` → `200`.
2. All connected public clients receive `GAME / UPDATE` and refresh via REST.

**Adding a new team:**
1. `POST /api/teacher/teams` → `201`. If the group doesn't exist yet, create it first via `POST /api/teacher/groups`.

**Deleting a group that is still in use:**
1. `DELETE /api/teacher/groups/{id}` → `409`.
2. Show the user which teams block it (e.g. via `GET /api/teams`), move/delete them, retry.

## 9. Quick error-handling recipe

```text
201/200 → use response.data, update local state
204       → remove item from local state
400       → show response.error.message next to the form (user input problem)
404       → item is gone, reload the list
409       → show response.error.message (duplicate name or item still in use)
500       → generic "server error, try again" message
+ WS event → re-fetch affected data via the public GET endpoints
```
