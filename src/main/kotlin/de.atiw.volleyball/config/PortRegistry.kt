package de.atiw.volleyball.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.context.WebServerInitializedEvent
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Holds the actual local ports of the two HTTP connectors once the embedded
 * server is up. Needed because the public port may be random in tests
 * (`RANDOM_PORT`) and the admin port may be overridden per environment.
 */
@Component
class PortRegistry(
    @Value("\${app.admin-port:8081}")
    private val configuredAdminPort: Int
) {
    @Volatile
    var publicPort: Int? = null

    @Volatile
    var adminPort: Int? = null

    @EventListener(WebServerInitializedEvent::class)
    fun onWebServerInitialized(event: WebServerInitializedEvent) {
        publicPort = event.webServer.port
        val server = event.webServer
        if (server is TomcatWebServer) {
            for (connector in server.tomcat.service.findConnectors()) {
                val localPort = connector.localPort
                if (localPort > 0 && localPort != publicPort) {
                    adminPort = localPort
                    break
                }
            }
        }
        if (adminPort == null && configuredAdminPort > 0 && configuredAdminPort != publicPort) {
            adminPort = configuredAdminPort
        }
    }
}
