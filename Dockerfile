# Crowdspark-Backend/Dockerfile
# Feature #29 — Docker + docker-compose
#
# Multi-stage build: stage 1 compiles with the project's own Maven wrapper so
# the build uses the exact Maven version the repo already pins; stage 2 ships
# only a JRE + the built jar (no JDK, no source tree, no Maven cache).
#
# IMPORTANT: pom.xml's maven-compiler-plugin passes --enable-preview, which
# marks the compiled classes as Java 21 preview bytecode. The JVM refuses to
# load preview-flagged classes at all unless launched with --enable-preview
# too, so that flag is repeated in ENTRYPOINT below — removing it will make
# every container exit immediately with an UnsupportedClassVersionError.

# ---------- Stage 1: build ----------
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build

# Copy wrapper + POM first so dependency resolution is cached in its own
# Docker layer — editing a .java file won't force a re-download of the world.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src ./src
RUN ./mvnw -B -q -DskipTests clean package \
    && mv target/*.jar app.jar

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# curl is only for HEALTHCHECK below. There's no Actuator dependency yet
# (that's Feature #31), so this probes a real, always-public GET route
# instead of a dedicated health endpoint.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*


# DEPLOYMENT FIX (Render): Render's "Secret Files" feature (used below for
# firebase-service-account.json -- see FirebaseConfig / the Render deployment
# notes) mounts files at /etc/secrets/<name> owned by GID 1000. Render's own
# docs warn that a container running as a different, unrelated group will get
# a permission-denied reading them. `groupadd --system spring` alone gives
# this user its own auto-assigned system GID (not 1000), so it would hit
# exactly that. Ensuring GID 1000 exists and adding the app user to it as a
# supplementary group (on top of its own "spring" primary group) fixes this
# for Render while changing nothing for local/Compose use, where no secret
# file is mounted and this extra group membership is simply unused.
RUN groupadd --system spring \
    && (getent group 1000 || groupadd -g 1000 rendersecrets) \
    && useradd --system --gid spring --groups 1000 spring
COPY --from=build /build/app.jar ./app.jar
RUN chown spring:spring app.jar
USER spring

EXPOSE 8080 8081

# Feature #31: probes the real Actuator health endpoint (added this feature)
# instead of Feature #29's original workaround of hitting a public business
# endpoint as a liveness proxy. Actuator listens on 8081 (management.server.
# port), a different port from the public API on 8080, and does NOT carry
# the /crowdspark context-path prefix that 8080 does -- see the comment on
# management.server.port in application.properties for why.
HEALTHCHECK --interval=30s --timeout=5s --start-period=75s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]
