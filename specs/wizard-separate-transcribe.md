# Wizard Separation And Transcription

## Status

Implemented/current. The Create Song Wizard can separate vocals and transcribe
them during the lyrics step, then carry generated assets into the final song
folder.

## Purpose

The wizard can automate a strong first draft: download/prepare audio, separate
vocals, transcribe the vocal stem, populate lyrics, and later generate notes
from transcript timing instead of only from plain lyric splitting.

## User-Facing Behavior

- The lyrics step can show **Separate Vocals + Transcribe**.
- The button is hidden or disabled when prerequisites are missing.
- The unavailable tooltip/message explains the missing prerequisite:
  - no audio source
  - no configured separation provider
  - WhisperX health check required for local transcription
- The workflow shows a modal progress dialog with cancel support.
- Existing completed wizard separation/transcription runs can be reused.
- After completion, lyrics are populated and wizard status indicates ready
  transcript state.
- On wizard finish, separated stems and transcript artifacts are copied into the
  final song directory and applied to the song.

## Core Rules

- The wizard uses `WizardTranscriptionState` to carry:
  - run directory
  - source audio
  - converted source WAV
  - `SeparationResult`
  - `OpenAiTranscriptionResult`
- Reuse is allowed only when persisted metadata matches the current source/base
  song identity, including YouTube id where applicable.
- Separation-only persisted state can skip re-separation but still needs
  transcription before lyrics are ready.
- Audio is converted to WAV for separation/transcription when needed.
- The selected separation provider follows `separation-preference`.
- Transcription uses OpenAI or WhisperX depending on configured availability and
  flow rules.
- Wizard finish saves `yass-transcript.json` and copies relevant transcript
  cache/debug files.
- Rebuilt notes can be aligned to detected melody in create-wizard context so
  detected octave is preserved.

## Data And Configuration

- Temp workspace:
  - `.yass` temp base
  - `wizard-separation` run directories
  - persisted wizard run metadata
- Final song artifacts:
  - copied vocal/instrumental stems
  - `yass-transcript.json`
  - copied transcript cache where available
- Uses separation and transcription properties from their respective settings
  panels.

## Code Entry Points

- `src/yass/wizard/CreateSongWizard.java`
  - button availability
  - `startSeparateAndTranscribeFromLyrics()`
  - persisted run reuse
  - separation/transcription worker
  - applying `WizardTranscriptionState`
- `src/yass/wizard/WizardTranscriptionState.java`
- `src/yass/YassActions.java`
  - `applyWizardTranscriptionOutputs(...)`
  - post-wizard separation prompt
  - final song folder asset application
- `src/yass/alignment/TranscriptNoteRebuildService.java`
  - transcript-as-truth note creation
- `src/yass/integration/separation/*`
  - separation providers
- `src/yass/integration/transcription/*`
  - OpenAI/WhisperX transcript providers and artifacts

## Regression Coverage

- `YassActionsWizardSpec`
  - post-wizard separation offer rules
  - transcript artifact lookup
  - prompt text when transcript artifact exists
- `TranscriptNoteRebuildServiceSpec`
  - transcript-as-truth note generation
- `LyricsAlignmentServiceSpec`
  - real transcript fixture rebuild behavior
- `WhisperXHealthCheckServiceSpec`
  - local transcription availability assumptions
- Add focused wizard worker tests before changing reuse/cancel/final-copy
  behavior.

## Extension Notes

- Keep wizard temp reuse strict. Reusing a stale run is worse than redoing work.
- Do not show YouTube URL inputs if yt-dlp is not available; this belongs to the
  broader wizard availability model.
- Keep cancellation cooperative: external processes may need explicit cancel or
  interruption handling.
- Apply generated outputs only once and avoid overwriting user-selected stems
  without a clear decision.
