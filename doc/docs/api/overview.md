# HTTP API overview

Base URL of the scheduler service: `http://<host>:8080`. All routes are versioned under `/api/v1/`.

## Endpoints

| Method | Path                  | Purpose                                       |
|--------|-----------------------|-----------------------------------------------|
| POST   | `/api/v1/files/upload`| Upload a file to storage. See [Upload file](upload-file.md). |
| POST   | `/api/v1/teg/schedule`| Schedule a TEG. See [Schedule TEG](schedule-teg.md).         |

## Authentication

The service does not authenticate requests itself. It assumes an **auth gateway** at the infrastructure boundary
that validates credentials and forwards two headers:

| Header             | Value                                       |
|--------------------|---------------------------------------------|
| `X-Auth-Principal` | The caller's identifier, for example `"alice"`. |
| `X-Auth-Roles`     | Comma-separated list, for example `"admin,editor"`. |

Both headers are required on every call. Missing headers return `400 Bad Request`.

## Content type

- JSON requests: `Content-Type: application/json`
- File uploads: `Content-Type: multipart/form-data` with a `file` part

## Errors

All errors return JSON of shape:

```json
{
  "cause": "Human-readable description of the failure",
  "errorId": "Sentry event id, only present for unexpected errors"
}
```

| Status | Returned for                                                              |
|--------|---------------------------------------------------------------------------|
| 400    | Validation failures and `TegSchedulingError` variants (see below).        |
| 500    | Uncaught exceptions. `errorId` references the Sentry event.               |

### Scheduling errors

| `cause` example                                                                | Reason                                       |
|--------------------------------------------------------------------------------|----------------------------------------------|
| `Empty Task Execution Graphs are not allowed`                                  | `tasks` was empty.                           |
| `No starting task found in the Task Execution Graph`                           | No task has all inputs satisfied at submit.  |
| `Missing producer for artefact 'X' required by task 'Y'`                       | Input has no matching output or init artefact. |
| `Cyclic dependency detected among tasks: a -> b -> a`                          | The graph is cyclic.                         |
| `Multiple tasks have the same name 'X'`                                        | Duplicate task name.                         |
| `Multiple tasks produce the same artefact 'X': a, b`                           | Duplicate output artefact name.              |

## Auth gateway responsibilities

The gateway should also enforce **upload size limits** (typically via the reverse proxy, for example
`client_max_body_size` in nginx). The service intentionally does not duplicate this policy.
