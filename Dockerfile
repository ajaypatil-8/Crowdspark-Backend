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

RUN groupadd --system spring && useradd --system --gid spring spring
COPY --from=build /build/app.jar ./app.jar
RUN chown spring:spring app.jar
USER spring

EXPOSE 8080

# start-period is generous: Flyway migrations + Firebase/Cloudinary client
# init + DB/Redis connection pool warm-up can take a while on first boot.
HEALTHCHECK --interval=30s --timeout=5s --start-period=75s --retries=3 \
    CMD curl -f http://localhost:8080/crowdspark/api/v1/categories || exit 1

ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]
