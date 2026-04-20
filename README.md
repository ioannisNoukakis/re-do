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

Run the full demo stack (builds task plugins, starts scheduler + runner + infrastructure):

```bash
./demo.sh
```

The scheduler API is available at `http://localhost:8080`.

### Testing with the HTTP files

The `.http` files in `adapter_driving_scheduler_spring/src/test/api/` can be run directly from IntelliJ or any HTTP
client that supports the JetBrains format. Select the `local` environment defined in `http-client.env.json`.

| File                      | Description                                            |
|---------------------------|--------------------------------------------------------|
| `schedule_demo_task.http` | Schedule a TEG using the `DemoEchoTask` implementation |
| `upload_file.http`        | Upload a file to be referenced as task input           |

Please first upload a file and use it in lieu of the current `fileId` in `schedule_demo_task.http`
to see the full flow of file download, task execution, and artefact generation. Rust FS has a ui at
`http://localhost:9003` where you can see the uploaded file and its contents along with the generated artefacts.
Credentials are specified in `docker-compose.demo.yml` as `RUSTFS_ACCESS_KEY` and `RUSTFS_SECRET_KEY`.

## Notes

### File upload size limit

The maximum size of an uploaded file is enforced at the **reverse proxy** level (e.g. nginx `client_max_body_size`).
This service does not impose its own limit — doing so here would duplicate policy that belongs at the infrastructure
boundary and would produce inconsistent error responses depending on where the request was blocked.
