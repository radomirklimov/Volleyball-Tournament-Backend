package de.atiw.volleyball.controller

import de.atiw.volleyball.dto.ApiErrorBody
import de.atiw.volleyball.dto.DataEnvelope
import de.atiw.volleyball.dto.ErrorEnvelope
import de.atiw.volleyball.dto.GroupDto
import de.atiw.volleyball.dto.toDto
import de.atiw.volleyball.service.GroupService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/groups")
class GroupController(
    private val groupService: GroupService
) {

    @GetMapping
    fun getGroups(): DataEnvelope<List<GroupDto>> {
        return DataEnvelope(groupService.getAll().map { it.toDto() })
    }

    @GetMapping("/{id}")
    fun getGroupById(@PathVariable id: String): ResponseEntity<Any> {
        val numericId = id.toIntOrNull()
            ?: return ResponseEntity.status(404)
                .body(ErrorEnvelope(ApiErrorBody("RESOURCE_NOT_FOUND", "Group not found")))
        val group = groupService.getById(numericId)
            ?: return ResponseEntity.status(404)
                .body(ErrorEnvelope(ApiErrorBody("RESOURCE_NOT_FOUND", "Group not found")))
        return ResponseEntity.ok(DataEnvelope(group.toDto()))
    }
}
