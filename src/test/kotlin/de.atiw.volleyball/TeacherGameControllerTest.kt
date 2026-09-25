package de.atiw.volleyball

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
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional

@AutoConfigureMockMvc
@Transactional
class TeacherGameControllerTest : AbstractIntegrationTest() {

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

    private data class Setup(
        val roundId: Int,
        val fieldId: Int,
        val teamAId: Int,
        val teamBId: Int,
        val refereeId: Int
    )

    private fun setup(): Setup {
        val g = groupRepository.save(TournamentGroup(designation = "A"))
        val t1 = teamRepository.save(Team(group = g, teamClass = "C", name = "T1"))
        val t2 = teamRepository.save(Team(group = g, teamClass = "C", name = "T2"))
        val t3 = teamRepository.save(Team(group = g, teamClass = "C", name = "T3"))
        val round = roundRepository.save(Round(roundNumber = 1))
        val field = fieldRepository.save(Field(name = "Court 1"))
        return Setup(round.roundId, field.fieldId, t1.teamId, t2.teamId, t3.teamId)
    }

    private fun gameBody(s: Setup, extra: Map<String, Any?> = emptyMap()): Map<String, Any?> =
        mapOf(
            "roundId" to s.roundId,
            "fieldId" to s.fieldId,
            "teamAId" to s.teamAId,
            "teamBId" to s.teamBId,
            "refereeTeamId" to s.refereeId,
            "scoreA" to 0,
            "scoreB" to 0
        ) + extra

