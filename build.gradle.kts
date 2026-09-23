plugins {
    id("org.springframework.boot") version "3.3.0"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.spring") version "1.9.24"
    kotlin("plugin.jpa") version "1.9.24"
}

group = "de.atiw.volleyball"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

// Spring Boot's BOM pins an old docker-java client that Docker 29 rejects
// (min API 1.40). Force a newer docker-java for Testcontainers.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.github.docker-java") {
            useVersion("3.4.0")
            because("Docker 29 requires API >= 1.40")
        }
        if (requested.group == "org.testcontainers") {
            useVersion("1.20.4")
            because("Keep all Testcontainers modules aligned")
        }
    }
}

dependencies {
    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // WebSocket (live updates at /ws/live)
    implementation("org.springframework.boot:spring-boot-starter-websocket")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // JPA
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // MariaDB
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client")

    // PostgreSQL
//     runtimeOnly("org.postgresql:postgresql")

    // Swagger / OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers:1.20.4")
    testImplementation("org.testcontainers:mariadb:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
    // Testcontainers 1.20.x defaults the Docker API version to 1.32, which
    // Docker 29 rejects (min 1.40). Pin an explicit version instead.
    systemProperty("api.version", "1.44")
    environment("API_VERSION", "1.44")
    environment("DOCKER_API_VERSION", "1.44")
}
