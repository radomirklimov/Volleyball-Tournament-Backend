package de.atiw.volleyball.realtime

import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

@Component
class LiveWebSocketHandler : TextWebSocketHandler() {

    private val sessions = ConcurrentHashMap.newKeySet<WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions.add(session)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessions.remove(session)
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        sessions.remove(session)
        try {
            if (session.isOpen) session.close(CloseStatus.SERVER_ERROR)
        } catch (_: Exception) {
        }
    }

    /** Public clients are listeners only; inbound messages are ignored. */
    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
    }

    fun broadcast(payload: String) {
        val message = TextMessage(payload)
        for (session in sessions.toList()) {
            if (!session.isOpen) {
                sessions.remove(session)
                continue
            }
            try {
                session.sendMessage(message)
            } catch (_: Exception) {
                sessions.remove(session)
            }
        }
    }
}