    @Test
    fun `createGame returns 201`() {
        val s = setup()

        mockMvc.post("/api/admin/games") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(gameBody(s))
        }.andExpect {
            status { isCreated() }
            jsonPath("$.data.scoreA") { value(0) }
            jsonPath("$.data.status") { value("SCHEDULED") }
        }
    }

    @Test
    fun `createGame rejects missing round`() {
        val s = setup()

        mockMvc.post("/api/admin/games") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(gameBody(s, mapOf("roundId" to 999999)))
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `createGame rejects negative score`() {
        val s = setup()

        mockMvc.post("/api/admin/games") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(gameBody(s, mapOf("scoreA" to -1)))
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `createGame defaults omitted scores to 0`() {
        val s = setup()
        val body = gameBody(s).toMutableMap().apply { remove("scoreA"); remove("scoreB") }

        val result = mockMvc.post("/api/admin/games") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(body)
        }.andExpect { status { isCreated() } }.andReturn()

        val data = objectMapper.readTree(result.response.contentAsString).get("data")
        assert(data.get("scoreA").asInt() == 0)
        assert(data.get("scoreB").asInt() == 0)
    }

    @Test
    fun `createGame rejects explicit null scores with 400`() {
        val s = setup()

        mockMvc.post("/api/admin/games") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(gameBody(s, mapOf("scoreA" to null, "scoreB" to null)))
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `updateGame rejects null scores with 400`() {
        val s = setup()
        val round = roundRepository.findById(s.roundId).orElseThrow()
        val field = fieldRepository.findById(s.fieldId).orElseThrow()
        val teamA = teamRepository.findById(s.teamAId).orElseThrow()
        val teamB = teamRepository.findById(s.teamBId).orElseThrow()
        val ref = teamRepository.findById(s.refereeId).orElseThrow()
        val game = gameRepository.save(Game(round = round, field = field, teamA = teamA, teamB = teamB, refereeTeam = ref, pointsA = 25, pointsB = 21))

        mockMvc.put("/api/admin/games/${game.gameId}") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(gameBody(s, mapOf("scoreA" to null, "scoreB" to null)))
        }.andExpect { status { isBadRequest() } }

        val reloaded = gameRepository.findById(game.gameId).orElseThrow()
        assert(reloaded.pointsA == 25 && reloaded.pointsB == 21)
    }

    @Test
    fun `createGame rejects same team for A and B`() {
        val s = setup()

        mockMvc.post("/api/admin/games") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(gameBody(s, mapOf("teamBId" to s.teamAId)))
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `createGame rejects referee equal to team A`() {
        val s = setup()

        mockMvc.post("/api/admin/games") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(gameBody(s, mapOf("refereeTeamId" to s.teamAId)))
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `createGame rejects referee equal to team B`() {
        val s = setup()

        mockMvc.post("/api/admin/games") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(gameBody(s, mapOf("refereeTeamId" to s.teamBId)))
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `updateGame can change only score values`() {
        val s = setup()
        val g = groupRepository.save(TournamentGroup(designation = "B"))
        val t1 = teamRepository.save(Team(group = g, teamClass = "C", name = "X1"))
        val t2 = teamRepository.save(Team(group = g, teamClass = "C", name = "X2"))
        val t3 = teamRepository.save(Team(group = g, teamClass = "C", name = "X3"))
        val round = roundRepository.findById(s.roundId).orElseThrow()
        val field = fieldRepository.findById(s.fieldId).orElseThrow()
        val game = gameRepository.save(Game(round = round, field = field, teamA = t1, teamB = t2, refereeTeam = t3))

        val body = mapOf(
            "roundId" to s.roundId,
            "fieldId" to s.fieldId,
            "teamAId" to t1.teamId,
            "teamBId" to t2.teamId,
            "refereeTeamId" to t3.teamId,
            "scoreA" to 18,
            "scoreB" to 21
        )

        mockMvc.put("/api/admin/games/${game.gameId}") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(body)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.scoreA") { value(18) }
            jsonPath("$.data.scoreB") { value(21) }
            jsonPath("$.data.status") { value("SCHEDULED") }
        }
    }

    @Test
    fun `updateGame keeps lifecycle status and rejects status field`() {
        val s = setup()
        val round = roundRepository.findById(s.roundId).orElseThrow()
        val field = fieldRepository.findById(s.fieldId).orElseThrow()
        val teamA = teamRepository.findById(s.teamAId).orElseThrow()
        val teamB = teamRepository.findById(s.teamBId).orElseThrow()
        val ref = teamRepository.findById(s.refereeId).orElseThrow()
        val game = gameRepository.save(
            Game(
                round = round, field = field, teamA = teamA, teamB = teamB, refereeTeam = ref,
                pointsA = 5, pointsB = 3, status = de.atiw.volleyball.entity.GameStatus.RUNNING
            )
        )

        // A normal update preserves the lifecycle status.
        mockMvc.put("/api/admin/games/${game.gameId}") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(gameBody(s, mapOf("scoreA" to 6, "scoreB" to 3)))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.scoreA") { value(6) }
            jsonPath("$.data.status") { value("RUNNING") }
        }

        // Smuggling "status" into the update body is rejected.
        mockMvc.put("/api/admin/games/${game.gameId}") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(gameBody(s, mapOf("scoreA" to 6, "scoreB" to 3, "status" to "FINISHED")))
        }.andExpect { status { isBadRequest() } }

        val reloaded = gameRepository.findById(game.gameId).orElseThrow()
        assert(reloaded.status == de.atiw.volleyball.entity.GameStatus.RUNNING)
    }

    @Test
    fun `updateGame returns 404 when missing`() {
        val s = setup()

        mockMvc.put("/api/admin/games/999999") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(gameBody(s))
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `deleteGame returns 204`() {
        val s = setup()
        val round = roundRepository.findById(s.roundId).orElseThrow()
        val field = fieldRepository.findById(s.fieldId).orElseThrow()
        val teamA = teamRepository.findById(s.teamAId).orElseThrow()
        val teamB = teamRepository.findById(s.teamBId).orElseThrow()
        val ref = teamRepository.findById(s.refereeId).orElseThrow()
        val game = gameRepository.save(Game(round = round, field = field, teamA = teamA, teamB = teamB, refereeTeam = ref))

        mockMvc.delete("/api/admin/games/${game.gameId}")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `deleteGame returns 404 when missing`() {
        mockMvc.delete("/api/admin/games/999999")
            .andExpect { status { isNotFound() } }
    }
}
