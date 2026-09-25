package de.atiw.volleyball

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Admin game CRUD contract on port 8081 (real HTTP): creation with non-null
 * scores, reference/team-combination validation, full-replacement updates and
 * deletes — all with `data`/`error` envelopes and machine-readable codes.
 */
class AdminGameCrudIT : RealPortIT() {

    private fun refs(prefix: String): Fixture = newFixture(prefix)

    private fun gameJson(f: Fixture, scoreA: String, scoreB: String): String =
        """{"roundId":${f.roundId},"fieldId":${f.fieldId},"teamAId":${f.teamA},"teamBId":${f.teamB},"refereeTeamId":${f.referee},"scoreA":$scoreA,"scoreB":$scoreB}"""

    @Test
    fun `create game with zero scores returns 201 and status SCHEDULED`() {
        val f = refs("CN")
        val res = assertStatus(post(adminPort, "/api/admin/games", gameJson(f, "0", "0")), 201, "POST games")
        val data = dataOf(res, "POST games")
        assertTrue(data.path("gameId").isTextual(), "gameId must be a string: $data")
        assertEquals(f.roundId, data.path("roundId").asText())
        assertEquals(f.fieldId, data.path("fieldId").asText())
        assertEquals(f.teamA, data.path("teamAId").asText())
        assertEquals(f.teamB, data.path("teamBId").asText())
        assertEquals(f.referee, data.path("refereeTeamId").asText())
        assertEquals(0, data.path("scoreA").asInt())
        assertEquals(0, data.path("scoreB").asInt())
        assertEquals("SCHEDULED", data.path("status").asText(), "new games must be SCHEDULED: $data")
        // And readable through the public API with numeric scores.
        val viaPublic = dataOf(
            assertStatus(get(publicPort, "/api/games/${data.path("gameId").asText()}"), 200, "GET game"),
            "GET game"
        )
        assertEquals(0, viaPublic.path("scoreA").asInt())
        assertEquals(0, viaPublic.path("scoreB").asInt())
    }

    @Test
    fun `create game defaults omitted scores to 0`() {
        val f = refs("CO")
        val body = gameJson(f, "0", "0")
            .replace(""", "scoreA":0,"scoreB":0""", "")
        val res = assertStatus(post(adminPort, "/api/admin/games", body), 201, "POST games without scores")
        val data = dataOf(res, "POST games without scores")
        assertEquals(0, data.path("scoreA").asInt(), "omitted scoreA must default to 0: $data")
        assertEquals(0, data.path("scoreB").asInt(), "omitted scoreB must default to 0: $data")
    }

    @Test
    fun `create game rejects explicit null scores with 400`() {
        val f = refs("CX")
        assertError(
            post(adminPort, "/api/admin/games", gameJson(f, "null", "null")),
            400, "BAD_REQUEST", "POST games with null scores"
        )
    }

    @Test
    fun `create game with scores returns 201`() {
        val f = refs("CS")
        val res = assertStatus(post(adminPort, "/api/admin/games", gameJson(f, "7", "9")), 201, "POST games with scores")
        val data = dataOf(res, "POST games with scores")
        assertEquals(7, data.path("scoreA").asInt())
        assertEquals(9, data.path("scoreB").asInt())
    }

    @Test
    fun `create game rejects missing references with 400`() {
        val f = refs("MR")
        val base = gameJson(f, "0", "0")
        fun withReplaced(target: String, replacement: String) =
            base.replace(""""$target":${jsonValue(base, target)}""", """"$target":$replacement""")

        assertError(
            post(adminPort, "/api/admin/games", withReplaced("roundId", "999999999")),
            400, "BAD_REQUEST", "POST game with missing round"
        )
        assertError(
            post(adminPort, "/api/admin/games", withReplaced("fieldId", "999999999")),
            400, "BAD_REQUEST", "POST game with missing field"
        )
        assertError(
            post(adminPort, "/api/admin/games", withReplaced("teamAId", "999999999")),
            400, "BAD_REQUEST", "POST game with missing team A"
        )
        assertError(
            post(adminPort, "/api/admin/games", withReplaced("teamBId", "999999999")),
            400, "BAD_REQUEST", "POST game with missing team B"
        )
        assertError(
            post(adminPort, "/api/admin/games", withReplaced("refereeTeamId", "999999999")),
            400, "BAD_REQUEST", "POST game with missing referee"
        )
    }

    private fun jsonValue(json: String, key: String): String {
        val regex = """"$key":([^,}]+)""".toRegex()
        return regex.find(json)!!.groupValues[1]
    }

    @Test
    fun `create game rejects negative scores and illegal team combinations`() {
        val f = refs("IG")
        assertError(
            post(adminPort, "/api/admin/games", gameJson(f, "-1", "0")),
            400, "BAD_REQUEST", "POST game with negative scoreA"
        )
        assertError(
            post(adminPort, "/api/admin/games", gameJson(f, "0", "-2")),
            400, "BAD_REQUEST", "POST game with negative scoreB"
        )
        val sameTeams = gameJson(f, "0", "0").replace(""""teamBId":${f.teamB}""", """"teamBId":${f.teamA}""")
        assertError(
            post(adminPort, "/api/admin/games", sameTeams),
            400, "BAD_REQUEST", "POST game with teamA == teamB"
        )
        val refIsA = gameJson(f, "0", "0").replace(""""refereeTeamId":${f.referee}""", """"refereeTeamId":${f.teamA}""")
        assertError(
            post(adminPort, "/api/admin/games", refIsA),
            400, "BAD_REQUEST", "POST game with referee == teamA"
        )
        val refIsB = gameJson(f, "0", "0").replace(""""refereeTeamId":${f.referee}""", """"refereeTeamId":${f.teamB}""")
        assertError(
            post(adminPort, "/api/admin/games", refIsB),
            400, "BAD_REQUEST", "POST game with referee == teamB"
        )
    }

