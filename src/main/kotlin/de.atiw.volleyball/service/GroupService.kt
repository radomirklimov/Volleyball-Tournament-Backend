package de.atiw.volleyball.service

import de.atiw.volleyball.entity.TournamentGroup
import de.atiw.volleyball.realtime.EntityType
import de.atiw.volleyball.realtime.OperationType
import de.atiw.volleyball.realtime.TournamentChangeEvent
import de.atiw.volleyball.repository.GroupRepository
import de.atiw.volleyball.repository.TeamRepository
import de.atiw.volleyball.teacher.common.BadRequestException
import de.atiw.volleyball.teacher.common.ConflictException
import de.atiw.volleyball.teacher.common.NotFoundException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GroupService(
    private val groupRepository: GroupRepository,
    private val teamRepository: TeamRepository,
    private val events: ApplicationEventPublisher
) {
    fun getAll(): List<TournamentGroup> = groupRepository.findAll()

    fun getById(id: Int): TournamentGroup? =
        groupRepository.findById(id).orElse(null)

    @Transactional
    fun create(name: String?): TournamentGroup {
        val designation = validateDesignation(name)
        if (groupRepository.existsByDesignation(designation)) {
            throw ConflictException("Group '$designation' already exists.")
        }
        val saved = groupRepository.save(TournamentGroup(designation = designation))
        events.publishEvent(TournamentChangeEvent(EntityType.GROUP, OperationType.CREATE, saved.groupId))
        return saved
    }

    @Transactional
    fun update(id: Int, name: String?): TournamentGroup {
        val designation = validateDesignation(name)
        val group = groupRepository.findById(id).orElse(null)
            ?: throw NotFoundException("Group")
        if (group.designation != designation && groupRepository.existsByDesignation(designation)) {
            throw ConflictException("Group '$designation' already exists.")
        }
        group.designation = designation
        val saved = groupRepository.save(group)
        events.publishEvent(TournamentChangeEvent(EntityType.GROUP, OperationType.UPDATE, saved.groupId))
        return saved
    }

    @Transactional
    fun delete(id: Int) {
        if (!groupRepository.existsById(id)) throw NotFoundException("Group")
        if (teamRepository.existsByGroup_GroupId(id)) {
            throw ConflictException("Group cannot be deleted because teams still reference it.")
        }
        groupRepository.deleteById(id)
        events.publishEvent(TournamentChangeEvent(EntityType.GROUP, OperationType.DELETE, id))
    }

    private fun validateDesignation(name: String?): String {
        val designation = name?.trim()
        if (designation.isNullOrEmpty()) throw BadRequestException("Group name must not be blank.")
        if (designation.length > 10) throw BadRequestException("Group name must be at most 10 characters.")
        return designation
    }
}
