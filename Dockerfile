# ---------- Etapa 1: compilación ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ---------- Etapa 2: ejecución ----------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/astronaut-backend.jar app.jar

# MySQL (DB_*) y DynamoDB (AWS_*, DYNAMODB_TABLE) se configuran con variables de entorno / .env.
# Respaldo local de informes (montar un volumen en /app/data para no perderlos)
ENV REPORTS_LOCAL_DIR=/app/data/reports
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
