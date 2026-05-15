# Configuration

Both services are Spring Boot applications. Every setting can be overridden via environment variables using
Spring's relaxed binding (`scheduler.persistence.mode` becomes `SCHEDULER_PERSISTENCE_MODE`).

## Scheduler

Defined in `adapter_driving_scheduler_spring/src/main/resources/application.yaml`.

### Operating modes

Each subsystem has an `in-memory` mode and a real backend. The in-memory modes are useful for local development
without Docker.

| Property                                  | Values                  | Default      |
|-------------------------------------------|-------------------------|--------------|
| `scheduler.messaging.mode`                | `in-memory`, `rabbitmq` | `in-memory`  |
| `scheduler.persistence.mode`              | `in-memory`, `mongodb`  | `in-memory`  |
| `scheduler.file-storage.mode`             | `in-memory`, `s3`       | `in-memory`  |
| `scheduler.file-reference-store.mode`     | `in-memory`, `mongodb`  | `in-memory`  |
| `scheduler.mutual-exclusion-lock.mode`    | `in-memory`, `mongodb`  | `mongodb`    |

### Retries and locks

| Property                                          | Default | Meaning                                       |
|---------------------------------------------------|---------|-----------------------------------------------|
| `scheduler.max-failures-before-giving-up`         | `3`     | Per-task retry budget.                        |
| `scheduler.mutual-exclusion-lock.retry-interval`  | `500ms` | Polling interval when waiting for a TEG lock. |
| `scheduler.mutual-exclusion-lock.timeout`         | `30s`   | Maximum wait before giving up on a lock.      |

### RabbitMQ

| Property                                          | Default                          |
|---------------------------------------------------|----------------------------------|
| `scheduler.rabbitmq.task-exchange`                | `teg.tasks`                      |
| `scheduler.rabbitmq.reply-exchange`               | `teg.results`                    |
| `scheduler.rabbitmq.reply-queue`                  | `teg.scheduler.updates`          |
| `scheduler.rabbitmq.reply-routing-key`            | `#`                              |
| `scheduler.rabbitmq.dead-letter-exchange`         | `teg.scheduler.dead-letter`      |
| `scheduler.rabbitmq.dead-letter-routing-key`      | `scheduler.dead-letter`          |

### S3

| Property                                  | Default                  |
|-------------------------------------------|--------------------------|
| `scheduler.file-storage.s3.endpoint`      | `http://localhost:9000`  |
| `scheduler.file-storage.s3.bucket`        | `scheduler-files`        |
| `scheduler.file-storage.s3.region`        | `us-east-1`              |
| `scheduler.file-storage.s3.access-key`    | `root` (demo)            |
| `scheduler.file-storage.s3.secret-key`    | `example` (demo)         |

### MongoDB

| Property                                                       | Default |
|----------------------------------------------------------------|---------|
| `scheduler.mongodb.cursor-batch-size-for-get-all-teg-not-events` | `100`   |
| `scheduler.mongodb.teg-event-lookback-duration`                | `30d`   |

### Upload limits

| Property                              | Default |
|---------------------------------------|---------|
| `spring.servlet.multipart.max-file-size`  | `500MB` |
| `spring.servlet.multipart.max-request-size` | `500MB` |

Note: the README recommends enforcing the real upload size limit at the reverse proxy.

## Runner

Defined in `adapter_driving_runner_spring/src/main/resources/application.yaml`.

| Property                                | Default                  |
|-----------------------------------------|--------------------------|
| `runner.tasks.plugin-folder`            | `./task_handler_plugins` |
| `runner.rabbitmq.task-exchange`         | `teg.tasks`              |
| `runner.rabbitmq.reply-exchange`        | `teg.results`            |
| `runner.rabbitmq.reply-routing-key`     | `task.result`            |
| `runner.rabbitmq.dead-letter-exchange`  | `teg.dead-letter`        |
| `runner.rabbitmq.dead-letter-routing-key` | `task.dead-letter`     |
| `runner.file-storage.s3.endpoint`       | `http://localhost:9000`  |
| `runner.file-storage.s3.bucket`         | `scheduler-files`        |

The runner's task and reply exchanges must match the scheduler's.

## Plugin environment

Some plugins read configuration from the **runner process environment**:

- Whisper: `OPENAI_API_KEY`, `OPENAI_BASE_URL`, `OPENAI_MODEL`.
- LLM translate / summarise: `LLM_TRANSLATE_*` and `LLM_SUMMARISE_*` (see each plugin page).

See each plugin's page under [Tasks](../tasks/index.md) for the full list.
