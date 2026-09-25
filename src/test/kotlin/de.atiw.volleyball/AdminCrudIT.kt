package de.atiw.volleyball

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Admin CRUD contract on port 8081 (real HTTP) for groups, teams, rounds and
 * fields: status codes, `data`/`error` envelopes, machine-readable codes,
 * validation, uniqueness and delete-blocked behavior.
 */
class AdminCrudIT : RealPortIT() {

    // ---- groups ----

    @Test
    fun `create group returns 201 with data envelope and string id`() {
        val tag = nextTag("CG")
        val res = assertStatus(post(adminPort, "/api/admin/groups", """{"name":"G$tag"}"""), 201, "POST groups")
        assertTrue(res.headers().firstValue("location").isPresent, "create must set a Location header")
        val data = dataOf(res, "POST groups")
        assertTrue(data.path("groupId").isTextual(), "groupId must be a string: $data")
        assertEquals("G$tag", data.path("name").asText())
    }

    @Test
    fun `create group rejects blank and too-long names with 400`() {
        assertError(post(adminPort, "/api/admin/groups", """{"name":"  "}"""), 400, "BAD_REQUEST", "POST blank name")
        assertError(post(adminPort, "/api/admin/groups", """{}"""), 400, "BAD_REQUEST", "POST missing name")
        assertError(
            post(adminPort, "/api/admin/groups", """{"name":"WayTooLongName"}"""),
            400, "BAD_REQUEST", "POST too-long name"
        )
    }

    @Test
    fun `create group rejects duplicate with 409`() {
        val tag = nextTag("DG")
        createGroup("G$tag")
        assertError(
            post(adminPort, "/api/admin/groups", """{"name":"G$tag"}"""),
            409, "CONFLICT", "POST duplicate group"
        )
    }

    @Test
    fun `update group replaces name`() {
        val tag = nextTag("UG")
        val id = createGroup("G$tag").path("groupId").asText()
        val res = assertStatus(put(adminPort, "/api/admin/groups/$id", """{"name":"H$tag"}"""), 200, "PUT group")
        val data = dataOf(res, "PUT group")
        assertEquals(id, data.path("groupId").asText())
        assertEquals("H$tag", data.path("name").asText())
        // Visible through the public API.
        val viaPublic = dataOf(assertStatus(get(publicPort, "/api/groups/$id"), 200, "GET group"), "GET group")
        assertEquals("H$tag", viaPublic.path("name").asText())
    }

    @Test
    fun `update group handles missing duplicate and invalid`() {
        assertError(
            put(adminPort, "/api/admin/groups/999999999", """{"name":"Z"}"""),
            404, "RESOURCE_NOT_FOUND", "PUT missing group"
        )
        assertError(
            put(adminPort, "/api/admin/groups/abc", """{"name":"Z"}"""),
            404, "RESOURCE_NOT_FOUND", "PUT non-numeric group"
        )
        val tag = nextTag("UD")
        createGroup("G$tag")
        val other = createGroup("H$tag").path("groupId").asText()
        assertError(
            put(adminPort, "/api/admin/groups/$other", """{"name":"G$tag"}"""),
            409, "CONFLICT", "PUT duplicate group name"
        )
        assertError(
            put(adminPort, "/api/admin/groups/$other", """{"name":""}"""),
            400, "BAD_REQUEST", "PUT blank group name"
        )
    }

    @Test
    fun `delete group returns 204 with empty body`() {
        val tag = nextTag("XG")
        val id = createGroup("G$tag").path("groupId").asText()
        val res = assertStatus(delete(adminPort, "/api/admin/groups/$id"), 204, "DELETE group")
        assertTrue(res.body().isEmpty(), "DELETE must return an empty body, got: ${res.body()}")
        assertError(get(publicPort, "/api/groups/$id"), 404, "RESOURCE_NOT_FOUND", "GET deleted group")
    }

