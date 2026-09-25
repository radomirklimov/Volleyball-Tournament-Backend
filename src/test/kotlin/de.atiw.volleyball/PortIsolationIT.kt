package de.atiw.volleyball

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import org.testcontainers.containers.MariaDBContainer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Proves the two-port separation with real HTTP traffic (MockMvc cannot see
 * ports, so these tests hit both connectors directly):
 *
 * - public port serves the read-only API + WS /ws/live, rejects admin routes
 * - admin port serves CRUD + scoring, rejects public read routes + WS
 * - a successful admin write produces exactly one realtime event on the
 *   public WebSocket; a failed write produces none
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["app.admin-port=18081"]
)
class PortIsolationIT {

    companion object {
        // Own container (not the shared AbstractIntegrationTest singleton):
        // this class writes via real HTTP without rollback, so it must not
        // share a database with the MockMvc test classes.
        @JvmStatic
        @ServiceConnection
        val mariadb: MariaDBContainer<*> = MariaDBContainer("mariadb:11.4").apply { start() }
    }

    @LocalServerPort
    var publicPort: Int = 0

    private val adminPort: Int = 18081

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private val http: HttpClient = HttpClient.newHttpClient()
    private val suffix = AtomicInteger(Random.nextInt(100000, 999999))

    private fun nextTag(prefix: String): String = "$prefix${suffix.getAndIncrement()}"

