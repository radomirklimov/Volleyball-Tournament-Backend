package de.atiw.volleyball.entity

import java.io.Serializable

data class PlatzierungId(
    val runde: String,
    val team: String?
) : Serializable
