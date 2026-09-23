package de.atiw.volleyball.teacher

import de.atiw.volleyball.dto.DataEnvelope
import de.atiw.volleyball.dto.TeamDto
import de.atiw.volleyball.dto.toDto
import de.atiw.volleyball.service.TeamService
import de.atiw.volleyball.teacher.common.NotFoundException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/teacher/teams")
class TeacherTeamController(
    private val teamService: TeamService
) {
    @PostMapping
    fun create(@RequestBody body: CreateTeamRequest): ResponseEntity<DataEnvelope<TeamDto>> {
        val created = teamService.create(body.groupId, body.clazz, body.name)
        return ResponseEntity
            .created(URI.create("/api/teams/${created.teamId}"))
            .body(DataEnvelope(created.toDto()))
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody body: UpdateTeamRequest
    ): DataEnvelope<TeamDto> {
        val numericId = id.toIntOrNull() ?: throw NotFoundException("Team")
        return DataEnvelope(teamService.update(numericId, body.groupId, body.clazz, body.name).toDto())
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Void> {
        val numericId = id.toIntOrNull() ?: throw NotFoundException("Team")
        teamService.delete(numericId)
        return ResponseEntity.noContent().build()
    }
}
