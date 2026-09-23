package de.atiw.volleyball.teacher

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty

data class CreateGroupRequest(val name: String?)
data class UpdateGroupRequest(val name: String?)

data class CreateTeamRequest(
    val groupId: Int?,
    @JsonProperty("class") @JsonAlias("clazz") val clazz: String?,
    val name: String?
)

data class UpdateTeamRequest(
    val groupId: Int?,
    @JsonProperty("class") @JsonAlias("clazz") val clazz: String?,
    val name: String?
)

data class CreateRoundRequest(val number: Int?)
data class UpdateRoundRequest(val number: Int?)

data class CreateFieldRequest(val name: String?)
data class UpdateFieldRequest(val name: String?)

data class CreateGameRequest(
    val roundId: Int?,
    val fieldId: Int?,
    val teamAId: Int?,
    val teamBId: Int?,
    val refereeTeamId: Int?,
    val scoreA: Int?,
    val scoreB: Int?
)

data class UpdateGameRequest(
    val roundId: Int?,
    val fieldId: Int?,
    val teamAId: Int?,
    val teamBId: Int?,
    val refereeTeamId: Int?,
    val scoreA: Int?,
    val scoreB: Int?
)
