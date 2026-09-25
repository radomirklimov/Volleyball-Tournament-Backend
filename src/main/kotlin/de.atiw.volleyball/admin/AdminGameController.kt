package de.atiw.volleyball.admin

import de.atiw.volleyball.dto.DataEnvelope
import de.atiw.volleyball.dto.GameDto
import de.atiw.volleyball.dto.toDto
import de.atiw.volleyball.service.GameService
import de.atiw.volleyball.admin.common.NotFoundException
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
@RequestMapping("/api/admin/games")
class AdminGameController(
    private val gameService: GameService
) {
    @PostMapping
    fun create(@RequestBody body: CreateGameRequest): ResponseEntity<DataEnvelope<GameDto>> {
        val created = gameService.create(
            body.roundId, body.fieldId, body.teamAId, body.teamBId,
            body.refereeTeamId, body.scoreA, body.scoreB
        )
        return ResponseEntity
            .created(URI.create("/api/games/${created.gameId}"))
            .body(DataEnvelope(created.toDto()))
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody body: UpdateGameRequest
    ): DataEnvelope<GameDto> {
        val numericId = id.toIntOrNull() ?: throw NotFoundException("Game")
        return DataEnvelope(
            gameService.update(
                numericId, body.roundId, body.fieldId, body.teamAId, body.teamBId,
                body.refereeTeamId, body.scoreA, body.scoreB
            ).toDto()
        )
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Void> {
        val numericId = id.toIntOrNull() ?: throw NotFoundException("Game")
        gameService.delete(numericId)
        return ResponseEntity.noContent().build()
    }
}
