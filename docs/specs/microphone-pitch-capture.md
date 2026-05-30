# Microphone Pitch Capture

This internal spec tracks a possible editor feature for setting the pitch of a
selected note from a short microphone recording.

## Purpose

When a user knows the intended melody but does not want to drag a note manually,
Yass Reloaded could let them sing or hum the target pitch into a microphone and
apply the detected pitch to the selected editor note.

This is distinct from Note-Tapping Recording. The feature would not record song
audio or create new notes; it would only use a short microphone capture as an
input method for the selected note height.

## Implementation Gap Assessment

Current implemented foundation:

- `YassCaptureAudio` can enumerate and open microphone devices and exposes
  current pitch/note helpers.
- `YassProperties` already stores `control-mic` and `control-mics`, populated
  at startup.
- `YassActions.testMic` opens the existing microphone test UI.
- `YassActions.initMic()` contains an old disabled microphone-session path and
  currently returns `null`.
- `YassTable` already owns selected-note mutation and undo integration patterns.
- `PitchDetector` contains robust file-based pitch detection and tuning-offset
  analysis, but not a short live-capture service for editor input.

Missing for implementation:

- A dedicated editor action such as **Set Pitch From Microphone**.
- A conflict-checked shortcut. Do not choose a key until the editor keybinding
  registry has been consulted.
- A small modal capture dialog with clear states: waiting, listening, detected,
  no stable pitch, cancel.
- A service that samples microphone input for a bounded duration and extracts a
  stable pitch estimate.
- Conversion from detected frequency or note name to Yass note height in the
  current song context.
- Validation that exactly one editable note is selected.
- Undo snapshot before changing the note height.
- Clear handling for missing microphone devices, denied device access, noisy
  input, and no stable pitch.
- Tests around pitch conversion, selection validation, undo behavior, and
  failure states.

## Candidate User Flow

1. User selects exactly one singable note in the editor.
2. User triggers **Set Pitch From Microphone** from a menu action or shortcut.
3. Yass Reloaded opens a small dialog and activates the configured microphone.
4. User sings or hums the intended note.
5. Once a stable pitch is detected, the dialog shows the detected note.
6. OK applies that pitch to the selected note; Cancel leaves the song unchanged.

The first implementation should require explicit OK instead of applying the
pitch immediately. That keeps noisy microphone input from unexpectedly changing
the song.

## Technical Direction

Prefer a new bounded capture helper over reusing the disabled `initMic()` live
session path. The old path was designed around playback/capture state and is
currently disconnected from editor note mutation. A small service can be tested
more easily and avoids destabilizing playback.

The pitch detector should ignore unstable frames and choose a median or
trimmed-median pitch from the stable part of the capture window. The result
should be converted to the nearest chromatic pitch, then mapped to the same
relative Yass note-height scale used by selected notes and pitch rendering.

The feature should not store captured audio. At most, it may log high-level
debug information such as capture duration, selected device, detected frequency,
and chosen note.

## UI Considerations

- The dialog should be modal and owned by the editor window.
- It should show the configured microphone device and a concise status line.
- If no microphone is configured or available, show a clear message that points
  to Options / Control.
- If no stable pitch is found, keep OK disabled and let the user retry.
- The action should be disabled when no song is open, no editable note is
  selected, more than one note is selected, or lyrics/header editing has focus.

## Regression Coverage

When implemented, cover:

- action disabled without exactly one selected note
- missing microphone device produces a clear non-mutating failure
- cancel leaves the note unchanged
- stable detected pitch updates the selected note height
- no stable pitch leaves OK disabled and the note unchanged
- undo restores the previous note height
- detected pitch conversion is octave-correct for representative frequencies
- shortcut does not fire while text fields or lyrics are actively edited

## Open Questions

- Which shortcut is least surprising and conflict-free?
- Should the first version auto-stop after the first stable pitch, or require
  the user to stop listening manually?
- Should the feature prefer the current note octave when multiple equivalent
  octave mappings are plausible?
- Should it optionally support applying the detected pitch to multiple selected
  notes later, or stay single-note only?