    @Test
    fun `update game replaces the full object including scores`() {
        val f = refs("UG")
        val res = assertStatus(
            put(adminPort, "/api/admin/games/${f.freshGame}", gameJson(f, "18", "21")),
            200, "PUT game"
        )
        val data = dataOf(res, "PUT game")
        assertEquals(f.freshGame.toString(), data.path("gameId").asText())
        assertEquals(18, data.path("scoreA").asInt())
        assertEquals(21, data.path("scoreB").asInt())
    }

    @Test
    fun `update game changes team A score only`() {
        val f = refs("UA")
        val data = dataOf(
            assertStatus(
                put(adminPort, "/api/admin/games/${f.playedGame}", gameJson(f, "6", "3")),
                200, "PUT game changing scoreA"
            ),
            "PUT game changing scoreA"
        )
        assertEquals(6, data.path("scoreA").asInt())
        assertEquals(3, data.path("scoreB").asInt())
    }

    @Test
    fun `update game changes team B score only`() {
        val f = refs("UB")
        val data = dataOf(
            assertStatus(
                put(adminPort, "/api/admin/games/${f.playedGame}", gameJson(f, "5", "4")),
                200, "PUT game changing scoreB"
            ),
            "PUT game changing scoreB"
        )
        assertEquals(5, data.path("scoreA").asInt())
        assertEquals(4, data.path("scoreB").asInt())
    }

    @Test
    fun `update game changes both scores`() {
        val f = refs("UO")
        val data = dataOf(
            assertStatus(
                put(adminPort, "/api/admin/games/${f.playedGame}", gameJson(f, "20", "18")),
                200, "PUT game changing both scores"
            ),
            "PUT game changing both scores"
        )
        assertEquals(20, data.path("scoreA").asInt())
        assertEquals(18, data.path("scoreB").asInt())
    }

    @Test
    fun `update game rejects negative scores with 400 and keeps state`() {
        val f = refs("UE")
        assertError(
            put(adminPort, "/api/admin/games/${f.playedGame}", gameJson(f, "-1", "9")),
            400, "BAD_REQUEST", "PUT game with negative scoreA"
        )
        assertError(
            put(adminPort, "/api/admin/games/${f.playedGame}", gameJson(f, "9", "-2")),
            400, "BAD_REQUEST", "PUT game with negative scoreB"
        )
        val game = dataOf(
            assertStatus(get(publicPort, "/api/games/${f.playedGame}"), 200, "GET game"),
            "GET game"
        )
        assertEquals(5, game.path("scoreA").asInt(), "failed update must not modify state: $game")
        assertEquals(3, game.path("scoreB").asInt(), "failed update must not modify state: $game")
    }

    @Test
    fun `update game rejects null scores with 400 and keeps state`() {
        val f = refs("UN")
        assertError(
            put(adminPort, "/api/admin/games/${f.playedGame}", gameJson(f, "null", "9")),
            400, "BAD_REQUEST", "PUT game with null scoreA"
        )
        val game = dataOf(
            assertStatus(get(publicPort, "/api/games/${f.playedGame}"), 200, "GET game"),
            "GET game"
        )
        assertEquals(5, game.path("scoreA").asInt(), "failed update must not modify state: $game")
        assertEquals(3, game.path("scoreB").asInt(), "failed update must not modify state: $game")
    }

    @Test
    fun `update game cannot change status through CRUD`() {
        val f = refs("US")
        val body = gameJson(f, "6", "3").replace("}", """, "status":"FINISHED"}""")
        assertError(
            put(adminPort, "/api/admin/games/${f.playedGame}", body),
            400, "BAD_REQUEST", "PUT game with status field"
        )
        val game = dataOf(
            assertStatus(get(publicPort, "/api/games/${f.playedGame}"), 200, "GET game"),
            "GET game"
        )
        assertEquals("SCHEDULED", game.path("status").asText(), "status must be unchanged: $game")
        assertEquals(5, game.path("scoreA").asInt(), "failed update must not modify state: $game")
    }

    @Test
    fun `update missing game returns 404 and invalid references return 400`() {
        val f = refs("UM")
        assertError(
            put(adminPort, "/api/admin/games/999999999", gameJson(f, "0", "0")),
            404, "RESOURCE_NOT_FOUND", "PUT missing game"
        )
        val badRound = gameJson(f, "0", "0").replace(""""roundId":${f.roundId}""", """"roundId":999999999""")
        assertError(
            put(adminPort, "/api/admin/games/${f.freshGame}", badRound),
            400, "BAD_REQUEST", "PUT game with missing round"
        )
    }

    @Test
    fun `delete game returns 204 with empty body`() {
        val f = refs("DG")
        val res = assertStatus(delete(adminPort, "/api/admin/games/${f.freshGame}"), 204, "DELETE game")
        assertTrue(res.body().isEmpty(), "DELETE must return an empty body")
        assertError(
            get(publicPort, "/api/games/${f.freshGame}"),
            404, "RESOURCE_NOT_FOUND", "GET deleted game"
        )
    }

    @Test
    fun `delete missing game returns 404`() {
        assertError(delete(adminPort, "/api/admin/games/999999999"), 404, "RESOURCE_NOT_FOUND", "DELETE missing game")
    }
}
