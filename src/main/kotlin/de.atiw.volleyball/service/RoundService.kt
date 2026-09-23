package de.atiw.volleyball.service

import de.atiw.volleyball.entity.Round
import de.atiw.volleyball.repository.RoundRepository
import org.springframework.stereotype.Service

@Service
class RoundService(
    private val roundRepository: RoundRepository
) {
    fun getAll(): List<Round> = roundRepository.findAll()
}
