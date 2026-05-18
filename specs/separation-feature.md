# Audio Separation

## Status

Implemented/current. Yass Reloaded supports external audio separation through
MVSEP and local audio-separator, with editor, wizard, and selected import flows.

## Purpose

Audio separation creates vocals and instrumental stems for a song and can assign
them to UltraStar audio tags. It supports manual editor workflows and automated
wizard/import workflows while keeping external service/tool details behind a
shared separation interface.

## User-Facing Behavior

- External Tools settings include MVSEP and audio-separator configuration.
- Wizard settings expose a separation preference:
  - local first
  - online first
  - MVSEP only
  - audio-separator only
- Editor separation chooses configured services according to the current
  preference and availability.
- The wizard can run separation before transcription when **Separate Vocals +
  Transcribe** is available.
- USDB import can queue audio separation after import when separation is
  configured.
- Existing `#VOCALS` and `#INSTRUMENTAL` assignments should not be overwritten
  silently when they already point to configured files.

## Core Rules

- `SeparationService` is the shared service interface.
- `SeparationRequest` carries source audio, target directory, model, output
  format, base name, and optional model type.
- `SeparationResult` carries vocals/lead/instrumental/backing files.
- MVSEP requires an API token and performs remote upload, job creation, polling,
  result download, and optional local transcode.
- audio-separator requires a working Python environment and health check.
- Quiet/background separation should report status through a consumer/listener
  and keep the UI responsive.
- Automatic tag assignment uses the preferred instrumental file from
  `SeparationResult` and configured instrumental-default rules.
- Generated file names should be song-local, predictable, and based on the song
  base name.

## Data And Configuration

- MVSEP properties:
  - `mvsep-api-token`
  - `mvsep-model`
  - `mvsep-model-type`
  - `mvsep-output-format`
  - `mvsep-instrumental-default`
  - `mvsep-poll-interval`
- audio-separator properties:
  - `audiosep-python`
  - `audiosep-model`
  - `audiosep-model-dir`
  - `audiosep-output-format`
  - `audiosep-health-ok`
- Shared property:
  - `separation-preference`
- Song tags affected by successful assignment:
  - `#VOCALS`
  - `#INSTRUMENTAL`

## Code Entry Points

- `src/yass/integration/separation/SeparationService.java`
- `src/yass/integration/separation/SeparationRequest.java`
- `src/yass/integration/separation/SeparationResult.java`
- `src/yass/integration/separation/SeparationPreference.java`
- `src/yass/integration/separation/mvsep/MvsepSeparationService.java`
- `src/yass/integration/separation/audioseparator/AudioSeparatorSeparationService.java`
- `src/yass/options/MvsepPanel.java`
- `src/yass/options/AudioSeparatorPanel.java`
- `src/yass/options/WizardPanel.java`
- `src/yass/YassActions.java`
  - configured separation checks
  - editor separation
  - post-wizard application
  - quiet separation for import queue
- `src/yass/wizard/CreateSongWizard.java`
  - wizard separation/transcription flow
- `src/yass/usdb/UsdbImportQueueService.java`
  - queued import separation

## Regression Coverage

- `MvsepSeparationServiceSpec`
  - FFmpeg transcode arguments and intermediate cache handling
- `MvsepOutputFormatSpec`
  - output format parsing and local-transcode metadata
- `MvsepDefaultsSpec`
  - default model/output behavior
- `AudioSeparatorHealthCheckServiceSpec`
  - managed environment and health detection
- `AudioSeparatorModelSpec`
  - local model metadata
- `YassActionsWizardSpec`
  - post-wizard separation prompt behavior
- USDB/import queue behavior has service-level coverage where available; add
  tests before changing queue state transitions.

## Extension Notes

- Keep provider-specific behavior inside provider services. UI/workflow code
  should depend on `SeparationService` and `SeparationResult`.
- Do not silently overwrite valid existing audio tag assignments.
- MVSEP is remote and privacy-sensitive; keep upload/account/status messaging
  explicit.
- audio-separator and WhisperX both use managed Python environments; keep their
  update/cancel/status UX consistent.
