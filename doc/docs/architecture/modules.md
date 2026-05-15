# Modules

The repository is a Gradle multi-project build. Every directory at the root is a module.

## Core and adapters

| Module                              | Role                                                                       |
|-------------------------------------|----------------------------------------------------------------------------|
| `core`                              | Pure business logic. Domain models, use cases, port interfaces. No I/O.    |
| `adapter_driving_scheduler_spring`  | Spring Boot app. HTTP controllers and runner-result message consumer.      |
| `adapter_driving_runner_spring`     | Spring Boot app. Consumes task dispatches; loads plugin jars.              |
| `adapter_common_rabbitmq_spring`    | RabbitMQ adapter shared by scheduler and runner.                           |
| `adapter_common_mongodb_spring`     | MongoDB adapter (event store, file refs, mutual-exclusion lock).           |
| `adapter_common_s3`                 | S3-compatible file storage adapter.                                        |
| `llm_inference_core`                | Shared LLM client primitives used by the LLM task plugins.                 |

## Task plugins

Each `task_impl_*` module is a separate plugin packaged as a shadow jar. The runner loads them at startup from
`task_handler_plugins/` via `ServiceLoader`.

| Module                              | `implementationName`         |
|-------------------------------------|------------------------------|
| `task_impl_demo`                    | `DemoEchoTask`               |
| `task_impl_http_fetch`              | `HttpFetchTask`              |
| `task_impl_ffmpeg`                  | `FFMPEGTask`                 |
| `task_impl_whisper_transcription`   | `WhisperTranscriptionTask`   |
| `task_impl_llm_translate`           | `LlmTranslateTask`           |
| `task_impl_llm_summarise`           | `LlmSummariseTask`           |

See [Tasks](../tasks/index.md) for what each plugin does.

## Build conventions

`buildSrc/` defines four reusable Gradle convention plugins:

| Convention plugin                          | Used by                                                 |
|--------------------------------------------|---------------------------------------------------------|
| `buildlogic.kotlin-common-conventions`     | All Kotlin modules (Java 21, common deps, tests).       |
| `buildlogic.kotlin-library-conventions`    | Library modules (for example `core`).                   |
| `buildlogic.kotlin-spring-common-conventions` | Spring Boot adapter modules.                         |
| `buildlogic.kotlin-task-plugin-conventions`| Task plugin modules (adds Shadow, disables plain jar).  |
| `buildlogic.spotless-conventions`          | Formatting.                                             |

## Naming convention

The codebase enforces consistent names so module layout is predictable.

| Thing            | Pattern                       | Example                       |
|------------------|-------------------------------|-------------------------------|
| Driven port      | `<Noun><Role>Port`            | `PersistencePort`             |
| Real adapter     | `<Tech><Noun><Role>Adapter`   | `MongoDbPersistenceAdapter`   |
| Test stub        | `Stub<Noun><Role>Adapter`     | `StubPersistenceAdapter`      |
| Test fake        | `Fake<Noun><Role>Adapter`     | `FakePersistenceAdapter`      |
| Driving port     | `<Action>Port`                | `MessagingPort`               |
| Driving adapter  | `<Tech><Action>Adapter`       | `RabbitMqMessagingAdapter`    |
