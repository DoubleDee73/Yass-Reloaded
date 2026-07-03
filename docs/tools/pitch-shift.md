---
title: Pitch Shift
---

# Pitch Shift

Some recordings are tuned slightly above or below standard concert pitch while
staying internally consistent. **Pitch Shift** lets you measure that tuning
offset and apply a song-local correction so playback, the detected pitch lines,
and melody alignment all line up with the chromatic grid.

## Opening the Dialog

- Open a song in the editor.
- Choose `Extras -> Pitch Shift…`.

The dialog is available for any open song. Buttons that cannot run in the current
context stay visible but disabled, with a tooltip explaining why, so the feature
is never hidden.

## Units

- The precise unit is **cents**; `100 cents = 1 semitone`.
- A friendly semitone value is shown alongside, for example `+18 cents
  (+0.18 semitones)`.
- **Negative** values mean the recording is **flat** (below pitch).
- **Positive** values mean the recording is **sharp** (above pitch).

## Analyzing a Song

- **Analyze** is available when Aubio is configured and an audio track is loaded.
- Analysis is always an explicit action — loading audio never scans automatically.
- Yass prefers the `#INSTRUMENTAL` track when the song declares one, because a
  fixed-pitch instrument holds tuning far better than a sung vocal.
- The dialog shows the **detected source offset** and, when the measurement is
  marginal, a **low-confidence** note.
- Offsets below **5 cents** are reported as "no correction needed" — they are not
  meaningfully audible.

If a recording's tuning drifts over time rather than sitting at one consistent
offset, Yass reports that no single reliable correction exists. A global pitch
shift can only fix a uniform offset.

## Applying a Correction

- **Apply recommended** stores the suggested correction.
- You can also type a manual cents value.
- **Remove** clears the stored correction.

The correction is stored with the song so it travels with the `.txt` file. Other
song comment data is preserved.

## Quick Adjust from the Toolbar

Once a song has a correction, a small **cents box** appears next to the key icon
in the editor toolbar (for example `+23 ct`). It is a fast way to nudge the
correction without reopening the dialog.

- The box is **only shown when the song has a non-zero correction**. It appears
  after you apply a value in the dialog and disappears when the correction is
  cleared.
- Adjust the value with the up/down arrows or by typing.
- The change is applied when you press **Enter** or click away (focus loss).
  Each apply re-converts the playback audio, so adjusting is "set, then hear"
  rather than continuous — this matches the dialog's live preview.
- Setting the value to **0** removes the correction; the box then hides itself.
- Values follow the same convention as the dialog: negative is flat, positive is
  sharp, in cents (`100 cents = 1 semitone`).

The box and the dialog edit the same stored value, so changes made in one are
reflected in the other.

## Hearing the Correction

Hearing the correction happens in two stages.

### Live preview (automatic, non-destructive)

- Playback is automatically heard already corrected.
- Your original audio files are never touched — the shift lives only in a
  throwaway temporary file Yass uses for playback.
- Changing the value re-converts the playback audio so the next playback reflects
  it. This is "apply then hear", not real-time scrubbing; an unchanged value
  replays instantly from cache.

### Make permanent (optional)

- **Render to Files…** bakes the current correction into new audio files. It is
  available only when a non-zero correction is stored and an FFmpeg backend is
  available.
- Yass renders shifted copies of every audio file referenced by the song
  (`#AUDIO`, `#VOCALS`, `#INSTRUMENTAL`) with collision-safe names and never
  overwrites the originals.
- On full success the song points at the new files and the stored correction is
  removed (the shift now lives in the files, so it is not applied twice).
- If you cancel or any render fails, your files, tags, and stored correction are
  left untouched.

## How the Pitch Lines Stay Correct

When the pitch lines are shown from detected audio, the correction is applied to
the audio first and the pitch is detected from that corrected audio. The pitch
lines therefore already reflect the correction and are not shifted a second time —
audio and visuals stay in agreement.

## Related Pages

- [External Tools](overview.md)
- [Song Editor Overview](../editor/overview.md)
- [Absolute Pitch View](../editor/absolute-pitch-view.md)
- [Settings Overview](../settings/overview.md)
