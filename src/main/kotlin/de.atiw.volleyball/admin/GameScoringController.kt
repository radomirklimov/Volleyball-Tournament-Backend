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
 * Game start action for admins. Scores are changed exclusively through the
 * admin game CRUD API (`PUT /api/admin/games/{id}`); this controller only
 * exposes the idempotent `/start` operation, which never modifies the game
 * and therefore emits no realtime event.
 */
@RestController
@RequestMapping("/api/games")
class GameScoringController(
    private val gameService: GameService
) {
    @PostMapping("/{id}/start")
    fun start(@PathVariable id: String): DataEnvelope<GameDto> =
        DataEnvelope(gameService.startGame(numericId(id)).toDto())

    private fun numericId(id: String): Int =
        id.toIntOrNull() ?: throw BadRequestException("Game ID must be numeric.")
}
