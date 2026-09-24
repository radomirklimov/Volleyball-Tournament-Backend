package de.atiw.volleyball

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.MariaDBContainer

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractIntegrationTest {

    companion object {
        // Singleton for the whole test JVM: started once at class-load, never
        // restarted between test classes. A per-class managed container changes
        // its mapped port on restart, which breaks subsequently built
        // ApplicationContexts (connection refused).
        @JvmStatic
        @ServiceConnection
        val mariadb: MariaDBContainer<*> = MariaDBContainer("mariadb:11.4").apply { start() }
    }
}
