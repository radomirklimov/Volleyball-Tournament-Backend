package de.atiw.volleyball

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class VolleyballBackendApplication

fun main(args: Array<String>) {
    runApplication<VolleyballBackendApplication>(*args)
}
