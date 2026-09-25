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
class TeacherTeamControllerTest : AbstractIntegrationTest() {

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

    private fun group(name: String = "A") = groupRepository.save(TournamentGroup(designation = name))

    @Test
    fun `createTeam returns 201`() {
        val g = group()
        val body = mapOf("groupId" to g.groupId, "class" to "U18 Boys", "name" to "New School")

        mockMvc.post("/api/admin/teams") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(body)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.data.name") { value("New School") }
        }
    }

    @Test
    fun `createTeam rejects missing group`() {
        val body = mapOf("groupId" to 999999, "class" to "U18", "name" to "X")

        mockMvc.post("/api/admin/teams") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(body)
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `createTeam rejects blank class`() {
        val g = group()
        val body = mapOf("groupId" to g.groupId, "class" to " ", "name" to "X")

        mockMvc.post("/api/admin/teams") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(body)
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `createTeam rejects blank name`() {
        val g = group()
        val body = mapOf("groupId" to g.groupId, "class" to "U18", "name" to "")

        mockMvc.post("/api/admin/teams") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(body)
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `updateTeam updates group class and name`() {
        val g1 = group("A")
        val g2 = group("B")
        val team = teamRepository.save(Team(group = g1, teamClass = "U18", name = "Old"))

        val body = mapOf("groupId" to g2.groupId, "class" to "U20", "name" to "New")

        mockMvc.put("/api/admin/teams/${team.teamId}") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(body)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.name") { value("New") }
        }
    }

    @Test
    fun `updateTeam returns 404 when missing`() {
        val g = group()
        val body = mapOf("groupId" to g.groupId, "class" to "U18", "name" to "X")

        mockMvc.put("/api/admin/teams/999999") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(body)
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `deleteTeam returns 409 when used by game`() {
        val g = group()
        val t1 = teamRepository.save(Team(group = g, teamClass = "C", name = "T1"))
        val t2 = teamRepository.save(Team(group = g, teamClass = "C", name = "T2"))
        val t3 = teamRepository.save(Team(group = g, teamClass = "C", name = "T3"))
        val round = roundRepository.save(Round(roundNumber = 1))
        val field = fieldRepository.save(Field(name = "Court 1"))
        gameRepository.save(Game(round = round, field = field, teamA = t1, teamB = t2, refereeTeam = t3))

        mockMvc.delete("/api/admin/teams/${t1.teamId}")
            .andExpect { status { isConflict() } }
    }

    @Test
    fun `deleteTeam returns 204 when unused`() {
        val g = group()
        val team = teamRepository.save(Team(group = g, teamClass = "C", name = "Solo"))

        mockMvc.delete("/api/admin/teams/${team.teamId}")
            .andExpect { status { isNoContent() } }
    }
}
