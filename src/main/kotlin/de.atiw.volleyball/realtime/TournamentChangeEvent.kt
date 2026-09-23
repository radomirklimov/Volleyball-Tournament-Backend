package de.atiw.volleyball.realtime

enum class EntityType {
    GROUP,
    TEAM,
    ROUND,
    FIELD,
    GAME
}

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE
}

/** Published inside the write transaction, broadcast only after commit. */
data class TournamentChangeEvent(
    val entity: EntityType,
    val operation: OperationType,
    val entityId: Int
)

/** Wire format sent to WS clients at /ws/live. */
data class LiveEvent(
    val type: String = "TOURNAMENT_DATA_CHANGED",
    val entity: EntityType,
    val operation: OperationType,
    val entityId: Int
)
