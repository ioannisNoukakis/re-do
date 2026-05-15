# WhisperTranscriptionTask

**Implementation name:** `WhisperTranscriptionTask`

Transcribes a single audio file using an OpenAI-compatible Whisper endpoint and writes the transcript to a file.

## Arguments

| Index | Required | Description                                                       |
|-------|----------|-------------------------------------------------------------------|
| 0     | yes      | Output filename for the transcript.                               |
| 1     | no       | Language code hint passed to the API (for example `en`, `fr`).    |

## Inputs

Exactly one `FILE` artefact (the audio file). Any other count fails the task.

## Outputs

A single `FILE` artefact with the transcript text.

## Environment

| Variable          | Default     | Purpose                                                       |
|-------------------|-------------|---------------------------------------------------------------|
| `OPENAI_API_KEY`  | (required)  | API key. The runner refuses to start the plugin without it.   |
| `OPENAI_BASE_URL` | OpenAI      | Override base URL for a self-hosted, OpenAI-compatible server.|
| `OPENAI_MODEL`    | `whisper-1` | Model name passed to the API.                                 |

## Example

```json
{
  "name": "transcribe",
  "implementationName": "WhisperTranscriptionTask",
  "inputs":  [ { "name": "audio.mp3",     "type": "FILE" } ],
  "outputs": [ { "name": "transcript.txt","type": "FILE" } ],
  "arguments": [ "transcript.txt" ],
  "timeout": { "amount": 300, "temporalUnit": "SECONDS" }
}
```
