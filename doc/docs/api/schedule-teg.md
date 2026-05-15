# Schedule TEG

`POST /api/v1/teg/schedule`

Validates and persists a TEG, then dispatches every task whose inputs are already satisfied.

## Request

```http
POST /api/v1/teg/schedule HTTP/1.1
X-Auth-Principal: "alice"
X-Auth-Roles: "admin"
Content-Type: application/json
```

### Body schema

```json
{
  "tasks":         [ TegTask, ... ],
  "initArtefacts": [ TEGArtefact, ... ]
}
```

`TegTask`:

| Field                | Type                              | Notes                                                  |
|----------------------|-----------------------------------|--------------------------------------------------------|
| `name`               | string                            | Unique within the TEG. Non-blank.                      |
| `implementationName` | string                            | Plugin key, for example `FFMPEGTask`. Non-blank.       |
| `inputs`             | `[ ArtefactDef ]`                 | May be empty.                                          |
| `outputs`            | `[ ArtefactDef ]`                 | May be empty.                                          |
| `arguments`          | `[ string ]`                      | Plugin-specific positional arguments.                  |
| `timeout`            | `{ amount, temporalUnit }`        | `temporalUnit`: `MILLIS`, `SECONDS`, `MINUTES`, `HOURS`, `DAYS`. `amount` must be positive. |

`ArtefactDef`:

| Field | Type                       | Notes                                          |
|-------|----------------------------|------------------------------------------------|
| `name`| string                     | Non-blank.                                     |
| `type`| `STRING_VALUE` or `FILE`   |                                                |

`TEGArtefact` is one of:

```json
{ "name": "...", "type": "STRING_VALUE", "value": "..." }
```

```json
{ "name": "...", "type": "FILE", "ref": "...", "storedWith": "..." }
```

## Response

### 200 OK

```json
{ "tegId": "f1d6c8f4-..." }
```

### 400 Bad Request

Returned for validation failures and TEG semantic errors. See
[Errors](overview.md#errors) for the catalogue.

## Example

A two-step pipeline that fetches an MP4 over HTTP and converts it to MP3.

```json
{
  "tasks": [
    {
      "name": "fetch",
      "implementationName": "HttpFetchTask",
      "inputs": [],
      "outputs": [
        { "name": "src.mp4", "type": "FILE" }
      ],
      "arguments": [
        "https://download.samplelib.com/mp4/sample-5s.mp4",
        "src.mp4"
      ],
      "timeout": { "amount": 120, "temporalUnit": "SECONDS" }
    },
    {
      "name": "convert",
      "implementationName": "FFMPEGTask",
      "inputs":  [ { "name": "src.mp4", "type": "FILE" } ],
      "outputs": [ { "name": "out.mp3", "type": "FILE" } ],
      "arguments": [
        "-i src.mp4 -vn -acodec libmp3lame out.mp3",
        "30"
      ],
      "timeout": { "amount": 60, "temporalUnit": "SECONDS" }
    }
  ],
  "initArtefacts": []
}
```

More examples live in `adapter_driving_scheduler_spring/src/test/api/`.
