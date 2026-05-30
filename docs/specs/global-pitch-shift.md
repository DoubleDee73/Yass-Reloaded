# Global Pitch Shift

This internal spec tracks a song-level pitch shift feature for audio playback,
pitch detection, and optional rendered audio correction.

## Purpose

Some recordings are tuned slightly above or below concert pitch while remaining
internally consistent. Users may want Yass to account for that offset when
authoring notes, aligning to detected pitch lines, or preparing helper audio.

Yass already estimates global tuning offset from Aubio pitch data. This feature
should turn that analysis into a controlled user-facing setting.

## Implementation Gap Assessment

Current implemented foundation:

- `PitchDetector.analyzeTuningOffset(...)` estimates global tuning drift in
  cents and returns `TuningOffsetAnalysis`.
- Pitch detection logs `[PitchTuning]` with detected offset, recommended
  correction, factor, sample count, inliers, and MAD.
- `PitchDetectorSpec` covers positive drift, outlier robustness, and too-few
  frames.
- `YassSheet` and `YassPlayer` already have pitch waveform transpose concepts,
  but they are not song-level pitch-shift settings.

Missing for implementation:

- Song-local parsing/updating for `#COMMENT:pitchShiftCents=...`.
- A small service/helper for pitch-shift metadata so comment-key handling is
  tested and not duplicated in UI/actions.
- UI entry point under `Extras > Pitch Shift...` to show detected offset,
  semitone display, recommendation, and manual cents input.
- Threshold behavior: offsets below `5` cents should not recommend or persist a
  correction automatically.
- Integration into pitch-line rendering so corrected pitch data is shown exactly
  once.
- Integration into `alignToMelody` so alignment uses the same corrected pitch
  line the user sees.
- Tests ensuring existing `#COMMENT:key=...` does not influence tuning-offset
  analysis.
- Optional render action that applies the correction to all song audio files,
  with collision-safe filenames, FFmpeg availability checks, cancel behavior,
  and all-or-nothing tag updates.
- Regression tests for render cancellation, missing FFmpeg, filename collision,
  and no double application with absolute pitch view / transpose rendering.

Recommended implementation slices:

1. Add metadata service and tests for `pitchShiftCents` comment parsing/updating.
2. Add non-rendering UI/action to inspect detected offset and store manual or
   recommended cents values.
3. Apply stored correction to pitch-line rendering and `alignToMelody` with
   focused tests for "applied once".
4. Add optional offline audio rendering as a separate action after the visual
   and alignment behavior is stable.

## Concepts

The user-facing feature name should be **Pitch Shift**. This is the more common
term in audio software for deliberately moving audio or pitch material up or
down. The analysis result can still be described as a **detected tuning offset**
because that explains what Yass measured before applying a correction.

The setting should use cents as the precise unit and semitones as the musician
friendly display:

- `100 cents = 1 semitone`
- `50 cents = 0.5 semitones`
- negative values mean the source is flat
- positive values mean the source is sharp

Two values should be distinguished:

- detected source offset: how far the audio appears to be from equal-tempered
  concert pitch
- correction amount: the opposite shift needed to bring audio/pitch data back
  to the nearest chromatic grid

## User Flow

The editor entry point should be `Extras > Pitch Shift...`. The action should be
discoverable for open songs; context-sensitive buttons inside the dialog should
communicate when analysis or rendering is unavailable instead of hiding the
feature entirely.

Loading audio files must not automatically scan for a tuning offset. Analysis is
an explicit user action from the dialog, for example `Analyze Vocals`.

The analysis action should be enabled only when Aubio is configured/available
and the current audio context is `#VOCALS`. If Aubio is unavailable, or the user
is not on the vocal audio source, the dialog should still allow viewing,
editing, applying, or removing an existing manual song-level correction.

The first user-facing version should expose a global pitch-shift setting for the
current song:

