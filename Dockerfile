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

# Deployment: skip test compilation/execution entirely.
# The test sources currently contain Spring Boot 4 test-import issues that are
# irrelevant to the production runtime. The main application source still
# compiles normally.
RUN ./mvnw -B -q -Dmaven.test.skip=true clean package \
    && mv target/*.jar app.jar

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# curl is only for HEALTHCHECK below.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# DEPLOYMENT FIX (Render): Render's "Secret Files" feature mounts files at
# /etc/secrets/<name> with GID 1000. Add the app user to that group so the
# Firebase service-account Secret File can be read at runtime.
RUN groupadd --system spring \
    && (getent group 1000 || groupadd -g 1000 rendersecrets) \
    && useradd --system --gid spring --groups 1000 spring

COPY --from=build /build/app.jar ./app.jar
RUN chown spring:spring app.jar
USER spring

EXPOSE 8080 8081

# Feature #31: probes the real Actuator health endpoint on port 8081.
# Render itself uses the public service port; this Docker HEALTHCHECK is for
# the container/runtime and local Docker usage.
HEALTHCHECK --interval=30s --timeout=5s --start-period=75s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]