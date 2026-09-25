package de.atiw.volleyball.admin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.atiw.volleyball.admin.common.BadRequestException
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
    private val gameService: GameService,
    private val objectMapper: ObjectMapper
) {
    @PostMapping
    fun create(@RequestBody body: JsonNode): ResponseEntity<DataEnvelope<GameDto>> {
        val request = readRequest<CreateGameRequest>(body)
        val created = gameService.create(
            request.roundId, request.fieldId, request.teamAId, request.teamBId,
            request.refereeTeamId, request.scoreA, request.scoreB
        )
        return ResponseEntity
            .created(URI.create("/api/games/${created.gameId}"))
            .body(DataEnvelope(created.toDto()))
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody body: JsonNode
    ): DataEnvelope<GameDto> {
        val numericId = id.toIntOrNull() ?: throw NotFoundException("Game")
        val request = readRequest<UpdateGameRequest>(body)
        return DataEnvelope(
            gameService.update(
                numericId, request.roundId, request.fieldId, request.teamAId, request.teamBId,
                request.refereeTeamId, request.scoreA, request.scoreB
            ).toDto()
        )
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Void> {
        val numericId = id.toIntOrNull() ?: throw NotFoundException("Game")
        gameService.delete(numericId)
        return ResponseEntity.noContent().build()
    }

    /**
     * Binds the request body to the game DTO. The lifecycle `status` is not
     * part of CRUD: it is rejected here (instead of silently ignored) so a
     * client can never mistake a CRUD call for a status transition. Status
     * changes happen exclusively via `POST /api/games/{id}/start|end`.
     */
    private inline fun <reified T> readRequest(body: JsonNode): T {
        if (body.has("status")) {
            throw BadRequestException("Game status cannot be changed through game CRUD. Use POST /api/games/{id}/start or /end.")
        }
        try {
            return objectMapper.treeToValue(body, T::class.java)
        } catch (ex: Exception) {
            throw BadRequestException("Invalid request data.")
        }
    }
}
