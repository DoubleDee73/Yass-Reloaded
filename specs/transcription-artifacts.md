# Transcription Artifacts

## Status

Implemented/current. Yass persists canonical transcript artifacts and can reuse
them across wizard and editor flows.

## Purpose

Lyrics and timing can come from LRCLib, OpenAI, WhisperX, or downloaded
subtitles. Yass normalizes those sources into one transcript model so wizard and
editor workflows can reuse text and timing without caring where the data
originated.

## User-Facing Behavior

- The create-song wizard can save `yass-transcript.json` into the final song
  directory when transcript data exists.
- The editor looks for `yass-transcript.json` before asking the user to create a
  fresh OpenAI/WhisperX transcript.
- Post-wizard separation prompts mention reusable transcript data when the
  artifact is present.
- LRCLib lyrics can be compared against an existing transcript and can replace
  transcript text while preserving timing.
- Obsolete transcript pseudo-tags are removed from `#COMMENT`; transcript
  provenance is not duplicated in the song header.

## Core Rules

- `OpenAiTranscriptionResult` is the canonical in-memory transcript model even
  for LRCLib, subtitles, and WhisperX.
- Text and timing sources are tracked separately:
  - LRCLib is preferred for clean text when selected.
  - OpenAI/WhisperX word timestamps are preferred for timing.
  - Subtitles are fallback text/timing when no better transcript exists.
- `yass-transcript.json` is the stable song-local artifact.
- Transient OpenAI/WhisperX cache files may still exist for debugging or reuse,
  but editor reuse should prefer the canonical artifact.
- Subtitle cues are converted into transcript segments and word timestamps by
  distributing cue duration across tokens.
- Rolling YouTube captions are collapsed before conversion.
- Vocal-aware refinement can adjust transcript timing before alignment when a
  vocal pitch/energy signal is close enough to transcript anchors.
- If no vocal pitch is found within roughly +/-5 seconds around the expected
  first lyric start, alignment can search from the beginning for the first
  relevant vocal pitch instead of giving up immediately.

## Data And Configuration

- Canonical artifact filename: `yass-transcript.json`
- WhisperX/OpenAI cache folder defaults to `.yass-cache`.
- Removed `#COMMENT` pseudo-tags:
  - `transcriptText`
  - `transcriptTiming`
  - `transcriptSource`

## Code Entry Points

- `src/yass/integration/transcription/TranscriptArtifactService.java`
  - save/load `yass-transcript.json`
- `src/yass/integration/transcription/TranscriptSourceComment.java`
  - removes obsolete transcript pseudo-tags from `#COMMENT`
- `src/yass/integration/transcription/SubtitleTranscriptionAdapter.java`
  - subtitle cue to transcript conversion
- `src/yass/alignment/TranscriptTruthRewriteService.java`
  - applies corrected/clean lyric text to transcript structure
- `src/yass/alignment/TranscriptTimingRefinementService.java`
  - vocal-aware transcript timing refinement
- `src/yass/YassActions.java`
  - editor reuse and post-wizard transcript handling
- `src/yass/wizard/CreateSongWizard.java`
  - wizard transcript creation, LRCLib integration, artifact persistence

## Regression Coverage

- `TranscriptArtifactServiceSpec`
  - save/load canonical transcript JSON
- `TranscriptSourceCommentSpec`
  - removes obsolete transcript pseudo-tags while preserving other comments
- `SubtitleTranscriptionAdapterSpec`
  - converts subtitle cues into transcript segments and words
- `TranscriptTimingRefinementServiceSpec`
  - vocal onset/end analysis and refinement behavior
- `TranscriptNoteRebuildServiceSpec`
  - rebuilds note rows from transcript timing
- `YassActionsWizardSpec`
  - finds transcript artifacts and mentions them in post-wizard prompts
- `LrcLibSearchServiceSpec`
  - converts LRCLib results into the canonical transcript model

## Extension Notes

- Do not reintroduce transcript provenance into `#COMMENT`; keep it in
  `yass-transcript.json`.
- Keep source-specific parsing at the edges. Downstream alignment/rebuild code
  should consume `OpenAiTranscriptionResult`.
- Vocal-aware timing refinement should remain conservative. It may trim obvious
  silence and adjust close anchors, but should not infer new lyrics or reorder
  phrases.
