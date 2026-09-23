package de.atiw.volleyball.controller

import de.atiw.volleyball.dto.DataEnvelope
import de.atiw.volleyball.dto.RoundDto
import de.atiw.volleyball.dto.toDto
import de.atiw.volleyball.service.RoundService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/rounds")
class RoundController(
    private val roundService: RoundService
) {

    @GetMapping
    fun getRounds(): DataEnvelope<List<RoundDto>> {
        return DataEnvelope(roundService.getAll().map { it.toDto() })
    }
}
