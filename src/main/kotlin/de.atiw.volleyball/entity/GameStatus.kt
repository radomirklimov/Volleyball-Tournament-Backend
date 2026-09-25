package de.atiw.volleyball.entity

/**
 * Lifecycle of a tournament game:
 *
 * ```text
 * SCHEDULED --POST /start--> RUNNING --POST /end--> FINISHED
 * ```
 *
 * There are no reverse transitions. Status changes happen exclusively through
 * the dedicated lifecycle endpoints; the generic game CRUD API never modifies
 * it.
 */
enum class GameStatus {
    SCHEDULED,
    RUNNING,
    FINISHED
}
