# DemoEchoTask

**Implementation name:** `DemoEchoTask`

A no-op plugin used to exercise the full TEG flow without doing any real work. For every input artefact it emits
an output with the same content and a `-processed` suffix appended to the name.

## Arguments

Ignored. Anything may be passed.

## Inputs and outputs

For every input artefact `foo`, the task produces `foo-processed` of the same type. Declare outputs to match.

## Example

```json
{
  "name": "echo",
  "implementationName": "DemoEchoTask",
  "inputs": [
    { "name": "video.mp4", "type": "FILE" },
    { "name": "label",     "type": "STRING_VALUE" }
  ],
  "outputs": [
    { "name": "video.mp4-processed", "type": "FILE" },
    { "name": "label-processed",     "type": "STRING_VALUE" }
  ],
  "arguments": [ "one", "two" ],
  "timeout": { "amount": 20, "temporalUnit": "SECONDS" }
}
```
