package de.atiw.volleyball.service

import de.atiw.volleyball.entity.Team
import de.atiw.volleyball.repository.TeamRepository
import org.springframework.stereotype.Service

@Service
class TeamService(
    private val teamRepository: TeamRepository
) {

    fun getAllTeams(): List<Team> {
        return teamRepository.findAll()
    }

    fun getTeam(teamId: Int): Team? {
        return teamRepository.findById(teamId).orElse(null)
    }
}