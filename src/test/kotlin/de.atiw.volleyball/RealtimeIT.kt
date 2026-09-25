package de.atiw.volleyball

import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * Realtime contract: writes go to port 8081, events arrive on the public
 * WebSocket (`ws://…/ws/live`). Every successful admin write produces exactly
 * one event; failed writes produce none.
 */
class RealtimeIT : RealPortIT() {

    private fun awaitEvent(collector: Collector, what: String): JsonNode {
        val event = nextEvent(collector, 10)
        assertNotNull(event, "$what -> expected a realtime event")
        return event!!
    }

    private fun assertLiveEvent(event: JsonNode, entity: String, operation: String, entityId: Int) {
        assertEquals("TOURNAMENT_DATA_CHANGED", event.path("type").asText(), "event type: $event")
        assertEquals(entity, event.path("entity").asText(), "event entity: $event")
        assertEquals(operation, event.path("operation").asText(), "event operation: $event")
        assertEquals(entityId, event.path("entityId").asInt(), "event entityId: $event")
    }

    private fun assertNoEvent(collector: Collector, what: String) {
        assertNull(collector.messages.poll(2, TimeUnit.SECONDS), "$what -> failed write must not emit an event")
    }

    @Test
    fun `start emits one game update event`() {
        val f = newFixture("ES")
        val collector = Collector()
        val session = connectWs(publicPort, collector)
        try {
            assertStatus(post(adminPort, "/api/games/${f.unstartedGame}/start"), 200, "POST start")
            assertLiveEvent(awaitEvent(collector, "start"), "GAME", "UPDATE", f.unstartedGame)
            assertNoEvent(collector, "start")
        } finally {
            session.close()
        }
    }

    @Test
    fun `all four score operations emit game update events`() {
        val f = newFixture("EO")
        val collector = Collector()
        val session = connectWs(publicPort, collector)
        try {
            assertStatus(post(adminPort, "/api/games/${f.unstartedGame}/start"), 200, "POST start")
            assertLiveEvent(awaitEvent(collector, "start"), "GAME", "UPDATE", f.unstartedGame)

            assertStatus(post(adminPort, "/api/games/${f.unstartedGame}/score/team-a/increment"), 200, "inc A")
            assertLiveEvent(awaitEvent(collector, "inc A"), "GAME", "UPDATE", f.unstartedGame)

            assertStatus(post(adminPort, "/api/games/${f.unstartedGame}/score/team-a/decrement"), 200, "dec A")
            assertLiveEvent(awaitEvent(collector, "dec A"), "GAME", "UPDATE", f.unstartedGame)

            assertStatus(post(adminPort, "/api/games/${f.unstartedGame}/score/team-b/increment"), 200, "inc B")
            assertLiveEvent(awaitEvent(collector, "inc B"), "GAME", "UPDATE", f.unstartedGame)

            assertStatus(post(adminPort, "/api/games/${f.unstartedGame}/score/team-b/decrement"), 200, "dec B")
            assertLiveEvent(awaitEvent(collector, "dec B"), "GAME", "UPDATE", f.unstartedGame)

            assertNoEvent(collector, "score operations")
        } finally {
            session.close()
        }
    }

    @Test
    fun `game crud emits create update delete events`() {
        val f = newFixture("EC")
        val collector = Collector()
        val session = connectWs(publicPort, collector)
        try {
            val created = createGame(f.roundId, f.fieldId, f.teamA, f.teamB, f.referee, null, null)
            val gameId = created.path("gameId").asText().toInt()
            assertLiveEvent(awaitEvent(collector, "create game"), "GAME", "CREATE", gameId)

            assertStatus(
                put(
                    adminPort, "/api/admin/games/$gameId",
                    """{"roundId":${f.roundId},"fieldId":${f.fieldId},"teamAId":${f.teamA},"teamBId":${f.teamB},"refereeTeamId":${f.referee},"scoreA":1,"scoreB":2}"""
                ),
                200, "PUT game"
            )
            assertLiveEvent(awaitEvent(collector, "update game"), "GAME", "UPDATE", gameId)

            assertStatus(delete(adminPort, "/api/admin/games/$gameId"), 204, "DELETE game")
            assertLiveEvent(awaitEvent(collector, "delete game"), "GAME", "DELETE", gameId)

            assertNoEvent(collector, "game crud")
        } finally {
            session.close()
        }
    }

    @Test
    fun `group create emits group event`() {
        val collector = Collector()
        val session = connectWs(publicPort, collector)
        try {
            val tag = nextTag("EG")
            val created = createGroup("G$tag")
            assertLiveEvent(
                awaitEvent(collector, "create group"),
                "GROUP", "CREATE", created.path("groupId").asText().toInt()
            )
        } finally {
            session.close()
        }
    }

    @Test
    fun `failed writes emit no event`() {
        val f = newFixture("EN")
        val collector = Collector()
        val session = connectWs(publicPort, collector)
        try {
            // Validation failure.
            assertError(
                post(adminPort, "/api/admin/groups", """{"name":""}"""),
                400, "BAD_REQUEST", "POST invalid group"
            )
            // Business-rule conflict.
            assertError(
                post(adminPort, "/api/games/${f.unstartedGame}/score/team-a/increment"),
                409, "CONFLICT", "POST inc on unstarted game"
            )
            // Floor conflict.
            assertStatus(post(adminPort, "/api/games/${f.unstartedGame}/start"), 200, "POST start")
            awaitEvent(collector, "start")
            assertError(
                post(adminPort, "/api/games/${f.unstartedGame}/score/team-b/decrement"),
                409, "CONFLICT", "POST dec B at zero"
            )
            // Missing entity.
            assertError(delete(adminPort, "/api/admin/games/999999999"), 404, "RESOURCE_NOT_FOUND", "DELETE missing game")

            assertNoEvent(collector, "failed writes")
        } finally {
            session.close()
        }
    }

    @Test
    fun `two clients receive the same event`() {
        val f = newFixture("EM")
        val first = Collector()
        val second = Collector()
        val sessionA = connectWs(publicPort, first)
        val sessionB = connectWs(publicPort, second)
        try {
            assertStatus(post(adminPort, "/api/games/${f.unstartedGame}/start"), 200, "POST start")
            assertLiveEvent(awaitEvent(first, "client A"), "GAME", "UPDATE", f.unstartedGame)
            assertLiveEvent(awaitEvent(second, "client B"), "GAME", "UPDATE", f.unstartedGame)
        } finally {
            sessionA.close()
            sessionB.close()
        }
    }

    @Test
    fun `disconnecting one client does not affect the other`() {
        val f = newFixture("ED")
        val leaving = Collector()
        val staying = Collector()
        val sessionA = connectWs(publicPort, leaving)
        val sessionB = connectWs(publicPort, staying)
        sessionA.close()
        try {
            assertStatus(post(adminPort, "/api/games/${f.unstartedGame}/start"), 200, "POST start")
            assertLiveEvent(awaitEvent(staying, "remaining client"), "GAME", "UPDATE", f.unstartedGame)
        } finally {
            sessionB.close()
        }
        assertTrue(true, "application stayed healthy")
    }
}
