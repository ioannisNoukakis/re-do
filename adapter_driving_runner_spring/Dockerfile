FROM eclipse-temurin:21.0.10_7-jdk-alpine-3.22@sha256:45df4ae087e53ba551d35d5142a2ba6837eaa52592882745ee4377b12c1655f2 AS builder

# Space-separated list of task_impl_* module names to bake into the runner image.
# Examples:
#   --build-arg TASK_IMPLS="task_impl_ffmpeg"
#   --build-arg TASK_IMPLS="task_impl_llm_summarise task_impl_llm_translate"
ARG TASK_IMPLS="task_impl_demo"

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
# These modules are listed in settings.gradle.kts but not needed by the runner.
# Gradle requires every included project directory to exist at configuration time.
RUN mkdir -p adapter_common_mongodb_spring adapter_driving_scheduler_spring

# Sources for any task_impl_* module and its transitive project deps.
COPY llm_inference_core/ llm_inference_core/
COPY task_handler_test_support/ task_handler_test_support/
COPY task_impl_demo/ task_impl_demo/
COPY task_impl_ffmpeg/ task_impl_ffmpeg/
COPY task_impl_http_fetch/ task_impl_http_fetch/
COPY task_impl_llm_summarise/ task_impl_llm_summarise/
COPY task_impl_llm_translate/ task_impl_llm_translate/
COPY task_impl_whisper_transcription/ task_impl_whisper_transcription/

RUN chmod +x gradlew && \
    ./gradlew :adapter_driving_runner_spring:bootJar --no-daemon && \
    mkdir -p /build/plugins && \
    set -e; \
    for m in $TASK_IMPLS; do \
        ./gradlew ":$m:shadowJar" --no-daemon; \
        cp "$m/build/libs/"*-all.jar /build/plugins/; \
    done

FROM eclipse-temurin:21.0.10_7-jre-alpine-3.22@sha256:b08e4d2ef04cda3786f38cc92f96005c0e32361c353d109a6be0f03c3e9a157f

# Set to "true" to install the ffmpeg binary into the runtime image
# (required when TASK_IMPLS includes task_impl_ffmpeg).
ARG INCLUDE_FFMPEG=false

# Harden the JVM: disable entropy blocking, Log4j lookups, and enforce TLSv1.2+ for outbound connections
ENV JAVA_TOOL_OPTIONS="\
  -Djava.security.egd=file:/dev/./urandom \
  -Dlog4j2.formatMsgNoLookups=true \
  -Dhttps.protocols=TLSv1.2,TLSv1.3"

WORKDIR /app

RUN if [ "$INCLUDE_FFMPEG" = "true" ]; then apk add --no-cache ffmpeg; fi

# Create a system group
RUN addgroup -g 10001 -S appgroup && \
    # System user, Don't assign a password, Don't create a home directory \
     adduser -S -D -H --uid 10001 -G appgroup appuser && \
     mkdir task_handler_plugins && \
     chown appuser:appgroup task_handler_plugins
USER appuser

COPY --from=builder --chown=appuser:appgroup /build/adapter_driving_runner_spring/build/libs/*.jar app.jar
COPY --from=builder --chown=appuser:appgroup /build/plugins/ task_handler_plugins/

ENTRYPOINT ["java", "-jar", "app.jar"]
