FROM eclipse-temurin:21-jdk-alpine AS build

ARG SERVICE_NAME
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY backend ./backend

RUN chmod +x gradlew \
    && ./gradlew ":backend:${SERVICE_NAME}:bootJar" --no-daemon \
    && cp "backend/${SERVICE_NAME}/build/libs/${SERVICE_NAME}-0.1.0-SNAPSHOT.jar" /workspace/application.jar

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S ssolab && adduser -S -G ssolab -H ssolab
WORKDIR /application
COPY --from=build --chown=ssolab:ssolab /workspace/application.jar application.jar

USER ssolab
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/application/application.jar"]
