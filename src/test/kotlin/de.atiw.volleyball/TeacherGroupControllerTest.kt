package de.atiw.volleyball

import com.fasterxml.jackson.databind.ObjectMapper
import de.atiw.volleyball.entity.TournamentGroup
import de.atiw.volleyball.repository.GroupRepository
import de.atiw.volleyball.repository.TeamRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional

@AutoConfigureMockMvc
@Transactional
class TeacherGroupControllerTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var groupRepository: GroupRepository

    @Autowired
    lateinit var teamRepository: TeamRepository

    @Test
    fun `createGroup returns 201 and created group`() {
        mockMvc.post("/api/admin/groups") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("name" to "A"))
        }.andExpect {
            status { isCreated() }
            jsonPath("$.data.name") { value("A") }
        }
    }

    @Test
    fun `createGroup rejects blank name`() {
        mockMvc.post("/api/admin/groups") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("name" to "  "))
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `createGroup rejects duplicate name with 409`() {
        groupRepository.save(TournamentGroup(designation = "A"))

        mockMvc.post("/api/admin/groups") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("name" to "A"))
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `updateGroup returns 404 when group does not exist`() {
        mockMvc.put("/api/admin/groups/999999") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("name" to "B"))
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `updateGroup returns 200 and updated group`() {
        val group = groupRepository.save(TournamentGroup(designation = "A"))

        mockMvc.put("/api/admin/groups/${group.groupId}") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("name" to "B"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.name") { value("B") }
        }
    }

    @Test
    fun `updateGroup rejects duplicate name with 409`() {
        groupRepository.save(TournamentGroup(designation = "A"))
        val b = groupRepository.save(TournamentGroup(designation = "B"))

        mockMvc.put("/api/admin/groups/${b.groupId}") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("name" to "A"))
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `deleteGroup returns 204 when unused`() {
        val group = groupRepository.save(TournamentGroup(designation = "Z"))

        mockMvc.delete("/api/admin/groups/${group.groupId}")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `deleteGroup returns 409 when teams reference group`() {
        val group = groupRepository.save(TournamentGroup(designation = "A"))
        teamRepository.save(
            de.atiw.volleyball.entity.Team(
                group = group,
                teamClass = "U18",
                name = "School 1"
            )
        )

        mockMvc.delete("/api/admin/groups/${group.groupId}")
            .andExpect { status { isConflict() } }
    }

    @Test
    fun `deleteGroup returns 404 when missing`() {
        mockMvc.delete("/api/admin/groups/999999")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `public GET still works after teacher write`() {
        mockMvc.post("/api/admin/groups") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("name" to "C"))
        }.andExpect { status { isCreated() } }

        mockMvc.get("/api/groups").andExpect { status { isOk() } }
    }
}
