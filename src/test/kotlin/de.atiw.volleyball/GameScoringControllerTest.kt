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
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

@AutoConfigureMockMvc
@Transactional
class GameScoringControllerTest : AbstractIntegrationTest() {

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

    private fun game(scoreA: Int? = null, scoreB: Int? = null): Game {
        val g = groupRepository.save(TournamentGroup(designation = "A"))
        val t1 = teamRepository.save(Team(group = g, teamClass = "C", name = "T1"))
        val t2 = teamRepository.save(Team(group = g, teamClass = "C", name = "T2"))
        val t3 = teamRepository.save(Team(group = g, teamClass = "C", name = "T3"))
        val round = roundRepository.save(Round(roundNumber = 1))
        val field = fieldRepository.save(Field(name = "Court 1"))
        return gameRepository.save(
            Game(round = round, field = field, teamA = t1, teamB = t2, refereeTeam = t3, pointsA = scoreA, pointsB = scoreB)
        )
    }

    @Test
    fun `start unstarted game sets 0-0`() {
        val game = game(null, null)

        mockMvc.post("/api/games/${game.gameId}/start")
            .andExpect {
                status { isOk() }
                jsonPath("$.data.scoreA") { value(0) }
                jsonPath("$.data.scoreB") { value(0) }
            }
    }

    @Test
    fun `start already started game keeps scores`() {
        val game = game(12, 8)

        mockMvc.post("/api/games/${game.gameId}/start")
            .andExpect {
                status { isOk() }
                jsonPath("$.data.scoreA") { value(12) }
                jsonPath("$.data.scoreB") { value(8) }
            }
    }

    @Test
    fun `start nonexistent game returns 404`() {
        mockMvc.post("/api/games/999999/start")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `start with non-numeric id returns 400`() {
        mockMvc.post("/api/games/abc/start")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `increment team A increases only scoreA`() {
        val game = game(5, 3)

        mockMvc.post("/api/games/${game.gameId}/score/team-a/increment")
            .andExpect {
                status { isOk() }
                jsonPath("$.data.scoreA") { value(6) }
                jsonPath("$.data.scoreB") { value(3) }
            }
    }

    @Test
    fun `increment team A on unstarted game returns 409 and keeps NULLs`() {
        val game = game(null, null)

        mockMvc.post("/api/games/${game.gameId}/score/team-a/increment")
            .andExpect { status { isConflict() } }

        val reloaded = gameRepository.findById(game.gameId).orElseThrow()
        assert(reloaded.pointsA == null && reloaded.pointsB == null)
    }

    @Test
    fun `increment team A on missing game returns 404`() {
        mockMvc.post("/api/games/999999/score/team-a/increment")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `decrement team A decreases only scoreA`() {
        val game = game(5, 3)

        mockMvc.post("/api/games/${game.gameId}/score/team-a/decrement")
            .andExpect {
                status { isOk() }
                jsonPath("$.data.scoreA") { value(4) }
                jsonPath("$.data.scoreB") { value(3) }
            }
    }

    @Test
    fun `decrement team A from zero returns 409 and keeps zero`() {
        val game = game(0, 3)

        mockMvc.post("/api/games/${game.gameId}/score/team-a/decrement")
            .andExpect { status { isConflict() } }

        assert(gameRepository.findById(game.gameId).orElseThrow().pointsA == 0)
    }

    @Test
    fun `decrement team A on unstarted game returns 409`() {
        val game = game(null, null)

        mockMvc.post("/api/games/${game.gameId}/score/team-a/decrement")
            .andExpect { status { isConflict() } }
    }

    @Test
    fun `increment team B increases only scoreB`() {
        val game = game(5, 3)

        mockMvc.post("/api/games/${game.gameId}/score/team-b/increment")
            .andExpect {
                status { isOk() }
                jsonPath("$.data.scoreA") { value(5) }
                jsonPath("$.data.scoreB") { value(4) }
            }
    }

    @Test
    fun `increment team B on unstarted game returns 409`() {
        val game = game(null, null)

        mockMvc.post("/api/games/${game.gameId}/score/team-b/increment")
            .andExpect { status { isConflict() } }
    }

    @Test
    fun `decrement team B decreases only scoreB`() {
        val game = game(5, 3)

        mockMvc.post("/api/games/${game.gameId}/score/team-b/decrement")
            .andExpect {
                status { isOk() }
                jsonPath("$.data.scoreA") { value(5) }
                jsonPath("$.data.scoreB") { value(2) }
            }
    }

    @Test
    fun `decrement team B from zero returns 409 and keeps zero`() {
        val game = game(5, 0)

        mockMvc.post("/api/games/${game.gameId}/score/team-b/decrement")
            .andExpect { status { isConflict() } }

        assert(gameRepository.findById(game.gameId).orElseThrow().pointsB == 0)
    }

    @Test
    fun `decrement team B on unstarted game returns 409`() {
        val game = game(null, null)

        mockMvc.post("/api/games/${game.gameId}/score/team-b/decrement")
            .andExpect { status { isConflict() } }
    }

    @Test
    fun `decrement team B on missing game returns 404`() {
        mockMvc.post("/api/games/999999/score/team-b/decrement")
            .andExpect { status { isNotFound() } }
    }
}
