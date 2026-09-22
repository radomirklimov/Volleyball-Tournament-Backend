package de.atiw.volleyball.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "platzierung")
@IdClass(PlatzierungId::class)
class Platzierung(

    @Id
    @Column(name = "runde", length = 50, nullable = false)
    val runde: String,

    @Id
    @ManyToOne
    @JoinColumn(
        name = "team",
        referencedColumnName = "teamname"
    )
    val team: Team?,

    @Column(name = "platzierung", nullable = false)
    val platzierung: Int
)
