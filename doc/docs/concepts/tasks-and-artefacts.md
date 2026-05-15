# Tasks and artefacts

## Task

A task is a unit of work executed by a runner. It is defined by:

| Field                | Description                                                                |
|----------------------|----------------------------------------------------------------------------|
| `name`               | Unique name within the TEG.                                                |
| `implementationName` | The plugin's implementation key (for example `FFMPEGTask`).                |
| `inputs`             | Declared input artefact definitions (name + type).                         |
| `outputs`            | Declared output artefact definitions (name + type).                        |
| `arguments`          | Plugin-specific positional string arguments.                               |
| `timeout`            | Duration after which the task is considered timed out.                     |

The pair `(name, type)` in `inputs` and `outputs` is a **definition**, not a value. Values are bound at dispatch
time from previously produced artefacts.

## Artefact

An artefact is the value flowing along an edge of the TEG. There are two types:

### `STRING_VALUE`

A small inline string. Carried in messages directly.

```json
{ "name": "language", "type": "STRING_VALUE", "value": "French" }
```

### `FILE`

A file held in object storage. Referenced by an opaque `ref` and the storage backend that owns it.

```json
{
  "name": "audio.mp3",
  "type": "FILE",
  "ref": "dd5fa300-77f2-4e4c-aeaa-d1f3de0eca22",
  "storedWith": "s3"
}
```

The runner downloads `FILE` artefacts into the task's working directory before invoking the plugin, and uploads any
file outputs back to storage afterwards.

## Conformance checks

When a runner returns results, the scheduler verifies the set of produced artefact **names** matches exactly what
the task declared in `outputs`. Any mismatch fails the task.

## Initial artefacts

`initArtefacts` are full artefact values (with `ref` for files, `value` for strings) that you supply at submission
time. They are visible to any starting task whose `inputs` reference them by name.
