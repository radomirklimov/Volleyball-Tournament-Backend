package de.atiw.volleyball.service

import de.atiw.volleyball.entity.Round
import de.atiw.volleyball.realtime.EntityType
import de.atiw.volleyball.realtime.OperationType
import de.atiw.volleyball.realtime.TournamentChangeEvent
import de.atiw.volleyball.repository.GameRepository
import de.atiw.volleyball.repository.RoundRepository
import de.atiw.volleyball.admin.common.BadRequestException
import de.atiw.volleyball.admin.common.ConflictException
import de.atiw.volleyball.admin.common.NotFoundException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RoundService(
    private val roundRepository: RoundRepository,
    private val gameRepository: GameRepository,
    private val events: ApplicationEventPublisher
) {
    fun getAll(): List<Round> = roundRepository.findAll()

    @Transactional
    fun create(number: Int?): Round {
        val roundNumber = validateNumber(number)
        if (roundRepository.existsByRoundNumber(roundNumber)) {
            throw ConflictException("Round $roundNumber already exists.")
        }
        val saved = roundRepository.save(Round(roundNumber = roundNumber))
        events.publishEvent(TournamentChangeEvent(EntityType.ROUND, OperationType.CREATE, saved.roundId))
        return saved
    }

    @Transactional
    fun update(id: Int, number: Int?): Round {
        val roundNumber = validateNumber(number)
        val round = roundRepository.findById(id).orElse(null) ?: throw NotFoundException("Round")
        if (round.roundNumber != roundNumber && roundRepository.existsByRoundNumber(roundNumber)) {
            throw ConflictException("Round $roundNumber already exists.")
        }
        round.roundNumber = roundNumber
        val saved = roundRepository.save(round)
        events.publishEvent(TournamentChangeEvent(EntityType.ROUND, OperationType.UPDATE, saved.roundId))
        return saved
    }

    @Transactional
    fun delete(id: Int) {
        if (!roundRepository.existsById(id)) throw NotFoundException("Round")
        if (gameRepository.existsByRound_RoundId(id)) {
            throw ConflictException("Round cannot be deleted because games still reference it.")
        }
        roundRepository.deleteById(id)
        events.publishEvent(TournamentChangeEvent(EntityType.ROUND, OperationType.DELETE, id))
    }

    private fun validateNumber(number: Int?): Int {
        if (number == null || number <= 0) throw BadRequestException("Round number must be > 0.")
        return number
    }
}
