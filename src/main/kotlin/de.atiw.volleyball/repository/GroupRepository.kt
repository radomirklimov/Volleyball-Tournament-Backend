package de.atiw.volleyball.repository

import de.atiw.volleyball.entity.TournamentGroup
import org.springframework.data.jpa.repository.JpaRepository

interface GroupRepository : JpaRepository<TournamentGroup, Int> {
    fun existsByDesignation(designation: String): Boolean
}
