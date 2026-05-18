# WhisperX Alignment

## Status

Implemented/current. WhisperX is the local transcription engine for the shared
transcription-alignment workflow and includes health checks, package update, and
VRAM-based auto runtime recommendations.

## Purpose

WhisperX lets users generate timestamped transcripts locally for note alignment
and wizard transcription. It avoids OpenAI API cost and can run offline when the
local environment is configured.

## User-Facing Behavior

- External Tools / Transcription exposes WhisperX settings:
  - Python executable
  - module invocation toggle
  - command override
  - model
  - device
  - compute type
  - cache folder
  - health check
  - package update/cancel button
- `Auto` is available for model, device, and compute type.
- Health check reports Python, WhisperX, FFmpeg, GPU name, VRAM, MPS/CUDA
  status, recommendation, applied values, and reason.
- Auto-apply only changes fields that are set to `auto`; manual choices are not
  overwritten.
- During transcription, WhisperX output is streamed into progress UI.
- If float16 fails on the current backend, transcription retries with CPU/int8.

## Core Rules

- Health check probes Python, WhisperX availability/version, FFmpeg, CUDA,
  VRAM, and MPS where possible.
- Runtime recommendations prioritize stability:
  - CUDA with at least 12288 MiB VRAM: `large-v3`, `cuda`, `float16`
  - CUDA with 8192-12287 MiB VRAM: `medium`, `cuda`, `float16`
  - CUDA with 6144-8191 MiB VRAM: `small`, `cuda`, `float16`
  - CUDA below 6144 MiB: `small`, `cpu`, `int8`
  - MPS without CUDA: `medium`, `mps`, `float32`
  - no usable telemetry: `small`, `cpu`, `int8`
- Effective values are stored separately and used only when the visible setting
  is `auto`.
- WhisperX JSON is converted into `OpenAiTranscriptionResult`.
- Opus input is converted to temporary WAV before WhisperX if needed.
- Cache lookup happens before running WhisperX.
- Advanced WhisperX flags are intentionally reserved in code but not exposed in
  UI yet.

## Data And Configuration

- Properties:
  - `whisperx-python`
  - `whisperx-use-module`
  - `whisperx-command`
  - `whisperx-model`
  - `whisperx-device`
  - `whisperx-compute-type`
  - `whisperx-effective-model`
  - `whisperx-effective-device`
  - `whisperx-effective-compute-type`
  - `whisperx-cache-folder`
  - `whisperx-health-ok`
- Managed environment:
  - `%USERPROFILE%\.yass\whisperx-venv`
- Default cache folder:
  - `.yass-cache`

## Code Entry Points

- `src/yass/integration/transcription/whisperx/WhisperXHealthCheckService.java`
  - health check
  - VRAM/MPS/CUDA telemetry
  - package update
  - runtime recommendation
- `src/yass/integration/transcription/whisperx/WhisperXHealthCheckResult.java`
- `src/yass/integration/transcription/whisperx/WhisperXTranscriptionService.java`
  - request execution
  - command building
  - cache loading
  - CPU/int8 fallback
  - JSON conversion
- `src/yass/integration/transcription/whisperx/WhisperXModel.java`
- `src/yass/integration/transcription/whisperx/WhisperXDevice.java`
- `src/yass/integration/transcription/whisperx/WhisperXComputeType.java`
- `src/yass/options/WhisperXPanel.java`
  - settings UI, health check, update/cancel, auto-apply
- `src/yass/YassActions.java`
  - editor transcription flow
- `src/yass/wizard/CreateSongWizard.java`
  - wizard transcription flow

## Regression Coverage

- `WhisperXHealthCheckServiceSpec`
  - VRAM recommendation matrix
  - auto/effective value resolution
  - manual choices not overridden
  - managed venv path
- `WhisperXTranscriptionServiceSpec`
  - timeout classification and messages
- `LyricsAlignmentServiceSpec`
  - WhisperX transcript fixtures for alignment/rebuild

## Extension Notes

- Keep the stable fallback `small/cpu/int8` for weak or unknown hardware.
- Do not auto-change manual model/device/compute selections.
- If adding beam/VAD options, keep them in advanced flags and test command
  construction separately from UI.
- Package update must remain cancelable and must stream progress; long pip logs
  are expected.
