package de.atiw.volleyball

import de.atiw.volleyball.entity.Field
import de.atiw.volleyball.entity.Round
import de.atiw.volleyball.entity.TournamentGroup
import de.atiw.volleyball.repository.FieldRepository
import de.atiw.volleyball.repository.GroupRepository
import de.atiw.volleyball.repository.RoundRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional

@AutoConfigureMockMvc
@Transactional
class PublicApiContractTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var groupRepository: GroupRepository

    @Autowired
    lateinit var roundRepository: RoundRepository

    @Autowired
    lateinit var fieldRepository: FieldRepository

    @Test
    fun `GET api-groups returns all groups`() {
        groupRepository.save(TournamentGroup(designation = "A"))
        groupRepository.save(TournamentGroup(designation = "B"))

        mockMvc.get("/api/groups")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.length()") { value(2) } }
    }

    @Test
    fun `GET api-groups-id returns 404 for missing id`() {
        mockMvc.get("/api/groups/999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `GET api-rounds returns all rounds`() {
        roundRepository.save(Round(roundNumber = 1))

        mockMvc.get("/api/rounds")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data[0].number") { value(1) } }
    }

    @Test
    fun `GET api-fields returns all fields`() {
        fieldRepository.save(Field(name = "Court 1"))

        mockMvc.get("/api/fields")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data[0].name") { value("Court 1") } }
    }
}
