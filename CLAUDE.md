# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

**re-do** is a distributed task scheduler for **Task Execution Graphs (TEGs)** — directed acyclic graphs of tasks where
each task consumes artifacts produced by its predecessors. It exposes an HTTP API to schedule TEGs and uses RabbitMQ to
dispatch tasks to runners, which report progress/results back to the scheduler. State is persisted in MongoDB; file
artifacts are stored in S3-compatible storage.

## Build & test commands

```bash
./gradlew build                                         # Build all modules
./gradlew test                                          # Run all tests
./gradlew :core:test                                    # Tests for a specific module
./gradlew :<module>:test --tests "*ClassName"           # Single test class
./gradlew :<module>:test --tests "*ClassName.methodName" # Single test
./demo.sh                                               # Build task plugins and start full stack via Docker Compose
```

Java 21 is required. Gradle 8.x with Kotlin DSL; configuration cache is enabled.

## Module layout

```
core/                              Pure business logic — no framework, no I/O
  src/main/
    scheduler/                     TEGScheduler, TegUpdateHandler, UploadFileUseCase
    runner/                        TaskRunner
    common/                        Shared models (TEGTask, TEGArtefact, Identity, TEGMessage*)
  src/test/
    adapters/driven/               Hand-written test doubles (Stub*, Fake*, Spy*)
    *SutBuilder                    SUT builders that wire test doubles for each use case

adapter_driving_scheduler_spring/  HTTP controllers + RabbitMQ consumer for scheduler
adapter_driving_runner_spring/     RabbitMQ consumer for task runner
adapter_common_rabbitmq_spring/    RabbitMQ messaging adapter
adapter_common_mongodb_spring/     MongoDB persistence adapter
adapter_common_s3/                 S3 file storage adapter
task_impl_demo/                    Demo (echo) task plugin
task_impl_ffmpeg/                  FFmpeg task plugin (in progress)
buildSrc/                          Gradle convention plugins (kotlin-common, kotlin-spring, kotlin-library)
```

## Architecture: hexagonal (ports & adapters)

The core rule: **the domain and use cases never depend on adapters**. The dependency arrow always points inward.

```
[ Driving adapter ] → [ Use case ] → [ Driven port interface ]
                                              ↑
                                    [ Driven adapter (real or test double) ]
```

**Naming conventions** — follow these exactly when adding new ports or adapters:

| Thing           | Pattern                     | Example                     |
|-----------------|-----------------------------|-----------------------------|
| Driven port     | `<Noun><Role>Port`          | `PersistencePort`           |
| Real adapter    | `<Tech><Noun><Role>Adapter` | `MongoDbPersistenceAdapter` |
| Test stub       | `Stub<Noun><Role>Adapter`   | `StubPersistenceAdapter`    |
| Test fake       | `Fake<Noun><Role>Adapter`   | `FakePersistenceAdapter`    |
| Driving port    | `<Action>Port`              | `MessagingPort`             |
| Driving adapter | `<Tech><Action>Adapter`     | `RabbitMqMessagingAdapter`  |

**What belongs where:**

- Policy logic (authorization, business rules, validations) → use case
- Technology-specific constraint handling (DB unique key, connection failure) → adapter
- A use case accepts a command object and returns a result object; errors are domain exceptions, never null or error
  codes.

## Core use cases

- **`TEGScheduler.scheduleTeg()`** — Validates and persists a new TEG (checks: non-empty, unique task/artifact names,
  all inputs have producers, at least one starting task, no cycles).
- **`TEGScheduler.handleTegUpdate()`** — Processes result/failure/progress messages from runners; schedules dependent
  tasks when their inputs are satisfied; retries failed tasks (max 3).
- **`TEGScheduler.runTimeoutCheck()`** — Periodic sweep for timed-out scheduled tasks.
- **`TaskRunner.execute()`** — Downloads inputs from S3, calls the `TaskHandler` plugin, uploads outputs, reports back.
- **`UploadFileUseCase.upload()`** — Uploads a file to S3 and persists its reference.

## Testing rules (from Architecture.md — strictly enforced)

1. **No mocking libraries.** All test doubles are hand-written classes that implement port interfaces.
2. **One assertion per test.** Three failure conditions → three tests.
3. **Assert by deep equality.** Construct the full expected object and compare it; do not assert individual fields.
4. **TDD cycle:** write a failing test first, then the minimum code to pass it, then refactor.

**Three double types:**

- **Stub** — pre-configured return value; raises domain exception to simulate failure.
- **Fake** — working in-memory implementation; used to verify state after a write.
- **Spy** — records calls; used only when collaboration itself is the contract (use sparingly).

Use `*SutBuilder` classes in `core/src/test` to wire up the system-under-test with appropriate doubles rather than
constructing use cases directly in tests.

## Key dependencies

- Kotlin + Spring Boot 3.x
- RabbitMQ / AMQP (inter-service messaging)
- MongoDB (event/state persistence — event-sourced TEG state via `TEGEvent`)
- AWS SDK v2 (S3-compatible storage)
- Arrow-kt (`Either` monad for typed error handling)
- Apache Tika (file type detection)
- JUnit 5 + TestContainers (integration tests spin up real MongoDB and RabbitMQ)
