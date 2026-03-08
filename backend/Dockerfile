# Multi-stage build — optimized for CI caching (AC: 7, Task 7)
# Stage 1: Cache Gradle dependencies (layer cached unless build.gradle changes)
FROM eclipse-temurin:25-jdk-alpine AS deps
WORKDIR /home/app

# Copy only dependency declarations first to maximize layer cache reuse.
# Note: settings.gradle, build.gradle, gradlew, and gradle/ are all in backend/ (the Docker context).
COPY build.gradle settings.gradle gradlew ./
COPY gradle/ gradle/

# Pre-fetch dependencies (cached as a separate layer).
# 'dependencies' task resolves all declared deps without compiling source.
# Do NOT use '|| true' — real network/config failures should fail the build, not be silently ignored.
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon --parallel --quiet

# Stage 2: Build application
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /home/app

# Restore cached Gradle home from deps stage
COPY --from=deps /root/.gradle /root/.gradle

# Copy full source
COPY . .

# Build — skip tests (tests run in CI before Docker build)
RUN ./gradlew clean build -x test --no-daemon --parallel

# Stage 3: Minimal runtime image
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

# Create non-root user for security
RUN addgroup -S riskguard && adduser -S riskguard -G riskguard

# Copy the built jar
COPY --from=build /home/app/build/libs/risk-guard-0.0.1-SNAPSHOT.jar app.jar

# Own the app file
RUN chown riskguard:riskguard app.jar

USER riskguard

EXPOSE 8080

# Graceful shutdown support — use exec form to receive SIGTERM
ENTRYPOINT ["java", "-jar", "app.jar"]
