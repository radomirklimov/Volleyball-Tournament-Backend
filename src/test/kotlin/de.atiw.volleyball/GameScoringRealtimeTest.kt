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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@AutoConfigureMockMvc
class GameScoringRealtimeTest : AbstractIntegrationTest() {

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

    private fun game(scoreA: Int? = null, scoreB: Int? = null): Game {
        val g = groupRepository.save(TournamentGroup(designation = "A"))
        val t1 = teamRepository.save(Team(group = g, teamClass = "C", name = "T1"))
        val t2 = teamRepository.save(Team(group = g, teamClass = "C", name = "T2"))
        val t3 = teamRepository.save(Team(group = g, teamClass = "C", name = "T3"))
        val round = roundRepository.save(Round(roundNumber = 1))
        val field = fieldRepository.save(Field(name = "Court 1"))
        return gameRepository.save(
            Game(round = round, field = field, teamA = t1, teamB = t2, refereeTeam = t3, pointsA = scoreA, pointsB = scoreB)
        )
    }

    private fun assertGameUpdateEvent(collector: Collector, gameId: Int) {
        val event = nextEvent(collector)
        assertEquals("TOURNAMENT_DATA_CHANGED", event.get("type").asText())
        assertEquals("GAME", event.get("entity").asText())
        assertEquals("UPDATE", event.get("operation").asText())
        assertEquals(gameId, event.get("entityId").asInt())
        assertTrue(collector.messages.isEmpty(), "Expected exactly one event.")
    }

    @Test
    fun `start broadcasts one update event`() {
        val game = game(null, null)
        val collector = Collector()
        val session = connect(collector)

        mockMvc.post("/api/games/${game.gameId}/start")
            .andExpect { status { isOk() } }

        assertGameUpdateEvent(collector, game.gameId)
        session.close()
    }

    @Test
    fun `team-a increment broadcasts one update event`() {
        val game = game(0, 0)
        val collector = Collector()
        val session = connect(collector)

        mockMvc.post("/api/games/${game.gameId}/score/team-a/increment")
            .andExpect { status { isOk() } }

        assertGameUpdateEvent(collector, game.gameId)
        session.close()
    }

    @Test
    fun `team-a decrement broadcasts one update event`() {
        val game = game(5, 3)
        val collector = Collector()
        val session = connect(collector)

        mockMvc.post("/api/games/${game.gameId}/score/team-a/decrement")
            .andExpect { status { isOk() } }

        assertGameUpdateEvent(collector, game.gameId)
        session.close()
    }

    @Test
    fun `team-b increment broadcasts one update event`() {
        val game = game(0, 0)
        val collector = Collector()
        val session = connect(collector)

        mockMvc.post("/api/games/${game.gameId}/score/team-b/increment")
            .andExpect { status { isOk() } }

        assertGameUpdateEvent(collector, game.gameId)
        session.close()
    }

    @Test
    fun `team-b decrement broadcasts one update event`() {
        val game = game(5, 3)
        val collector = Collector()
        val session = connect(collector)

        mockMvc.post("/api/games/${game.gameId}/score/team-b/decrement")
            .andExpect { status { isOk() } }

        assertGameUpdateEvent(collector, game.gameId)
        session.close()
    }

    @Test
    fun `failed decrement sends no event and changes nothing`() {
        val game = game(0, 3)
        val collector = Collector()
        val session = connect(collector)

        mockMvc.post("/api/games/${game.gameId}/score/team-a/decrement")
            .andExpect { status { isConflict() } }

        assertEquals(0, gameRepository.findById(game.gameId).orElseThrow().pointsA)
        val unexpected = collector.messages.poll(1, TimeUnit.SECONDS)
        assertTrue(unexpected == null, "No event expected for failed write, but got: $unexpected")
        session.close()
    }

    @Test
    fun `concurrent increments do not lose updates`() {
        val game = game(10, 10)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val futures = (1..2).map {
                pool.submit {
                    mockMvc.post("/api/games/${game.gameId}/score/team-a/increment")
                        .andExpect { status { isOk() } }
                }
            }
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdown()
        }

        val scoreA = mockMvc.get("/api/games/${game.gameId}")
            .andExpect { status { isOk() } }
            .andReturn().let {
                objectMapper.readTree(it.response.contentAsString).get("data").get("scoreA").asInt()
            }
        assertEquals(12, scoreA)
    }
}
