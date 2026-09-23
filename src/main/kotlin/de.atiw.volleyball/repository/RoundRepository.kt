package de.atiw.volleyball.repository

import de.atiw.volleyball.entity.Round
import org.springframework.data.jpa.repository.JpaRepository

interface RoundRepository : JpaRepository<Round, Int>