    private fun get(port: Int, path: String): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder(URI("http://localhost:$port$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )

    private fun post(port: Int, path: String, json: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json ?: ""))
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun put(port: Int, path: String, json: String): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder(URI("http://localhost:$port$path"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )

    private fun delete(port: Int, path: String): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder(URI("http://localhost:$port$path")).DELETE().build(),
            HttpResponse.BodyHandlers.ofString()
        )

    private fun dataId(body: String, key: String): Int =
        objectMapper.readTree(body).path("data").path(key).asText().toInt()

    private fun adminCreate(path: String, json: String, expectedStatus: Int = 201): JsonNode {
        val res = post(adminPort, path, json)
        assertEquals(expectedStatus, res.statusCode(), "POST $path -> ${res.body()}")
        return objectMapper.readTree(res.body()).path("data")
    }

    /** Seeds one full game via the admin port; returns its numeric game id. */
    private fun seedGame(scoreA: Int?, scoreB: Int?): Int {
        val tag = nextTag("T")
        val groupId = adminCreate("/api/admin/groups", """{"name":"G$tag"}""").path("groupId").asText()
        fun team(name: String): String {
            val res = post(adminPort, "/api/admin/teams", """{"groupId":$groupId,"class":"C","name":"$name$tag"}""")
            assertEquals(201, res.statusCode(), "create team -> ${res.body()}")
            return objectMapper.readTree(res.body()).path("data").path("teamId").asText()
        }
        val teamA = team("A")
        val teamB = team("B")
        val referee = team("R")
        val roundNumber = Random.nextInt(1000000, 9000000)
        val roundId = adminCreate("/api/admin/rounds", """{"number":$roundNumber}""").path("roundId").asText()
        val fieldId = adminCreate("/api/admin/fields", """{"name":"Court $tag"}""").path("fieldId").asText()
        val scoreJson = if (scoreA == null && scoreB == null) """, "scoreA":null,"scoreB":null""" else """, "scoreA":$scoreA,"scoreB":$scoreB"""
        val res = post(
            adminPort, "/api/admin/games",
            """{"roundId":$roundId,"fieldId":$fieldId,"teamAId":$teamA,"teamBId":$teamB,"refereeTeamId":$referee$scoreJson}"""
        )
        assertEquals(201, res.statusCode(), "create game -> ${res.body()}")
        return objectMapper.readTree(res.body()).path("data").path("gameId").asText().toInt()
    }

    // ---- public port: read API works ----

    @Test
    fun `public port serves group list and single group`() {
        val tag = nextTag("P")
        val created = adminCreate("/api/admin/groups", """{"name":"G$tag"}""")
        val id = created.path("groupId").asText()

        val list = get(publicPort, "/api/groups")
        assertEquals(200, list.statusCode())
        assertTrue(list.body().contains("\"name\":\"G$tag\""), list.body())

        val single = get(publicPort, "/api/groups/$id")
        assertEquals(200, single.statusCode())
        assertTrue(single.body().contains("\"groupId\":\"$id\""), single.body())
    }

    @Test
    fun `public port serves teams rounds fields games and filter`() {
        val gameId = seedGame(3, 2)
        val gameBody = objectMapper.readTree(get(publicPort, "/api/games/$gameId").body())
        val roundId = gameBody.path("data").path("roundId").asText()

        assertEquals(200, get(publicPort, "/api/teams").statusCode())
        assertEquals(200, get(publicPort, "/api/rounds").statusCode())
        assertEquals(200, get(publicPort, "/api/fields").statusCode())
        assertEquals(200, get(publicPort, "/api/games").statusCode())
        assertEquals(200, get(publicPort, "/api/games/$gameId").statusCode())

        val filtered = get(publicPort, "/api/games/filter/$roundId")
        assertEquals(200, filtered.statusCode())
        assertTrue(filtered.body().contains("\"gameId\":\"$gameId\""), filtered.body())
    }

    @Test
    fun `public port exposes root`() {
        // Root redirects to Swagger UI; it must exist here (any non-404).
        val res = get(publicPort, "/")
        assertTrue(res.statusCode() != 404, "GET / on public port -> ${res.statusCode()}")
    }

    // ---- public port: admin routes rejected ----

    @Test
    fun `public port rejects admin CRUD`() {
        assertEquals(404, post(publicPort, "/api/admin/groups", """{"name":"X"}""").statusCode())
        assertEquals(404, put(publicPort, "/api/admin/groups/1", """{"name":"X"}""").statusCode())
        assertEquals(404, delete(publicPort, "/api/admin/groups/1").statusCode())
        assertEquals(404, post(publicPort, "/api/admin/teams", "{}").statusCode())
        assertEquals(404, post(publicPort, "/api/admin/games", "{}").statusCode())
        assertEquals(404, post(publicPort, "/api/admin/fields", "{}").statusCode())
        assertEquals(404, post(publicPort, "/api/admin/rounds", "{}").statusCode())
    }

    @Test
    fun `public port rejects game scoring`() {
        val gameId = seedGame(null, null)
        assertEquals(404, post(publicPort, "/api/games/$gameId/start").statusCode())
        assertEquals(404, post(publicPort, "/api/games/$gameId/score/team-a/increment").statusCode())
        assertEquals(404, post(publicPort, "/api/games/$gameId/score/team-a/decrement").statusCode())
        assertEquals(404, post(publicPort, "/api/games/$gameId/score/team-b/increment").statusCode())
        assertEquals(404, post(publicPort, "/api/games/$gameId/score/team-b/decrement").statusCode())
        // Rejected before touching state: game must still be unstarted.
        val game = get(publicPort, "/api/games/$gameId")
        assertTrue(game.body().contains("\"scoreA\":null"), game.body())
    }

    // ---- admin port: admin API works ----

    @Test
    fun `admin port serves group CRUD`() {
        val tag = nextTag("A")
        val created = post(adminPort, "/api/admin/groups", """{"name":"G$tag"}""")
        assertEquals(201, created.statusCode(), created.body())
        val id = dataId(created.body(), "groupId")

        val updated = put(adminPort, "/api/admin/groups/$id", """{"name":"H$tag"}""")
        assertEquals(200, updated.statusCode(), updated.body())
        assertTrue(updated.body().contains("H$tag"), updated.body())

        assertEquals(204, delete(adminPort, "/api/admin/groups/$id").statusCode())
        assertEquals(404, get(publicPort, "/api/groups/$id").statusCode())
    }

    @Test
    fun `admin port serves game scoring lifecycle`() {
        val gameId = seedGame(null, null)

        val started = post(adminPort, "/api/games/$gameId/start")
        assertEquals(200, started.statusCode(), started.body())
        assertTrue(started.body().contains("\"scoreA\":0"), started.body())
        assertTrue(started.body().contains("\"scoreB\":0"), started.body())

        val inc = post(adminPort, "/api/games/$gameId/score/team-a/increment")
        assertEquals(200, inc.statusCode(), inc.body())
        assertTrue(inc.body().contains("\"scoreA\":1"), inc.body())
        assertTrue(inc.body().contains("\"scoreB\":0"), inc.body())

        val dec = post(adminPort, "/api/games/$gameId/score/team-a/decrement")
        assertEquals(200, dec.statusCode(), dec.body())
        assertTrue(dec.body().contains("\"scoreA\":0"), dec.body())

        // Decrement at zero -> 409, score unchanged.
        val conflict = post(adminPort, "/api/games/$gameId/score/team-a/decrement")
        assertEquals(409, conflict.statusCode(), conflict.body())

        // Starting an already started game keeps the score.
        val restart = post(adminPort, "/api/games/$gameId/start")
        assertEquals(200, restart.statusCode(), restart.body())
        assertTrue(restart.body().contains("\"scoreA\":0"), restart.body())
    }

    @Test
    fun `admin port rejects scoring on unstarted game`() {
        val gameId = seedGame(null, null)
        assertEquals(409, post(adminPort, "/api/games/$gameId/score/team-a/increment").statusCode())
        assertEquals(409, post(adminPort, "/api/games/$gameId/score/team-b/decrement").statusCode())
    }

    // ---- admin port: public routes rejected ----

    @Test
    fun `admin port rejects public read API`() {
        assertEquals(404, get(adminPort, "/").statusCode())
        assertEquals(404, get(adminPort, "/api/groups").statusCode())
        assertEquals(404, get(adminPort, "/api/groups/1").statusCode())
        assertEquals(404, get(adminPort, "/api/teams").statusCode())
        assertEquals(404, get(adminPort, "/api/rounds").statusCode())
        assertEquals(404, get(adminPort, "/api/fields").statusCode())
        assertEquals(404, get(adminPort, "/api/games").statusCode())
        assertEquals(404, get(adminPort, "/api/games/1").statusCode())
        assertEquals(404, get(adminPort, "/api/games/filter/1").statusCode())
    }

    @Test
    fun `no teacher paths exist on either port`() {        val viaPublic = post(publicPort, "/api/teacher/groups", """{"name":"X"}""")
        assertEquals(404, viaPublic.statusCode(), "public teacher path body: ${viaPublic.body()}")
        val viaAdmin = post(adminPort, "/api/teacher/groups", """{"name":"X"}""")
        assertEquals(404, viaAdmin.statusCode(), "admin teacher path body: ${viaAdmin.body()}")
    }

    @Test
    fun `swagger docs are separated by port`() {
        val publicConfig = get(publicPort, "/v3/api-docs/swagger-config")
        assertEquals(200, publicConfig.statusCode(), publicConfig.body())
        val publicUrls = objectMapper.readTree(publicConfig.body()).path("urls")
        assertEquals(1, publicUrls.size(), publicConfig.body())
        assertEquals("public", publicUrls[0].path("name").asText(), publicConfig.body())

        val adminConfig = get(adminPort, "/v3/api-docs/swagger-config")
        assertEquals(200, adminConfig.statusCode(), adminConfig.body())
        val adminUrls = objectMapper.readTree(adminConfig.body()).path("urls")
        assertEquals(1, adminUrls.size(), adminConfig.body())
        assertEquals("admin", adminUrls[0].path("name").asText(), adminConfig.body())

        val publicDocs = get(publicPort, "/v3/api-docs/public")
        assertEquals(200, publicDocs.statusCode(), publicDocs.body())
        assertTrue(publicDocs.body().contains("/api/groups"), publicDocs.body())
        assertTrue(!publicDocs.body().contains("/api/admin/groups"), "public docs must not contain admin paths")

        val adminDocs = get(adminPort, "/v3/api-docs/admin")
        assertEquals(200, adminDocs.statusCode(), adminDocs.body())
        assertTrue(adminDocs.body().contains("/api/admin/groups"), adminDocs.body())

        assertEquals(404, get(publicPort, "/v3/api-docs/admin").statusCode())
        assertEquals(404, get(publicPort, "/v3/api-docs").statusCode())
        assertEquals(404, get(adminPort, "/v3/api-docs/public").statusCode())
        assertEquals(404, get(adminPort, "/v3/api-docs").statusCode())
    }

    // ---- websocket + realtime ----

    private class Collector : TextWebSocketHandler() {
        val messages = LinkedBlockingQueue<String>()
        override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
            messages.offer(message.payload)
        }
    }

