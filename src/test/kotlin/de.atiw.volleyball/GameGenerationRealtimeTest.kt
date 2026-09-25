package de.atiw.volleyball

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.atiw.volleyball.entity.Field
import de.atiw.volleyball.entity.Game
import de.atiw.volleyball.entity.Round
import de.atiw.volleyball.entity.Team
import de.atiw.volleyball.entity.TournamentGroup
import de.atiw.volleyball.repository.FieldRepository
import de.atiw.volleyball.repository.GameRepository
import de.atiw.volleyball.repository.GroupRepository
import de.atiw.volleyball.repository.RoundRepository
import de.atiw.volleyball.repository.TeamRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Realtime delivery for game generation: N created games produce exactly N
 * `GAME / CREATE` events after commit; skipped or failed generation produces
 * none. Non-transactional with manual cleanup (events only fire after a real
 * commit), following the established realtime-test pattern.
 */
@AutoConfigureMockMvc
class GameGenerationRealtimeTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var groupRepository: GroupRepository

    @Autowired
    lateinit var teamRepository: TeamRepository

    @Autowired
    lateinit var roundRepository: RoundRepository

    @Autowired
    lateinit var fieldRepository: FieldRepository

    @Autowired
    lateinit var gameRepository: GameRepository

    @LocalServerPort
    var port: Int = 0

    @BeforeEach
    @AfterEach
    fun cleanDatabase() {
        gameRepository.deleteAll()
        teamRepository.deleteAll()
        groupRepository.deleteAll()
        roundRepository.deleteAll()
        fieldRepository.deleteAll()
    }

    private class Collector {
        val messages = LinkedBlockingQueue<String>()
        val handler = object : TextWebSocketHandler() {
            override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
                messages.add(message.payload)
            }
        }
    }

    private fun connect(collector: Collector): WebSocketSession {
        val client = StandardWebSocketClient()
        return client.execute(
            collector.handler,
            org.springframework.web.socket.WebSocketHttpHeaders(),
            java.net.URI("ws://localhost:$port/ws/live")
        ).get(5, TimeUnit.SECONDS)
    }

    private fun nextEvent(collector: Collector): JsonNode {
        val raw = collector.messages.poll(5, TimeUnit.SECONDS)
            ?: throw AssertionError("Expected WebSocket event but received none.")
        return objectMapper.readTree(raw)
    }

    private fun setup(): Round {
        val g = groupRepository.save(TournamentGroup(designation = "A"))
        teamRepository.save(Team(group = g, teamClass = "C", name = "T1"))
        teamRepository.save(Team(group = g, teamClass = "C", name = "T2"))
        teamRepository.save(Team(group = g, teamClass = "C", name = "T3"))
        roundRepository.save(Round(roundNumber = 1))
        fieldRepository.save(Field(name = "Court 1"))
        return roundRepository.save(Round(roundNumber = 2))
    }

    private fun generate(roundId: Int) =
        mockMvc.post("/api/admin/rounds/$roundId/generate-games/round-robin")
            .andExpect { status { isCreated() } }
            .andReturn().response.contentAsString

    @Test
    fun `each created game emits one CREATE event`() {
        val target = setup()
        val collector = Collector()
        val session = connect(collector)

        val body = generate(target.roundId)
        val games = objectMapper.readTree(body).get("data").get("games")
        assertEquals(3, games.size())

        val entityIds = mutableSetOf<Int>()
        repeat(3) {
            val event = nextEvent(collector)
            assertEquals("TOURNAMENT_DATA_CHANGED", event.get("type").asText())
            assertEquals("GAME", event.get("entity").asText())
            assertEquals("CREATE", event.get("operation").asText())
            entityIds += event.get("entityId").asInt()
        }
        val expected = games.map { it.get("gameId").asText().toInt() }.toSet()
        assertEquals(expected, entityIds)
        assertTrue(collector.messages.isEmpty(), "Expected exactly three events.")
        session.close()
    }

    @Test
    fun `duplicate generation emits no events`() {
        val target = setup()
        generate(target.roundId)

        val collector = Collector()
        val session = connect(collector)

        val body = generate(target.roundId)
        assertEquals(0, objectMapper.readTree(body).get("data").get("gamesCreated").asInt())

        val unexpected = collector.messages.poll(2, TimeUnit.SECONDS)
        assertTrue(unexpected == null, "No events expected for duplicate generation, but got: $unexpected")
        session.close()
    }

    @Test
    fun `failed generation emits no events`() {
        val target = setup()
        fieldRepository.deleteAll()

        val collector = Collector()
        val session = connect(collector)

        mockMvc.post("/api/admin/rounds/${target.roundId}/generate-games/round-robin")
            .andExpect { status { isBadRequest() } }

        val unexpected = collector.messages.poll(2, TimeUnit.SECONDS)
        assertTrue(unexpected == null, "No events expected for failed generation, but got: $unexpected")
        assertEquals(0, gameRepository.count())
        session.close()
    }

    @Test
    fun `knockout generation emits one CREATE event per game`() {
        val gA = groupRepository.save(TournamentGroup(designation = "A"))
        val gB = groupRepository.save(TournamentGroup(designation = "B"))
        teamRepository.save(Team(group = gA, teamClass = "C", name = "A1"))
        teamRepository.save(Team(group = gB, teamClass = "C", name = "B1"))
        val r1 = roundRepository.save(Round(roundNumber = 1))
        val f = fieldRepository.save(Field(name = "Court 1"))
        val ref = teamRepository.save(Team(group = gB, teamClass = "C", name = "B2"))
        val tA = teamRepository.findAll().first { it.name == "A1" }
        val tB = teamRepository.findAll().first { it.name == "B1" }
        gameRepository.save(
            Game(round = r1, field = f, teamA = tA, teamB = tB, refereeTeam = ref, pointsA = 25, pointsB = 10)
        )
        val target = roundRepository.save(Round(roundNumber = 2))

        val collector = Collector()
        val session = connect(collector)

        val body = mockMvc.post("/api/admin/rounds/${target.roundId}/generate-games/knockout")
            .andExpect { status { isCreated() } }
            .andReturn().response.contentAsString
        assertEquals(1, objectMapper.readTree(body).get("data").get("gamesCreated").asInt())

        val event = nextEvent(collector)
        assertEquals("GAME", event.get("entity").asText())
        assertEquals("CREATE", event.get("operation").asText())
        assertTrue(collector.messages.isEmpty(), "Expected exactly one event.")
        session.close()
    }
}
