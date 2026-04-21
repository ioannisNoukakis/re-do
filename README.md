# re-do

A scheduler service for Task Execution Graphs (TEGs) — directed acyclic graphs of tasks where each task can consume
artefacts produced by its predecessors.

## Architecture

The codebase follows hexagonal architecture with a strict boundary between business logic and infrastructure.
See [Architecture.md](Architecture.md) for conventions on ports, adapters, use cases, and testing.

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

Please first upload a file and use it in lieu of the current `initArtefacts.ref` in `schedule_demo_task.http`
to see the full flow of file download, task execution, and artefact generation. Rust FS has a ui at
`http://localhost:9003` where you can see the uploaded file and its contents along with the generated artefacts.
Credentials are specified in `docker-compose.demo.yml` as `RUSTFS_ACCESS_KEY` and `RUSTFS_SECRET_KEY`. You'll
have to create the "scheduler-files" bucket manually using the Rust FS API or UI before uploading files
(todo: automate). Once the teg has been submitted, watch the logs of the scheduler and runner services to see the execution flow. 
You should see the state of the demo TEG in mongodb and the generated artefacts in Rust FS after the tasks complete.

## Roadmap

### ✅ Done
- Full infrastructure for executing Task Execution Graphs (TEGs)
- Support for file artefacts and string values as task inputs/outputs
- Demo task plugin and HTTP API entry point
- MongoDB-backed state, RabbitMQ messaging, S3-compatible file storage

### 🚧 In Progress
- **FFmpeg task plugin** — video/audio processing as a first-class task implementation

### 🔜 Planned

#### AI / ML Task Plugins
- **Transcription** — speech-to-text via [Whisper](https://github.com/openai/whisper)
- **Translation** — multilingual translation via [Gemma Translate](https://ai.google.dev/gemma)
- **Summarisation** — text summarisation via [Gemma 4](https://ai.google.dev/gemma)

#### Observability
- **Progress events** — task runner emits fine-grained progress events during artefact downloads and uploads
- **Prometheus & Grafana integration** — metrics exposition and dashboards for TEG execution, queue depth, and task throughput

#### Distribution & Deployment
- **Monolith mode** — single-app, zero-external-dependencies mode (embedded broker, in-process storage) for easy integration 
and local development without Docker

## Notes

### Auth gateway

This project assumes an auth gateway is present at the infrastructure boundary that handles authentication and authorization,
and that the `X-Auth-Principal` and `X-Auth-Roles` headers are propagated to the service. For example:

```
X-Auth-Principal: "test-user"
X-Auth-Roles: "admin,other-role"
```

### File upload size limit

The maximum size of an uploaded file is enforced at the **reverse proxy** level (e.g. nginx `client_max_body_size`).
This service does not impose its own limit — doing so here would duplicate policy that belongs at the infrastructure
boundary and would produce inconsistent error responses depending on where the request was blocked.
