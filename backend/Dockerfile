# Build stage
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /home/app

# Copy the build files
COPY . .

# Build the application
RUN ./gradlew clean build -x test --no-daemon

# Run stage
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Copy the jar from the build stage
COPY --from=build /home/app/build/libs/risk-guard-0.0.1-SNAPSHOT.jar app.jar

# Expose the port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
