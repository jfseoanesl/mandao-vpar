# --- Compilación ---
FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package && cp target/mandao-backend-*.jar /app.jar

# --- Ejecución ---
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S mandao && adduser -S mandao -G mandao && mkdir -p /data/files && chown -R mandao:mandao /data
USER mandao
WORKDIR /app
COPY --from=build /app.jar app.jar
ENV STORAGE_DIR=/data/files \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
