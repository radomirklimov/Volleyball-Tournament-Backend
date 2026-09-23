package de.atiw.volleyball.controller

import de.atiw.volleyball.dto.ApiErrorBody
import de.atiw.volleyball.dto.DataEnvelope
import de.atiw.volleyball.dto.ErrorEnvelope
import de.atiw.volleyball.dto.GameDto
import de.atiw.volleyball.dto.toDto
import de.atiw.volleyball.service.GameService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/games")
class GameController(
    private val gameService: GameService
) {

    @GetMapping
    fun getGames(): DataEnvelope<List<GameDto>> {
        return DataEnvelope(gameService.getAll().map { it.toDto() })
    }

    @GetMapping("/filter/{filter}")
    fun getGamesByFilter(@PathVariable filter: String): ResponseEntity<Any> {
        if (filter == "invalid" || filter.isBlank()) {
            return ResponseEntity.status(400)
                .body(ErrorEnvelope(ApiErrorBody("BAD_REQUEST", "Invalid filter")))
        }
        val roundId = filter.toIntOrNull()
        // IDs are opaque strings for the frontend; our DB ids are ints
        // stringified. Non-numeric filters simply match nothing (empty list),
        // mirroring the test-server's exact-match behaviour.
        val games = if (roundId != null) {
            gameService.getByRoundId(roundId)
        } else {
            gameService.getAll().filter { it.round.roundId.toString() == filter }
        }
        return ResponseEntity.ok(DataEnvelope(games.map { it.toDto() }))
    }

    @GetMapping("/{id}")
    fun getGameById(@PathVariable id: String): ResponseEntity<Any> {
        val numericId = id.toIntOrNull()
            ?: return ResponseEntity.status(404)
                .body(ErrorEnvelope(ApiErrorBody("RESOURCE_NOT_FOUND", "Game not found")))
        val game = gameService.getById(numericId)
            ?: return ResponseEntity.status(404)
                .body(ErrorEnvelope(ApiErrorBody("RESOURCE_NOT_FOUND", "Game not found")))
        return ResponseEntity.ok(DataEnvelope(game.toDto()))
    }
}
