# Contributing

## Workflow

1. Check open issues for the most up-to-date roadmap.
2. Branch from `main`. Keep changes scoped to one concern.
3. Write tests first (see [Testing](architecture/testing.md)).
4. Run the full build before opening a merge request.

```bash
./gradlew spotlessApply
./gradlew build
```

CI runs `gradle spotlessCheck`, `gradle test`, a secret scan, and `gradle integrationTest` for merge requests and
default-branch builds.

## Code style

- Kotlin, formatted by Spotless.
- Follow the naming convention in [Modules](architecture/modules.md).
- Domain code does not import adapter packages.
- Use cases accept a command, return a result, and signal errors via `Either<DomainError, T>` (Arrow).

## Tests

The four rules in [Testing](architecture/testing.md) are enforced in review:

1. No mocking libraries.
2. One assertion per test.
3. Assert by deep equality.
4. TDD cycle: failing test first.

## Adding a new task plugin

See [Writing a task plugin](tasks/writing-a-task-plugin.md). Include a unit test using a `SpyTaskExecutionContext`
to assert on progress and log reports, and a validation test for argument parsing.

## Adding a new adapter

1. Create the adapter module (or extend an existing one) following the naming convention.
2. Implement the port interface; raise domain exceptions for technology-specific failures.
3. Wire the adapter in the matching Spring `Configuration` class with a `@ConditionalOnProperty` if it is an
   alternative to an existing adapter.
4. Add an integration test (`integrationTest` source set, TestContainers if needed).
