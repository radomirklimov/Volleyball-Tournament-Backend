package de.atiw.volleyball.controller

import de.atiw.volleyball.dto.DataEnvelope
import de.atiw.volleyball.dto.FieldDto
import de.atiw.volleyball.dto.toDto
import de.atiw.volleyball.service.FieldService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/fields")
class FieldController(
    private val fieldService: FieldService
) {

    @GetMapping
    fun getFields(): DataEnvelope<List<FieldDto>> {
        return DataEnvelope(fieldService.getAll().map { it.toDto() })
    }
}
