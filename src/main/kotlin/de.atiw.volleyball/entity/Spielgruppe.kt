package de.atiw.volleyball.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "spielgruppe")
class Spielgruppe(

    @Id
    @Column(name = "gruppenbez", length = 1, nullable = false)
    val gruppenbez: String
)
