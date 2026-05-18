# Wizard Clipboard Lyrics Diff

## Status

Implemented/current. The Create Song Wizard can paste lyrics directly or compare
clipboard lyrics against an existing transcript before applying them.

## Purpose

Users often have cleaner lyrics in the clipboard than an automatic transcript.
This feature lets them paste curated text into the wizard while preserving or
rewriting transcript timing when transcript data already exists.

## User-Facing Behavior

- Wizard lyrics step has **Paste Lyrics**.
- If no transcript exists, clipboard text is normalized and pasted directly into
  the lyrics field.
- If a transcript exists, Yass opens a diff dialog:
  - left side: transcript text
  - right side: clipboard text
  - differences are highlighted
  - the user can apply or cancel
- If LRCLib text is selected while transcript data exists, the same comparison
  path can be used.
- Empty or unavailable clipboard content shows an informational dialog.

## Core Rules

- Clipboard text is normalized before use:
  - line endings become `\n`
  - leading/trailing blank lines are trimmed
  - repeated trailing whitespace is collapsed
- The diff dialog is not a full merge editor; the applied right-side text is the
  user's chosen lyric text.
- When transcript state exists, `TranscriptTruthRewriteService` rewrites or
  aligns transcript text to the corrected lyrics while preserving timing where
  possible.
- Matching can join or split words when safe, but should reject or fall back
  rather than invent timing for ambiguous structure.
- Transcript text/timing should remain canonical in `WizardTranscriptionState`
  after applying corrected lyrics.

## Data And Configuration

- Input source: system clipboard text.
- Transcript state: `WizardTranscriptionState`.
- No persistent properties are changed.
- Resulting transcript can later be saved as `yass-transcript.json` when the
  wizard finishes.

## Code Entry Points

- `src/yass/wizard/CreateSongWizard.java`
  - `pasteLyricsFromClipboard()`
  - LRCLib comparison paths
  - applying updated transcript state
- `src/yass/wizard/ClipboardLyricsDiffDialog.java`
  - dialog UI and diff highlighting
- `src/yass/alignment/TranscriptTruthRewriteService.java`
  - transcript text rewrite and timing preservation
- `src/yass/wizard/Lyrics.java`
  - lyrics step UI state

## Regression Coverage

- `CreateSongWizardSpec`
  - wizard metadata/query behavior around lyric sources where covered
- `LrcLibSearchServiceSpec`
  - LRCLib transcript conversion
- `TranscriptNoteRebuildServiceSpec` and `TranscriptArtifactServiceSpec`
  - downstream usage of corrected transcript state
- Add direct dialog/rewrite tests before changing diff-application behavior.

## Extension Notes

- Keep direct paste simple when no transcript exists.
- When transcript exists, prioritize preserving reliable timing over matching
  every visual line perfectly.
- If adding a richer merge UI, keep `TranscriptTruthRewriteService` independent
  of Swing so it remains testable.
