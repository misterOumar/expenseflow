# syntax=docker/dockerfile:1

# --- Build : compile et découpe le jar en couches (dépendances, loader, application) ---
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/expenseflow-*.jar extract --layers --launcher --destination extracted

# --- Exécution : JRE seul, utilisateur sans privilèges ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S expenseflow && adduser -S expenseflow -G expenseflow \
    && mkdir -p /data/receipts && chown -R expenseflow:expenseflow /data

# Une couche par fréquence de changement : seule la couche « application » est reconstruite le plus souvent.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER expenseflow

ENV RECEIPTS_DIR=/data/receipts \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

VOLUME /data/receipts
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/readiness | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
