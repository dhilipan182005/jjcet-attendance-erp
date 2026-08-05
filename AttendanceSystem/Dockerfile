# Build Stage
FROM maven:3.9.6-eclipse-temurin-17-focal AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime Stage
FROM eclipse-temurin:17-jre-focal
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Expose the application port
EXPOSE 8080

# Environment variables with default values (can be overridden)
ENV SPRING_PROFILES_ACTIVE=prod
ENV DATABASE_URL=jdbc:postgresql://localhost:5432/postgres
ENV DATABASE_USERNAME=postgres
ENV DATABASE_PASSWORD=
ENV JWT_SECRET=

# Health Check
HEALTHCHECK --interval=30s --timeout=3s \
  CMD curl -f http://localhost:${PORT:-10000}/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
