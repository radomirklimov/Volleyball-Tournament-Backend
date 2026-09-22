package de.atiw.volleyball.repository

import de.atiw.volleyball.entity.Team
import org.springframework.data.jpa.repository.JpaRepository

interface TeamRepository : JpaRepository<Team, String>