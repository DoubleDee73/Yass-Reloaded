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
- The wizard LRCLib action first lets the user either search LRCLib online or
  import an existing `.lrc` file with the same synced-lyrics format.
- YouTube downloads in the create-song wizard can select a downloaded subtitle
  file for later transcript fallback; manual subtitles win over auto-generated
  subtitles when both are present.
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
- Subtitle files that are effectively all-caps are normalized to lyric-style
  sentence casing, not AP title casing.
- At wizard finish, `YassActions.withSubtitleTranscriptFallback(...)` converts a
  selected subtitle file as a fallback even if the lyrics panel did not already
  carry a transcript state.
- Subtitle fallback never replaces an existing wizard transcript. Blank subtitle
  paths, missing files, or subtitle conversions that produce no transcript leave
  the current wizard state unchanged.
- Local `.lrc` files are converted at the source edge into the same transcript
  segment and word-timing model, tagged as `#LRC`.
- Rolling YouTube captions are collapsed before conversion.
- Vocal-aware refinement can adjust transcript timing before alignment when a
  vocal pitch/energy signal is close enough to transcript anchors.
- When a coarse subtitle transcript receives a global timing offset from the
  opening phrase, vocal-aware refinement still checks local phrase starts near
  the original subtitle anchors before rebuilding notes. This keeps a later line
  from being pulled too far right when the first subtitle cue started early.
- Vocal-aware refinement can ignore trailing signal or occupancy clusters when
  they are actually the next phrase just before the next transcript anchor; this
  keeps dense subtitle lines from assigning the next phrase's first vocal block
  to the current phrase.
- Vocal-aware refinement filters weak support windows only when enough onset
  anchors remain for every word, snaps close onset buckets to following signal
  starts, and collapses duplicate onsets inside word-level signal windows.
- When there are more local signal windows than transcript words, extra windows
  are assigned preferentially to multi-syllable words so a short leading word
  does not swallow the next sung island.
- Wizard post-processing applies vocal-aware timing refinement before rebuilding
  notes when separated vocals are available, and passes the same raw vocal pitch
  frames into melody alignment for note height and length adjustment.
- Melody alignment can pull the first note after a page break back onto an
  unused free vocal pitch island between the previous page's last note and the
  current note, so page boundaries do not leave detected vocals unassigned.
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
- Diagnostic log prefixes for this path:
  - `[WizardYouTubeSubtitle]` records yt-dlp subtitle discovery, temp-asset
    selection, and whether manual or auto subtitles became the wizard value.
  - `[WizardSubtitleTranscript]` records wizard-finish fallback decisions, skip
    reasons, transcript segment/word counts, and the saved artifact path.

## Code Entry Points

- `src/yass/integration/transcription/TranscriptArtifactService.java`
  - save/load `yass-transcript.json`
- `src/yass/integration/transcription/TranscriptSourceComment.java`
  - removes obsolete transcript pseudo-tags from `#COMMENT`
- `src/yass/integration/transcription/SubtitleTranscriptionAdapter.java`
  - subtitle cue to transcript conversion
- `src/yass/integration/lyrics/lrc/LrcTranscriptionAdapter.java`
  - local `.lrc` file to transcript conversion
- `src/yass/alignment/TranscriptTruthRewriteService.java`
  - applies corrected/clean lyric text to transcript structure
- `src/yass/alignment/TranscriptTimingRefinementService.java`
  - vocal-aware transcript timing refinement
- `src/yass/YassActions.java`
  - editor reuse, subtitle fallback, and post-wizard transcript handling
- `src/yass/wizard/CreateSongWizard.java`
  - wizard transcript creation, LRCLib integration, artifact persistence
- `src/yass/wizard/YouTube.java`
  - yt-dlp subtitle discovery and create-wizard subtitle field selection

## Regression Coverage

- `TranscriptArtifactServiceSpec`
  - save/load canonical transcript JSON
- `TranscriptSourceCommentSpec`
  - removes obsolete transcript pseudo-tags while preserving other comments
- `SubtitleTranscriptionAdapterSpec`
  - converts subtitle cues into transcript segments and words
- `SubtitleParserSpec`
  - preserves mixed-case subtitles and normalizes all-caps lyric subtitles
- `CreateSongWizardSpec`
  - carries subtitle transcript results from the lyrics panel into wizard finish
- `TranscriptTimingRefinementServiceSpec`
  - vocal onset/end analysis, dense subtitle windows, and refinement behavior
- `TranscriptNoteRebuildServiceSpec`
  - rebuilds note rows from transcript timing and refined vocal windows
- `AlignToMelodySpec`
  - keeps page-start notes aligned to unused lead-in vocal pitch islands
- `YassActionsWizardSpec`
  - finds transcript artifacts, builds subtitle fallbacks at wizard finish,
    refines wizard transcripts against copied vocals, and mentions artifacts in
    post-wizard prompts
- `LrcLibSearchServiceSpec`
  - converts LRCLib results into the canonical transcript model
- `LrcTranscriptionAdapterSpec` and `CreateSongWizardSpec`
  - convert local `.lrc` files and route the wizard choice dialog

## Extension Notes

- Do not reintroduce transcript provenance into `#COMMENT`; keep it in
  `yass-transcript.json`.
- Keep source-specific parsing at the edges. Downstream alignment/rebuild code
  should consume `OpenAiTranscriptionResult`.
- When debugging a missing wizard transcript artifact after a YouTube download,
  first trace `[WizardYouTubeSubtitle]` and `[WizardSubtitleTranscript]` entries
  in `log.txt` before changing conversion or finish behavior.
- Vocal-aware timing refinement should remain conservative. It may trim obvious
  silence and adjust close anchors, but should not infer new lyrics or reorder
  phrases.
