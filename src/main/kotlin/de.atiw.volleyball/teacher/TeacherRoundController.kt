package de.atiw.volleyball.teacher

import de.atiw.volleyball.dto.DataEnvelope
import de.atiw.volleyball.dto.RoundDto
import de.atiw.volleyball.dto.toDto
import de.atiw.volleyball.service.RoundService
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
@RequestMapping("/api/teacher/rounds")
class TeacherRoundController(
    private val roundService: RoundService
) {
    @PostMapping
    fun create(@RequestBody body: CreateRoundRequest): ResponseEntity<DataEnvelope<RoundDto>> {
        val created = roundService.create(body.number)
        return ResponseEntity
            .created(URI.create("/api/rounds/${created.roundId}"))
            .body(DataEnvelope(created.toDto()))
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody body: UpdateRoundRequest
    ): DataEnvelope<RoundDto> {
        val numericId = id.toIntOrNull() ?: throw NotFoundException("Round")
        return DataEnvelope(roundService.update(numericId, body.number).toDto())
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Void> {
        val numericId = id.toIntOrNull() ?: throw NotFoundException("Round")
        roundService.delete(numericId)
        return ResponseEntity.noContent().build()
    }
}
