package de.atiw.volleyball.service

import de.atiw.volleyball.entity.Team
import de.atiw.volleyball.realtime.EntityType
import de.atiw.volleyball.realtime.OperationType
import de.atiw.volleyball.realtime.TournamentChangeEvent
import de.atiw.volleyball.repository.GameRepository
import de.atiw.volleyball.repository.GroupRepository
import de.atiw.volleyball.repository.TeamRepository
import de.atiw.volleyball.admin.common.BadRequestException
import de.atiw.volleyball.admin.common.NotFoundException
import de.atiw.volleyball.admin.common.ConflictException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TeamService(
    private val teamRepository: TeamRepository,
    private val groupRepository: GroupRepository,
    private val gameRepository: GameRepository,
    private val events: ApplicationEventPublisher
) {

    fun getAllTeams(): List<Team> {
        return teamRepository.findAll()
    }

    fun getTeam(teamId: Int): Team? {
        return teamRepository.findById(teamId).orElse(null)
    }

    @Transactional
    fun create(groupId: Int?, clazz: String?, name: String?): Team {
        val group = resolveGroup(groupId)
        val teamClass = validateText(clazz, "Team class", 100)
        val teamName = validateText(name, "Team name", 100)
        val saved = teamRepository.save(Team(group = group, teamClass = teamClass, name = teamName))
        events.publishEvent(TournamentChangeEvent(EntityType.TEAM, OperationType.CREATE, saved.teamId))
        return saved
    }

    @Transactional
    fun update(id: Int, groupId: Int?, clazz: String?, name: String?): Team {
        val team = teamRepository.findById(id).orElse(null) ?: throw NotFoundException("Team")
        val group = resolveGroup(groupId)
        team.group = group
        team.teamClass = validateText(clazz, "Team class", 100)
        team.name = validateText(name, "Team name", 100)
        val saved = teamRepository.save(team)
        events.publishEvent(TournamentChangeEvent(EntityType.TEAM, OperationType.UPDATE, saved.teamId))
        return saved
    }

    @Transactional
    fun delete(id: Int) {
        if (!teamRepository.existsById(id)) throw NotFoundException("Team")
        if (gameRepository.existsByTeamA_TeamIdOrTeamB_TeamIdOrRefereeTeam_TeamId(id, id, id)) {
            throw ConflictException("Team cannot be deleted because games still reference it.")
        }
        teamRepository.deleteById(id)
        events.publishEvent(TournamentChangeEvent(EntityType.TEAM, OperationType.DELETE, id))
    }

    private fun resolveGroup(groupId: Int?) =
        if (groupId == null) throw BadRequestException("Group ID is required.")
        else groupRepository.findById(groupId).orElse(null) ?: throw BadRequestException("Group $groupId does not exist.")

    private fun validateText(value: String?, field: String, max: Int): String {
        val trimmed = value?.trim()
        if (trimmed.isNullOrEmpty()) throw BadRequestException("$field must not be blank.")
        if (trimmed.length > max) throw BadRequestException("$field must be at most $max characters.")
        return trimmed
    }
}
