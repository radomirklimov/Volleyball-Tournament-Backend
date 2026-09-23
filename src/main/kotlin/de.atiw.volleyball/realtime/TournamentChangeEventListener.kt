package de.atiw.volleyball.realtime

import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class TournamentChangeEventListener(
    private val broadcaster: WebSocketBroadcaster
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onChange(event: TournamentChangeEvent) {
        broadcaster.broadcast(event)
    }
}
