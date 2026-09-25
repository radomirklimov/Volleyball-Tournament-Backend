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
                pointsB = resolved.pointsB
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
     * Starts a game: NULL/NULL becomes 0/0. Idempotent — calling it on an
     * already started game changes nothing and publishes no event.
     */
    @Transactional
    fun startGame(id: Int): Game {
        val game = gameRepository.findByIdForUpdate(id) ?: throw NotFoundException("Game")
        if (game.pointsA == null || game.pointsB == null) {
            game.pointsA = 0
            game.pointsB = 0
            val saved = gameRepository.save(game)
            events.publishEvent(TournamentChangeEvent(EntityType.GAME, OperationType.UPDATE, saved.gameId))
            return saved
        }
        return game
    }

    @Transactional
    fun incrementTeamAScore(id: Int): Game = adjustScore(id, teamB = false, delta = 1)

    @Transactional
    fun decrementTeamAScore(id: Int): Game = adjustScore(id, teamB = false, delta = -1)

    @Transactional
    fun incrementTeamBScore(id: Int): Game = adjustScore(id, teamB = true, delta = 1)

    @Transactional
    fun decrementTeamBScore(id: Int): Game = adjustScore(id, teamB = true, delta = -1)

    private fun adjustScore(id: Int, teamB: Boolean, delta: Int): Game {
        val game = gameRepository.findByIdForUpdate(id) ?: throw NotFoundException("Game")
        val side = if (teamB) "B" else "A"
        val current = (if (teamB) game.pointsB else game.pointsA)
            ?: throw ConflictException("Game $id has not been started.")
        if (delta < 0 && current == 0) {
            throw ConflictException("Team $side score cannot be decreased below 0.")
        }
        if (teamB) game.pointsB = current + delta else game.pointsA = current + delta
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
        val pointsA: Int?,
        val pointsB: Int?
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
        val pointsA = if (scoreA != null && scoreA < 0) throw BadRequestException("Score A must be >= 0.") else scoreA
        val pointsB = if (scoreB != null && scoreB < 0) throw BadRequestException("Score B must be >= 0.") else scoreB
        if (teamA.teamId == teamB.teamId) throw BadRequestException("Team A and team B must be different.")
        if (referee.teamId == teamA.teamId) throw BadRequestException("Referee team must differ from team A.")
        if (referee.teamId == teamB.teamId) throw BadRequestException("Referee team must differ from team B.")
        return ResolvedGameState(round, field, teamA, teamB, referee, pointsA, pointsB)
    }
}
