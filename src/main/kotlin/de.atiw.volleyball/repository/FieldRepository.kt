package de.atiw.volleyball.repository

import de.atiw.volleyball.entity.Field
import org.springframework.data.jpa.repository.JpaRepository

interface FieldRepository : JpaRepository<Field, Int> {
    fun existsByName(name: String): Boolean
}
