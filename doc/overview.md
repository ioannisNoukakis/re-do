---
marp: true
theme: default
paginate: true
style: |
  section {
    font-family: 'Segoe UI', sans-serif;
  }
  h1 { color: #1a56db; }
  h2 { color: #1e429f; border-bottom: 2px solid #1e429f; padding-bottom: 0.2em; }
  code { background: #f3f4f6; padding: 2px 6px; border-radius: 4px; }
  pre { background: #1e293b; color: #e2e8f0; }
  .columns { display: grid; grid-template-columns: 1fr 1fr; gap: 2em; }
  .tag { background: #dbeafe; color: #1e40af; padding: 2px 10px; border-radius: 12px; font-size: 0.8em; }
---

# re-do

### Distributed Task Execution Graph Scheduler

A platform for orchestrating directed acyclic graphs of tasks — where each task consumes artefacts
produced by its predecessors.

---

## What is a TEG?

A **Task Execution Graph (TEG)** is a directed acyclic graph (DAG) of tasks.

```
[Upload audio] ──► [Transcribe (Whisper)] ──► [Translate (Gemma)] ──► [Summarise (LLM)]
                                          └──► [Archive to S3]
```

- Each **task** declares its inputs and outputs as **artefacts**
- Artefacts can be files or string values
- A task is scheduled only when all its inputs are available
- The scheduler dispatches tasks to runners via RabbitMQ

---

## Core Concepts

| Concept | Description |
|---------|-------------|
| **TEG** | The full graph — submitted via HTTP API, tracked in MongoDB |
| **Task** | One node in the graph; has inputs, outputs, and a plugin handler |
| **Artefact** | A named file or value produced by a task and consumed by another |
| **Runner** | A worker that picks up a dispatched task, runs the plugin, reports back |
| **Task plugin** | A `TaskHandler` implementation loaded in-process by the runner |

---

## System Architecture

```
  Client
    │  POST /api/v1/teg/schedule
    ▼
┌─────────────┐                    ┌─────────────┐
│  Scheduler  │                    │   Runner    │
│  (Spring)   │                    │  (Spring)   │
└──────┬──────┘                    └──────┬──────┘
       │  RabbitMQ dispatch               │ RabbitMQ result
       └────────────────────────────────►─┘
                                          │
                                    Task plugins
                                 (FFmpeg, Whisper, LLM…)
                                          │
                                    S3-compatible
                                     file store
```

---

## Hexagonal Architecture

The domain never depends on adapters. Dependency arrows always point **inward**.

```
[ Driving adapter ]  →  [ Use case ]  →  [ Driven port interface ]
  HTTP controller         TEGScheduler       PersistencePort
  RabbitMQ consumer       TaskRunner         MessagingPort
                                             FileStoragePort
                                                    ↑
                                         [ Driven adapter ]
                                          MongoDB / S3 / RabbitMQ
```

> Policy logic lives in use cases. Technology concerns live in adapters.

---

## Task Plugins

| Plugin | Description |
|--------|-------------|
| `DemoEchoTask` | Echo — useful for testing the full flow |
| `FFMPEGTask` | Video/audio processing |
| `HttpFetchTask` | HTTP fetch as a task |
| `WhisperTranscriptionTask` | Speech-to-text via OpenAI Whisper |
| `LlmSummariseTask` | Text summarisation via Gemma / GPT |
| `LlmTranslateTask` | Multilingual translation via Gemma |

Plugins are loaded **in-process** by the runner. No config required for demo/FFmpeg/HTTP plugins.

---

## HTTP API

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/teg/schedule` | POST | Submit a TEG for execution |
| `/api/v1/teg/{tegId}/events` | GET | SSE stream of TEG events |
| `/api/v1/files/upload` | POST | Upload a file artefact |
| `/v3/api-docs` | GET | OpenAPI 3 spec (JSON) |
| `/swagger-ui.html` | GET | Interactive API explorer |

Auth is handled by an upstream gateway — `X-Auth-Principal` and `X-Auth-Roles` headers are expected.

---

## SSE Event Stream

`GET /api/v1/teg/{tegId}/events`

- Returns `text/event-stream`
- Replays full event history, then continues live
- Closes after a terminal event: `NoMoreTasksToSchedule` or `TEGFailed`
- `X-Auth-Principal` must match the TEG submitter (403 otherwise)

---

## Testing Philosophy

<div class="columns">

**What we do**
- Hand-written test doubles (Stub, Fake, Spy)
- One assertion per test
- Assert by deep equality
- TDD: failing test first
- `*SutBuilder` wires the SUT

**What we don't do**
- No mocking libraries
- No asserting individual fields
- No framework magic in unit tests

</div>

> "No mocks" is a hard rule — not a preference.

---

## Infrastructure Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin + Java 21 |
| Framework | Spring Boot 3.x |
| Messaging | RabbitMQ / AMQP |
| Persistence | MongoDB (event-sourced via `TEGEvent`) |
| File storage | S3-compatible (RustFS in demo) |
| Error handling | Arrow-kt `Either` monad |
| Tests | JUnit 5 + TestContainers |

---

## Roadmap

**Done**
- Full TEG execution infrastructure
- File artefacts + string values
- MongoDB state, RabbitMQ messaging, S3 storage
- FFmpeg, Whisper, LLM summarise/translate plugins
- OpenAPI + SSE event stream

**Planned**
- Fine-grained progress events from runners
- Prometheus + Grafana integration
- Monolith mode (no external dependencies for local dev)

---

# Thank you

**re-do** — task execution graphs, batteries included.

