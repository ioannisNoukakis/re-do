# Testing rules

The project follows four hard rules. They are enforced in review.

## 1. No mocking libraries

No Mockito, no MockK, no `mock()` call sites. Test doubles are **hand-written** classes that implement the port
interface.

There are three flavours:

- **Stub**: returns a pre-configured value, or raises a domain exception to simulate failure.
- **Fake**: a working in-memory implementation. Use it when the test asserts on state after a write.
- **Spy**: records calls. Use it only when the collaboration itself is the contract (sparingly).

All test doubles live under `core/src/test/.../adapters/driven/` and follow the `Stub*`, `Fake*`, `Spy*` naming
convention.

## 2. One assertion per test

Three failure conditions means three separate tests. Each test name states the exact condition.

## 3. Assert by deep equality

Construct the full expected object and compare it. Do not assert on individual fields. This keeps tests resilient
when new fields are added and makes the test self-documenting.

```kotlin
// Good
assertEquals(
    expected = TEGEvent.Failed(taskName = "t1", timestamp = now, reason = "boom"),
    actual = persisted.single(),
)

// Bad
assertEquals("t1", persisted.single().taskName)
assertEquals(now, persisted.single().timestamp)
```

## 4. TDD cycle

Failing test, then minimum code to pass, then refactor. Tests are written **before** the production code they
exercise.

## SUT builders

`core/src/test` contains `*SutBuilder` classes that wire each use case with appropriate doubles. Use them rather
than constructing the SUT directly in every test, so a port-signature change does not ripple through hundreds of
test files.

## Where each concern lives

The guiding question is: *would this check make sense in a different storage or transport technology?*

| Concern                                | Lives in                                 |
|----------------------------------------|------------------------------------------|
| Policy / business rule                 | Use case                                 |
| Validation of a domain invariant       | Use case                                 |
| Connection refused, network failure    | Adapter (raises an infrastructure error) |
| DB unique-constraint violation         | Adapter (raises a domain exception)      |
| Token expired (semantic check)         | Use case                                 |
| Token signature invalid (mechanical)   | Adapter                                  |
