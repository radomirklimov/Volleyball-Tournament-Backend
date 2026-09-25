package de.atiw.volleyball

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Port isolation with real HTTP traffic (MockMvc cannot see ports, so these
 * tests hit both connectors directly):
 *
 * - public port serves the read-only API + WS `/ws/live`, rejects admin routes
 * - admin port serves the admin API, rejects public read routes + WS
 *
 * Detailed endpoint behavior lives in [PublicApiIT], [AdminCrudIT],
 * [AdminGameCrudIT], [GameStartIT] and [RealtimeIT].
 */
class PortIsolationIT : RealPortIT() {

    @Test
    fun `public port serves the read API`() {
        val f = newFixture("I")
        assertStatus(get(publicPort, "/api/groups"), 200, "GET groups")
        assertStatus(get(publicPort, "/api/groups/${f.groupA}"), 200, "GET group by id")
        assertStatus(get(publicPort, "/api/teams"), 200, "GET teams")
        assertStatus(get(publicPort, "/api/teams/${f.teamA}"), 200, "GET team by id")
        assertStatus(get(publicPort, "/api/rounds"), 200, "GET rounds")
        assertStatus(get(publicPort, "/api/fields"), 200, "GET fields")
        assertStatus(get(publicPort, "/api/games"), 200, "GET games")
        assertStatus(get(publicPort, "/api/games/${f.playedGame}"), 200, "GET game by id")
        assertStatus(get(publicPort, "/api/games/filter/${f.roundId}"), 200, "GET games by filter")
        assertStatus(get(publicPort, "/api/groups/${f.groupA}/leaderboard"), 200, "GET group leaderboard")
    }

    @Test
    fun `public port rejects all admin CRUD routes`() {
        // Groups
        assertStatus(post(publicPort, "/api/admin/groups", """{"name":"X"}"""), 404, "POST admin groups")
        assertStatus(put(publicPort, "/api/admin/groups/1", """{"name":"X"}"""), 404, "PUT admin groups")
        assertStatus(delete(publicPort, "/api/admin/groups/1"), 404, "DELETE admin groups")
        // Teams
        assertStatus(post(publicPort, "/api/admin/teams", "{}"), 404, "POST admin teams")
        assertStatus(put(publicPort, "/api/admin/teams/1", "{}"), 404, "PUT admin teams")
        assertStatus(delete(publicPort, "/api/admin/teams/1"), 404, "DELETE admin teams")
        // Games
        assertStatus(post(publicPort, "/api/admin/games", "{}"), 404, "POST admin games")
        assertStatus(put(publicPort, "/api/admin/games/1", "{}"), 404, "PUT admin games")
        assertStatus(delete(publicPort, "/api/admin/games/1"), 404, "DELETE admin games")
        // Fields
        assertStatus(post(publicPort, "/api/admin/fields", "{}"), 404, "POST admin fields")
        assertStatus(put(publicPort, "/api/admin/fields/1", "{}"), 404, "PUT admin fields")
        assertStatus(delete(publicPort, "/api/admin/fields/1"), 404, "DELETE admin fields")
        // Rounds
        assertStatus(post(publicPort, "/api/admin/rounds", "{}"), 404, "POST admin rounds")
        assertStatus(put(publicPort, "/api/admin/rounds/1", "{}"), 404, "PUT admin rounds")
        assertStatus(delete(publicPort, "/api/admin/rounds/1"), 404, "DELETE admin rounds")
    }

    @Test
    fun `removed score endpoints return 404 on both ports`() {
        val f = newFixture("S")
        val paths = listOf(
            "/api/games/${f.freshGame}/score/team-a/increment",
            "/api/games/${f.freshGame}/score/team-a/decrement",
            "/api/games/${f.freshGame}/score/team-b/increment",
            "/api/games/${f.freshGame}/score/team-b/decrement"
        )
        for (path in paths) {
            assertStatus(post(publicPort, path), 404, "POST $path on public port")
            assertStatus(post(adminPort, path), 404, "POST $path on admin port")
        }
        // Nothing was modified through the removed routes.
        val game = dataOf(assertStatus(get(publicPort, "/api/games/${f.freshGame}"), 200, "GET game"), "GET game")
        assertEquals(0, game.path("scoreA").asInt(), "scoreA must stay 0")
        assertEquals(0, game.path("scoreB").asInt(), "scoreB must stay 0")
    }

