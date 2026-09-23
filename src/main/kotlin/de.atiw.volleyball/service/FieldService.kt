package de.atiw.volleyball.service

import de.atiw.volleyball.entity.Field
import de.atiw.volleyball.repository.FieldRepository
import org.springframework.stereotype.Service

@Service
class FieldService(
    private val fieldRepository: FieldRepository
) {
    fun getAll(): List<Field> = fieldRepository.findAll()
}
