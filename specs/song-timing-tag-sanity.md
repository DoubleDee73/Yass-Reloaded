# Song Timing Tag Sanity

## Status

Implemented/current. Timing tag validation runs automatically during editor
open and when leaving the editor after a touched song session.

## Purpose

Malformed timing tags can make the editor view unusable. Extremely negative or
large values in tags such as `#START`, `#END`, `#GAP`, `#VIDEOGAP`,
`#PREVIEWSTART`, `#MEDLEYSTARTBEAT`, or `#MEDLEYENDBEAT` can push the audio,
video, timeline, preview markers, or grid far outside the useful range.

## User-Facing Behavior

- Unsafe timing tags are cleaned automatically.
- Wizard-created songs can be cleaned silently.
- Normal editor-open cleanup may notify the user with the tags that were
  removed or corrected.
- Returning to the library revalidates timing only if the open song was touched
  during the editor session.
- If cleanup changes anything, the table remains marked modified so the user can
  save through the normal flow.

## Core Rules

- `#GAP` must be greater than or equal to `0`; invalid `#GAP` is corrected to
  `0`.
- `#START`, `#END`, and `#PREVIEWSTART` must be greater than or equal to `0`.
- `#END` must be after `#START` when both are present and non-zero.
- `#START`, `#END`, and `#PREVIEWSTART` must not exceed actual audio duration
  when duration is known; invalid values are removed.
- `#START` must be before the first singable note in audio time.
- `#VIDEOGAP` may be negative, but implausibly large values are removed.
- `#MEDLEYENDBEAT` must be after `#MEDLEYSTARTBEAT`.
- If either medley tag is invalid, both medley tags are removed.
- Negative note beats are allowed only when plausible relative to `#GAP`.
- Every negative note beat must be checked, not only the first note.
- The touched flag lifecycle:
  - set to untouched when a song is opened
  - set to touched when `storeFile(...)` is called
  - reset when the song is closed

## Data And Configuration

- Validated tags:
  - `#GAP`
  - `#START`
  - `#END`
  - `#PREVIEWSTART`
  - `#MEDLEYSTARTBEAT`
  - `#MEDLEYENDBEAT`
  - `#VIDEOGAP`
- Audio duration is used when available.
- Cleanup removes invalid optional header rows except `#GAP`, which is corrected
  to `0`.

## Code Entry Points

- `src/yass/SongTimingTagSanityService.java`
  - validation rules and cleanup findings
- `src/yass/YassActions.java`
  - editor-open integration
  - return-to-library revalidation
  - touched-song tracking
- `src/yass/YassTable.java`
  - header tag storage and cleanup application

## Regression Coverage

- `SongTimingTagSanityServiceSpec`
  - negative and out-of-range `#START`
  - `#END` before `#START`
  - preview after song duration
  - `#START` after first singable note
  - negative `#GAP` correction
  - plausible and implausible negative note beats
  - medley pair removal
  - extreme `#VIDEOGAP`
  - normal missing optional tags
- Integration coverage in action/table tests covers touched-song behavior and
  wizard silent cleanup where applicable.

## Extension Notes

- Keep validation conservative. The goal is to prevent broken editor geometry,
  not to reject every unusual but valid authoring choice.
- If adding more timing tags, define whether invalid values are removed,
  corrected, or reported only.
- Do not show cleanup prompts in wizard-controlled flows unless users need to
  make a decision.
