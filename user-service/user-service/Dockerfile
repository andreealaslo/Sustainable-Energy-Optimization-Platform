# Stage 1: Build the Spring Boot application
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# Copy the pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline
# Copy the rest of the source code
COPY src ./src
# Package the application into a JAR file
RUN mvn clean package -DskipTests

# Stage 2: Create the final, smaller runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Copy the JAR file from the build stage
COPY --from=build /app/target/*.jar user-service.jar
# Expose the server port defined in application.properties
EXPOSE 8081
# Command to run the application
ENTRYPOINT ["java", "-jar", "user-service.jar"]