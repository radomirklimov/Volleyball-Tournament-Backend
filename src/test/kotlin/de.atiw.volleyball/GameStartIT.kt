package de.atiw.volleyball

import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Game lifecycle contract on port 8081 (real HTTP): `SCHEDULED -> RUNNING`
 * via `/start`, `RUNNING -> FINISHED` via `/end`, invalid transitions
 * rejected with 409, and proof that both endpoints return 404 on the public
 * port. Scores are changed exclusively via `PUT /api/admin/games/{id}`
 * (see AdminGameCrudIT).
 */
class GameStartIT : RealPortIT() {

    private fun scores(gameId: Int): JsonNode =
        dataOf(assertStatus(get(publicPort, "/api/games/$gameId"), 200, "GET game"), "GET game")

    @Test
    fun `start moves SCHEDULED to RUNNING without touching scores`() {
        val f = newFixture("ST")
        val res = assertStatus(post(adminPort, "/api/games/${f.freshGame}/start"), 200, "POST start")
        val data = dataOf(res, "POST start")
        assertEquals(f.freshGame.toString(), data.path("gameId").asText())
        assertEquals("RUNNING", data.path("status").asText())
        assertEquals(0, data.path("scoreA").asInt())
        assertEquals(0, data.path("scoreB").asInt())
        val viaPublic = scores(f.freshGame)
        assertEquals("RUNNING", viaPublic.path("status").asText())
    }

    @Test
    fun `end moves RUNNING to FINISHED without touching scores`() {
        val f = newFixture("EN")
        assertStatus(post(adminPort, "/api/games/${f.playedGame}/start"), 200, "POST start")
        val res = assertStatus(post(adminPort, "/api/games/${f.playedGame}/end"), 200, "POST end")
        val data = dataOf(res, "POST end")
        assertEquals("FINISHED", data.path("status").asText())
        assertEquals(5, data.path("scoreA").asInt())
        assertEquals(3, data.path("scoreB").asInt())
        val viaPublic = scores(f.playedGame)
        assertEquals("FINISHED", viaPublic.path("status").asText())
        assertEquals(5, viaPublic.path("scoreA").asInt())
        assertEquals(3, viaPublic.path("scoreB").asInt())
    }

    @Test
    fun `invalid transitions return 409 and keep state`() {
        val f = newFixture("IV")
        // SCHEDULED -> end
        assertError(
            post(adminPort, "/api/games/${f.freshGame}/end"),
            409, "CONFLICT", "POST end on SCHEDULED game"
        )
        assertEquals("SCHEDULED", scores(f.freshGame).path("status").asText())
        // SCHEDULED -> RUNNING -> start again
        assertStatus(post(adminPort, "/api/games/${f.freshGame}/start"), 200, "POST start")
        assertError(
            post(adminPort, "/api/games/${f.freshGame}/start"),
            409, "CONFLICT", "POST start on RUNNING game"
        )
        assertEquals("RUNNING", scores(f.freshGame).path("status").asText())
        // RUNNING -> FINISHED -> start/end again
        assertStatus(post(adminPort, "/api/games/${f.freshGame}/end"), 200, "POST end")
        assertError(
            post(adminPort, "/api/games/${f.freshGame}/start"),
            409, "CONFLICT", "POST start on FINISHED game"
        )
        assertError(
            post(adminPort, "/api/games/${f.freshGame}/end"),
            409, "CONFLICT", "POST end on FINISHED game"
        )
        assertEquals("FINISHED", scores(f.freshGame).path("status").asText())
    }

    @Test
    fun `start and end handle invalid and missing ids`() {
        assertError(post(adminPort, "/api/games/abc/start"), 400, "BAD_REQUEST", "POST start non-numeric id")
        assertError(post(adminPort, "/api/games/abc/end"), 400, "BAD_REQUEST", "POST end non-numeric id")
        assertError(post(adminPort, "/api/games/999999999/start"), 404, "RESOURCE_NOT_FOUND", "POST start missing game")
        assertError(post(adminPort, "/api/games/999999999/end"), 404, "RESOURCE_NOT_FOUND", "POST end missing game")
    }

    @Test
    fun `start and end return 404 on the public port`() {
        val f = newFixture("WP")
        assertStatus(post(publicPort, "/api/games/${f.freshGame}/start"), 404, "POST start on public port")
        assertStatus(post(publicPort, "/api/games/${f.freshGame}/end"), 404, "POST end on public port")
        assertEquals("SCHEDULED", scores(f.freshGame).path("status").asText())
    }
}
