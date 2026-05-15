# Tasks

A **task** is invoked by the runner through a plugin that implements `TaskHandler`. Plugins are packaged as
self-contained shadow jars and loaded from `task_handler_plugins/` at runner startup.

## Built-in plugins

| Implementation name           | Purpose                                                     | Docs                                    |
|-------------------------------|-------------------------------------------------------------|-----------------------------------------|
| `DemoEchoTask`                | Renames inputs to `*-processed` for demos.                  | [Demo echo](demo-echo.md)               |
| `HttpFetchTask`               | Download a file from an HTTP URL.                           | [HTTP fetch](http-fetch.md)             |
| `FFMPEGTask`                  | Run an `ffmpeg` command line.                               | [FFmpeg](ffmpeg.md)                     |
| `WhisperTranscriptionTask`    | Speech-to-text via an OpenAI-compatible Whisper endpoint.   | [Whisper transcription](whisper-transcription.md) |
| `LlmTranslateTask`            | Translate text via an OpenAI-compatible chat completion API.| [LLM translate](llm-translate.md)       |
| `LlmSummariseTask`            | Summarise text via an OpenAI-compatible chat completion API.| [LLM summarise](llm-summarise.md)       |

## Adding your own

See [Writing a task plugin](writing-a-task-plugin.md).

## Plugin contract

```kotlin
interface TaskHandler {
    fun run(
        artefacts: List<LocalTegArtefact>,
        arguments: List<String>,
        context: TaskExecutionContext,
    ): TaskImplementationResult

    fun implementationName(): String
}
```

The runner:

1. Downloads `FILE` artefacts into a fresh working directory.
2. Calls `run` with the local artefact list, arguments, and an execution context.
3. Uploads any file outputs and returns the result to the scheduler.

Plugins should be **deterministic** and **side-effect free outside the working directory**. Reporting progress and
logs is done via the `TaskExecutionContext`.
