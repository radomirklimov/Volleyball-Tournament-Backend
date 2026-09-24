package de.atiw.volleyball.repository

import de.atiw.volleyball.entity.Game
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface GameRepository : JpaRepository<Game, Int> {
    fun findByRound_RoundId(roundId: Int): List<Game>

    fun existsByRound_RoundId(roundId: Int): Boolean

    fun existsByField_FieldId(fieldId: Int): Boolean

    fun existsByTeamA_TeamIdOrTeamB_TeamIdOrRefereeTeam_TeamId(
        teamAId: Int,
        teamBId: Int,
        refereeTeamId: Int
    ): Boolean

    /**
     * Loads a game with a pessimistic write lock so concurrent score operations
     * on the same row serialize instead of overwriting each other (no lost
     * updates). Must be called inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Game g where g.gameId = :id")
    fun findByIdForUpdate(id: Int): Game?
}
