# LlmSummariseTask

**Implementation name:** `LlmSummariseTask`

Summarises the concatenation of an optional inline text argument and any text inputs, using an OpenAI-compatible
chat completion API.

## Arguments

| Index | Required | Description                                                       |
|-------|----------|-------------------------------------------------------------------|
| 0     | yes      | Output filename.                                                  |
| 1     | no       | Inline text to summarise, prepended to artefact contents.         |

## Inputs

Any number of artefacts. Files are read as UTF-8 text; string-value artefacts are used directly. Empty content
fails the task.

## Outputs

A single `FILE` artefact with the summary.

## Environment

| Variable                                | Default                                                          |
|-----------------------------------------|------------------------------------------------------------------|
| `LLM_SUMMARISE_SYSTEM_PROMPT`           | A built-in concise-summary prompt.                               |
| `LLM_SUMMARISE_MODEL`                   | `gpt-4o-mini`                                                    |
| `LLM_SUMMARISE_MAX_TOKENS`              | `1024`                                                           |
| `LLM_SUMMARISE_TEMPERATURE`             | `0.2`                                                            |
| `LLM_SUMMARISE_CONTEXT_WINDOW_TOKENS`   | `128000`                                                         |
| `LLM_SUMMARISE_TIMEOUT_SECONDS`         | `120`                                                            |

Backend credentials are taken from `OPENAI_API_KEY` and `OPENAI_BASE_URL` (the LangChain4j defaults).

## Example

```json
{
  "name": "summarise",
  "implementationName": "LlmSummariseTask",
  "inputs":  [ { "name": "transcript.txt", "type": "FILE" } ],
  "outputs": [ { "name": "summary.txt",    "type": "FILE" } ],
  "arguments": [ "summary.txt" ],
  "timeout": { "amount": 300, "temporalUnit": "SECONDS" }
}
```
