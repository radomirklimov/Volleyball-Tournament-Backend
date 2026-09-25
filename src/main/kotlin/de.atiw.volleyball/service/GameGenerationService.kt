package de.atiw.volleyball.service

import de.atiw.volleyball.admin.common.BadRequestException
import de.atiw.volleyball.admin.common.ConflictException
import de.atiw.volleyball.admin.common.NotFoundException
import de.atiw.volleyball.entity.Field
import de.atiw.volleyball.entity.Game
import de.atiw.volleyball.entity.GameStatus
import de.atiw.volleyball.entity.Round
import de.atiw.volleyball.repository.FieldRepository
import de.atiw.volleyball.repository.GameRepository
import de.atiw.volleyball.repository.GroupRepository
import de.atiw.volleyball.repository.RoundRepository
import de.atiw.volleyball.repository.TeamRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Automatic tournament game generation (admin only).
 *
 * The frontend decides *when* to request generation; this service decides
 * *whether* generation is valid and *which* teams participate. Generation is
 * additive and idempotent: pairings already present in the target round
 * (order-insensitive) are skipped without modification and without realtime
 * events. Everything is validated before anything is persisted, and each
 * operation runs in a single transaction, so failed generation leaves no
 * partial state.
 *
 * Shared deterministic rules:
 * - fields: all fields sorted by `field_id ASC`, assigned in rotation
 *   (game 1 -> field 1, game 2 -> field 2, ... wrapping around);
 * - referees: all teams sorted by `team_id ASC`, first team that is neither
 *   Team A nor Team B (never random);
 * - generated games always start as `SCHEDULED` with `0 : 0`.
 */
