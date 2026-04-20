FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /build

COPY . .
RUN chmod +x gradlew && ./gradlew :adapter_driving_runner_spring:bootJar --no-daemon

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
RUN mkdir task_handler_plugins
COPY --from=builder --chown=appuser:appgroup /build/adapter_driving_runner_spring/build/libs/*.jar app.jar
RUN chown appuser:appgroup task_handler_plugins
USER appuser
ENTRYPOINT ["java", "-jar", "app.jar"]
