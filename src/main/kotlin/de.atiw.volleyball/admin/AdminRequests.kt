package de.atiw.volleyball.admin

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

// NB: no "status" property on purpose. AdminGameController rejects a
// "status" key explicitly with 400: every new game starts as SCHEDULED.
data class CreateGameRequest(
    val roundId: Int?,
    val fieldId: Int?,
    val teamAId: Int?,
    val teamBId: Int?,
    val refereeTeamId: Int?,
    // Scores are always non-null integers. Omitted scores default to 0;
    // an explicit null is rejected with 400 by service validation.
    val scoreA: Int? = 0,
    val scoreB: Int? = 0
)

// NB: no "status" property on purpose. AdminGameController rejects a
// "status" key explicitly with 400: the lifecycle is controlled exclusively
// by POST .../start and .../end, never by generic CRUD.
data class UpdateGameRequest(
    val roundId: Int?,
    val fieldId: Int?,
    val teamAId: Int?,
    val teamBId: Int?,
    val refereeTeamId: Int?,
    // Null scores are rejected with 400; there is no "clear to NULL".
    val scoreA: Int? = 0,
    val scoreB: Int? = 0
)
