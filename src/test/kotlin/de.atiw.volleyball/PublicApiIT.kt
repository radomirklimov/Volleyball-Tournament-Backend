package de.atiw.volleyball

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Public read API contract on port 8080 (real HTTP):
 * response envelopes, DTO shapes, string IDs, nullable scores and the game
 * filter behaviors.
 */
class PublicApiIT : RealPortIT() {

    @Test
    fun `root redirects to swagger UI`() {
        val res = get(publicPort, "/")
        assertEquals(302, res.statusCode(), "GET / -> ${res.body()}")
        assertTrue(
            (res.headers().firstValue("location").orElse("")).contains("swagger-ui"),
            "GET / location: ${res.headers().firstValue("location")}"
        )
    }

    @Test
    fun `groups list and single group`() {
        val f = newFixture("G")
        val list = dataOf(assertStatus(get(publicPort, "/api/groups"), 200, "GET groups"), "GET groups")
        assertTrue(list.isArray && list.size() >= 2, "expected group list, got: $list")
        val ids = list.map { it.path("groupId").asText() }
        assertTrue(ids.contains(f.groupA), "created group A must be listed: $ids")
        assertTrue(ids.contains(f.groupB), "created group B must be listed: $ids")

        val single = dataOf(
            assertStatus(get(publicPort, "/api/groups/${f.groupA}"), 200, "GET group by id"),
            "GET group by id"
        )
        assertEquals(f.groupA, single.path("groupId").asText())
        assertTrue(single.path("groupId").isTextual(), "groupId must be a string: $single")
        assertTrue(single.path("name").asText().isNotBlank())
    }

    @Test
    fun `missing and non-numeric group return 404 with code`() {
        assertError(get(publicPort, "/api/groups/999999999"), 404, "RESOURCE_NOT_FOUND", "GET missing group")
        assertError(get(publicPort, "/api/groups/abc"), 404, "RESOURCE_NOT_FOUND", "GET non-numeric group")
    }

    @Test
    fun `teams list and single team`() {
        val f = newFixture("T")
        val list = dataOf(assertStatus(get(publicPort, "/api/teams"), 200, "GET teams"), "GET teams")
        assertTrue(list.isArray && list.size() >= 3, "expected team list, got: $list")

        val single = dataOf(
            assertStatus(get(publicPort, "/api/teams/${f.teamA}"), 200, "GET team by id"),
            "GET team by id"
        )
        assertEquals(f.teamA, single.path("teamId").asText())
        assertTrue(single.path("teamId").isTextual(), "teamId must be a string: $single")
        assertTrue(single.path("groupId").isTextual(), "groupId must be a string: $single")
        assertTrue(single.has("class"), "team must expose 'class' field: $single")
        assertTrue(single.path("class").asText().isNotBlank())
        assertTrue(single.path("name").asText().isNotBlank())
    }

    @Test
    fun `missing and non-numeric team return 404 with code`() {
        assertError(get(publicPort, "/api/teams/999999999"), 404, "RESOURCE_NOT_FOUND", "GET missing team")
        assertError(get(publicPort, "/api/teams/abc"), 404, "RESOURCE_NOT_FOUND", "GET non-numeric team")
    }

    @Test
    fun `rounds list uses string ids`() {
        newFixture("R")
        val list = dataOf(assertStatus(get(publicPort, "/api/rounds"), 200, "GET rounds"), "GET rounds")
        assertTrue(list.isArray && list.size() >= 1, "expected round list, got: $list")
        for (round in list) {
            assertTrue(round.path("roundId").isTextual(), "roundId must be a string: $round")
            assertTrue(round.path("number").isInt && round.path("number").asInt() > 0, "number must be > 0: $round")
        }
    }

    @Test
    fun `fields list returns ids and names`() {
        val f = newFixture("F")
        val list = dataOf(assertStatus(get(publicPort, "/api/fields"), 200, "GET fields"), "GET fields")
        assertTrue(list.isArray && list.size() >= 1, "expected field list, got: $list")
        assertTrue(list.any { it.path("fieldId").asText() == f.fieldId }, "created field must be listed: $list")
        for (field in list) {
            assertTrue(field.path("fieldId").isTextual(), "fieldId must be a string: $field")
            assertTrue(field.path("name").asText().isNotBlank())
        }
    }

    @Test
    fun `games list and single game preserve nullable scores`() {
        val f = newFixture("M")
        val list = dataOf(assertStatus(get(publicPort, "/api/games"), 200, "GET games"), "GET games")
        assertTrue(list.isArray && list.size() >= 2, "expected game list, got: ${list.size()}")

        val unstarted = dataOf(
            assertStatus(get(publicPort, "/api/games/${f.unstartedGame}"), 200, "GET unstarted game"),
            "GET unstarted game"
        )
        assertCompleteGameDto(unstarted, f.unstartedGame.toString(), f)
        assertTrue(unstarted.path("scoreA").isNull(), "scoreA must stay null: $unstarted")
        assertTrue(unstarted.path("scoreB").isNull(), "scoreB must stay null: $unstarted")

        val started = dataOf(
            assertStatus(get(publicPort, "/api/games/${f.startedGame}"), 200, "GET started game"),
            "GET started game"
        )
        assertCompleteGameDto(started, f.startedGame.toString(), f)
        assertEquals(5, started.path("scoreA").asInt())
        assertEquals(3, started.path("scoreB").asInt())
    }

