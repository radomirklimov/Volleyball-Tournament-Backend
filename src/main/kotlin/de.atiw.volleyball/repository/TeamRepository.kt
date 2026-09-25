package de.atiw.volleyball.repository

import de.atiw.volleyball.entity.Team
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TeamRepository : JpaRepository<Team, Int> {
    fun existsByGroup_GroupId(groupId: Int): Boolean

    /**
     * Leaderboard aggregation for one group, Round 1 only (rounds.round_number = 1).
     *
     * - teams.group_id = :groupId selects the teams
     * - scores are always non-null integers, so each game simply contributes
     *   its numeric value (0 contributes 0)
     * - games are joined only when the team participates as team_a/team_b AND
     *   the game belongs to the round with round_number = 1 (via subselect, so
     *   the LEFT JOIN never drops teams without Round 1 games)
     * - referee_team_id is never used, so refereeing contributes no points
     * - the outer COALESCE only covers SUM over zero rows (teams without
     *   games score 0)
     *
     * Returns rows of [team_id (Number), name (String), points (Number)]
     * ordered by points DESC, team_id ASC.
     */
    @Query(
        value = """
            SELECT t.team_id, t.name,
                COALESCE(SUM(
                    CASE
                        WHEN g.team_a_id = t.team_id THEN g.points_a
                        WHEN g.team_b_id = t.team_id THEN g.points_b
                        ELSE 0
                    END
                ), 0) AS points
            FROM teams t
            LEFT JOIN games g
                ON (g.team_a_id = t.team_id OR g.team_b_id = t.team_id)
                AND g.round_id IN (SELECT r.round_id FROM rounds r WHERE r.round_number = 1)
            WHERE t.group_id = :groupId
            GROUP BY t.team_id, t.name
            ORDER BY points DESC, t.team_id ASC
        """,
        nativeQuery = true
    )
    fun findLeaderboardByGroupId(@Param("groupId") groupId: Int): List<Array<Any>>
}
