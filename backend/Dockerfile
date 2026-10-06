# -------- Build Stage --------
# Use Maven with Java 21 to compile and package the Spring Boot application
FROM maven:3.9.16-eclipse-temurin-21 AS build

# Set the working directory inside the build container
WORKDIR /app

# Copy Maven configuration file
COPY pom.xml .

# Copy the backend source code
COPY src ./src

# Build the Spring Boot JAR without running tests
RUN mvn -B clean package -DskipTests


# -------- Runtime Stage --------
# Use a lightweight Alpine-based Java 21 JRE image to run the application
FROM eclipse-temurin:21-jre-alpine

# Update Alpine packages to their latest security-fixed versions and create an
# unprivileged user to run the application
RUN apk upgrade --no-cache \
    && addgroup -S -g 10001 app \
    && adduser -S -D -H -u 10001 -G app app

# Set the working directory inside the runtime container. The app user owns it
# because the default dev profile keeps its H2 database file under ./data.
WORKDIR /app
RUN mkdir -p /app/data && chown -R app:app /app

# Copy the generated JAR from the build stage
COPY --from=build --chown=app:app /app/target/*.jar app.jar

# Run as the unprivileged user (numeric, so Kubernetes runAsNonRoot can verify it)
USER 10001:10001

# Expose the Spring Boot application port
EXPOSE 8080

# Start the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