    @Test
    fun `delete group handles missing and referenced group`() {
        assertError(delete(adminPort, "/api/admin/groups/999999999"), 404, "RESOURCE_NOT_FOUND", "DELETE missing group")
        val tag = nextTag("BG")
        val groupId = createGroup("G$tag").path("groupId").asText()
        createTeam(groupId, "C", "Blocking$tag")
        assertError(
            delete(adminPort, "/api/admin/groups/$groupId"),
            409, "CONFLICT", "DELETE referenced group"
        )
    }

    // ---- teams ----

    @Test
    fun `create team returns 201 with data envelope`() {
        val tag = nextTag("CT")
        val groupId = createGroup("G$tag").path("groupId").asText()
        val res = assertStatus(
            post(adminPort, "/api/admin/teams", """{"groupId":$groupId,"class":"U18 Boys","name":"Team$tag"}"""),
            201, "POST teams"
        )
        val data = dataOf(res, "POST teams")
        assertTrue(data.path("teamId").isTextual(), "teamId must be a string: $data")
        assertEquals("U18 Boys", data.path("class").asText())
        assertEquals("Team$tag", data.path("name").asText())
        assertEquals(groupId, data.path("groupId").asText())
    }

    @Test
    fun `create team validates references and fields`() {
        val tag = nextTag("VT")
        val groupId = createGroup("G$tag").path("groupId").asText()
        assertError(
            post(adminPort, "/api/admin/teams", """{"groupId":999999999,"class":"C","name":"N"}"""),
            400, "BAD_REQUEST", "POST team with missing group"
        )
        assertError(
            post(adminPort, "/api/admin/teams", """{"groupId":$groupId,"class":"  ","name":"N"}"""),
            400, "BAD_REQUEST", "POST team with blank class"
        )
        assertError(
            post(adminPort, "/api/admin/teams", """{"groupId":$groupId,"class":"C","name":""}"""),
            400, "BAD_REQUEST", "POST team with blank name"
        )
    }

    @Test
    fun `update team replaces all fields and can move groups`() {
        val tag = nextTag("UT")
        val groupA = createGroup("A$tag").path("groupId").asText()
        val groupB = createGroup("B$tag").path("groupId").asText()
        val teamId = createTeam(groupA, "C1", "Old$tag").path("teamId").asText()
        val res = assertStatus(
            put(adminPort, "/api/admin/teams/$teamId", """{"groupId":$groupB,"class":"C2","name":"New$tag"}"""),
            200, "PUT team"
        )
        val data = dataOf(res, "PUT team")
        assertEquals(teamId, data.path("teamId").asText())
        assertEquals(groupB, data.path("groupId").asText())
        assertEquals("C2", data.path("class").asText())
        assertEquals("New$tag", data.path("name").asText())
    }

    @Test
    fun `update missing team returns 404`() {
        assertError(
            put(adminPort, "/api/admin/teams/999999999", """{"groupId":1,"class":"C","name":"N"}"""),
            404, "RESOURCE_NOT_FOUND", "PUT missing team"
        )
    }

    @Test
    fun `delete team handles unused used and missing teams`() {
        val f = newFixture("DT")
        val freeTeam = createTeam(f.groupA, "C", "Free${nextTag("FT")}").path("teamId").asText()
        val deleted = assertStatus(delete(adminPort, "/api/admin/teams/$freeTeam"), 204, "DELETE unused team")
        assertTrue(deleted.body().isEmpty(), "DELETE must return an empty body")
        assertError(
            delete(adminPort, "/api/admin/teams/${f.teamA}"),
            409, "CONFLICT", "DELETE team used by a game"
        )
        assertError(delete(adminPort, "/api/admin/teams/999999999"), 404, "RESOURCE_NOT_FOUND", "DELETE missing team")
    }

    // ---- rounds ----

