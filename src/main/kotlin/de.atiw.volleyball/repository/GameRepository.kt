package de.atiw.volleyball.repository

import de.atiw.volleyball.entity.Game
import org.springframework.data.jpa.repository.JpaRepository

interface GameRepository : JpaRepository<Game, Int> {
    fun findByRound_RoundId(roundId: Int): List<Game>

    fun existsByRound_RoundId(roundId: Int): Boolean

    fun existsByField_FieldId(fieldId: Int): Boolean

    fun existsByTeamA_TeamIdOrTeamB_TeamIdOrRefereeTeam_TeamId(
        teamAId: Int,
        teamBId: Int,
        refereeTeamId: Int
    ): Boolean
}
