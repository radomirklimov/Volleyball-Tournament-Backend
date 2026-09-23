package de.atiw.volleyball.teacher

import de.atiw.volleyball.dto.DataEnvelope
import de.atiw.volleyball.dto.FieldDto
import de.atiw.volleyball.dto.toDto
import de.atiw.volleyball.service.FieldService
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
@RequestMapping("/api/teacher/fields")
class TeacherFieldController(
    private val fieldService: FieldService
) {
    @PostMapping
    fun create(@RequestBody body: CreateFieldRequest): ResponseEntity<DataEnvelope<FieldDto>> {
        val created = fieldService.create(body.name)
        return ResponseEntity
            .created(URI.create("/api/fields/${created.fieldId}"))
            .body(DataEnvelope(created.toDto()))
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody body: UpdateFieldRequest
    ): DataEnvelope<FieldDto> {
        val numericId = id.toIntOrNull() ?: throw NotFoundException("Field")
        return DataEnvelope(fieldService.update(numericId, body.name).toDto())
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Void> {
        val numericId = id.toIntOrNull() ?: throw NotFoundException("Field")
        fieldService.delete(numericId)
        return ResponseEntity.noContent().build()
    }
}
