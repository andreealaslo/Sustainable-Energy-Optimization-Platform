# Stage 1: Build the Spring Boot application using Maven
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy the pom.xml and download dependencies to take advantage of Docker caching
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the source code and build the JAR
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create the final runtime image
# Using the slim JRE image to minimize footprint
FROM eclipse-temurin:17-jre-alpine
# Install curl for health checks if needed
RUN apk add --no-cache curl

WORKDIR /app

# Copy the JAR from the build stage
COPY --from=build /app/target/*.jar notification-service.jar

# Expose the port defined in your Canvas configuration (8084)
EXPOSE 8084

# Run the application
ENTRYPOINT ["java", "-jar", "notification-service.jar"]