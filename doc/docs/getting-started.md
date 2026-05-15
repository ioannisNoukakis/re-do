# Getting started

This page walks through running the demo stack and submitting a TEG end to end.

## Prerequisites

- Java 21 SDK
- Docker with the Compose plugin
- GNU Make 4.4.1 or newer

## Run the demo stack

The full stack consists of the scheduler, a runner, RabbitMQ, MongoDB, and RustFS (S3-compatible storage).

```bash
make all
```

This builds the task plugin jars, copies them under `task_handler_plugins/`, then starts everything via
`docker-compose.demo.yml`.

| Service       | URL                     | Notes                                   |
|---------------|-------------------------|-----------------------------------------|
| Scheduler API | http://localhost:8080   | HTTP entry point                        |
| RabbitMQ UI   | http://localhost:5051   | user `root`, password `example`         |
| MongoDB       | localhost:27017         | user `root`, password `example`         |
| RustFS UI     | http://localhost:9003   | user `root`, password `example`         |

Before uploading files, create the `scheduler-files` bucket in RustFS (UI or API). This step is not yet automated.

## Submit a TEG

Two `.http` files in `adapter_driving_scheduler_spring/src/test/api/` cover the basic flow. They use the JetBrains
HTTP client format and can be run from IntelliJ. Select the `local` environment from `http-client.env.json`.

1. Run **`upload_file.http`** to upload a sample file. The response contains a `ref` and `storedWith` value.
2. Open **`schedule_demo_task.http`**, replace the `ref` under `initArtefacts` with the value from step 1, and run it.
3. Watch the scheduler and runner logs to see dispatch and completion.
4. Inspect the resulting events in MongoDB and the produced files in RustFS.

See [HTTP API](api/overview.md) for the request shapes.

## Stop the stack

```bash
docker compose -f docker-compose.demo.yml down
```

Add `-v` to also drop the MongoDB and RustFS volumes.
