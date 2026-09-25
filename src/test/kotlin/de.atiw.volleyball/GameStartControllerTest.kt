package de.atiw.volleyball

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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

/**
 * Lifecycle contract for `POST /api/games/{id}/start` and `/end`:
 * `SCHEDULED -> RUNNING -> FINISHED`, scores untouched, all other
 * transitions rejected with 409 and no state change. Scores are changed
 * exclusively via `PUT /api/admin/games/{id}` (see TeacherGameControllerTest).
 */
@AutoConfigureMockMvc
@Transactional
class GameStartControllerTest : AbstractIntegrationTest() {

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

    private fun game(scoreA: Int = 0, scoreB: Int = 0, status: GameStatus = GameStatus.SCHEDULED): Game {
        val g = groupRepository.save(TournamentGroup(designation = "A"))
        val t1 = teamRepository.save(Team(group = g, teamClass = "C", name = "T1"))
        val t2 = teamRepository.save(Team(group = g, teamClass = "C", name = "T2"))
        val t3 = teamRepository.save(Team(group = g, teamClass = "C", name = "T3"))
        val round = roundRepository.save(Round(roundNumber = 1))
        val field = fieldRepository.save(Field(name = "Court 1"))
        return gameRepository.save(
            Game(
                round = round, field = field, teamA = t1, teamB = t2, refereeTeam = t3,
                pointsA = scoreA, pointsB = scoreB, status = status
            )
        )
    }

    @Test
    fun `start moves SCHEDULED to RUNNING without touching scores`() {
        val game = game(5, 3, GameStatus.SCHEDULED)

        mockMvc.post("/api/games/${game.gameId}/start")
            .andExpect {
                status { isOk() }
                jsonPath("$.data.status") { value("RUNNING") }
                jsonPath("$.data.scoreA") { value(5) }
                jsonPath("$.data.scoreB") { value(3) }
            }

        val reloaded = gameRepository.findById(game.gameId).orElseThrow()
        assert(reloaded.status == GameStatus.RUNNING)
        assert(reloaded.pointsA == 5 && reloaded.pointsB == 3)
    }

    @Test
    fun `start on RUNNING game returns 409 and changes nothing`() {
        val game = game(5, 3, GameStatus.RUNNING)

        mockMvc.post("/api/games/${game.gameId}/start")
            .andExpect { status { isConflict() } }

        val reloaded = gameRepository.findById(game.gameId).orElseThrow()
        assert(reloaded.status == GameStatus.RUNNING)
        assert(reloaded.pointsA == 5 && reloaded.pointsB == 3)
    }

    @Test
    fun `start on FINISHED game returns 409 and changes nothing`() {
        val game = game(25, 21, GameStatus.FINISHED)

        mockMvc.post("/api/games/${game.gameId}/start")
            .andExpect { status { isConflict() } }

        assert(gameRepository.findById(game.gameId).orElseThrow().status == GameStatus.FINISHED)
    }

    @Test
    fun `end moves RUNNING to FINISHED without touching scores`() {
        val game = game(25, 21, GameStatus.RUNNING)

        mockMvc.post("/api/games/${game.gameId}/end")
            .andExpect {
                status { isOk() }
                jsonPath("$.data.status") { value("FINISHED") }
                jsonPath("$.data.scoreA") { value(25) }
                jsonPath("$.data.scoreB") { value(21) }
            }

        val reloaded = gameRepository.findById(game.gameId).orElseThrow()
        assert(reloaded.status == GameStatus.FINISHED)
        assert(reloaded.pointsA == 25 && reloaded.pointsB == 21)
    }

    @Test
    fun `end on SCHEDULED game returns 409 and changes nothing`() {
        val game = game(0, 0, GameStatus.SCHEDULED)

        mockMvc.post("/api/games/${game.gameId}/end")
            .andExpect { status { isConflict() } }

        assert(gameRepository.findById(game.gameId).orElseThrow().status == GameStatus.SCHEDULED)
    }

    @Test
    fun `end on FINISHED game returns 409 and changes nothing`() {
        val game = game(25, 21, GameStatus.FINISHED)

        mockMvc.post("/api/games/${game.gameId}/end")
            .andExpect { status { isConflict() } }

        assert(gameRepository.findById(game.gameId).orElseThrow().status == GameStatus.FINISHED)
    }

    @Test
    fun `start and end on nonexistent game return 404`() {
        mockMvc.post("/api/games/999999/start")
            .andExpect { status { isNotFound() } }
        mockMvc.post("/api/games/999999/end")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `start and end with non-numeric id return 400`() {
        mockMvc.post("/api/games/abc/start")
            .andExpect { status { isBadRequest() } }
        mockMvc.post("/api/games/abc/end")
            .andExpect { status { isBadRequest() } }
    }
}
