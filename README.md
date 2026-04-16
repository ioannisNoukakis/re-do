# re-do

A scheduler service for Task Execution Graphs (TEGs) — directed acyclic graphs of tasks where each task can consume
artefacts produced by its predecessors.

## Architecture

The codebase follows hexagonal architecture with a strict boundary between business logic and infrastructure.
See [Architecture.md](Architecture.md) for conventions on ports, adapters, use cases, and testing.

### Submodules

| Module                             | Description                                            |
|------------------------------------|--------------------------------------------------------|
| `core`                             | Domain model, use cases, ports, and in-memory adapters |
| `adapter_driving_scheduler_spring` | Spring Boot HTTP entry point for the scheduler         |
| `adapter_driving_runner_spring`    | Spring Boot entry point for task runners               |
| `adapter_common_mongodb_spring`    | Shared MongoDB-backed driven adapters                  |
| `adapter_common_rabbitmq_spring`   | Shared RabbitMQ-backed driven adapters                 |
| `task_impl_demo`                   | Demo task implementation plugin                        |

## Running locally

```bash
docker-compose -f docker-compose.dev.yml up -d
./gradlew :adapter_driving_scheduler_spring:bootRun
```

## Notes

### File upload size limit

The maximum size of an uploaded file is enforced at the **reverse proxy** level (e.g. nginx `client_max_body_size`).
This service does not impose its own limit — doing so here would duplicate policy that belongs at the infrastructure
boundary and would produce inconsistent error responses depending on where the request was blocked.
