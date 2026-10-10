# syntax=docker/dockerfile:1
FROM node:24-alpine AS web-build
WORKDIR /web
COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
ENV CI=1
ENV EXPO_PUBLIC_API_URL=/api
RUN npx expo export --platform web

FROM maven:3.9.9-eclipse-temurin-21 AS api-build
WORKDIR /app
COPY backend/pom.xml ./
RUN mvn -B dependency:go-offline
COPY backend/src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS api
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 dbgram && useradd --uid 10001 --gid dbgram --create-home dbgram \
    && mkdir -p /app/uploads && chown -R dbgram:dbgram /app
WORKDIR /app
COPY --from=api-build --chown=dbgram:dbgram /app/target/*.jar /app/server.jar
USER dbgram
EXPOSE 8080
HEALTHCHECK --interval=30s --start-period=90s --timeout=5s --retries=3 CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/server.jar"]

FROM caddy:2-alpine AS web
COPY --from=web-build /web/dist /srv
COPY deploy/Caddyfile /etc/caddy/Caddyfile
EXPOSE 80 443


# Default target for Render and other platforms that expose one HTTP port.
FROM api AS hosted
USER root
RUN apt-get update && apt-get install -y --no-install-recommends gosu \
    && rm -rf /var/lib/apt/lists/*
COPY --from=web-build --chown=dbgram:dbgram /web/dist /app/public
COPY deploy/hosted-entrypoint.sh /app/hosted-entrypoint.sh
RUN sed -i 's/\r$//' /app/hosted-entrypoint.sh && chmod 0755 /app/hosted-entrypoint.sh
ENV SPRING_WEB_RESOURCES_STATIC_LOCATIONS=file:/app/public/
ENV DBGRAM_STORAGE_PATH=/app/uploads
HEALTHCHECK --interval=30s --start-period=90s --timeout=5s --retries=3 CMD curl -fsS "http://localhost:${PORT:-8080}/actuator/health" || exit 1
ENTRYPOINT ["/app/hosted-entrypoint.sh"]
