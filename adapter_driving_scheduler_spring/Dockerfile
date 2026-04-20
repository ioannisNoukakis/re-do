FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /build

COPY . .
RUN chmod +x gradlew && ./gradlew :adapter_driving_scheduler_spring:bootJar --no-daemon

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=builder --chown=appuser:appgroup /build/adapter_driving_scheduler_spring/build/libs/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
