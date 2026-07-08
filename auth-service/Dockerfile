# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /workspace/app

COPY pom.xml .
# Intentamos descargar dependencias (esto se cachea si pom.xml no cambia)
# Agregamos || true por si fallara la descarga inicial sin src
RUN mvn dependency:go-offline || true

COPY src src
# Compilamos el proyecto (omitiendo tests para acelerar la build)
RUN mvn clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copiamos el JAR compilado desde el stage anterior
COPY --from=build /workspace/app/target/*.jar app.jar

EXPOSE 8081

# Healthcheck apuntando al actuator (debe estar habilitado en el proyecto)
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -q -O - http://localhost:8081/actuator/health | grep UP || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
