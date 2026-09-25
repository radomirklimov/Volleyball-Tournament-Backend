package de.atiw.volleyball

import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Game start / score-control contract on port 8081 (real HTTP): state
 * transitions, single-point steps, guards (unstarted, negative floor),
 * `data` envelopes and machine-readable error codes. Database state is
 * double-checked through the public read API.
 */
class GameScoringIT : RealPortIT() {

    private fun scores(gameId: Int): JsonNode =
        dataOf(assertStatus(get(publicPort, "/api/games/$gameId"), 200, "GET game"), "GET game")

    @Test
    fun `start unstarted game sets 0-0 and returns the dto`() {
        val f = newFixture("ST")
        val res = assertStatus(post(adminPort, "/api/games/${f.unstartedGame}/start"), 200, "POST start")
        val data = dataOf(res, "POST start")
        assertEquals(f.unstartedGame.toString(), data.path("gameId").asText())
        assertEquals(0, data.path("scoreA").asInt())
        assertEquals(0, data.path("scoreB").asInt())
        // Persisted, visible publicly.
        assertEquals(0, scores(f.unstartedGame).path("scoreA").asInt())
        assertEquals(0, scores(f.unstartedGame).path("scoreB").asInt())
    }

    @Test
    fun `start already started game keeps scores`() {
        val f = newFixture("SS")
        val res = assertStatus(post(adminPort, "/api/games/${f.startedGame}/start"), 200, "POST start on started game")
        val data = dataOf(res, "POST start on started game")
        assertEquals(5, data.path("scoreA").asInt())
        assertEquals(3, data.path("scoreB").asInt())
    }

    @Test
    fun `start handles invalid and missing ids`() {
        assertError(post(adminPort, "/api/games/abc/start"), 400, "BAD_REQUEST", "POST start non-numeric id")
        assertError(post(adminPort, "/api/games/999999999/start"), 404, "RESOURCE_NOT_FOUND", "POST start missing game")
    }

    @Test
    fun `increment team A by exactly one without touching team B`() {
        val f = newFixture("IA")
        val res = assertStatus(
            post(adminPort, "/api/games/${f.startedGame}/score/team-a/increment"), 200, "POST inc A"
        )
        val data = dataOf(res, "POST inc A")
        assertEquals(6, data.path("scoreA").asInt())
        assertEquals(3, data.path("scoreB").asInt())
    }

    @Test
    fun `increment unstarted game returns 409 and keeps null`() {
        val f = newFixture("IU")
        assertError(
            post(adminPort, "/api/games/${f.unstartedGame}/score/team-a/increment"),
            409, "CONFLICT", "POST inc A on unstarted game"
        )
        assertError(
            post(adminPort, "/api/games/${f.unstartedGame}/score/team-b/increment"),
            409, "CONFLICT", "POST inc B on unstarted game"
        )
        val game = scores(f.unstartedGame)
        assertEquals(true, game.path("scoreA").isNull())
        assertEquals(true, game.path("scoreB").isNull())
    }

    @Test
    fun `decrement team A steps down to the zero floor`() {
        val f = newFixture("DA")
        var expected = 5
        repeat(5) {
            val res = assertStatus(
                post(adminPort, "/api/games/${f.startedGame}/score/team-a/decrement"), 200, "POST dec A"
            )
            expected -= 1
            assertEquals(expected, dataOf(res, "POST dec A").path("scoreA").asInt())
        }
        assertError(
            post(adminPort, "/api/games/${f.startedGame}/score/team-a/decrement"),
            409, "CONFLICT", "POST dec A at zero"
        )
        assertEquals(0, scores(f.startedGame).path("scoreA").asInt())
    }

    @Test
    fun `decrement unstarted game returns 409`() {
        val f = newFixture("DU")
        assertError(
            post(adminPort, "/api/games/${f.unstartedGame}/score/team-a/decrement"),
            409, "CONFLICT", "POST dec A on unstarted game"
        )
        assertError(
            post(adminPort, "/api/games/${f.unstartedGame}/score/team-b/decrement"),
            409, "CONFLICT", "POST dec B on unstarted game"
        )
    }

    @Test
    fun `team B increment and decrement mirror team A`() {
        val f = newFixture("TB")
        val inc = dataOf(
            assertStatus(post(adminPort, "/api/games/${f.startedGame}/score/team-b/increment"), 200, "POST inc B"),
            "POST inc B"
        )
        assertEquals(4, inc.path("scoreB").asInt())
        assertEquals(5, inc.path("scoreA").asInt())

        val dec = dataOf(
            assertStatus(post(adminPort, "/api/games/${f.startedGame}/score/team-b/decrement"), 200, "POST dec B"),
            "POST dec B"
        )
        assertEquals(3, dec.path("scoreB").asInt())
        assertEquals(5, dec.path("scoreA").asInt())
    }

    @Test
    fun `scoring a missing game returns 404`() {
        assertError(
            post(adminPort, "/api/games/999999999/score/team-a/increment"),
            404, "RESOURCE_NOT_FOUND", "POST inc on missing game"
        )
    }
}
