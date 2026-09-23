package de.atiw.volleyball.service

import de.atiw.volleyball.entity.TournamentGroup
import de.atiw.volleyball.repository.GroupRepository
import org.springframework.stereotype.Service

@Service
class GroupService(
    private val groupRepository: GroupRepository
) {
    fun getAll(): List<TournamentGroup> = groupRepository.findAll()

    fun getById(id: Int): TournamentGroup? =
        groupRepository.findById(id).orElse(null)
}
