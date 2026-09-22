package de.atiw.volleyball.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "teams")
class Team(

    @Id
    @Column(name = "teamname", length = 100, nullable = false)
    val teamname: String,

    @Column(name = "klasse", length = 100, nullable = false)
    val klasse: String,

    @Column(name = "isparticipating", nullable = false)
    val isParticipating: Boolean,

    @ManyToOne
    @JoinColumn(
        name = "gruppenid",
        referencedColumnName = "gruppenbez"
    )
    val spielgruppe: Spielgruppe?,

    @Column(name = "id", nullable = false)
    val id: Int,

    @Column(name = "nummer")
    val nummer: Int?
)
