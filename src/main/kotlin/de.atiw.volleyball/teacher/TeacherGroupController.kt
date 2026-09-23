package de.atiw.volleyball.teacher

import de.atiw.volleyball.dto.DataEnvelope
import de.atiw.volleyball.dto.GroupDto
import de.atiw.volleyball.dto.toDto
import de.atiw.volleyball.service.GroupService
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
@RequestMapping("/api/teacher/groups")
class TeacherGroupController(
    private val groupService: GroupService
) {
    @PostMapping
    fun create(@RequestBody body: CreateGroupRequest): ResponseEntity<DataEnvelope<GroupDto>> {
        val created = groupService.create(body.name)
        return ResponseEntity
            .created(URI.create("/api/groups/${created.groupId}"))
            .body(DataEnvelope(created.toDto()))
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody body: UpdateGroupRequest
    ): DataEnvelope<GroupDto> {
        val numericId = id.toIntOrNull() ?: throw NotFoundException("Group")
        return DataEnvelope(groupService.update(numericId, body.name).toDto())
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Void> {
        val numericId = id.toIntOrNull() ?: throw NotFoundException("Group")
        groupService.delete(numericId)
        return ResponseEntity.noContent().build()
    }
}
