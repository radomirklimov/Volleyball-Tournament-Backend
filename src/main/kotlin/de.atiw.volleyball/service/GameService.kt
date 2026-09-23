package de.atiw.volleyball.service

import de.atiw.volleyball.entity.Game
import de.atiw.volleyball.repository.GameRepository
import org.springframework.stereotype.Service

@Service
class GameService(
    private val gameRepository: GameRepository
) {
    fun getAll(): List<Game> = gameRepository.findAll()

    fun getById(id: Int): Game? =
        gameRepository.findById(id).orElse(null)

    fun getByRoundId(roundId: Int): List<Game> =
        gameRepository.findByRound_RoundId(roundId)
}
