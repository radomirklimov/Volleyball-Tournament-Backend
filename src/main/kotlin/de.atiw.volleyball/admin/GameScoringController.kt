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
 * Button-action scoring for admins. No request bodies — the endpoint fully
 * describes the action. Every state-changing call publishes a realtime event
 * after commit via [GameService].
 */
@RestController
@RequestMapping("/api/games")
class GameScoringController(
    private val gameService: GameService
) {
    @PostMapping("/{id}/start")
    fun start(@PathVariable id: String): DataEnvelope<GameDto> =
        DataEnvelope(gameService.startGame(numericId(id)).toDto())

    @PostMapping("/{id}/score/team-a/increment")
    fun incrementTeamA(@PathVariable id: String): DataEnvelope<GameDto> =
        DataEnvelope(gameService.incrementTeamAScore(numericId(id)).toDto())

    @PostMapping("/{id}/score/team-a/decrement")
    fun decrementTeamA(@PathVariable id: String): DataEnvelope<GameDto> =
        DataEnvelope(gameService.decrementTeamAScore(numericId(id)).toDto())

    @PostMapping("/{id}/score/team-b/increment")
    fun incrementTeamB(@PathVariable id: String): DataEnvelope<GameDto> =
        DataEnvelope(gameService.incrementTeamBScore(numericId(id)).toDto())

    @PostMapping("/{id}/score/team-b/decrement")
    fun decrementTeamB(@PathVariable id: String): DataEnvelope<GameDto> =
        DataEnvelope(gameService.decrementTeamBScore(numericId(id)).toDto())

    private fun numericId(id: String): Int =
        id.toIntOrNull() ?: throw BadRequestException("Game ID must be numeric.")
}
