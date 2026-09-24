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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@AutoConfigureMockMvc
class TeacherRealtimeIntegrationTest : AbstractIntegrationTest() {

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

    private data class Setup(
        val roundId: Int,
        val fieldId: Int,
        val teamAId: Int,
        val teamBId: Int,
        val refereeId: Int,
        val gameId: Int
    )

    private fun setup(): Setup {
        val g = groupRepository.save(TournamentGroup(designation = "A"))
        val t1 = teamRepository.save(Team(group = g, teamClass = "C", name = "T1"))
        val t2 = teamRepository.save(Team(group = g, teamClass = "C", name = "T2"))
        val t3 = teamRepository.save(Team(group = g, teamClass = "C", name = "T3"))
        val round = roundRepository.save(Round(roundNumber = 1))
        val field = fieldRepository.save(Field(name = "Court 1"))
        val game = gameRepository.save(Game(round = round, field = field, teamA = t1, teamB = t2, refereeTeam = t3))
        return Setup(round.roundId, field.fieldId, t1.teamId, t2.teamId, t3.teamId, game.gameId)
    }

    private fun gameBody(s: Setup, scoreA: Int = 0, scoreB: Int = 0) = mapOf(
        "roundId" to s.roundId,
        "fieldId" to s.fieldId,
        "teamAId" to s.teamAId,
        "teamBId" to s.teamBId,
        "refereeTeamId" to s.refereeId,
        "scoreA" to scoreA,
        "scoreB" to scoreB
    )

    @Test
    fun `websocket connection can be established and reconnects`() {
        val c = Collector()
        val session = connect(c)
        assertTrue(session.isOpen)
        session.close()
        val c2 = Collector()
        val session2 = connect(c2)
        assertTrue(session2.isOpen)
        session2.close()
    }

    @Test
    fun `successful teacher update broadcasts one update event`() {
        val s = setup()
        val c = Collector()
        val session = connect(c)

        mockMvc.put("/api/teacher/games/${s.gameId}") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(gameBody(s, 18, 21))
        }.andExpect { status { isOk() } }

        val event = nextEvent(c)
        assertEquals("TOURNAMENT_DATA_CHANGED", event.get("type").asText())
        assertEquals("GAME", event.get("entity").asText())
        assertEquals("UPDATE", event.get("operation").asText())
        assertEquals(s.gameId, event.get("entityId").asInt())
        assertTrue(c.messages.isEmpty())
        session.close()
    }

    @Test
    fun `successful teacher create and delete broadcast events`() {
        val s = setup()
        val c = Collector()
        val session = connect(c)

        val createResult = mockMvc.post("/api/teacher/games") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(gameBody(s, 5, 7))
        }.andExpect { status { isCreated() } }.andReturn()
        val createdId = objectMapper.readTree(createResult.response.contentAsString)
            .get("data").get("gameId").asText().toInt()

        val created = nextEvent(c)
        assertEquals("CREATE", created.get("operation").asText())
        assertEquals(createdId, created.get("entityId").asInt())

        mockMvc.delete("/api/teacher/games/$createdId")
            .andExpect { status { isNoContent() } }

        val deleted = nextEvent(c)
        assertEquals("DELETE", deleted.get("operation").asText())
        assertEquals(createdId, deleted.get("entityId").asInt())
        session.close()
    }

    @Test
    fun `failed transaction does not broadcast event`() {
        val s = setup()
        val c = Collector()
        val session = connect(c)

        mockMvc.post("/api/teacher/games") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(gameBody(s).toMutableMap().apply { put("scoreA", -1) })
        }.andExpect { status { isBadRequest() } }

        val unexpected = c.messages.poll(1, TimeUnit.SECONDS)
        assertTrue(unexpected == null, "No event expected for failed write, but got: $unexpected")
        assertEquals(1, gameRepository.count())
        session.close()
    }

    @Test
    fun `multiple clients receive same event and survivor stays healthy after disconnect`() {
        val s = setup()
        val a = Collector()
        val b = Collector()
        val sessionA = connect(a)
        val sessionB = connect(b)

        mockMvc.put("/api/teacher/games/${s.gameId}") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(gameBody(s, 10, 12))
        }.andExpect { status { isOk() } }

        val eventA = nextEvent(a)
        val eventB = nextEvent(b)
        assertEquals(eventA.get("entityId").asInt(), eventB.get("entityId").asInt())
        assertEquals("UPDATE", eventA.get("operation").asText())

        sessionA.close()

        mockMvc.put("/api/teacher/games/${s.gameId}") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(gameBody(s, 11, 12))
        }.andExpect { status { isOk() } }

        val second = nextEvent(b)
        assertEquals("UPDATE", second.get("operation").asText())
        sessionB.close()
    }
}
