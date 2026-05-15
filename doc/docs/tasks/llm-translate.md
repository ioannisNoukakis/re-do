# LlmTranslateTask

**Implementation name:** `LlmTranslateTask`

Translates the concatenation of an optional inline text argument and any text inputs into a target language,
using an OpenAI-compatible chat completion API.

## Arguments

| Index | Required | Description                                                                                |
|-------|----------|--------------------------------------------------------------------------------------------|
| 0     | yes      | Output filename.                                                                           |
| 1     | yes      | Target language. Must match the regex `^[A-Za-z][A-Za-z \-]{0,39}$` (for example `French`).|
| 2     | no       | Inline text to translate, prepended to artefact contents.                                  |

## Inputs

Any number of artefacts. Files are read as UTF-8 text; string-value artefacts are used directly. Empty content
fails the task.

## Outputs

A single `FILE` artefact with the translated text.

## Environment

| Variable                                   | Default                                  |
|--------------------------------------------|------------------------------------------|
| `LLM_TRANSLATE_SYSTEM_PROMPT_TEMPLATE`     | A built-in template; `%s` is replaced with the target language. |
| `LLM_TRANSLATE_MODEL`                      | `gpt-4o-mini`                            |
| `LLM_TRANSLATE_MAX_TOKENS`                 | `2048`                                   |
| `LLM_TRANSLATE_TEMPERATURE`                | `0.2`                                    |
| `LLM_TRANSLATE_CONTEXT_WINDOW_TOKENS`      | `128000`                                 |
| `LLM_TRANSLATE_TIMEOUT_SECONDS`            | `120`                                    |

Backend credentials are taken from `OPENAI_API_KEY` and `OPENAI_BASE_URL` (the LangChain4j defaults).

## Long inputs

The plugin uses a chunking LLM client. Inputs larger than `contextWindowTokens` are split, translated chunk by
chunk, and concatenated.

## Example

```json
{
  "name": "translate-to-french",
  "implementationName": "LlmTranslateTask",
  "inputs":  [ { "name": "transcript.txt",  "type": "FILE" } ],
  "outputs": [ { "name": "translation.txt", "type": "FILE" } ],
  "arguments": [ "translation.txt", "French" ],
  "timeout": { "amount": 300, "temporalUnit": "SECONDS" }
}
```
