# HttpFetchTask

**Implementation name:** `HttpFetchTask`

Downloads a single file from an HTTP or HTTPS URL and emits it as a file artefact.

## Arguments

| Index | Required | Description                                                                     |
|-------|----------|---------------------------------------------------------------------------------|
| 0     | yes      | URL to fetch. Must be `http` or `https`.                                        |
| 1     | yes      | Output filename for the produced artefact.                                      |
| 2+    | no       | Optional request header name/value pairs (must come in pairs).                  |

## Inputs

None.

## Outputs

A single `FILE` artefact whose `name` equals argument 1.

## Safety defaults

- Maximum response size: 512 MB.
- Maximum redirects: 5.
- Connect timeout: 10 s; read timeout: 60 s.
- Hosts resolving to private or internal addresses are **rejected** by default.
- Restricted headers (`host`, `content-length`, ...) are silently dropped.

## Example

```json
{
  "name": "fetch-source",
  "implementationName": "HttpFetchTask",
  "inputs": [],
  "outputs": [ { "name": "fetched.mp4", "type": "FILE" } ],
  "arguments": [
    "https://download.samplelib.com/mp4/sample-5s.mp4",
    "fetched.mp4"
  ],
  "timeout": { "amount": 120, "temporalUnit": "SECONDS" }
}
```