    private fun connectWs(port: Int, collector: Collector): WebSocketSession {
        val client = StandardWebSocketClient()
        val future = client.execute(collector, org.springframework.web.socket.WebSocketHttpHeaders(), URI("ws://localhost:$port/ws/live"))
        return future.get(10, TimeUnit.SECONDS)
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

    @Test
    fun `successful admin write emits event on public websocket, failed write does not`() {
        val gameId = seedGame(null, null)
        val collector = Collector()
        val session = connectWs(publicPort, collector)
        try {
            val started = post(adminPort, "/api/games/$gameId/start")
            assertEquals(200, started.statusCode(), started.body())

            val event = collector.messages.poll(10, TimeUnit.SECONDS)
            assertNotNull(event, "expected realtime event for game start")
            val node = objectMapper.readTree(event!!)
            assertEquals("TOURNAMENT_DATA_CHANGED", node.path("type").asText())
            assertEquals("GAME", node.path("entity").asText())
            assertEquals("UPDATE", node.path("operation").asText())
            assertEquals(gameId, node.path("entityId").asInt())

            // Failed operation: decrement at 0 -> 409, no event.
            val conflict = post(adminPort, "/api/games/$gameId/score/team-a/decrement")
            assertEquals(409, conflict.statusCode(), conflict.body())
            assertNull(collector.messages.poll(2, TimeUnit.SECONDS), "failed write must not emit an event")
        } finally {
            session.close()
        }
    }
}
