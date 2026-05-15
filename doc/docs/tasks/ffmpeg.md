# FFMPEGTask

**Implementation name:** `FFMPEGTask`

Runs `ffmpeg` against the input files in the working directory and emits the declared output files.

## Arguments

| Index | Required | Description                                                                                                                                  |
|-------|----------|----------------------------------------------------------------------------------------------------------------------------------------------|
| 0     | yes      | A single ffmpeg argument string. The plugin parses input filenames (after `-i`) and output filenames (positional, last) from this string.    |
| 1     | yes      | Subprocess timeout in seconds.                                                                                                               |

The plugin prepends `ffmpeg -y -hide_banner` and runs the resulting command inside the task working directory.

## Validation

The plugin checks that:

- Every input filename referenced in argument 0 is present as a file artefact.
- Every provided file artefact is referenced in argument 0.

A mismatch fails the task before ffmpeg is invoked.

## Inputs and outputs

`inputs` and `outputs` must list every file referenced in the argument string. File names in the declarations must
match the filenames inside the argument string exactly.

## Progress reporting

The plugin parses ffmpeg's stderr to report a percentage based on the parsed total duration and the current
encoding position. Each stderr line is also reported as a log message.

## Example

```json
{
  "name": "to-mp3",
  "implementationName": "FFMPEGTask",
  "inputs":  [ { "name": "video.mp4", "type": "FILE" } ],
  "outputs": [ { "name": "audio.mp3", "type": "FILE" } ],
  "arguments": [
    "-i video.mp4 -vn -acodec libmp3lame audio.mp3",
    "30"
  ],
  "timeout": { "amount": 50, "temporalUnit": "SECONDS" }
}
```
