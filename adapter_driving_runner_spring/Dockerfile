FROM eclipse-temurin:21.0.10_7-jdk-alpine-3.22@sha256:45df4ae087e53ba551d35d5142a2ba6837eaa52592882745ee4377b12c1655f2 AS builder

WORKDIR /build

COPY gradlew .
COPY gradle/ gradle/
COPY settings.gradle.kts .
COPY gradle.properties .
COPY buildSrc/ buildSrc/
COPY core/ core/
COPY adapter_common_rabbitmq_spring/ adapter_common_rabbitmq_spring/
COPY adapter_common_s3/ adapter_common_s3/
COPY adapter_driving_runner_spring/ adapter_driving_runner_spring/

RUN chmod +x gradlew && ./gradlew :adapter_driving_runner_spring:bootJar --no-daemon

FROM eclipse-temurin:21.0.10_7-jre-alpine-3.22@sha256:b08e4d2ef04cda3786f38cc92f96005c0e32361c353d109a6be0f03c3e9a157f

# Harden the JVM: disable entropy blocking, Log4j lookups, and enforce TLSv1.2+ for outbound connections
ENV JAVA_TOOL_OPTIONS="\
  -Djava.security.egd=file:/dev/./urandom \
  -Dlog4j2.formatMsgNoLookups=true \
  -Dhttps.protocols=TLSv1.2,TLSv1.3"

WORKDIR /app

# Create a system group
RUN addgroup -g 10001 -S appgroup && \
    # System user, Don't assign a password, Don't create a home directory \
     adduser -S -D -H --uid 10001 -G appgroup appuser && \
     mkdir task_handler_plugins && \
     chown appuser:appgroup task_handler_plugins
USER appuser

COPY --from=builder --chown=appuser:appgroup /build/adapter_driving_runner_spring/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
