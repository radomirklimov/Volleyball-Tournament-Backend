package de.atiw.volleyball

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Concurrent score updates must not lose increments: N parallel
 * `team-a/increment` calls from the same base score must all be applied
 * (the service serializes them with a pessimistic write lock).
 */
class ScoreConcurrencyIT : RealPortIT() {

    @Test
    fun `parallel increments are all applied`() {
        val f = newFixture("CC")
        assertStatus(post(adminPort, "/api/games/${f.unstartedGame}/start"), 200, "POST start")

        val threads = 20
        val incrementsPerThread = 5
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val tasks = List(threads) {
                Callable {
                    repeat(incrementsPerThread) {
                        val res = post(adminPort, "/api/games/${f.unstartedGame}/score/team-a/increment")
                        if (res.statusCode() != 200) {
                            throw AssertionError("increment failed: ${res.statusCode()} ${res.body()}")
                        }
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

        val game = dataOf(
            assertStatus(get(publicPort, "/api/games/${f.unstartedGame}"), 200, "GET game"),
            "GET game"
        )
        assertEquals(threads * incrementsPerThread, game.path("scoreA").asInt(), "lost updates detected: $game")
        assertEquals(0, game.path("scoreB").asInt(), "team B must be untouched: $game")
    }
}