    @Test
    fun `game start is admin-only`() {
        val f = newFixture("ST")
        assertStatus(post(publicPort, "/api/games/${f.freshGame}/start"), 404, "POST start on public port")
        val data = dataOf(
            assertStatus(post(adminPort, "/api/games/${f.freshGame}/start"), 200, "POST start on admin port"),
            "POST start on admin port"
        )
        assertEquals(0, data.path("scoreA").asInt())
        assertEquals(0, data.path("scoreB").asInt())
    }

    @Test
    fun `admin port rejects public read API`() {
        assertStatus(get(adminPort, "/"), 404, "GET /")
        assertStatus(get(adminPort, "/api/groups"), 404, "GET groups")
        assertStatus(get(adminPort, "/api/groups/1"), 404, "GET group by id")
        assertStatus(get(adminPort, "/api/groups/1/leaderboard"), 404, "GET group leaderboard")
        assertStatus(get(adminPort, "/api/teams"), 404, "GET teams")
        assertStatus(get(adminPort, "/api/teams/1"), 404, "GET team by id")
        assertStatus(get(adminPort, "/api/rounds"), 404, "GET rounds")
        assertStatus(get(adminPort, "/api/fields"), 404, "GET fields")
        assertStatus(get(adminPort, "/api/games"), 404, "GET games")
        assertStatus(get(adminPort, "/api/games/1"), 404, "GET game by id")
        assertStatus(get(adminPort, "/api/games/filter/1"), 404, "GET games by filter")
    }

    @Test
    fun `no teacher paths exist on either port`() {
        val viaPublic = post(publicPort, "/api/teacher/groups", """{"name":"X"}""")
        assertEquals(404, viaPublic.statusCode(), "public teacher path body: ${viaPublic.body()}")
        val viaAdmin = post(adminPort, "/api/teacher/groups", """{"name":"X"}""")
        assertEquals(404, viaAdmin.statusCode(), "admin teacher path body: ${viaAdmin.body()}")
    }

    @Test
    fun `swagger docs are separated by port`() {
        val publicConfig = assertStatus(get(publicPort, "/v3/api-docs/swagger-config"), 200, "public swagger-config")
        val publicUrls = objectMapper.readTree(publicConfig.body()).path("urls")
        assertEquals(1, publicUrls.size(), publicConfig.body())
        assertEquals("public", publicUrls[0].path("name").asText(), publicConfig.body())

        val adminConfig = assertStatus(get(adminPort, "/v3/api-docs/swagger-config"), 200, "admin swagger-config")
        val adminUrls = objectMapper.readTree(adminConfig.body()).path("urls")
        assertEquals(1, adminUrls.size(), adminConfig.body())
        assertEquals("admin", adminUrls[0].path("name").asText(), adminConfig.body())

        val publicDocs = assertStatus(get(publicPort, "/v3/api-docs/public"), 200, "public api-docs")
        assertTrue(publicDocs.body().contains("/api/groups"), publicDocs.body())
        assertTrue(!publicDocs.body().contains("/api/admin/groups"), "public docs must not contain admin paths")

        val adminDocs = assertStatus(get(adminPort, "/v3/api-docs/admin"), 200, "admin api-docs")
        assertTrue(adminDocs.body().contains("/api/admin/groups"), adminDocs.body())

        assertStatus(get(publicPort, "/v3/api-docs/admin"), 404, "admin docs on public port")
        assertStatus(get(publicPort, "/v3/api-docs"), 404, "aggregate docs on public port")
        assertStatus(get(adminPort, "/v3/api-docs/public"), 404, "public docs on admin port")
        assertStatus(get(adminPort, "/v3/api-docs"), 404, "aggregate docs on admin port")
    }

    @Test
    fun `websocket live is available on public port but not on admin port`() {
        val session = connectWs(publicPort, Collector())
        assertTrue(session.isOpen)
        session.close()

        var adminFailed = false
        try {
            connectWs(adminPort, Collector()).close()
        } catch (_: Exception) {
            adminFailed = true
        }
        assertTrue(adminFailed, "WS /ws/live must not be available on the admin port")
    }
}
