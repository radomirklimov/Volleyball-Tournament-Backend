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

    fun getTeam(teamname: String): Team? {
        return teamRepository.findById(teamname).orElse(null)
    }
}