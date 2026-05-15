# Writing a task plugin

A task plugin is a Kotlin module that implements `TaskHandler`, declares itself via the JDK `ServiceLoader`
mechanism, and ships as a shadow jar.

## 1. Create the module

Add a new `task_impl_<name>` directory and register it in `settings.gradle.kts`:

```kotlin
include(
    // ...
    "task_impl_mything",
)
```

Add a minimal `build.gradle.kts`:

```kotlin
plugins {
    id("buildlogic.kotlin-task-plugin-conventions")
}
```

The convention plugin applies the Kotlin common configuration, the Shadow plugin (for fat-jar packaging) and a
dependency on `core`.

## 2. Implement `TaskHandler`

```kotlin
class MyThingTask : TaskHandler {
    override fun implementationName() = "MyThingTask"

    override fun run(
        artefacts: List<LocalTegArtefact>,
        arguments: List<String>,
        context: TaskExecutionContext,
    ): TaskImplementationResult {
        context.reportProgress(0, "STARTING")
        val workingDir = context.workingDir()
        // ... do the work, write outputs to workingDir ...
        context.reportProgress(100, "DONE")
        return TaskImplementationResult.Success(
            listOf(LocalTegArtefact.LocalTegArtefactFile("output.txt", workingDir.resolve("output.txt"))),
        )
    }
}
```

Return `TaskImplementationResult.Failure(reason)` for any error you can describe.

## 3. Register via ServiceLoader

Create the file
`src/main/resources/META-INF/services/me.noukakis.re_do.runner.port.TaskHandler` containing the fully qualified
class name:

```
com.example.MyThingTask
```

The runner discovers plugins through this file. Without it the jar will not register.

## 4. Build and deploy

```bash
./gradlew :task_impl_mything:shadowJar
cp task_impl_mything/build/libs/*.jar task_handler_plugins/
```

Restart the runner. On startup it logs every handler it loads and the implementation name it registers.

## Working directory

Each task call gets a fresh temp directory accessible via `context.workingDir()`. File inputs are already present
there with their declared names. Anything you write must be inside this directory; outputs are referenced by their
file path relative to it.

## Reporting

| Method                                  | Effect                                              |
|-----------------------------------------|-----------------------------------------------------|
| `context.reportProgress(percent, step)` | Emits a `Progress` event for the scheduler.         |
| `context.reportLog(line)`               | Emits a `Log` event for the scheduler.              |

Progress percentages should be in `[0, 100]`. Step labels are free-form strings.

## Don'ts

- Do not call out to RabbitMQ, MongoDB, or any re-do internal port. The runner orchestrates that.
- Do not write outside the working directory.
- Do not perform retries or backoff. The scheduler handles retries based on `timeout` and the global retry budget.
