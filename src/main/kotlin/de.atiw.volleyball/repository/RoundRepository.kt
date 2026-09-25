package de.atiw.volleyball.repository

import de.atiw.volleyball.entity.Round
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface RoundRepository : JpaRepository<Round, Int> {
    fun existsByRoundNumber(roundNumber: Int): Boolean

    /**
     * Loads a round with a pessimistic write lock so concurrent generations
     * for the same target round serialize: the second transaction sees the
     * first one's committed games and skips those pairings instead of
     * creating duplicates. Must be called inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Round r where r.roundId = :id")
    fun findByIdForUpdate(id: Int): Round?
}