    private fun assertCompleteGameDto(game: com.fasterxml.jackson.databind.JsonNode, id: String, f: Fixture) {
        assertEquals(id, game.path("gameId").asText())
        assertTrue(game.path("gameId").isTextual(), "gameId must be a string: $game")
        assertEquals(f.roundId, game.path("roundId").asText())
        assertEquals(f.fieldId, game.path("fieldId").asText())
        assertEquals(f.teamA, game.path("teamAId").asText())
        assertEquals(f.teamB, game.path("teamBId").asText())
        assertEquals(f.referee, game.path("refereeTeamId").asText())
    }

    @Test
    fun `group leaderboard aggregates round 1 points`() {
        val tag = nextTag("L")
        val groupId = createGroup("G$tag").path("groupId").asText()
        val teamA = createTeam(groupId, "C", "Team A$tag").path("teamId").asText()
        val teamB = createTeam(groupId, "C", "Team B$tag").path("teamId").asText()
        val teamC = createTeam(groupId, "C", "Team C$tag").path("teamId").asText()
        val teamD = createTeam(groupId, "C", "Team D$tag").path("teamId").asText()
        val round1 = createRound(1).path("roundId").asText()
        val otherRound = createRound(Random.nextInt(10000000, 19999999)).path("roundId").asText()
        val fieldId = createField("Court $tag").path("fieldId").asText()

        fun game(round: String, a: String, b: String, ref: String, scoreA: String, scoreB: String) {
            createGame(round, fieldId, a, b, ref, scoreA.toIntOrNull(), scoreB.toIntOrNull())
        }

        game(round1, teamA, teamB, teamC, "10", "8")      // A += 10, B += 8
        game(round1, teamA, teamC, teamB, "5", "5")       // A += 5, C += 5 (refereeing game 1 gives C nothing)
        game(otherRound, teamB, teamC, teamA, "100", "100") // other rounds do not count
        game(round1, teamA, teamB, teamC, "null", "null") // unstarted games contribute 0

        val board = dataOf(
            assertStatus(get(publicPort, "/api/groups/$groupId/leaderboard"), 200, "GET leaderboard"),
            "GET leaderboard"
        )
        assertTrue(board.isArray && board.size() == 4, "expected 4 entries, got: $board")
        val entries = board.map {
            Triple(it.path("teamId").asText(), it.path("name").asText(), it.path("points").asInt())
        }
        assertTrue(board.all { it.path("teamId").isTextual() }, "teamId must be a string: $board")
        assertEquals(
            listOf(Triple(teamA, "Team A$tag", 15), Triple(teamB, "Team B$tag", 8), Triple(teamC, "Team C$tag", 5)),
            entries.take(3),
            "points DESC with round-1-only scoring: $board"
        )
        assertEquals(Triple(teamD, "Team D$tag", 0), entries[3], "team without games scores 0: $board")
    }

    @Test
    fun `leaderboard for missing and non-numeric group returns 404 with code`() {
        assertError(
            get(publicPort, "/api/groups/999999999/leaderboard"),
            404, "RESOURCE_NOT_FOUND", "GET leaderboard for missing group"
        )
        assertError(
            get(publicPort, "/api/groups/abc/leaderboard"),
            404, "RESOURCE_NOT_FOUND", "GET leaderboard for non-numeric group"
        )
    }

    @Test
    fun `missing and non-numeric game return 404 with code`() {
        assertError(get(publicPort, "/api/games/999999999"), 404, "RESOURCE_NOT_FOUND", "GET missing game")
        assertError(get(publicPort, "/api/games/abc"), 404, "RESOURCE_NOT_FOUND", "GET non-numeric game")
    }

    @Test
    fun `filter by valid round returns matching games`() {
        val f = newFixture("V")
        val res = dataOf(
            assertStatus(get(publicPort, "/api/games/filter/${f.roundId}"), 200, "GET filter valid round"),
            "GET filter valid round"
        )
        assertTrue(res.isArray, "filter must return a list: $res")
        val ids = res.map { it.path("gameId").asText() }
        assertTrue(ids.contains(f.unstartedGame.toString()), "unstarted game must match: $ids")
        assertTrue(ids.contains(f.startedGame.toString()), "started game must match: $ids")
    }

    @Test
    fun `filter by numeric round without games returns empty list`() {
        val roundId = createRound(Random.nextInt(10000000, 99999999)).path("roundId").asText()
        val res = dataOf(
            assertStatus(get(publicPort, "/api/games/filter/$roundId"), 200, "GET filter empty round"),
            "GET filter empty round"
        )
        assertTrue(res.isArray && res.size() == 0, "expected empty list, got: $res")
    }

    @Test
    fun `filter invalid returns 400 with code`() {
        assertError(get(publicPort, "/api/games/filter/invalid"), 400, "BAD_REQUEST", "GET filter 'invalid'")
    }

    @Test
    fun `blank filter returns 400`() {
        assertError(get(publicPort, "/api/games/filter/%20"), 400, "BAD_REQUEST", "GET blank filter")
    }

    @Test
    fun `other non-numeric filter returns empty list`() {
        val res = dataOf(
            assertStatus(get(publicPort, "/api/games/filter/abc123"), 200, "GET filter abc123"),
            "GET filter abc123"
        )
        assertTrue(res.isArray && res.size() == 0, "expected empty list, got: $res")
    }
}
