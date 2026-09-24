package de.atiw.volleyball.dto

import com.fasterxml.jackson.annotation.JsonProperty
import de.atiw.volleyball.entity.Field
import de.atiw.volleyball.entity.Game
import de.atiw.volleyball.entity.Round
import de.atiw.volleyball.entity.Team
import de.atiw.volleyball.entity.TournamentGroup

data class GroupDto(
    val groupId: String,
    val name: String
)

data class TeamDto(
    val teamId: String,
    @JsonProperty("class")
    val clazz: String,
    val name: String,
    val groupId: String
)

data class RoundDto(
    val roundId: String,
    val number: Int
)

data class FieldDto(
    val fieldId: String,
    val name: String
)

data class GameDto(
    val gameId: String,
    val roundId: String,
    val fieldId: String,
    val teamAId: String,
    val teamBId: String,
    val refereeTeamId: String,
    val scoreA: Int?,
    val scoreB: Int?
)

fun TournamentGroup.toDto() = GroupDto(
    groupId = groupId.toString(),
    name = designation
)

fun Team.toDto() = TeamDto(
    teamId = teamId.toString(),
    clazz = teamClass,
    name = name,
    groupId = group.groupId.toString()
)

fun Round.toDto() = RoundDto(
    roundId = roundId.toString(),
    number = roundNumber
)

fun Field.toDto() = FieldDto(
    fieldId = fieldId.toString(),
    name = name
)

fun Game.toDto() = GameDto(
    gameId = gameId.toString(),
    roundId = round.roundId.toString(),
    fieldId = field.fieldId.toString(),
    teamAId = teamA.teamId.toString(),
    teamBId = teamB.teamId.toString(),
    refereeTeamId = refereeTeam.teamId.toString(),
    scoreA = pointsA,
    scoreB = pointsB
)
