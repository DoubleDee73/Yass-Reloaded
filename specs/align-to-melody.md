# Align To Melody

## Status

Implemented/current. The feature includes full melody alignment plus separate
timing-only and pitch-only variants.

## Purpose

Align To Melody uses detected pitch data to move selected UltraStar notes toward
the sung melody. It is meant as an editor cleanup tool after manual editing,
wizard generation, tapping recording, or transcription-based note creation.

## User-Facing Behavior

- Edit menu actions:
  - **Align to Melody**: `M`
  - **Align Timing**: `Ctrl+M`
  - **Align Pitch**: `Shift+M`
- Actions require selected note rows and available pitch data.
- Actions are ignored while lyric editing is active or focus is inside the song
  header.
- Full alignment can change pitch, note start, and note length.
- Timing-only alignment changes start/length while preserving pitch.
- Pitch-only alignment changes pitch while preserving start/length.

## Core Rules

- `YassTable.AlignToMelodyMode.PITCH_AND_LENGTH` is the full behavior.
- `YassTable.AlignToMelodyMode.LENGTH_ONLY` preserves pitch.
- `YassTable.AlignToMelodyMode.PITCH_ONLY` preserves timing.
- `YassTable.AlignToMelodyContext.MANUAL` may use octave bias so selected notes
  land in a coherent octave.
- `CREATE_WIZARD` and `RECORDING` contexts keep the detected octave. Do not
  apply manual octave correction there.
- Visible pitch-data transpose offsets must be applied before alignment so the
  action matches the pitch line shown to the user.
- Timing alignment should prefer the strong sung body:
  - trim weak trailing beats
  - ignore weak onset tails when they are much quieter than the kept note body
  - stop at real internal voice gaps instead of bridging across silence
  - avoid collisions between selected notes by tracking occupied beats
- If a key from `#COMMENT:key=...` is available and pitch detection is exactly
  between two notes, the in-key pitch is a tie-breaker only.

## Data And Configuration

- Input pitch data comes from the current player/sheet pitch data list.
- Musical key hints are read from `#COMMENT:key=...` when present.
- No persistent settings are changed by the action.

## Code Entry Points

- `src/yass/YassTable.java`
  - `alignToMelody(...)`
  - `AlignToMelodyMode`
  - `AlignToMelodyContext`
- `src/yass/YassActions.java`
  - editor actions and shortcuts for Align To Melody, Align Timing, Align Pitch
- `src/yass/analysis/PitchDetector.java`
  - musical-key-aware smoothing and pitch data creation
- `src/yass/alignment/TranscriptNoteRebuildService.java`
  - wizard/transcript rebuild path that can call alignment in create-wizard
    context

## Regression Coverage

- `AlignToMelodySpec`
  - weak trailing beat trimming
  - low-energy tail trimming
  - weak onset rejection
  - internal voice-gap handling
  - octave-shifted manual pitch alignment
  - recording and wizard contexts preserving detected octave
  - `LENGTH_ONLY` preserving pitch
  - `PITCH_ONLY` preserving timing
- `EditorShortcutBindingsSpec`
  - menu actions and shortcuts for `M`, `Ctrl+M`, and `Shift+M`

## Extension Notes

- Be careful when changing octave behavior. Manual alignment and generated-note
  alignment intentionally differ.
- Keep timing heuristics conservative. The action should produce a strong manual
  starting point, not rewrite every ambiguous pitch frame.
- If adding new alignment modes, extend `AlignToMelodyMode` and add tests for
  pitch preservation, timing preservation, and context-specific octave behavior.
