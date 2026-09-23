package de.atiw.volleyball.repository

import de.atiw.volleyball.entity.Game
import org.springframework.data.jpa.repository.JpaRepository

interface GameRepository : JpaRepository<Game, Int> {
    fun findByRound_RoundId(roundId: Int): List<Game>
}
