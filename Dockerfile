# Build source for local Compose and for DigitalOcean App Platform (ADR 0009): App Platform's
# buildpacks do not cover Java, so this Dockerfile must stay buildable from a bare repo clone.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# The Maven wrapper pins the build tool, so no Maven install is needed in the image.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/

# Tests need Docker (Testcontainers) and run in CI instead; the image build only packages.
RUN chmod +x mvnw && ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# curl backs the Compose and App Platform /actuator/health probes.
RUN apt-get update \
    && apt-get install --no-install-recommends --assume-yes curl \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home --shell /usr/sbin/nologin ratelimiter

# Only the fat JAR ships; `*.jar.original` from the Boot repackage is left behind.
COPY --from=build /build/target/rate-limiter-*.jar app.jar

USER ratelimiter
EXPOSE 8080

# Connection details (SPRING_DATASOURCE_*, SPRING_DATA_REDIS_*, APP_API_KEY) come from the
# environment at run time; nothing datastore-specific is baked into the image.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
