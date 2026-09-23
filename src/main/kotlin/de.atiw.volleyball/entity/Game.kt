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
@Table(name = "games")
class Game(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "game_id")
    val gameId: Int = 0,

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(
        name = "round_id",
        referencedColumnName = "round_id",
        nullable = false
    )
    val round: Round,

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(
        name = "field_id",
        referencedColumnName = "field_id",
        nullable = false
    )
    val field: Field,

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(
        name = "team_a_id",
        referencedColumnName = "team_id",
        nullable = false
    )
    val teamA: Team,

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(
        name = "team_b_id",
        referencedColumnName = "team_id",
        nullable = false
    )
    val teamB: Team,

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(
        name = "referee_team_id",
        referencedColumnName = "team_id",
        nullable = false
    )
    val refereeTeam: Team,

    @Column(name = "points_a", nullable = false)
    val pointsA: Int = 0,

    @Column(name = "points_b", nullable = false)
    val pointsB: Int = 0
)
