# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Stage 1 - build
#
# Uses the project's own Maven wrapper rather than a Maven base image, so the
# container builds with exactly the Maven version the repository pins.
#
# The module poms are copied before the sources: when only source files change,
# the dependency resolution layer stays cached. The BuildKit cache mount keeps
# the local Maven repository across builds, which is what makes a rebuild fast.
#
# Tests are skipped here on purpose. They need Testcontainers, which needs a
# Docker daemon - not something an image build should depend on. Tests run in
# the build pipeline via `./mvnw verify`, not while packaging the image.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build

WORKDIR /build

COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
COPY shared/pom.xml shared/
COPY ingestion/pom.xml ingestion/
COPY analysis/pom.xml analysis/
COPY realtime/pom.xml realtime/
COPY app/pom.xml app/

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp dependency:go-offline

COPY shared/src shared/src
COPY ingestion/src ingestion/src
COPY analysis/src analysis/src
COPY realtime/src realtime/src
COPY app/src app/src

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp package -DskipTests

# Split the fat jar into Spring Boot's standard layers, ordered by how often
# they change. Third-party dependencies are ~25 MB and change rarely; our own
# classes are ~100 KB and change on every commit. Copying them as separate
# image layers means a code change rebuilds and ships only the small one.
RUN java -Djarmode=tools -jar app/target/incident-report.jar \
        extract --layers --launcher --destination extracted

# ---------------------------------------------------------------------------
# Stage 2 - runtime
#
# Alpine JRE: no compiler, no build tree, and roughly half the size of the
# glibc JRE image. Everything here is pure Java, so musl is not a concern.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# curl is here for the container health check; nothing else needs it.
RUN apk add --no-cache curl

# Run as an unprivileged user. A compromised application should not be root.
RUN addgroup -S -g 1001 app \
    && adduser -S -u 1001 -G app -h /app app

WORKDIR /app

# Order matters: least volatile layer first, so the cached layers stay valid.
COPY --from=build --chown=app:app /build/extracted/dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/application/ ./

USER app

EXPOSE 8080

# Container-level liveness. Compose uses this to gate `depends_on`.
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
    CMD curl --fail --silent http://localhost:8080/actuator/health || exit 1

# MaxRAMPercentage lets the JVM size its heap from the container memory limit
# instead of the host's, which is what makes memory limits actually work.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# `exec` so the JVM becomes PID 1 and receives SIGTERM directly, giving Spring
# a chance to shut down gracefully instead of being killed after the timeout.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
