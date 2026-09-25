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
class TeacherRoundAndFieldControllerTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var roundRepository: RoundRepository

    @Autowired
    lateinit var fieldRepository: FieldRepository

    @Autowired
    lateinit var groupRepository: GroupRepository

    @Autowired
    lateinit var teamRepository: TeamRepository

    @Autowired
    lateinit var gameRepository: GameRepository

    private fun gameSetup(): Triple<Round, Field, List<Team>> {
        val g = groupRepository.save(TournamentGroup(designation = "A"))
        val teams = (1..3).map { teamRepository.save(Team(group = g, teamClass = "C", name = "T$it")) }
        val round = roundRepository.save(Round(roundNumber = 1))
        val field = fieldRepository.save(Field(name = "Court 1"))
        gameRepository.save(Game(round = round, field = field, teamA = teams[0], teamB = teams[1], refereeTeam = teams[2]))
        return Triple(round, field, teams)
    }

    @Test
    fun `createRound returns 201`() {
        mockMvc.post("/api/admin/rounds") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("number" to 6))
        }.andExpect {
            status { isCreated() }
            jsonPath("$.data.number") { value(6) }
        }
    }

    @Test
    fun `createRound rejects non-positive number`() {
        mockMvc.post("/api/admin/rounds") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("number" to 0))
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `createRound rejects duplicate number`() {
        roundRepository.save(Round(roundNumber = 2))

        mockMvc.post("/api/admin/rounds") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("number" to 2))
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `updateRound updates number`() {
        val round = roundRepository.save(Round(roundNumber = 3))

        mockMvc.put("/api/admin/rounds/${round.roundId}") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("number" to 7))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.number") { value(7) }
        }
    }

    @Test
    fun `deleteRound returns 409 when games reference round`() {
        val (round, _, _) = gameSetup()

        mockMvc.delete("/api/admin/rounds/${round.roundId}")
            .andExpect { status { isConflict() } }
    }

    @Test
    fun `deleteRound returns 204 when unused`() {
        val round = roundRepository.save(Round(roundNumber = 9))

        mockMvc.delete("/api/admin/rounds/${round.roundId}")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `createField returns 201`() {
        mockMvc.post("/api/admin/fields") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("name" to "Court 5"))
        }.andExpect {
            status { isCreated() }
            jsonPath("$.data.name") { value("Court 5") }
        }
    }

    @Test
    fun `createField rejects blank name`() {
        mockMvc.post("/api/admin/fields") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("name" to " "))
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `createField rejects duplicate name`() {
        fieldRepository.save(Field(name = "Court 1"))

        mockMvc.post("/api/admin/fields") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("name" to "Court 1"))
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `updateField updates name`() {
        val field = fieldRepository.save(Field(name = "Old"))

        mockMvc.put("/api/admin/fields/${field.fieldId}") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("name" to "Court A"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.name") { value("Court A") }
        }
    }

    @Test
    fun `deleteField returns 409 when games reference field`() {
        val (_, field, _) = gameSetup()

        mockMvc.delete("/api/admin/fields/${field.fieldId}")
            .andExpect { status { isConflict() } }
    }

    @Test
    fun `deleteField returns 204 when unused`() {
        val field = fieldRepository.save(Field(name = "Alone"))

        mockMvc.delete("/api/admin/fields/${field.fieldId}")
            .andExpect { status { isNoContent() } }
    }
}
