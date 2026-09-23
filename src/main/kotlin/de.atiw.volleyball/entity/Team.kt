package de.atiw.volleyball.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "teams")
class Team(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "team_id")
    val teamId: Int = 0,

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(
        name = "group_id",
        referencedColumnName = "group_id",
        nullable = false
    )
    val group: TournamentGroup,

    @Column(name = "team_class", length = 100, nullable = false)
    val teamClass: String,

    @Column(name = "name", length = 100, nullable = false)
    val name: String
)
