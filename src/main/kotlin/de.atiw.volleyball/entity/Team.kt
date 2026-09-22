package de.atiw.volleyball.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "teams")
class Team(

    @Id
    @Column(name = "teamname")
    val teamname: String,

    @Column(name = "klasse", nullable = false)
    val klasse: String,

    @Column(name = "isparticipating", nullable = false)
    val isParticipating: Boolean,

    @Column(name = "gruppenid")
    val gruppenId: String?,

    @Column(name = "id", nullable = false)
    val id: Int,

    @Column(name = "nummer")
    val nummer: Int?
)
