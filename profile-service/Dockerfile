# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /workspace/app

COPY pom.xml .
RUN mvn dependency:go-offline || true

COPY src src
RUN mvn clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /workspace/app/target/*.jar app.jar

EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -q -O - http://localhost:8082/actuator/health | grep UP || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
