package de.atiw.volleyball.admin

import de.atiw.volleyball.dto.DataEnvelope
import de.atiw.volleyball.dto.GenerationResultDto
import de.atiw.volleyball.dto.toDto
import de.atiw.volleyball.service.GameGenerationService
import de.atiw.volleyball.admin.common.NotFoundException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Automatic tournament game generation (admin only, port 8081).
 *
 * The frontend decides *when* to request generation; the backend validates
 * *whether* generation is possible and determines the participants itself —
 * no team IDs are accepted. All three operations are transactional and
 * idempotent: pairings already present in the target round are skipped
 * without modification, so repeating a call only reports higher
 * `gamesSkipped`. Every created game starts as `SCHEDULED` with `0 : 0` and
 * emits one `GAME / CREATE` realtime event after commit.
 */
@RestController
@RequestMapping("/api/admin/rounds/{id}/generate-games")
@Tag(name = "admin-generation", description = "Automatic tournament game generation")
class AdminGenerationController(
    private val generation: GameGenerationService
) {
    @PostMapping("/round-robin")
    @Operation(
        summary = "Generate round-robin games",
        description = "Every team plays every other team inside its own group exactly once " +
            "in the target round (n*(n-1)/2 games per group). Never creates cross-group " +
            "pairings. Idempotent: existing pairings are skipped."
    )
    fun roundRobin(@PathVariable id: String): ResponseEntity<DataEnvelope<GenerationResultDto>> =
        generate(numericId(id), GameGenerationService::generateRoundRobin)

    @PostMapping("/knockout")
    @Operation(
        summary = "Generate knockout games",
        description = "First stage (no previous knockout games): the top-ranked team of each " +
            "group leaderboard (round_number = 1) qualifies; the count must be a power of " +
            "two. Later stages: winners of the previous knockout stage qualify, which must " +
            "be fully FINISHED with exactly one winner per game (unfinished -> 409, " +
            "draw -> 400). Pairing is deterministic (first vs last). Idempotent."
    )
    fun knockout(@PathVariable id: String): ResponseEntity<DataEnvelope<GenerationResultDto>> =
        generate(numericId(id), GameGenerationService::generateKnockout)

    @PostMapping("/consolation")
    @Operation(
        summary = "Generate consolation games",
        description = "The lowest-ranked team of each group leaderboard (round_number = 1) " +
            "participates. Requires an even number of selected teams (no byes); every " +
            "team plays exactly once, never against its own group. Idempotent."
    )
    fun consolation(@PathVariable id: String): ResponseEntity<DataEnvelope<GenerationResultDto>> =
        generate(numericId(id), GameGenerationService::generateConsolation)

    private fun generate(
        roundId: Int,
        op: GameGenerationService.(Int) -> GameGenerationService.GenerationResult
    ): ResponseEntity<DataEnvelope<GenerationResultDto>> {
        val result = generation.op(roundId)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            DataEnvelope(
                GenerationResultDto(
                    roundId = roundId.toString(),
                    gamesCreated = result.created.size,
                    gamesSkipped = result.skipped,
                    games = result.created.map { it.toDto() }
                )
            )
        )
    }

    private fun numericId(id: String): Int =
        id.toIntOrNull() ?: throw NotFoundException("Round")
}
