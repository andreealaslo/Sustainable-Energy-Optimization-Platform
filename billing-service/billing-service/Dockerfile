# Stage 1: Build the Spring Boot application
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# Copy project files and build dependencies
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
# Compile and package the application
RUN mvn clean package -DskipTests

# Stage 2: Create the final, smaller runtime image (JRE)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Copy the built artifact from the build stage
COPY --from=build /app/target/*.jar billing-service.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "billing-service.jar"]