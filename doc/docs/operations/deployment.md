# Deployment

## Demo stack

The fastest way to run everything is via Docker Compose. `docker-compose.demo.yml` starts:

- `rabbitMQ`: RabbitMQ 3 with management UI.
- `mongoDB`: MongoDB 7.
- `rustfs`: S3-compatible storage.
- `scheduler`: the scheduler service, built from `adapter_driving_scheduler_spring/Dockerfile`.
- `runner`: the runner service, built from `adapter_driving_runner_spring/Dockerfile`. Mounts
  `./task_handler_plugins` so plugin jars are loaded at startup.

```bash
make all                    # build plugins and start the stack
docker compose -f docker-compose.demo.yml down       # stop
docker compose -f docker-compose.demo.yml down -v    # stop and drop volumes
```

## Dev stack

`docker-compose.dev.yml` runs only the infrastructure (RabbitMQ, MongoDB, RustFS). Use this when you want to run
the scheduler and runner from your IDE or `./gradlew bootRun`.

```bash
docker compose -f docker-compose.dev.yml up -d
./gradlew :adapter_driving_scheduler_spring:bootRun
./gradlew :adapter_driving_runner_spring:bootRun
```

## Building images

```bash
docker build -f adapter_driving_scheduler_spring/Dockerfile -t redo-scheduler .
docker build -f adapter_driving_runner_spring/Dockerfile    -t redo-runner    .
```

Both Dockerfiles are multi-stage builds (Temurin JDK 21 builder, JRE 21 runtime, Alpine base). They run as a
non-root user and set safe JVM flags (TLS 1.2+, no Log4j lookups).

## Required infrastructure in production

| Component          | Purpose                                           |
|--------------------|---------------------------------------------------|
| RabbitMQ           | Task dispatch and result/progress messaging.      |
| MongoDB            | Event store, file references, mutual-exclusion lock. |
| S3-compatible      | File artefact storage. Bucket name configurable.  |
| Auth gateway       | Validates callers and forwards `X-Auth-*` headers.|
| Reverse proxy      | TLS termination and upload size enforcement.      |

## Bucket setup

The S3 bucket (default `scheduler-files`) must exist before the first file upload. Creation is not yet automated.

## Plugins

Plugin jars are not baked into the runner image. They are mounted at runtime under `task_handler_plugins/` so they
can be replaced without rebuilding. To bake them in instead, copy the jars into the image in your own Dockerfile
or use a sidecar that populates a shared volume.

## Health and observability

- Spring Boot Actuator endpoints are available if exposed (not configured in the demo).
- Plugin progress and log events flow back to the scheduler and are persisted as `TEGEvent` records.
- The roadmap calls for Prometheus and Grafana integration; not yet implemented.
