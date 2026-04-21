FROM eclipse-temurin:21.0.10_7-jdk-alpine-3.22 AS builder
WORKDIR /build

COPY . .
RUN chmod +x gradlew && ./gradlew :adapter_driving_runner_spring:bootJar --no-daemon

FROM eclipse-temurin:21.0.10_7-jre-alpine-3.22
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
RUN mkdir task_handler_plugins
COPY --from=builder --chown=appuser:appgroup /build/adapter_driving_runner_spring/build/libs/*.jar app.jar
RUN chown appuser:appgroup task_handler_plugins
USER appuser
ENTRYPOINT ["java", "-jar", "app.jar"]
