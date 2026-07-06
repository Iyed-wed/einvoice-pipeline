# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the application ----
# Tests are intentionally skipped here: they rely on Testcontainers (a real
# PostgreSQL via the Docker daemon), which is not available inside the build
# container. Tests run locally and in CI (GitHub Actions) instead.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependencies first so this layer is cached unless the POM changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Runtime stage: minimal JRE, non-root, with a healthcheck ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# curl is used by the compose healthcheck to probe the Actuator endpoint.
RUN apt-get update \
    && apt-get install --no-install-recommends -y curl \
    && rm -rf /var/lib/apt/lists/*

# Run as an unprivileged user, never as root.
RUN groupadd --system app && useradd --system --gid app app
USER app

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080

# Container-friendly JVM defaults; overridable at runtime via JAVA_OPTS.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
