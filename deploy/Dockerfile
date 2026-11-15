# Imagen de PRODUCCIÓN que usa el CI/CD.
# GitHub Actions compila y prueba el jar; en el EC2 solo se copia el jar ya probado
# (build rápido y liviano, sin Maven en el servidor).
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY astronaut-backend.jar app.jar

# Respaldo local de los informes cuando DynamoDB no está disponible (volumen ./data)
ENV REPORTS_LOCAL_DIR=/app/data/reports \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
