package de.atiw.volleyball

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Concurrent generation for the same target round must not create duplicate
 * games: the round row is locked (`SELECT ... FOR UPDATE`), so parallel
 * requests serialize and all but one only report skipped pairings.
 */
class GenerationConcurrencyIT : RealPortIT() {

    @Test
    fun `parallel round-robin generations create no duplicates`() {
        val tag = nextTag("GC")
        val groupId = createGroup("G$tag").path("groupId").asText()
        val teamIds = (1..4).map { createTeam(groupId, "C", "Team $it$tag").path("teamId").asText().toInt() }
        val roundId = createRound(Random.nextInt(20000000, 29999999)).path("roundId").asText()
        createField("Court $tag")

        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val tasks = List(threads) {
                Callable {
                    val res = post(adminPort, "/api/admin/rounds/$roundId/generate-games/round-robin")
                    if (res.statusCode() != 201) {
                        throw AssertionError("generation failed: ${res.statusCode()} ${res.body()}")
                    }
                }
            }
            val futures = pool.invokeAll(tasks, 120, TimeUnit.SECONDS)
            for (future in futures) {
                future.get(10, TimeUnit.SECONDS)
            }
        } finally {
            pool.shutdownNow()
        }

        // Round-scoped assertion (other tests' groups share this database, so
        // only this round's games are counted): without serialization every
        // parallel call would recreate the same 6 pairings.
        val games = dataOf(
            assertStatus(get(publicPort, "/api/games/filter/$roundId"), 200, "GET generated games"),
            "GET generated games"
        )
        assertEquals(6, games.size(), "duplicate games were created: $games")
        val pairs = games.map {
            setOf(it.path("teamAId").asText().toInt(), it.path("teamBId").asText().toInt())
        }.toSet()
        val expected = mutableSetOf<Set<Int>>()
        for (i in teamIds.indices) for (j in i + 1 until teamIds.size) expected += setOf(teamIds[i], teamIds[j])
        assertEquals(expected, pairs, "exactly the 6 unique pairings must exist")
    }
}
