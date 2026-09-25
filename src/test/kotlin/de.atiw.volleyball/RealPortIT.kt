package de.atiw.volleyball

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
 * Base class for port-aware integration tests.
 *
 * Unlike the MockMvc-based tests (which cannot see ports), these tests use
 * real HTTP against both connectors of the single application:
 *
 * - [publicPort] (random) -> public read API + WS `/ws/live`
 * - [adminPort] (`18081`, deliberately different from the default `8081` so
 *   this context can live next to the default-configured one) -> admin API
 *
 * All subclasses share one application context, one server and one isolated
 * MariaDB container. Nothing is rolled back, so every test seeds uniquely
 * named data via [nextTag]; tests must never rely on fixed IDs.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["app.admin-port=18081"]
)
abstract class RealPortIT {

    companion object {
        @JvmStatic
        @ServiceConnection
        val mariadb: MariaDBContainer<*> = MariaDBContainer("mariadb:11.4").apply { start() }

        private val suffix = AtomicInteger(Random.nextInt(100000, 999999))
    }

    @LocalServerPort
    var publicPort: Int = 0

    protected val adminPort: Int = 18081

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    protected val http: HttpClient = HttpClient.newHttpClient()

    protected fun nextTag(prefix: String): String = "$prefix${suffix.getAndIncrement()}"

    // ---- raw HTTP ----

    protected fun get(port: Int, path: String): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder(URI("http://localhost:$port$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )

    protected fun post(port: Int, path: String, json: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json ?: ""))
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    protected fun put(port: Int, path: String, json: String): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder(URI("http://localhost:$port$path"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )

    protected fun delete(port: Int, path: String): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder(URI("http://localhost:$port$path")).DELETE().build(),
            HttpResponse.BodyHandlers.ofString()
        )

    // ---- envelope assertions ----

    protected fun assertStatus(res: HttpResponse<String>, expected: Int, what: String): HttpResponse<String> {
        assertEquals(expected, res.statusCode(), "$what -> status ${res.statusCode()}, body: ${res.body()}")
        return res
    }

    /** Parses the body and returns the `data` node, failing if the envelope is wrong. */
    protected fun dataOf(res: HttpResponse<String>, what: String): JsonNode {
        val root = objectMapper.readTree(res.body())
        assertTrue(root.has("data"), "$what -> missing data envelope: ${res.body()}")
        return root.path("data")
    }

    /** Parses the body and returns `error.code`, failing if the envelope is wrong. */
    protected fun errorCodeOf(res: HttpResponse<String>, what: String): String {
        val root = objectMapper.readTree(res.body())
        assertTrue(root.has("error"), "$what -> missing error envelope: ${res.body()}")
        val code = root.path("error").path("code").asText()
        assertTrue(code.isNotBlank(), "$what -> missing error.code: ${res.body()}")
        assertTrue(root.path("error").path("message").asText().isNotBlank(), "$what -> missing error.message: ${res.body()}")
        return code
    }

    protected fun assertError(res: HttpResponse<String>, status: Int, code: String, what: String) {
        assertStatus(res, status, what)
        assertEquals(code, errorCodeOf(res, what), "$what -> wrong error.code: ${res.body()}")
    }

    // ---- admin seeding (all via the admin port) ----

    protected fun createGroup(name: String): JsonNode {
        val res = assertStatus(post(adminPort, "/api/admin/groups", """{"name":"$name"}"""), 201, "create group")
        return dataOf(res, "create group")
    }

    protected fun createTeam(groupId: String, clazz: String, name: String): JsonNode {
        val res = assertStatus(
            post(adminPort, "/api/admin/teams", """{"groupId":$groupId,"class":"$clazz","name":"$name"}"""),
            201, "create team"
        )
        return dataOf(res, "create team")
    }

    protected fun createRound(number: Int): JsonNode {
        val res = assertStatus(post(adminPort, "/api/admin/rounds", """{"number":$number}"""), 201, "create round")
        return dataOf(res, "create round")
    }

    protected fun createField(name: String): JsonNode {
        val res = assertStatus(post(adminPort, "/api/admin/fields", """{"name":"$name"}"""), 201, "create field")
        return dataOf(res, "create field")
    }

    protected fun createGame(
        roundId: String,
        fieldId: String,
        teamA: String,
        teamB: String,
        referee: String,
        scoreA: Int,
        scoreB: Int
    ): JsonNode {
        val res = assertStatus(
            post(
                adminPort, "/api/admin/games",
                """{"roundId":$roundId,"fieldId":$fieldId,"teamAId":$teamA,"teamBId":$teamB,"refereeTeamId":$referee,"scoreA":$scoreA,"scoreB":$scoreB}"""
            ),
            201, "create game"
        )
        return dataOf(res, "create game")
    }

    /**
     * Deterministic fixture structure (unique values per call, spec-aligned
     * roles): two groups, Team A + Team B as players, Team C as referee, one
     * round, one court, one fresh game (0/0) and one played game (5/3).
     * Scores are always non-null integers.
     */
    protected data class Fixture(
        val groupA: String,
        val groupB: String,
        val teamA: String,
        val teamB: String,
        val referee: String,
        val roundId: String,
        val roundNumber: Int,
        val fieldId: String,
        val freshGame: Int,
        val playedGame: Int
    )

    protected fun newFixture(prefix: String = "F"): Fixture {
        val tag = nextTag(prefix)
        val groupA = createGroup("A$tag").path("groupId").asText()
        val groupB = createGroup("B$tag").path("groupId").asText()
        val teamA = createTeam(groupA, "C", "Team A$tag").path("teamId").asText()
        val teamB = createTeam(groupA, "C", "Team B$tag").path("teamId").asText()
        val referee = createTeam(groupB, "C", "Team C$tag").path("teamId").asText()
        val roundNumber = Random.nextInt(1000000, 9000000)
        val roundId = createRound(roundNumber).path("roundId").asText()
        val fieldId = createField("Court $tag").path("fieldId").asText()
        val freshGame = createGame(roundId, fieldId, teamA, teamB, referee, 0, 0)
            .path("gameId").asText().toInt()
        val playedGame = createGame(roundId, fieldId, teamA, teamB, referee, 5, 3)
            .path("gameId").asText().toInt()
        return Fixture(groupA, groupB, teamA, teamB, referee, roundId, roundNumber, fieldId, freshGame, playedGame)
    }

    // ---- websocket ----

    protected class Collector : TextWebSocketHandler() {
        val messages = LinkedBlockingQueue<String>()
        override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
            messages.offer(message.payload)
        }
    }

    protected fun connectWs(port: Int, collector: Collector): WebSocketSession {
        val client = StandardWebSocketClient()
        val future = client.execute(
            collector,
            org.springframework.web.socket.WebSocketHttpHeaders(),
            URI("ws://localhost:$port/ws/live")
        )
        return future.get(10, TimeUnit.SECONDS)
    }

    protected fun nextEvent(collector: Collector, timeoutSec: Long = 10): JsonNode? {
        val raw = collector.messages.poll(timeoutSec, TimeUnit.SECONDS) ?: return null
        return objectMapper.readTree(raw)
    }
}
