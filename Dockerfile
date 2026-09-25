# Railway deploy: docker build from repo subdirectory Volleyball-Tournament-Backend.
# Build stage: compile the Spring Boot fat jar with the Gradle wrapper.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy wrapper + build scripts first for better layer caching
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew

# Download dependencies (cached unless build scripts change)
RUN ./gradlew dependencies --no-daemon || true

# Copy sources and build the fat jar (skip tests for faster deploys)
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# Run stage: minimal JRE image
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# Public API (8080) + Admin API (8081); Railway injects $PORT/$ADMIN_PORT at runtime.
EXPOSE 8080 8081
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
