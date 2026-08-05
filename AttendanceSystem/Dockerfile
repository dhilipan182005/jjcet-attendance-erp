# Build Stage
FROM maven:3.9.6-eclipse-temurin-21-focal AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime Stage
FROM eclipse-temurin:21-jre-focal
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Expose default port
EXPOSE 10000

# Environment defaults
ENV SPRING_PROFILES_ACTIVE=prod
ENV PORT=10000

# Health Check
HEALTHCHECK --interval=30s --timeout=3s \
  CMD curl -f http://localhost:${PORT:-10000}/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
