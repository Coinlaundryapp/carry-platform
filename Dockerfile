# ── Stage 1: Build ──────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Copy Gradle wrapper and build configuration first for layer caching
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./

# Copy all module build files for dependency resolution caching
# Common
COPY carry-common/build.gradle.kts carry-common/
COPY carry-event/build.gradle.kts carry-event/
# Infrastructure
COPY carry-infra-persistence/build.gradle.kts carry-infra-persistence/
COPY carry-infra-kafka/build.gradle.kts carry-infra-kafka/
COPY carry-infra-redis/build.gradle.kts carry-infra-redis/
COPY carry-infra-s3/build.gradle.kts carry-infra-s3/
COPY carry-infra-observability/build.gradle.kts carry-infra-observability/
# Security
COPY carry-security/build.gradle.kts carry-security/
# Domain Modules
COPY carry-user/build.gradle.kts carry-user/
COPY carry-laundromat/build.gradle.kts carry-laundromat/
COPY carry-price/build.gradle.kts carry-price/
COPY carry-geo/build.gradle.kts carry-geo/
COPY carry-order/build.gradle.kts carry-order/
COPY carry-payment/build.gradle.kts carry-payment/
COPY carry-dispatch/build.gradle.kts carry-dispatch/
COPY carry-delivery/build.gradle.kts carry-delivery/
COPY carry-operation/build.gradle.kts carry-operation/
COPY carry-review/build.gradle.kts carry-review/
COPY carry-notification/build.gradle.kts carry-notification/
COPY carry-media/build.gradle.kts carry-media/
COPY carry-service-availability/build.gradle.kts carry-service-availability/
# Application
COPY carry-app/build.gradle.kts carry-app/

# Download dependencies (allow failure for partial resolution)
RUN ./gradlew dependencies --no-daemon || true

# Copy full source code
COPY . .

# Build the bootJar, skipping tests
RUN ./gradlew :carry-app:bootJar --no-daemon -x test

# ── Stage 2: Runtime ────────────────────────────────────────────
FROM eclipse-temurin:21-jre

RUN groupadd --system carry && useradd --system --gid carry carry

WORKDIR /app

COPY --from=builder /app/carry-app/build/libs/*.jar app.jar

RUN chown -R carry:carry /app
USER carry

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
