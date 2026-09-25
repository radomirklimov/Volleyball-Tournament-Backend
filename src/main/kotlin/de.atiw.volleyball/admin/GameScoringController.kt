package de.atiw.volleyball.admin

import de.atiw.volleyball.dto.DataEnvelope
import de.atiw.volleyball.dto.GameDto
import de.atiw.volleyball.dto.toDto
import de.atiw.volleyball.service.GameService
import de.atiw.volleyball.admin.common.BadRequestException
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Game lifecycle actions for admins (`SCHEDULED -> RUNNING -> FINISHED`).
 * No request bodies — the endpoint fully describes the action. Every
 * state-changing call publishes a realtime event after commit via
 * [GameService]. Scores are changed exclusively through the admin game CRUD
 * API (`PUT /api/admin/games/{id}`) and are never modified here.
 */
@RestController
@RequestMapping("/api/games")
class GameScoringController(
    private val gameService: GameService
) {
    @PostMapping("/{id}/start")
    fun start(@PathVariable id: String): DataEnvelope<GameDto> =
        DataEnvelope(gameService.startGame(numericId(id)).toDto())

    @PostMapping("/{id}/end")
    fun end(@PathVariable id: String): DataEnvelope<GameDto> =
        DataEnvelope(gameService.endGame(numericId(id)).toDto())

    private fun numericId(id: String): Int =
        id.toIntOrNull() ?: throw BadRequestException("Game ID must be numeric.")
}
