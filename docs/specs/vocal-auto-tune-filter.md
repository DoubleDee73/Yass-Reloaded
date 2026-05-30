# Vocal Auto-Tune Filter

This internal spec explores optional pitch-correction processing for separated
vocal tracks.

## Purpose

Some karaoke source material has vocal stems that are internally consistent but
slightly off concert pitch, or vocals that would be easier to author if a
pitch-corrected helper stem were available. Yass should investigate whether it
can offer an optional "auto-tuned vocal helper" workflow without changing the
original song audio unless the user explicitly asks for it.

## Implementation Gap Assessment

Current implemented foundation:

- `#VOCALS` assignment and separated vocal workflows exist.
- `PitchDetector.TuningOffsetAnalysis` can estimate global tuning drift, but not
  note-by-note correction.
- FFmpeg location and health/prompt support exist.
- MVSEP Ogg output work already introduced local FFmpeg transcode patterns and
  collision-aware stem output handling that can be reused.

Missing for implementation:

- Backend decision. No usable auto-tune backend is integrated today.
- A capability check for the chosen backend, including clear error messaging.
- UI action such as `Create Pitch-Corrected Vocals`.
- Dialog to choose correction mode, key/scale behavior, output format, and
  whether to assign the result to `#VOCALS`.
- Key/scale resolution rules from `#COMMENT:key=...` or user selection.
- Offline render pipeline that preserves duration and avoids clipping.
- Collision-safe helper filename generation.
- Confirmation flow before updating `#VOCALS`.
- Logging of applied correction settings.
- Tests for missing vocals, missing backend, cancel behavior, safe filenames,
  assignment behavior, and original-file preservation.

Important dependency:

- The **Global Pitch Shift** spec should probably land first. It covers the
  simpler and more common case of a uniformly sharp/flat source and establishes
  the comment metadata, cents/semitone UI, and helper-render patterns that this
  feature can reuse.

## Non-Goals

This feature is not meant to be real-time live pitch correction during playback.

It should also not silently replace the original `#AUDIO`, `#VOCALS`, or
`#INSTRUMENTAL` files. Any rendered helper file must be created deliberately and
assigned only after user confirmation.

## Candidate User Flow

1. User selects a song with a configured vocal track.
2. User runs a new tool action such as `Create Pitch-Corrected Vocals`.
3. Yass analyzes the vocal track and proposes correction settings.
4. User chooses an output mode:
   - create a helper file only
   - assign as `#VOCALS`
   - cancel
5. Yass renders a new file next to the song, for example
   `<Artist> - <Title> (Vocals Tuned).ogg`.
6. The original vocal file remains untouched.

## Technical Direction

GSnap itself is a VST plugin, and relying on arbitrary VST hosting from Yass is
likely fragile across Windows, macOS, and Linux. The first implementation should
therefore prefer a command-line or library-backed pitch processing route.

Possible approaches:

- use FFmpeg only for global pitch shifting, not note-level correction
- use an external command-line pitch correction tool if a stable one is found
- use a Python-based processing pipeline in a managed environment
- expose a configurable external command template for advanced users

The feature should start as an offline render workflow. It can reuse existing
FFmpeg location handling and progress-dialog patterns.

## Data Inputs

Useful existing data:

- vocal track file from `#VOCALS`
- detected pitch frames from `PitchDetector`
- `PitchDetector.TuningOffsetAnalysis` for global tuning offset
- optional `#COMMENT:key=...` for scale-aware correction decisions

For strict auto-tune behavior, a key or scale is important. Without a key, Yass
should either use chromatic correction or ask the user to choose one.

## Output

Rendered output should:

- preserve duration as closely as possible
- use a format Yass can play reliably
- avoid clipping
- log the applied correction settings
- be recoverable from the song folder without relying on temp files

The first supported output format should probably be Ogg/Opus or WAV depending
on the chosen backend. If FFmpeg is used for the final encoding step, reuse the
same encoder checks planned for MVSEP Ogg output.

## UI Considerations

The UI should clearly distinguish between:

- global pitch correction, which shifts all audio equally
- auto-tune, which changes individual notes toward a scale or chromatic grid

The action should not be prominent in the normal editing path until the result
quality is proven. A good first home is an external-tools or song-extras action.

## Regression Coverage

When implemented, cover:

- missing vocal track shows a clear message
- missing FFmpeg or external backend shows a clear message
- cancel does not create files or alter song tags
- helper file uses collision-safe naming
- assigning the rendered file updates only `#VOCALS`
- original files are not overwritten
- processing failure leaves the song unchanged

## Open Questions

- Which backend is good enough and redistributable enough for Yass Reloaded?
- Should the feature require a key, or allow chromatic correction?
- Should the action operate on `#VOCALS` only, or also on `#AUDIO`?
- Should rendered helper files be tracked in `#COMMENT` metadata?
