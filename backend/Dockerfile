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
RUN mvn clean package -DskipTests


# -------- Runtime Stage --------
# Use a lightweight Java 21 JRE image to run the application
FROM eclipse-temurin:21-jre

# Set the working directory inside the runtime container
WORKDIR /app

# Copy the generated JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the Spring Boot application port
EXPOSE 8080

# Start the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
