package de.atiw.volleyball

import de.atiw.volleyball.entity.Field
import de.atiw.volleyball.entity.Game
import de.atiw.volleyball.entity.Round
import de.atiw.volleyball.entity.Team
import de.atiw.volleyball.entity.TournamentGroup
import de.atiw.volleyball.repository.FieldRepository
import de.atiw.volleyball.repository.GameRepository
import de.atiw.volleyball.repository.GroupRepository
import de.atiw.volleyball.repository.RoundRepository
import de.atiw.volleyball.repository.TeamRepository
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional

@AutoConfigureMockMvc
@Transactional
class GroupLeaderboardTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

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

    private data class Fixture(
        val group: TournamentGroup,
        val teams: List<Team>,
        val round1: Round,
        val field: Field
    )

    private fun baseFixture(groupName: String = "A", teamNames: List<String> = listOf("T1", "T2", "T3", "T4")): Fixture {
        val g = groupRepository.save(TournamentGroup(designation = groupName))
        val teams = teamNames.map { teamRepository.save(Team(group = g, teamClass = "C", name = it)) }
        val round1 = roundRepository.save(Round(roundNumber = 1))
        val field = fieldRepository.save(Field(name = "Court $groupName ${System.nanoTime()}"))
        return Fixture(g, teams, round1, field)
    }

    private fun game(round: Round, field: Field, a: Team, b: Team, ref: Team, scoreA: Int, scoreB: Int): Game =
        gameRepository.save(Game(round = round, field = field, teamA = a, teamB = b, refereeTeam = ref, pointsA = scoreA, pointsB = scoreB))

    @Test
    fun `successful leaderboard calculates and sorts`() {
        val f = baseFixture()
        val (t1, t2, t3, t4) = f.teams
        // T1 vs T2 25:21, T1 vs T3 18:25
        game(f.round1, f.field, t1, t2, t4, 25, 21)
        game(f.round1, f.field, t1, t3, t2, 18, 25)

        mockMvc.get("/api/groups/${f.group.groupId}/leaderboard")
            .andExpect {
                status { isOk() }
                jsonPath("$.data.length()") { value(4) }
                // T1=43, T3=25, T2=21, T4=0
                jsonPath("$.data[0].points") { value(43) }
                jsonPath("$.data[1].points") { value(25) }
                jsonPath("$.data[2].points") { value(21) }
                jsonPath("$.data[3].points") { value(0) }
                jsonPath("$.data[0].teamId") { value(t1.teamId.toString()) }
                jsonPath("$.data[3].teamId") { value(t4.teamId.toString()) }
            }
    }

    @Test
    fun `only round 1 counts`() {
        val f = baseFixture()
        val (t1, t2) = f.teams
        val r2 = roundRepository.save(Round(roundNumber = 2))
        val r3 = roundRepository.save(Round(roundNumber = 3))
        game(f.round1, f.field, t1, t2, f.teams[2], 10, 5)
        game(r2, f.field, t1, t2, f.teams[2], 50, 0)
        game(r3, f.field, t1, t2, f.teams[2], 100, 0)

        mockMvc.get("/api/groups/${f.group.groupId}/leaderboard")
            .andExpect {
                status { isOk() }
                jsonPath("$.data[?(@.teamId == '${t1.teamId}')].points") { value(10) }
            }
    }

    @Test
    fun `uses round_number not round_id`() {
        // Force round_id=1 to be round_number=2 by creating it first, then round_number=1.
        val g = groupRepository.save(TournamentGroup(designation = "RN"))
        val t1 = teamRepository.save(Team(group = g, teamClass = "C", name = "T1"))
        val t2 = teamRepository.save(Team(group = g, teamClass = "C", name = "T2"))
        val t3 = teamRepository.save(Team(group = g, teamClass = "C", name = "T3"))
        val field = fieldRepository.save(Field(name = "Court RN ${System.nanoTime()}"))
        val roundNumber2 = roundRepository.save(Round(roundNumber = 2))
        val roundNumber1 = roundRepository.save(Round(roundNumber = 1))
        // Game in round_number=2 (lower round_id) gives T1 100 pts — must be ignored.
        game(roundNumber2, field, t1, t2, t3, 100, 0)
        // Game in round_number=1 gives T1 7 pts — must be used.
        game(roundNumber1, field, t1, t2, t3, 7, 3)

        mockMvc.get("/api/groups/${g.groupId}/leaderboard")
            .andExpect {
                status { isOk() }
                jsonPath("$.data[?(@.teamId == '${t1.teamId}')].points") { value(7) }
                jsonPath("$.data[?(@.teamId == '${t2.teamId}')].points") { value(3) }
            }
    }

    @Test
    fun `team A and team B columns are used correctly and referee gets nothing`() {
        val f = baseFixture(teamNames = listOf("A", "B", "C"))
        val (a, b, c) = f.teams
        game(f.round1, f.field, a, b, c, 25, 18)
        game(f.round1, f.field, b, c, a, 20, 22)

        mockMvc.get("/api/groups/${f.group.groupId}/leaderboard")
            .andExpect {
                status { isOk() }
                jsonPath("$.data[?(@.teamId == '${a.teamId}')].points") { value(25) }
                jsonPath("$.data[?(@.teamId == '${b.teamId}')].points") { value(38) }
                jsonPath("$.data[?(@.teamId == '${c.teamId}')].points") { value(22) }
            }
    }

    @Test
    fun `team with no round1 games gets zero`() {
        val f = baseFixture(teamNames = listOf("A", "B", "C"))
        val (a, b, c) = f.teams
        game(f.round1, f.field, a, b, c, 25, 20)

        mockMvc.get("/api/groups/${f.group.groupId}/leaderboard")
            .andExpect {
                status { isOk() }
                jsonPath("$.data[?(@.teamId == '${c.teamId}')].points") { value(0) }
            }
    }

    @Test
    fun `zero scores contribute zero`() {
        val f = baseFixture(teamNames = listOf("A", "B"))
        val (a, b) = f.teams
        game(f.round1, f.field, a, b, a, 0, 0)

        mockMvc.get("/api/groups/${f.group.groupId}/leaderboard")
            .andExpect {
                status { isOk() }
                jsonPath("$.data[?(@.teamId == '${a.teamId}')].points") { value(0) }
                jsonPath("$.data[?(@.teamId == '${b.teamId}')].points") { value(0) }
            }
    }

    @Test
    fun `sorting is points DESC teamId ASC`() {
        val f = baseFixture(teamNames = listOf("A", "B", "C", "D"))
        val (a, b, c, d) = f.teams
        // A=60, C=40, B=15, D=0
        game(f.round1, f.field, a, b, d, 60, 15)
        game(f.round1, f.field, c, d, a, 40, 0)

        mockMvc.get("/api/groups/${f.group.groupId}/leaderboard")
            .andExpect {
                status { isOk() }
                jsonPath("$.data[0].teamId") { value(a.teamId.toString()) }
                jsonPath("$.data[1].teamId") { value(c.teamId.toString()) }
                jsonPath("$.data[2].teamId") { value(b.teamId.toString()) }
                jsonPath("$.data[3].teamId") { value(d.teamId.toString()) }
            }
    }

    @Test
    fun `ties ordered by teamId ASC`() {
        val f = baseFixture(teamNames = listOf("A", "B", "C"))
        val (a, b, c) = f.teams
        game(f.round1, f.field, a, c, b, 25, 0)
        game(f.round1, f.field, b, c, a, 25, 0)

        val firstId = minOf(a.teamId, b.teamId).toString()
        val secondId = maxOf(a.teamId, b.teamId).toString()
        mockMvc.get("/api/groups/${f.group.groupId}/leaderboard")
            .andExpect {
                status { isOk() }
                jsonPath("$.data[0].teamId") { value(firstId) }
                jsonPath("$.data[1].teamId") { value(secondId) }
            }
    }

    @Test
    fun `empty group returns empty list`() {
        val g = groupRepository.save(TournamentGroup(designation = "E"))
        mockMvc.get("/api/groups/${g.groupId}/leaderboard")
            .andExpect {
                status { isOk() }
                jsonPath("$.data") { hasSize<Any>(0) }
            }
    }

    @Test
    fun `missing group returns 404`() {
        mockMvc.get("/api/groups/999999/leaderboard")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `non-numeric id returns 404`() {
        mockMvc.get("/api/groups/abc/leaderboard")
            .andExpect { status { isNotFound() } }
    }
}
