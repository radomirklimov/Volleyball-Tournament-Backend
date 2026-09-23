package de.atiw.volleyball.realtime

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service

@Service
class WebSocketBroadcaster(
    private val handler: LiveWebSocketHandler,
    private val objectMapper: ObjectMapper
) {
    fun broadcast(event: TournamentChangeEvent) {
        val wire = LiveEvent(
            entity = event.entity,
            operation = event.operation,
            entityId = event.entityId
        )
        handler.broadcast(objectMapper.writeValueAsString(wire))
    }
}
