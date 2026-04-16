# Architecture

This document defines the architectural conventions for this project. All contributors are expected to follow them.
When in doubt, existing code is the reference — not personal preference.

---

## Table of contents

1. [Hexagonal architecture](#hexagonal-architecture)
2. [Project layout](#project-layout)
3. [Ports and adapters](#ports-and-adapters)
4. [Use cases](#use-cases)
5. [Test-driven development](#test-driven-development)
6. [Test doubles](#test-doubles)

---

## Hexagonal architecture

The codebase is structured around a strict boundary between **business logic** and **infrastructure**. The rule is
simple:
the domain and use cases know nothing about the outside world. They depend only on abstractions (ports).
Everything that touches the outside world is an adapter.

There are two kinds of ports:

**Driving ports** are the entry points into the application. They initiate use cases. Examples: an HTTP
controller that handles an incoming API request, a CLI entry point, a message consumer. Driving ports call use cases.

**Driven ports** are the dependencies that use cases call out to. Examples: a database, an HTTP client to an external
service, a cache, a filesystem. Use cases depend on driven port interfaces; adapters implement those interfaces.

```
[ Driving adapter ] → [ Use case ] → [ Driven port interface ]
                                              ↑
                                    [ Driven adapter (real or test double) ]
```

The dependency arrow always points inward. Adapters depend on the domain; the domain never depends on adapters.

---

## Project layout

The source tree is divided into four areas:

- **domain** — pure domain objects with no I/O and no framework dependencies.
- **use_cases/services** — one file or module per use case.
- **ports** — interface definitions (contracts), split into `driving` and `driven` sub-areas.
- **adapters** — concrete implementations of those interfaces, also split into `driving` and `driven`.

Tests mirror this structure, with separate areas for use case tests and adapter integration tests.

---

## Ports and adapters

### Naming convention

| Thing                    | Pattern                     | Example                        |
|--------------------------|-----------------------------|--------------------------------|
| Driven port (interface)  | `<Noun><Role>Port`          | `EntitlementStorePort`         |
| Real adapter             | `<Tech><Noun><Role>Adapter` | `RedisEntitlementStoreAdapter` |
| Test double (stub)       | `Stub<Noun><Role>Adapter`   | `StubEntitlementStoreAdapter`  |
| Test double (spy/fake)   | `Fake<Noun><Role>Adapter`   | `FakeEntitlementStoreAdapter`  |
| Driving port (interface) | `<Action>Port`              | `RequestVerifierPort`          |
| Driving adapter          | `<Tech><Action>Adapter`     | `HttpRequestVerifierAdapter`   |

### Defining a port

A port is an interface or abstract contract. It defines the operations the use case needs and nothing more — no
implementation detail, no technology assumption.

### Implementing a real adapter

Adapters implement the port interface. They are allowed to raise infrastructure errors (e.g. a connection failure, a
constraint violation). They are **not** allowed to contain business logic.

The guiding question when deciding where a concern belongs is: *would this check make sense in a different storage or
transport technology?* If yes, it belongs in the use case. If it is specific to how a particular technology enforces a
constraint, it belongs in the adapter.

### What belongs where

| Concern                                         | Lives in                             |
|-------------------------------------------------|--------------------------------------|
| Is this principal allowed by policy?            | Use case                             |
| Is this entitlement key missing from the store? | Use case (interprets the absence)    |
| Key does not exist → return empty result        | Adapter                              |
| Connection refused                              | Adapter raises infrastructure error  |
| Unique constraint violated on write             | Adapter raises duplicate entry error |
| A token is expired                              | Use case (reads the expiry claim)    |
| A token signature is invalid                    | Adapter raises invalid token error   |

---

## Use cases

Each use case is a unit of application behaviour with a single public entry point (conventionally called `execute`). It
receives its driven port dependencies via constructor injection. It never imports an adapter directly.

A use case accepts a **command object** (a plain value object carrying the input data) and returns a **result object** (
a plain value object carrying the output). Errors are signalled by raising domain exceptions, never by returning error
codes or null values.

---

## Test-driven development

Tests are written **before** the code they exercise. The cycle is: write a failing test → write the minimum code to make
it pass → refactor.

### Rules

**No mocks.** Framework-generated mock objects (e.g. anything produced by a mocking library at runtime) are banned. They
couple tests to implementation details and produce tests that pass even when behaviour is wrong. Use hand-written test
doubles instead (see below).

**One assertion per test.** Each test verifies one thing. If a use case has three distinct failure conditions, that is
three tests. The test name must state the exact condition being verified.

**Assert by deep equality.** Do not assert individual fields on a result. Construct the full expected object and compare
it to the actual object. This makes tests resilient to field additions and immediately readable.

---

## Test doubles

A test double is a hand-written class that implements a port interface and is used exclusively in tests. There are three
kinds used in this project.

### Stub

A stub returns a pre-configured response. Use it to control what a dependency returns to the use case under test. When a
stub should simulate a failure, it raises the appropriate domain exception directly rather than returning a sentinel
value.

### Fake

A fake has a working in-memory implementation. Use it when the test needs to verify the state of a driven port after the
use case has run — typically for write operations. Assert against the full collection of written state, not individual
items.

### Spy

A spy records the calls made to it. Use it when you need to verify that the use case called a dependency with the right
arguments, without caring about the return value.

Spies are used sparingly. Prefer asserting on output over asserting on collaboration. Only reach for a spy when the
collaboration itself is the contract being tested — for example, verifying that an authorizer was called with the
correct geo-context.

---

## Putting it together

When writing a test for a use case, replace every dependency the use case cannot control with the simplest test double
that satisfies the port contract:

- If you do not care what a dependency returns for this particular scenario, use a **stub** configured to return a
  permissive or neutral value.
- If you need to pre-seed state that the use case will read, use a **fake** initialised with that state.
- If you need to verify what arguments were passed to a dependency, use a **spy**.

Each test should have exactly one reason to fail: the specific behaviour named in its title.