@Service
class GameGenerationService(
    private val groupRepository: GroupRepository,
    private val teamRepository: TeamRepository,
    private val roundRepository: RoundRepository,
    private val fieldRepository: FieldRepository,
    private val gameRepository: GameRepository,
    private val groupService: GroupService,
    private val gameService: GameService
) {
    data class GenerationResult(
        val created: List<Game>,
        val skipped: Int
    )

    /**
     * Every team plays every other team inside its own group exactly once in
     * the target round (`n * (n - 1) / 2` games per group). Never creates
     * cross-group pairings.
     */
    @Transactional
    fun generateRoundRobin(roundId: Int): GenerationResult {
        targetRound(roundId)
        val pairs = groupRepository.findAll()
            .sortedBy { it.groupId }
            .flatMap { group ->
                unorderedPairs(teamIdsOfGroup(group.groupId))
            }
        return createNewGames(roundId, pairs)
    }

    /**
     * First call (no previous knockout games anywhere): the top-ranked team
     * of each group leaderboard (`rounds.round_number = 1`) qualifies.
     * Later calls: winners of the previous knockout stage (the round with the
     * highest `round_number` below consideration, excluding the group stage
     * round and the target round itself) qualify. The previous stage must be
     * fully `FINISHED` with exactly one winner per game.
     */
    @Transactional
    fun generateKnockout(roundId: Int): GenerationResult {
        val target = targetRound(roundId)
        val participants = knockoutParticipants(target).sorted()
        requirePowerOfTwo(participants.size, "knockout")
        return createNewGames(roundId, bracketPairs(participants))
    }

    /**
     * The lowest-ranked team of each group leaderboard
     * (`rounds.round_number = 1`) participates. Requires an even number of
     * selected teams (no byes); every team plays exactly once and never
     * against a team from its own group (each group contributes one team).
     */
    @Transactional
    fun generateConsolation(roundId: Int): GenerationResult {
        targetRound(roundId)
        val selected = groupRepository.findAll()
            .sortedBy { it.groupId }
            .mapNotNull { group ->
                groupService.getLeaderboard(group.groupId).lastOrNull()?.teamId?.toInt()
            }
            .sorted()
        if (selected.size % 2 == 1) {
            throw BadRequestException("Cannot generate consolation games: odd number of selected teams (${selected.size}); no byes are created.")
        }
        return createNewGames(roundId, bracketPairs(selected))
    }

    private fun targetRound(roundId: Int): Round =
        roundRepository.findById(roundId).orElse(null) ?: throw NotFoundException("Round")

    private fun teamIdsOfGroup(groupId: Int): List<Int> =
        teamRepository.findAll()
            .filter { it.group.groupId == groupId }
            .map { it.teamId }
            .sorted()

    /** Every unique unordered pair (i < j) of the given team IDs. */
    private fun unorderedPairs(teamIds: List<Int>): List<Pair<Int, Int>> {
        val pairs = mutableListOf<Pair<Int, Int>>()
        for (i in teamIds.indices) {
            for (j in i + 1 until teamIds.size) {
                pairs += teamIds[i] to teamIds[j]
            }
        }
        return pairs
    }

    /**
     * Deterministic bracket pairing for an ordered participant list:
     * first vs last, second vs second-to-last, ...
     * (`T1 vs T4, T2 vs T3`; `T1 vs T8, T2 vs T7, ...`).
     */
    private fun bracketPairs(ordered: List<Int>): List<Pair<Int, Int>> {
        val pairs = mutableListOf<Pair<Int, Int>>()
        var lo = 0
        var hi = ordered.size - 1
        while (lo < hi) {
            pairs += ordered[lo] to ordered[hi]
            lo++
            hi--
        }
        return pairs
    }

    /** Existing pairings in a round, order-insensitive (`{A, B}` == `{B, A}`). */
    private fun existingPairs(roundId: Int): MutableSet<Set<Int>> =
        gameRepository.findByRound_RoundId(roundId)
            .map { setOf(it.teamA.teamId, it.teamB.teamId) }
            .toMutableSet()

    private fun knockoutParticipants(target: Round): List<Int> {
        val previous = previousKnockoutRound(target)
            ?: return groupRepository.findAll()
                .sortedBy { it.groupId }
                .mapNotNull { group ->
                    groupService.getLeaderboard(group.groupId).firstOrNull()?.teamId?.toInt()
                }
        val games = gameRepository.findByRound_RoundId(previous.roundId)
        val unfinished = games.firstOrNull { it.status != GameStatus.FINISHED }
        if (unfinished != null) {
            throw ConflictException(
                "Knockout cannot be generated: game ${unfinished.gameId} " +
                    "of the previous stage (round ${previous.roundNumber}) is ${unfinished.status}, not FINISHED."
            )
        }
        return games.map { winnerOf(it) }
    }

    /**
     * Latest round (by `round_number`) that holds games, excluding the target
     * round and the group-stage round (`round_number = 1`), or `null` when no
     * previous knockout stage exists yet.
     */
    private fun previousKnockoutRound(target: Round): Round? =
        roundRepository.findAll()
            .filter {
                it.roundId != target.roundId &&
                    it.roundNumber != 1 &&
                    gameRepository.existsByRound_RoundId(it.roundId)
            }
            .maxByOrNull { it.roundNumber }

    private fun winnerOf(game: Game): Int {
        val a = game.pointsA
        val b = game.pointsB
        return when {
            a > b -> game.teamA.teamId
            b > a -> game.teamB.teamId
            else -> throw BadRequestException("Knockout cannot be generated: game ${game.gameId} has no winner (score $a : $b).")
        }
    }

    private fun requirePowerOfTwo(n: Int, what: String) {
        if (n < 2 || n and (n - 1) != 0) {
            throw BadRequestException("Cannot generate $what games: $n qualified teams (need a power of two: 2, 4, 8, ...).")
        }
    }

    /**
     * Creates every pairing not already present in the target round.
     * Existing pairings are skipped untouched (no score/status/field/referee
     * change, no realtime event). New games get rotating fields, a valid
     * referee, `0 : 0` and `SCHEDULED`; each one publishes a `GAME / CREATE`
     * event after commit via [GameService].
     */
    private fun createNewGames(roundId: Int, pairs: List<Pair<Int, Int>>): GenerationResult {
        val existing = existingPairs(roundId)
        val fields: List<Field> = fieldRepository.findAll().sortedBy { it.fieldId }
        val allTeams: List<Int> = teamRepository.findAll().map { it.teamId }.sorted()
        val created = mutableListOf<Game>()
        var skipped = 0
        for ((a, b) in pairs) {
            if (!existing.add(setOf(a, b))) {
                skipped++
                continue
            }
            if (fields.isEmpty()) {
                throw BadRequestException("Cannot generate games: no fields exist.")
            }
            val referee = allTeams.firstOrNull { it != a && it != b }
                ?: throw BadRequestException("Cannot generate games: no valid referee team available for $a vs $b.")
            val field = fields[created.size % fields.size]
            created += gameService.create(roundId, field.fieldId, a, b, referee, 0, 0)
        }
        return GenerationResult(created, skipped)
    }
}
