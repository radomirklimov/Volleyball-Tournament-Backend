package de.atiw.volleyball.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "spiel")
class Spiel(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "spielid")
    val spielid: Int,

    @ManyToOne
    @JoinColumn(
        name = "teama",
        referencedColumnName = "teamname",
        nullable = false
    )
    val teamA: Team,

    @ManyToOne
    @JoinColumn(
        name = "teamb",
        referencedColumnName = "teamname",
        nullable = false
    )
    val teamB: Team,

    @Column(name = "schiedsrichter", length = 100, nullable = false)
    val schiedsrichter: String,

    @Column(name = "punktea")
    val punkteA: Int?,

    @Column(name = "punkteb")
    val punkteB: Int?,

    @Column(name = "runde", length = 5, nullable = false)
    val runde: String,

    @Column(name = "feld")
    val feld: Int?
)
