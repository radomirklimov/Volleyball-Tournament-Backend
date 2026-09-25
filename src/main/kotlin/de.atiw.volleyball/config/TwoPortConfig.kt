package de.atiw.volleyball.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Exposes the admin API on a second HTTP connector while keeping exactly one
 * Spring Boot application, one module and one database.
 *
 * - primary connector (`server.port`, default 8080) -> public/student API + WS
 * - additional connector (`app.admin-port`, default 8081) -> admin API
 *
 * Route isolation by incoming port is enforced by [PortIsolationFilter].
 */
@Configuration
class TwoPortConfig(
    @Value("\${app.admin-port:8081}")
    private val adminPort: Int
) {
    @Bean
    fun adminConnectorCustomizer(): WebServerFactoryCustomizer<TomcatServletWebServerFactory> =
        WebServerFactoryCustomizer { factory ->
            if (adminPort > 0) {
                val connector = org.apache.catalina.connector.Connector(
                    TomcatServletWebServerFactory.DEFAULT_PROTOCOL
                )
                connector.port = adminPort
                factory.addAdditionalTomcatConnectors(connector)
            }
        }
}
