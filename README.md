# re-do

A scheduler service for Task Execution Graphs (TEGs) — directed acyclic graphs of tasks where each task can consume
artefacts produced by its predecessors.

## Architecture

The codebase follows hexagonal architecture with a strict boundary between business logic and infrastructure.
See [Architecture.md](Architecture.md) for conventions on ports, adapters, use cases, and testing.

## Running locally

You'll need java 21 sdk and docker (with docker compose plugin) installed to run the demo stack locally to run the commands below.

Run the full demo stack (builds the images and starts scheduler + runners + infrastructure):

```bash
make compose-up
```

The scheduler API is available at `http://localhost:8080`.

The demo stack runs two runner flavors, each built from `adapter_driving_runner_spring/Dockerfile` with different
build args:

| Service         | `TASK_IMPLS`                                                                                              | `INCLUDE_FFMPEG` |
|-----------------|-----------------------------------------------------------------------------------------------------------|------------------|
| `runner-ffmpeg` | `task_impl_ffmpeg`                                                                                        | `true`           |
| `runner-tasks`  | `task_impl_demo task_impl_http_fetch task_impl_llm_summarise task_impl_llm_translate task_impl_whisper_transcription` | `false`          |

Routing is per task type: each runner declares one RabbitMQ queue per loaded handler (named after its
`implementationName()`) and only consumes from those queues, so the scheduler dispatches each task to the
runner that actually has its handler. To add another flavor, copy one of the runner services in
`docker-compose.demo.yml` and adjust `TASK_IMPLS` / `INCLUDE_FFMPEG`.

### API

| Endpoint                     | Method | Description                                                |
|------------------------------|--------|------------------------------------------------------------|
| `/api/v1/teg/schedule`       | POST   | Submit a new TEG for execution                             |
| `/api/v1/teg/{tegId}/events` | GET    | Server-Sent Events stream of TEG events for the submitter  |
| `/api/v1/files/upload`       | POST   | Upload a file to be referenced as a task input             |
| `/v3/api-docs`               | GET    | OpenAPI 3 spec (JSON), auto-generated via springdoc        |
| `/v3/api-docs.yaml`          | GET    | OpenAPI 3 spec (YAML)                                      |
| `/swagger-ui.html`           | GET    | Interactive API explorer                                   |

#### Event stream

`GET /api/v1/teg/{tegId}/events` returns a `text/event-stream` of TEG events. The caller's `X-Auth-Principal`
must match the TEG's submitter — non-owners get `403`, unknown TEG IDs get `404`. The stream replays the full
event history then continues live, and closes after a terminal event (`NoMoreTasksToSchedule` or `TEGFailed`).

### Testing with the HTTP files

The `.http` files in `adapter_driving_scheduler_spring/src/test/api` can be run directly from IntelliJ or any HTTP
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

### Task plugin environment variables

Each runner flavor loads its task plugins in-process and reads their configuration from its own environment.
Set the variables on the runner service that bundles the relevant task (`runner-ffmpeg` for `FFMPEGTask`,
`runner-tasks` for everything else — see `docker-compose.demo.yml`). `DemoEchoTask`, `FFMPEGTask`, and
`HttpFetchTask` do not read any environment variables.

#### Shared LLM backend (`LlmSummariseTask`, `LlmTranslateTask`)

Both LLM tasks talk to an OpenAI-compatible chat completions endpoint via `LangChain4jOpenAiLlmBackendAdapter`.

| Variable            | Required | Default        | Description                                                |
|---------------------|----------|----------------|------------------------------------------------------------|
| `LLM_API_KEY`       | yes      | (none)         | API key for the OpenAI-compatible endpoint                 |
| `LLM_BASE_URL`      | no       | OpenAI default | Override the base URL (e.g. self-hosted Gemma)             |
| `LLM_DEBUG_LOGGING` | no       | `false`        | Set to `true` to log full prompts and responses at DEBUG   |

#### `LlmSummariseTask`

| Variable                              | Default                                |
|---------------------------------------|----------------------------------------|
| `LLM_SUMMARISE_SYSTEM_PROMPT`         | built-in concise-summary prompt        |
| `LLM_SUMMARISE_MODEL`                 | `gpt-4o-mini`                          |
| `LLM_SUMMARISE_MAX_TOKENS`            | `1024`                                 |
| `LLM_SUMMARISE_TEMPERATURE`           | `0.2`                                  |
| `LLM_SUMMARISE_CONTEXT_WINDOW_TOKENS` | `128000`                               |
| `LLM_SUMMARISE_TIMEOUT_SECONDS`       | `120`                                  |

#### `LlmTranslateTask`

| Variable                              | Default                                                       |
|---------------------------------------|---------------------------------------------------------------|
| `LLM_TRANSLATE_SYSTEM_PROMPT_TEMPLATE`| built-in template (`%s` is replaced by the target language)   |
| `LLM_TRANSLATE_MODEL`                 | `gpt-4o-mini`                                                 |
| `LLM_TRANSLATE_MAX_TOKENS`            | `2048`                                                        |
| `LLM_TRANSLATE_TEMPERATURE`           | `0.2`                                                         |
| `LLM_TRANSLATE_CONTEXT_WINDOW_TOKENS` | `128000`                                                      |
| `LLM_TRANSLATE_TIMEOUT_SECONDS`       | `120`                                                         |

#### `WhisperTranscriptionTask`

Uses the OpenAI Java SDK directly (not the shared LLM backend), so it has its own variables.

| Variable          | Required | Default        | Description                                                       |
|-------------------|----------|----------------|-------------------------------------------------------------------|
| `OPENAI_API_KEY`  | yes      | (none)         | API key for the OpenAI-compatible transcription endpoint          |
| `OPENAI_BASE_URL` | no       | OpenAI default | Override the base URL (e.g. self-hosted Whisper)                  |
| `OPENAI_MODEL`    | no       | `whisper-1`    | Transcription model name                                          |

## Roadmap

### ✅ Done
- Full infrastructure for executing Task Execution Graphs (TEGs)
- Support for file artefacts and string values as task inputs/outputs
- Demo task plugin and HTTP API entry point
- MongoDB-backed state, RabbitMQ messaging, S3-compatible file storage
- FFmpeg task plugin — video/audio processing as a first-class task implementation
- **Transcription** — speech-to-text via [Whisper](https://github.com/openai/whisper)
- **Translation** — multilingual translation via [Gemma Translate](https://ai.google.dev/gemma) (openai api-compatible)
- **Summarisation** — text summarisation via [Gemma 4](https://ai.google.dev/gemma) (openai api-compatible)

### 🔜 Planned

#### Tasks
Check the issues on the gitlab repository for the most up-to-date roadmap and to contribute!

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
