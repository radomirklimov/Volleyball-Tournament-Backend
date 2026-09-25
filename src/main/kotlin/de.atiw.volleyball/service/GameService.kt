package de.atiw.volleyball.service

import de.atiw.volleyball.entity.Game
import de.atiw.volleyball.realtime.EntityType
import de.atiw.volleyball.realtime.OperationType
import de.atiw.volleyball.realtime.TournamentChangeEvent
import de.atiw.volleyball.repository.FieldRepository
import de.atiw.volleyball.repository.GameRepository
import de.atiw.volleyball.repository.RoundRepository
import de.atiw.volleyball.repository.TeamRepository
import de.atiw.volleyball.admin.common.BadRequestException
import de.atiw.volleyball.admin.common.ConflictException
import de.atiw.volleyball.admin.common.NotFoundException
import de.atiw.volleyball.entity.GameStatus
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GameService(
    private val gameRepository: GameRepository,
    private val roundRepository: RoundRepository,
    private val fieldRepository: FieldRepository,
    private val teamRepository: TeamRepository,
    private val events: ApplicationEventPublisher
) {
    fun getAll(): List<Game> = gameRepository.findAll()

    fun getById(id: Int): Game? =
        gameRepository.findById(id).orElse(null)

    fun getByRoundId(roundId: Int): List<Game> =
        gameRepository.findByRound_RoundId(roundId)

    @Transactional
    fun create(
        roundId: Int?,
        fieldId: Int?,
        teamAId: Int?,
        teamBId: Int?,
        refereeTeamId: Int?,
        scoreA: Int?,
        scoreB: Int?
    ): Game {
        val resolved = resolveState(roundId, fieldId, teamAId, teamBId, refereeTeamId, scoreA, scoreB)
        val saved = gameRepository.save(
            Game(
                round = resolved.round,
                field = resolved.field,
                teamA = resolved.teamA,
                teamB = resolved.teamB,
                refereeTeam = resolved.referee,
                pointsA = resolved.pointsA,
                pointsB = resolved.pointsB,
                status = GameStatus.SCHEDULED
            )
        )
        events.publishEvent(TournamentChangeEvent(EntityType.GAME, OperationType.CREATE, saved.gameId))
        return saved
    }

    @Transactional
    fun update(
        id: Int,
        roundId: Int?,
        fieldId: Int?,
        teamAId: Int?,
        teamBId: Int?,
        refereeTeamId: Int?,
        scoreA: Int?,
        scoreB: Int?
    ): Game {
        val game = gameRepository.findById(id).orElse(null) ?: throw NotFoundException("Game")
        val resolved = resolveState(roundId, fieldId, teamAId, teamBId, refereeTeamId, scoreA, scoreB)
        game.round = resolved.round
        game.field = resolved.field
        game.teamA = resolved.teamA
        game.teamB = resolved.teamB
        game.refereeTeam = resolved.referee
        game.pointsA = resolved.pointsA
        game.pointsB = resolved.pointsB
        val saved = gameRepository.save(game)
        events.publishEvent(TournamentChangeEvent(EntityType.GAME, OperationType.UPDATE, saved.gameId))
        return saved
    }

    @Transactional
    fun delete(id: Int) {
        if (!gameRepository.existsById(id)) throw NotFoundException("Game")
        gameRepository.deleteById(id)
        events.publishEvent(TournamentChangeEvent(EntityType.GAME, OperationType.DELETE, id))
    }

    /**
     * Lifecycle transition `SCHEDULED -> RUNNING`. Scores are untouched.
     * Any other current status is rejected with `409 CONFLICT` and the game
     * is left unchanged. Publishes one `GAME / UPDATE` event after commit.
     */
    @Transactional
    fun startGame(id: Int): Game {
        val game = gameRepository.findById(id).orElse(null) ?: throw NotFoundException("Game")
        when (game.status) {
            GameStatus.SCHEDULED -> game.status = GameStatus.RUNNING
            GameStatus.RUNNING -> throw ConflictException("Game $id is already running.")
            GameStatus.FINISHED -> throw ConflictException("Game $id is already finished and cannot be restarted.")
        }
        val saved = gameRepository.save(game)
        events.publishEvent(TournamentChangeEvent(EntityType.GAME, OperationType.UPDATE, saved.gameId))
        return saved
    }

    /**
     * Lifecycle transition `RUNNING -> FINISHED`. Scores are untouched.
     * Any other current status is rejected with `409 CONFLICT` and the game
     * is left unchanged. Publishes one `GAME / UPDATE` event after commit.
     */
    @Transactional
    fun endGame(id: Int): Game {
        val game = gameRepository.findById(id).orElse(null) ?: throw NotFoundException("Game")
        when (game.status) {
            GameStatus.RUNNING -> game.status = GameStatus.FINISHED
            GameStatus.SCHEDULED -> throw ConflictException("Game $id cannot be finished because it is still scheduled.")
            GameStatus.FINISHED -> throw ConflictException("Game $id is already finished.")
        }
        val saved = gameRepository.save(game)
        events.publishEvent(TournamentChangeEvent(EntityType.GAME, OperationType.UPDATE, saved.gameId))
        return saved
    }

    private data class ResolvedGameState(
        val round: de.atiw.volleyball.entity.Round,
        val field: de.atiw.volleyball.entity.Field,
        val teamA: de.atiw.volleyball.entity.Team,
        val teamB: de.atiw.volleyball.entity.Team,
        val referee: de.atiw.volleyball.entity.Team,
        val pointsA: Int,
        val pointsB: Int
    )

    private fun resolveState(
        roundId: Int?,
        fieldId: Int?,
        teamAId: Int?,
        teamBId: Int?,
        refereeTeamId: Int?,
        scoreA: Int?,
        scoreB: Int?
    ): ResolvedGameState {
        val round = if (roundId == null) throw BadRequestException("Round ID is required.")
        else roundRepository.findById(roundId).orElse(null) ?: throw BadRequestException("Round $roundId does not exist.")
        val field = if (fieldId == null) throw BadRequestException("Field ID is required.")
        else fieldRepository.findById(fieldId).orElse(null) ?: throw BadRequestException("Field $fieldId does not exist.")
        val teamA = if (teamAId == null) throw BadRequestException("Team A ID is required.")
        else teamRepository.findById(teamAId).orElse(null) ?: throw BadRequestException("Team A $teamAId does not exist.")
        val teamB = if (teamBId == null) throw BadRequestException("Team B ID is required.")
        else teamRepository.findById(teamBId).orElse(null) ?: throw BadRequestException("Team B $teamBId does not exist.")
        val referee = if (refereeTeamId == null) throw BadRequestException("Referee team ID is required.")
        else teamRepository.findById(refereeTeamId).orElse(null)
            ?: throw BadRequestException("Referee team $refereeTeamId does not exist.")
        val pointsA = if (scoreA == null) throw BadRequestException("Score A is required.")
        else if (scoreA < 0) throw BadRequestException("Score A must be >= 0.") else scoreA
        val pointsB = if (scoreB == null) throw BadRequestException("Score B is required.")
        else if (scoreB < 0) throw BadRequestException("Score B must be >= 0.") else scoreB
        if (teamA.teamId == teamB.teamId) throw BadRequestException("Team A and team B must be different.")
        if (referee.teamId == teamA.teamId) throw BadRequestException("Referee team must differ from team A.")
        if (referee.teamId == teamB.teamId) throw BadRequestException("Referee team must differ from team B.")
        return ResolvedGameState(round, field, teamA, teamB, referee, pointsA, pointsB)
    }
}
