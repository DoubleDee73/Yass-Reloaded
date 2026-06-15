# Tapping Recording

## Status

Implemented/current. This feature is note-tapping recording, not audio
recording.

## Purpose

Tapping recording lets users retime existing lyrics by pressing and releasing a
key or mouse button while playback runs. Completed tap pairs update the start
and length of note rows in the selected range. Optional recorded pitch data can
then be used to align tapped notes to the detected melody.

## User-Facing Behavior

- Recording starts from the selected note row or the first processable note in
  the selected range.
- Non-note rows are skipped until a note row is found.
- Taps are collected as alternating start/end timestamps.
- When the user interrupts after some completed taps, tapped notes can still be
  applied and remaining untapped notes are placed after the last tap.
- Recording playback can use vocals as the playback source when available and
  restores the previous audio selection afterward.
- Recording overlays/tap boxes are visible only after recording playback has
  started.
- After accepting tapped notes, the editor jumps/zooms to the first processed
  tapped note so the user is not disoriented.

## Core Rules

- `YassTapNotes.evaluateTaps(...)` converts tap timestamps into UltraStar beats
  using table BPM, GAP, and playrate timebase.
- Odd trailing tap timestamps are discarded.
- For normal playback speed, reaction-time compensation is applied.
- If recording starts at the first note of the song, the first completed tap sets
  `#GAP` and the first processed note lands at beat `0`.
- First-note anchoring means the real audio start of the first note is stored in
  `#GAP`.
- If recording starts at a later note, absolute timing relative to `#GAP` is
  preserved.
- Post-recording Align To Melody uses
  `YassTable.AlignToMelodyContext.recording()` so detected octave is preserved.

## Data And Configuration

- Tap timestamps are stored in memory as microseconds during the recording
  session.
- Pitch overlay data is captured from the current/vocal audio pitch data.
- The feature updates note rows; it does not record or write audio files.

## Code Entry Points

- `src/yass/YassTapNotes.java`
  - `evaluateTaps(...)`
  - first-note GAP anchoring
- `src/yass/YassActions.java`
  - recording session state
  - playback start/stop/interruption handling
  - apply/accept/cancel flow
  - post-recording melody alignment
- `src/yass/YassSheet.java`
  - recording overlay and tap-box rendering
- `src/yass/YassSheetInfo.java`
  - page/cursor position display during recording

## Regression Coverage

- `YassTapNotesSpec`
  - recording from first song note sets `#GAP` and anchors first tapped note at
    beat `0`
  - recording from later song note keeps absolute timing relative to `#GAP`
- `AlignToMelodySpec`
  - recording context preserves detected octave during post-processing

## Extension Notes

- Keep the term "recording" clear in user-facing docs: this records tap timing,
  not microphone or audio input.
- If adding user-configurable reaction-time compensation, apply first-note
  anchoring after compensation so the first visible note still lands at beat
  `0`.
- Be careful around interruption/resume logic. Player stop events can arrive
  before playback really starts, and the code intentionally suppresses some
  stop events while prompts are open.
