package de.atiw.volleyball

import com.fasterxml.jackson.databind.ObjectMapper
import de.atiw.volleyball.entity.Field
import de.atiw.volleyball.entity.Game
import de.atiw.volleyball.entity.GameStatus
import de.atiw.volleyball.entity.Round
import de.atiw.volleyball.entity.Team
import de.atiw.volleyball.entity.TournamentGroup
import de.atiw.volleyball.repository.FieldRepository
import de.atiw.volleyball.repository.GameRepository
import de.atiw.volleyball.repository.GroupRepository
import de.atiw.volleyball.repository.RoundRepository
import de.atiw.volleyball.repository.TeamRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional

/**
 * Automatic game generation contract (MockMvc, transactional and therefore
 * isolated): round-robin, knockout and consolation algorithms, idempotency,
 * validation, generated-game defaults and port-agnostic error contracts.
 * Realtime event delivery is covered by [GameGenerationRealtimeTest], real
 * port separation by [PortIsolationIT].
 */
@AutoConfigureMockMvc
@Transactional
class GameGenerationTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var groupRepository: GroupRepository

    @Autowired
    lateinit var teamRepository: TeamRepository

    @Autowired
    lateinit var roundRepository: RoundRepository

    @Autowired
    lateinit var fieldRepository: FieldRepository

    @Autowired
    lateinit var gameRepository: GameRepository

    private fun group(name: String): TournamentGroup =
        groupRepository.save(TournamentGroup(designation = name))

    private fun team(group: TournamentGroup, name: String): Team =
        teamRepository.save(Team(group = group, teamClass = "C", name = name))

    private fun round(number: Int): Round =
        roundRepository.save(Round(roundNumber = number))

    private fun field(name: String): Field =
        fieldRepository.save(Field(name = name))

    private fun game(round: Round, field: Field, a: Team, b: Team, ref: Team, scoreA: Int, scoreB: Int): Game =
        gameRepository.save(
            Game(
                round = round, field = field, teamA = a, teamB = b, refereeTeam = ref,
                pointsA = scoreA, pointsB = scoreB, status = GameStatus.SCHEDULED
            )
        )

    private fun generate(kind: String, roundId: Int) =
        mockMvc.post("/api/admin/rounds/$roundId/generate-games/$kind")

    private fun generatedGameIds(body: String): List<Int> =
        objectMapper.readTree(body).get("data").get("games").map { it.get("gameId").asText().toInt() }

    // ---- round robin ----

    @Test
    fun `round-robin with four teams creates six games`() {
        val g = group("A")
        val teams = (1..4).map { team(g, "T$it") }
        val target = round(2)
        val f1 = field("Court 1")
        val f2 = field("Court 2")

        val result = generate("round-robin", target.roundId)
            .andExpect {
                status { isCreated() }
                jsonPath("$.data.roundId") { value(target.roundId.toString()) }
                jsonPath("$.data.gamesCreated") { value(6) }
                jsonPath("$.data.gamesSkipped") { value(0) }
                jsonPath("$.data.games.length()") { value(6) }
            }.andReturn()

        val games = objectMapper.readTree(result.response.contentAsString).get("data").get("games")
        val pairs = games.map { setOf(it.get("teamAId").asText().toInt(), it.get("teamBId").asText().toInt()) }
        val ids = teams.map { it.teamId }
        val expected = mutableSetOf<Set<Int>>()
        for (i in ids.indices) for (j in i + 1 until ids.size) expected += setOf(ids[i], ids[j])
        assert(pairs.toSet() == expected) { "all unique pairs must be generated: $pairs" }

        for (game in games) {
            assert(game.get("scoreA").asInt() == 0 && game.get("scoreB").asInt() == 0)
            assert(game.get("status").asText() == "SCHEDULED")
            val a = game.get("teamAId").asText().toInt()
            val b = game.get("teamBId").asText().toInt()
            val ref = game.get("refereeTeamId").asText().toInt()
            assert(ref != a && ref != b) { "referee must not participate: $game" }
        }
        // Group-synced fields: the single group plays everything on field 1,
        // even though a second field exists (no rotation across games).
        val fieldIds = games.map { it.get("fieldId").asText().toInt() }
        assert(fieldIds.all { it == f1.fieldId }) { "all group A games must be on field 1: $fieldIds" }
    }

    @Test
    fun `round-robin with two groups creates twelve games without cross-group play`() {
        val gA = group("A")
        val gB = group("B")
        val teamsA = (1..4).map { team(gA, "A$it") }
        val teamsB = (1..4).map { team(gB, "B$it") }
        val target = round(2)
        val f1 = field("Court 1")
        val f2 = field("Court 2")

        generate("round-robin", target.roundId)
            .andExpect {
                status { isCreated() }
                jsonPath("$.data.gamesCreated") { value(12) }
                jsonPath("$.data.gamesSkipped") { value(0) }
            }

        val groupOf = (teamsA + teamsB).associate { it.teamId to it.group.groupId }
        val games = gameRepository.findAll().filter { it.round.roundId == target.roundId }
        assert(games.size == 12)
        val inA = games.filter { groupOf[it.teamA.teamId] == gA.groupId && groupOf[it.teamB.teamId] == gA.groupId }
        val inB = games.filter { groupOf[it.teamA.teamId] == gB.groupId && groupOf[it.teamB.teamId] == gB.groupId }
        assert(inA.size == 6 && inB.size == 6) { "6 group A and 6 group B games expected" }
        // Group-synced fields: group A entirely on field 1, group B on field 2.
        assert(inA.all { it.field.fieldId == f1.fieldId }) { "group A games must be on field 1" }
        assert(inB.all { it.field.fieldId == f2.fieldId }) { "group B games must be on field 2" }
    }

    @Test
    fun `round-robin wraps fields when groups outnumber fields`() {
        val groups = listOf("A", "B", "C").map { group(it) }
        groups.forEachIndexed { index, g ->
            team(g, "T${index}1")
            team(g, "T${index}2")
        }
        val target = round(2)
        val f1 = field("Court 1")
        field("Court 2")

        generate("round-robin", target.roundId)
            .andExpect { jsonPath("$.data.gamesCreated") { value(3) } }

        val byGroup = gameRepository.findAll()
            .filter { it.round.roundId == target.roundId }
            .groupBy { it.teamA.group.groupId }
        // A -> field 1, B -> field 2, C wraps back to field 1.
        assert(byGroup.size == 3)
        assert(byGroup[groups[0].groupId]!!.all { it.field.fieldId == f1.fieldId })
        assert(byGroup[groups[1].groupId]!!.single().field.name == "Court 2")
        assert(byGroup[groups[2].groupId]!!.all { it.field.fieldId == f1.fieldId })
    }

    @Test
    fun `round-robin is idempotent and never modifies existing games`() {
        val g = group("A")
        val teams = (1..4).map { team(g, "T$it") }
        val target = round(2)
        field("Court 1")

        generate("round-robin", target.roundId)
            .andExpect {
                jsonPath("$.data.gamesCreated") { value(6) }
                jsonPath("$.data.gamesSkipped") { value(0) }
            }

        // Progress one game, then regenerate: it must stay untouched.
        val existing = gameRepository.findAll().first { it.round.roundId == target.roundId }
        putScores(existing, 15, 12)
        mockMvc.post("/api/games/${existing.gameId}/start").andExpect { status { isOk() } }

        generate("round-robin", target.roundId)
            .andExpect {
                status { isCreated() }
                jsonPath("$.data.gamesCreated") { value(0) }
                jsonPath("$.data.gamesSkipped") { value(6) }
                jsonPath("$.data.games.length()") { value(0) }
            }

        assert(gameRepository.findAll().count { it.round.roundId == target.roundId } == 6)
        val reloaded = gameRepository.findById(existing.gameId).orElseThrow()
        assert(reloaded.pointsA == 15 && reloaded.pointsB == 12)
        assert(reloaded.status == GameStatus.RUNNING)
    }

    @Test
    fun `round-robin without fields returns 400 and creates nothing`() {
        val g = group("A")
        (1..3).forEach { team(g, "T$it") }
        val target = round(2)

        generate("round-robin", target.roundId)
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("BAD_REQUEST") }
            }

        assert(gameRepository.count() == 0L)
    }

    @Test
    fun `round-robin without valid referee returns 400 and creates nothing`() {
        val g = group("A")
        team(g, "T1")
        team(g, "T2")
        val target = round(2)
        field("Court 1")

        generate("round-robin", target.roundId)
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("BAD_REQUEST") }
            }

        assert(gameRepository.count() == 0L)
    }

    @Test
    fun `generation on missing or non-numeric round returns 404`() {
        for (kind in listOf("round-robin", "knockout", "consolation")) {
            mockMvc.post("/api/admin/rounds/999999/generate-games/$kind")
                .andExpect { status { isNotFound() } }
            mockMvc.post("/api/admin/rounds/abc/generate-games/$kind")
                .andExpect { status { isNotFound() } }
        }
    }

    // ---- knockout ----

    private data class KnockoutSetup(
        val groups: List<TournamentGroup>,
        val leaders: List<Team>,
        val round1: Round,
        val semi: Round,
        val final: Round,
        val field: Field
    )

    /** Four groups of two teams each; X1 beats X2 in round 1, so X1 leads. */
    private fun knockoutSetup(): KnockoutSetup {
        val groups = listOf("A", "B", "C", "D").map { group(it) }
        val pairs = groups.map { g ->
            val first = team(g, "${g.designation}1")
            val second = team(g, "${g.designation}2")
            first to second
        }
        val r1 = round(1)
        val f = field("Court 1")
        field("Court 2")
        val neutralRef = pairs.last().first
        for ((first, second) in pairs) {
            game(r1, f, first, second, neutralRef, 25, 10)
        }
        val semi = round(2)
        val final = round(3)
        return KnockoutSetup(groups, pairs.map { it.first }, r1, semi, final, f)
    }

    @Test
    fun `knockout first stage pairs group winners deterministically`() {
        val s = knockoutSetup()
        val (a1, b1, c1, d1) = s.leaders

        val result = generate("knockout", s.semi.roundId)
            .andExpect {
                status { isCreated() }
                jsonPath("$.data.gamesCreated") { value(2) }
                jsonPath("$.data.gamesSkipped") { value(0) }
            }.andReturn()

        val games = objectMapper.readTree(result.response.contentAsString).get("data").get("games")
        val first = games[0]
        val second = games[1]
        assert(first.get("teamAId").asText().toInt() == a1.teamId)
        assert(first.get("teamBId").asText().toInt() == d1.teamId)
        assert(second.get("teamAId").asText().toInt() == b1.teamId)
        assert(second.get("teamBId").asText().toInt() == c1.teamId)
        // Group-synced fields follow Team A's group: A1vD1 on field 1, B1vC1 on field 2.
        assert(first.get("fieldId").asText() == s.field.fieldId.toString())
        assert(second.get("fieldId").asText() != s.field.fieldId.toString())
        for (game in games) {
            assert(game.get("status").asText() == "SCHEDULED")
            assert(game.get("scoreA").asInt() == 0 && game.get("scoreB").asInt() == 0)
            val ref = game.get("refereeTeamId").asText().toInt()
            assert(ref != game.get("teamAId").asText().toInt() && ref != game.get("teamBId").asText().toInt())
        }
        // Cross-group pairings only.
        val groupOf = s.groups.flatMap { g ->
            teamRepository.findAll().filter { it.group.groupId == g.groupId }.map { it.teamId to g.groupId }
        }.toMap()
        for (game in games) {
            assert(groupOf[game.get("teamAId").asText().toInt()] != groupOf[game.get("teamBId").asText().toInt()])
        }
    }

    @Test
    fun `knockout final uses semifinal winners`() {
        val s = knockoutSetup()
        val (a1, _, c1, _) = s.leaders

        val semiGames = generatedGameIds(
            generate("knockout", s.semi.roundId).andExpect { status { isCreated() } }.andReturn()
                .response.contentAsString
        )
        // Semifinal 1: A1 beats D1. Semifinal 2: C1 beats B1.
        finishGame(semiGames[0], 25, 20)
        finishGame(semiGames[1], 20, 25)

        val result = generate("knockout", s.final.roundId)
            .andExpect {
                status { isCreated() }
                jsonPath("$.data.gamesCreated") { value(1) }
                jsonPath("$.data.gamesSkipped") { value(0) }
            }.andReturn()

        val games = objectMapper.readTree(result.response.contentAsString).get("data").get("games")
        assert(games.size() == 1)
        assert(games[0].get("teamAId").asText().toInt() == a1.teamId)
        assert(games[0].get("teamBId").asText().toInt() == c1.teamId)
        assert(games[0].get("status").asText() == "SCHEDULED")
    }

    @Test
    fun `knockout with unfinished semifinal returns 409 and creates nothing`() {
        val s = knockoutSetup()
        val semiGames = generatedGameIds(
            generate("knockout", s.semi.roundId).andExpect { status { isCreated() } }.andReturn()
                .response.contentAsString
        )
        finishGame(semiGames[0], 25, 20)
        // Second semifinal stays SCHEDULED.

        generate("knockout", s.final.roundId)
            .andExpect {
                status { isConflict() }
                jsonPath("$.error.code") { value("CONFLICT") }
            }

        assert(gameRepository.findAll().none { it.round.roundId == s.final.roundId })
    }

    @Test
    fun `knockout with drawn semifinal returns 400 and creates nothing`() {
        val s = knockoutSetup()
        val semiGames = generatedGameIds(
            generate("knockout", s.semi.roundId).andExpect { status { isCreated() } }.andReturn()
                .response.contentAsString
        )
        finishGame(semiGames[0], 25, 20)
        finishGame(semiGames[1], 20, 20)

        generate("knockout", s.final.roundId)
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("BAD_REQUEST") }
            }

        assert(gameRepository.findAll().none { it.round.roundId == s.final.roundId })
    }

    @Test
    fun `knockout with six qualified teams returns 400`() {
        (1..6).forEach { i ->
            val g = group("G$i")
            team(g, "T$i")
        }
        val target = round(2)
        field("Court 1")

        generate("knockout", target.roundId)
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("BAD_REQUEST") }
            }

        assert(gameRepository.count() == 0L)
    }

    @Test
    fun `knockout duplicate generation creates nothing`() {
        val s = knockoutSetup()

        generate("knockout", s.semi.roundId)
            .andExpect {
                jsonPath("$.data.gamesCreated") { value(2) }
                jsonPath("$.data.gamesSkipped") { value(0) }
            }
        generate("knockout", s.semi.roundId)
            .andExpect {
                status { isCreated() }
                jsonPath("$.data.gamesCreated") { value(0) }
                jsonPath("$.data.gamesSkipped") { value(2) }
            }

        assert(gameRepository.findAll().count { it.round.roundId == s.semi.roundId } == 2)
    }

    // ---- consolation ----

    private data class ConsolationSetup(
        val groups: List<TournamentGroup>,
        val trailers: List<Team>,
        val target: Round,
        val field: Field
    )

    /** Four groups of three teams; X1 > X2 > X3 by round-1 points, so X3 is last. */
    private fun consolationSetup(): ConsolationSetup {
        val groups = listOf("A", "B", "C", "D").map { group(it) }
        val triples = groups.map { g ->
            Triple(team(g, "${g.designation}1"), team(g, "${g.designation}2"), team(g, "${g.designation}3"))
        }
        val r1 = round(1)
        val f = field("Court 1")
        field("Court 2")
        val neutralRef = triples.last().first
        for ((first, second, third) in triples) {
            game(r1, f, first, second, neutralRef, 25, 10)
            game(r1, f, first, third, neutralRef, 25, 5)
            game(r1, f, second, third, neutralRef, 25, 15)
        }
        return ConsolationSetup(groups, triples.map { it.third }, round(4), f)
    }

    @Test
    fun `consolation selects lowest-ranked teams and pairs deterministically`() {
        val s = consolationSetup()
        val (a4, b4, c4, d4) = s.trailers

        val result = generate("consolation", s.target.roundId)
            .andExpect {
                status { isCreated() }
                jsonPath("$.data.gamesCreated") { value(2) }
                jsonPath("$.data.gamesSkipped") { value(0) }
            }.andReturn()

        val games = objectMapper.readTree(result.response.contentAsString).get("data").get("games")
        // Lowest by points (highest team IDs here) proves leaderboard selection.
        assert(games[0].get("teamAId").asText().toInt() == a4.teamId)
        assert(games[0].get("teamBId").asText().toInt() == d4.teamId)
        assert(games[1].get("teamAId").asText().toInt() == b4.teamId)
        assert(games[1].get("teamBId").asText().toInt() == c4.teamId)
        // Group-synced fields follow Team A's group: A4vD4 on field 1, B4vC4 on field 2.
        assert(games[0].get("fieldId").asText() == s.field.fieldId.toString())
        assert(games[1].get("fieldId").asText() != s.field.fieldId.toString())
        // Each selected team participates exactly once.
        val participants = games.flatMap {
            listOf(it.get("teamAId").asText().toInt(), it.get("teamBId").asText().toInt())
        }
        assert(participants.sorted() == listOf(a4.teamId, b4.teamId, c4.teamId, d4.teamId).sorted())
        for (game in games) {
            assert(game.get("status").asText() == "SCHEDULED")
            assert(game.get("scoreA").asInt() == 0 && game.get("scoreB").asInt() == 0)
        }
    }

    @Test
    fun `consolation with odd number of teams returns 400`() {
        val r1 = round(1)
        val f = field("Court 1")
        listOf("A", "B", "C").forEach { name ->
            val g = group(name)
            val t1 = team(g, "${name}1")
            val t2 = team(g, "${name}2")
            game(r1, f, t1, t2, t1, 25, 10)
        }
        val target = round(4)

        generate("consolation", target.roundId)
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("BAD_REQUEST") }
            }
    }

    @Test
    fun `consolation duplicate generation creates nothing`() {
        val s = consolationSetup()

        generate("consolation", s.target.roundId)
            .andExpect {
                jsonPath("$.data.gamesCreated") { value(2) }
                jsonPath("$.data.gamesSkipped") { value(0) }
            }
        generate("consolation", s.target.roundId)
            .andExpect {
                jsonPath("$.data.gamesCreated") { value(0) }
                jsonPath("$.data.gamesSkipped") { value(2) }
            }

        assert(gameRepository.findAll().count { it.round.roundId == s.target.roundId } == 2)
    }

    @Test
    fun `full tournament flow from round-robin in round 1 to the final`() {
        // Group stage generated directly into the round_number = 1 round.
        val groups = listOf("A", "B", "C", "D").map { group(it) }
        val pairs = groups.map { g -> team(g, "${g.designation}1") to team(g, "${g.designation}2") }
        val groupStage = round(1)
        field("Court 1")
        val semi = round(2)
        val final = round(3)

        generate("round-robin", groupStage.roundId)
            .andExpect { jsonPath("$.data.gamesCreated") { value(4) } }

        // Finish every group game with the first team winning.
        for (game in gameRepository.findAll().filter { it.round.roundId == groupStage.roundId }) {
            putScores(game, 25, 10)
            mockMvc.post("/api/games/${game.gameId}/start").andExpect { status { isOk() } }
            mockMvc.post("/api/games/${game.gameId}/end").andExpect { status { isOk() } }
        }

        // Semifinals from the group-stage leaderboards: A1vD1, B1vC1.
        val leaders = pairs.map { it.first }
        val (a1, b1, c1, d1) = leaders
        val semiIds = generatedGameIds(
            generate("knockout", semi.roundId)
                .andExpect { jsonPath("$.data.gamesCreated") { value(2) } }
                .andReturn().response.contentAsString
        )
        val semiGames = semiIds.map { gameRepository.findById(it).orElseThrow() }
        assert(semiGames[0].teamA.teamId == a1.teamId && semiGames[0].teamB.teamId == d1.teamId)
        assert(semiGames[1].teamA.teamId == b1.teamId && semiGames[1].teamB.teamId == c1.teamId)

        // A1 and C1 win their semifinals; the final is A1 vs C1.
        finishGame(semiIds[0], 25, 20)
        finishGame(semiIds[1], 20, 25)
        val finalIds = generatedGameIds(
            generate("knockout", final.roundId)
                .andExpect { jsonPath("$.data.gamesCreated") { value(1) } }
                .andReturn().response.contentAsString
        )
        val finalGame = gameRepository.findById(finalIds.single()).orElseThrow()
        assert(finalGame.teamA.teamId == a1.teamId && finalGame.teamB.teamId == c1.teamId)
        assert(finalGame.status == GameStatus.SCHEDULED)
    }

    // ---- helpers ----

    private fun gameBody(game: Game, scoreA: Int, scoreB: Int): Map<String, Any?> {
        val reloaded = gameRepository.findById(game.gameId).orElseThrow()
        return mapOf(
            "roundId" to reloaded.round.roundId,
            "fieldId" to reloaded.field.fieldId,
            "teamAId" to reloaded.teamA.teamId,
            "teamBId" to reloaded.teamB.teamId,
            "refereeTeamId" to reloaded.refereeTeam.teamId,
            "scoreA" to scoreA,
            "scoreB" to scoreB
        )
    }

    private fun putScores(game: Game, scoreA: Int, scoreB: Int) {
        mockMvc.put("/api/admin/games/${game.gameId}") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(gameBody(game, scoreA, scoreB))
        }.andExpect { status { isOk() } }
    }

    private fun finishGame(gameId: Int, scoreA: Int, scoreB: Int) {
        val game = gameRepository.findById(gameId).orElseThrow()
        putScores(game, scoreA, scoreB)
        mockMvc.post("/api/games/$gameId/start").andExpect { status { isOk() } }
        mockMvc.post("/api/games/$gameId/end").andExpect { status { isOk() } }
    }

    @Test
    fun `generated games do not affect other rounds leaderboards`() {
        val g = group("A")
        val t1 = team(g, "T1")
        val t2 = team(g, "T2")
        team(g, "T3")
        round(1)
        val target = round(2)
        field("Court 1")

        generate("round-robin", target.roundId).andExpect { status { isCreated() } }

        // Round-robin games live in round 2; the round-1 leaderboard stays 0/0.
        mockMvc.get("/api/groups/${g.groupId}/leaderboard")
            .andExpect {
                status { isOk() }
                jsonPath("$.data[?(@.teamId == '${t1.teamId}')].points") { value(0) }
                jsonPath("$.data[?(@.teamId == '${t2.teamId}')].points") { value(0) }
            }
    }
}