    @Test
    fun `create round returns 201 and validates number`() {
        val number = Random.nextInt(20000000, 29999999)
        val res = assertStatus(post(adminPort, "/api/admin/rounds", """{"number":$number}"""), 201, "POST rounds")
        val data = dataOf(res, "POST rounds")
        assertTrue(data.path("roundId").isTextual(), "roundId must be a string: $data")
        assertEquals(number, data.path("number").asInt())

        assertError(post(adminPort, "/api/admin/rounds", """{"number":0}"""), 400, "BAD_REQUEST", "POST round 0")
        assertError(post(adminPort, "/api/admin/rounds", """{"number":-3}"""), 400, "BAD_REQUEST", "POST negative round")
        assertError(
            post(adminPort, "/api/admin/rounds", """{"number":$number}"""),
            409, "CONFLICT", "POST duplicate round"
        )
    }

    @Test
    fun `update and delete round`() {
        val number = Random.nextInt(30000000, 39999999)
        val id = createRound(number).path("roundId").asText()
        val updated = dataOf(
            assertStatus(put(adminPort, "/api/admin/rounds/$id", """{"number":${number + 1}}"""), 200, "PUT round"),
            "PUT round"
        )
        assertEquals(number + 1, updated.path("number").asInt())
        assertError(
            put(adminPort, "/api/admin/rounds/999999999", """{"number":5}"""),
            404, "RESOURCE_NOT_FOUND", "PUT missing round"
        )

        val res = assertStatus(delete(adminPort, "/api/admin/rounds/$id"), 204, "DELETE round")
        assertTrue(res.body().isEmpty(), "DELETE must return an empty body")
        assertError(delete(adminPort, "/api/admin/rounds/999999999"), 404, "RESOURCE_NOT_FOUND", "DELETE missing round")
    }

    @Test
    fun `delete round referenced by games returns 409`() {
        val f = newFixture("BR")
        assertError(
            delete(adminPort, "/api/admin/rounds/${f.roundId}"),
            409, "CONFLICT", "DELETE referenced round"
        )
    }

    // ---- fields ----

    @Test
    fun `create field returns 201 and validates name`() {
        val tag = nextTag("CF")
        val res = assertStatus(post(adminPort, "/api/admin/fields", """{"name":"Court $tag"}"""), 201, "POST fields")
        val data = dataOf(res, "POST fields")
        assertTrue(data.path("fieldId").isTextual(), "fieldId must be a string: $data")
        assertEquals("Court $tag", data.path("name").asText())

        assertError(post(adminPort, "/api/admin/fields", """{"name":"  "}"""), 400, "BAD_REQUEST", "POST blank field")
        assertError(
            post(adminPort, "/api/admin/fields", """{"name":"Court $tag"}"""),
            409, "CONFLICT", "POST duplicate field"
        )
    }

    @Test
    fun `update and delete field`() {
        val tag = nextTag("UF")
        val id = createField("Court $tag").path("fieldId").asText()
        val updated = dataOf(
            assertStatus(put(adminPort, "/api/admin/fields/$id", """{"name":"Arena $tag"}"""), 200, "PUT field"),
            "PUT field"
        )
        assertEquals("Arena $tag", updated.path("name").asText())
        assertError(
            put(adminPort, "/api/admin/fields/999999999", """{"name":"X"}"""),
            404, "RESOURCE_NOT_FOUND", "PUT missing field"
        )

        val res = assertStatus(delete(adminPort, "/api/admin/fields/$id"), 204, "DELETE field")
        assertTrue(res.body().isEmpty(), "DELETE must return an empty body")
        assertError(delete(adminPort, "/api/admin/fields/999999999"), 404, "RESOURCE_NOT_FOUND", "DELETE missing field")
    }

    @Test
    fun `delete field referenced by games returns 409`() {
        val f = newFixture("BF")
        assertError(
            delete(adminPort, "/api/admin/fields/${f.fieldId}"),
            409, "CONFLICT", "DELETE referenced field"
        )
    }
}
