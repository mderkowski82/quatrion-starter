# =============================================================================
# Quatrion Starter — Backend Dockerfile
#
# 2-stage build:
#   1. build   — compiles Quarkus app (framework pulled from GitHub Packages / mavenCentral)
#   2. runtime — minimal JRE image with uber-jar
#
# Requirements:
#   - Quatrion Portal framework published to GitHub Packages or mavenCentral
#
# Usage:
#   docker compose up
#   docker build -t my-portal-backend .
# =============================================================================

# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM gradle:8.14-jdk21 AS build
WORKDIR /app

# Cache Gradle dependencies first (separate layer)
COPY settings.gradle.kts build.gradle.kts ./
COPY gradle/ ./gradle/
RUN gradle dependencies --no-daemon 2>/dev/null || true

# Build uber-jar
COPY src/ ./src/
RUN gradle build -Dquarkus.package.jar.type=uber-jar -x test --no-daemon

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL org.opencontainers.image.title="My Portal App Backend"
LABEL org.opencontainers.image.description="Quarkus backend powered by Quatrion Portal framework"
LABEL org.opencontainers.image.source="https://github.com/your-org/your-repo"

RUN addgroup -S portal && adduser -S portal -G portal
WORKDIR /app

COPY --from=build /app/build/*-runner.jar /app/app.jar

USER portal
EXPOSE 8080

ENV JAVA_OPTS="-Xmx512m -Djava.net.preferIPv4Stack=true"

HEALTHCHECK --interval=10s --timeout=3s --retries=5 --start-period=30s \
    CMD wget -qO- http://localhost:8080/q/health/ready || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