1. Open `Extras > Pitch Shift...`.
2. Show any stored correction from `#COMMENT:pitchShiftCents=...`.
3. Let the user explicitly analyze the vocal track when the context allows it.
4. Show detected tuning offset, for example `+18 cents (+0.18 semitones)`.
5. Let the user apply the recommended correction or enter a manual cents value.
6. Store the value in song metadata.
7. Use the value consistently in pitch-line rendering and melody alignment.

Rendering corrected audio should be a separate explicit action:

1. User confirms applying the correction to all audio files referenced by the
   song, such as `#AUDIO`, `#VOCALS`, and `#INSTRUMENTAL`.
2. Yass validates FFmpeg/backend availability, source file existence, and target
   filenames before making changes.
3. Yass renders shifted copies via FFmpeg or another backend.
4. If every render succeeds, Yass assigns the rendered copies to the
   corresponding tags and removes `pitchShiftCents` from `#COMMENT`.
5. If the user cancels or any render fails, source files, tags, and comments
   remain unchanged.

## Storage

The first implementation should use song-local storage so the correction travels
with the `.txt` file. Store it as a pseudo tag inside `#COMMENT`, for example:

- `#COMMENT:pitchShiftCents=-18.0`

If multiple comment properties already exist, preserve them and update only the
pitch-shift key.

When the correction is rendered into the actual audio files, remove only the
`pitchShiftCents` comment property. This must happen only after all affected
audio tags have been updated successfully; partial audio correction must leave
the song-local correction metadata intact.

Application-wide defaults may come later, but should not replace the song-local
value.

Existing `#COMMENT:key=...` should not influence the suggested correction. The
goal is to shift the detected pitch data toward the chromatic grid so the
configured key can be applied meaningfully afterwards. If the detected offset is
so large or ambiguous that this assumption is not safe, Yass should ignore it
and avoid recommending an automatic correction.

Offsets below 5 cents should be treated as "no correction recommended". They are
generally not meaningfully audible and should not create extra UI noise or
metadata churn.

## Rendering and Alignment Behavior

Once a song has a global pitch-shift value:

- pitch lines should be displayed in the corrected visual position
- `alignToMelody` should align against the same corrected pitch line the user
  sees
- the displayed analysis should make clear whether it is showing source offset
  or applied correction
- manual note pitches should remain unchanged unless the user explicitly runs an
  alignment or pitch-shift action

This must be carefully integrated with the existing absolute pitch view and any
render transpose logic so the same correction is not applied twice.

## Audio Rendering

FFmpeg can do simple pitch shifting with filter chains based on `asetrate` and
`aresample`, but that changes time if not compensated correctly. A safer
implementation should either:

- use FFmpeg with a known duration-preserving filter chain, or
- use a dedicated pitch/time processing backend if one is available.

The rendered copy should be named collision-safely, for example:

- `<Artist> - <Title> (Pitch Shifted Vocals).ogg`
- `<Artist> - <Title> (Pitch Shifted Audio).ogg`

The original file must not be overwritten unless the user explicitly confirms
an overwrite.

The first rendering implementation should process all audio files referenced by
the song as one operation. It should not offer a partial subset selection. If
multiple tags point to the same source file, render that file once and update all
corresponding tags consistently.

Rendering should behave all-or-nothing at the song metadata level. Yass may
create temporary or candidate output files during processing, but it should not
update tags or remove `pitchShiftCents` until every required render has
completed successfully.

## Regression Coverage

Add tests for:

- detected cents value is converted to semitones correctly
- song-local comment value is parsed and updated without destroying other
  comment keys
- explicit analysis is not started by audio loading
- pitch-shift analysis is enabled only for Aubio plus the vocal audio context
- pitch-line rendering receives the expected correction once
- `alignToMelody` uses corrected pitch data consistently
- canceling audio render leaves tags and files unchanged
- successful full audio render updates all affected tags and removes only the
  `pitchShiftCents` comment property
- failed partial audio render leaves tags and comments unchanged
- rendered filenames avoid collisions
- missing FFmpeg/backend shows a clear message
- offsets below 5 cents do not produce an automatic correction recommendation
- existing `#COMMENT:key=...` does not bias the tuning-offset estimate
