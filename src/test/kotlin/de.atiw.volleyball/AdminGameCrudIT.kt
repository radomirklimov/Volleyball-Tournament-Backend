package de.atiw.volleyball

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Admin game CRUD contract on port 8081 (real HTTP): creation with nullable
 * scores, reference/team-combination validation, full-replacement updates and
 * deletes — all with `data`/`error` envelopes and machine-readable codes.
 */
class AdminGameCrudIT : RealPortIT() {

    private fun refs(prefix: String): Fixture = newFixture(prefix)

    private fun gameJson(f: Fixture, scoreA: String, scoreB: String): String =
        """{"roundId":${f.roundId},"fieldId":${f.fieldId},"teamAId":${f.teamA},"teamBId":${f.teamB},"refereeTeamId":${f.referee},"scoreA":$scoreA,"scoreB":$scoreB}"""

    @Test
    fun `create game with null scores returns 201 and preserves null`() {
        val f = refs("CN")
        val res = assertStatus(post(adminPort, "/api/admin/games", gameJson(f, "null", "null")), 201, "POST games")
        val data = dataOf(res, "POST games")
        assertTrue(data.path("gameId").isTextual(), "gameId must be a string: $data")
        assertEquals(f.roundId, data.path("roundId").asText())
        assertEquals(f.fieldId, data.path("fieldId").asText())
        assertEquals(f.teamA, data.path("teamAId").asText())
        assertEquals(f.teamB, data.path("teamBId").asText())
        assertEquals(f.referee, data.path("refereeTeamId").asText())
        assertTrue(data.path("scoreA").isNull(), "scoreA must stay null: $data")
        assertTrue(data.path("scoreB").isNull(), "scoreB must stay null: $data")
        // And readable through the public API with nulls intact.
        val viaPublic = dataOf(
            assertStatus(get(publicPort, "/api/games/${data.path("gameId").asText()}"), 200, "GET game"),
            "GET game"
        )
        assertTrue(viaPublic.path("scoreA").isNull() && viaPublic.path("scoreB").isNull())
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
            put(adminPort, "/api/admin/games/${f.unstartedGame}", gameJson(f, "18", "21")),
            200, "PUT game"
        )
        val data = dataOf(res, "PUT game")
        assertEquals(f.unstartedGame.toString(), data.path("gameId").asText())
        assertEquals(18, data.path("scoreA").asInt())
        assertEquals(21, data.path("scoreB").asInt())
        // Null clears a score back to "not played".
        val cleared = dataOf(
            assertStatus(
                put(adminPort, "/api/admin/games/${f.unstartedGame}", gameJson(f, "null", "null")),
                200, "PUT game clearing scores"
            ),
            "PUT game clearing scores"
        )
        assertTrue(cleared.path("scoreA").isNull() && cleared.path("scoreB").isNull())
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
            put(adminPort, "/api/admin/games/${f.unstartedGame}", badRound),
            400, "BAD_REQUEST", "PUT game with missing round"
        )
    }

    @Test
    fun `delete game returns 204 with empty body`() {
        val f = refs("DG")
        val res = assertStatus(delete(adminPort, "/api/admin/games/${f.unstartedGame}"), 204, "DELETE game")
        assertTrue(res.body().isEmpty(), "DELETE must return an empty body")
        assertError(
            get(publicPort, "/api/games/${f.unstartedGame}"),
            404, "RESOURCE_NOT_FOUND", "GET deleted game"
        )
    }

    @Test
    fun `delete missing game returns 404`() {
        assertError(delete(adminPort, "/api/admin/games/999999999"), 404, "RESOURCE_NOT_FOUND", "DELETE missing game")
    }
}
