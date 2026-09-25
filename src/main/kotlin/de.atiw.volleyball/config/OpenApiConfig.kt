package de.atiw.volleyball.config

import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Documents the two API surfaces separately without weakening port
 * separation (Swagger config alone never grants access; the
 * [PortIsolationFilter] enforces it):
 *
 * - `public` group -> port 8080 (read-only API + realtime)
 * - `admin` group -> port 8081 (CRUD + game scoring control)
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun publicApi(): GroupedOpenApi = GroupedOpenApi.builder()
        .group("public")
        .pathsToMatch(
            "/",
            "/api/groups",
            "/api/groups/*",
            "/api/teams",
            "/api/teams/*",
            "/api/rounds",
            "/api/rounds/*",
            "/api/fields",
            "/api/fields/*",
            "/api/games",
            "/api/games/*",
            "/api/games/filter/*"
        )
        .build()

    @Bean
    fun adminApi(): GroupedOpenApi = GroupedOpenApi.builder()
        .group("admin")
        .pathsToMatch(
            "/api/admin/**",
            "/api/games/*/start",
            "/api/games/*/score/**"
        )
        .build()
}
