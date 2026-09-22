package de.atiw.volleyball.controller

import de.atiw.volleyball.entity.Team
import de.atiw.volleyball.service.TeamService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/teams")
class TeamController(
    private val teamService: TeamService
) {

    @GetMapping
    fun getTeams(): List<Team> {
        return teamService.getAllTeams()
    }

    @GetMapping("/{teamname}")
    fun getTeam(
        @PathVariable teamname: String
    ): ResponseEntity<Team> {

        val team = teamService.getTeam(teamname)

        return if (team != null) {
            ResponseEntity.ok(team)
        } else {
            ResponseEntity.notFound().build()
        }
    }
}