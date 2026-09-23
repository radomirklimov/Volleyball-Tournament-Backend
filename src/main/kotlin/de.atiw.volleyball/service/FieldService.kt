package de.atiw.volleyball.service

import de.atiw.volleyball.entity.Field
import de.atiw.volleyball.realtime.EntityType
import de.atiw.volleyball.realtime.OperationType
import de.atiw.volleyball.realtime.TournamentChangeEvent
import de.atiw.volleyball.repository.FieldRepository
import de.atiw.volleyball.repository.GameRepository
import de.atiw.volleyball.teacher.common.BadRequestException
import de.atiw.volleyball.teacher.common.ConflictException
import de.atiw.volleyball.teacher.common.NotFoundException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FieldService(
    private val fieldRepository: FieldRepository,
    private val gameRepository: GameRepository,
    private val events: ApplicationEventPublisher
) {
    fun getAll(): List<Field> = fieldRepository.findAll()

    @Transactional
    fun create(name: String?): Field {
        val fieldName = validateName(name)
        if (fieldRepository.existsByName(fieldName)) {
            throw ConflictException("Field '$fieldName' already exists.")
        }
        val saved = fieldRepository.save(Field(name = fieldName))
        events.publishEvent(TournamentChangeEvent(EntityType.FIELD, OperationType.CREATE, saved.fieldId))
        return saved
    }

    @Transactional
    fun update(id: Int, name: String?): Field {
        val fieldName = validateName(name)
        val field = fieldRepository.findById(id).orElse(null) ?: throw NotFoundException("Field")
        if (field.name != fieldName && fieldRepository.existsByName(fieldName)) {
            throw ConflictException("Field '$fieldName' already exists.")
        }
        field.name = fieldName
        val saved = fieldRepository.save(field)
        events.publishEvent(TournamentChangeEvent(EntityType.FIELD, OperationType.UPDATE, saved.fieldId))
        return saved
    }

    @Transactional
    fun delete(id: Int) {
        if (!fieldRepository.existsById(id)) throw NotFoundException("Field")
        if (gameRepository.existsByField_FieldId(id)) {
            throw ConflictException("Field cannot be deleted because games still reference it.")
        }
        fieldRepository.deleteById(id)
        events.publishEvent(TournamentChangeEvent(EntityType.FIELD, OperationType.DELETE, id))
    }

    private fun validateName(name: String?): String {
        val trimmed = name?.trim()
        if (trimmed.isNullOrEmpty()) throw BadRequestException("Field name must not be blank.")
        if (trimmed.length > 100) throw BadRequestException("Field name must be at most 100 characters.")
        return trimmed
    }
}
