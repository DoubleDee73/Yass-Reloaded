---
title: Recording and Tapping
---

# Recording and Tapping

Yass Reloaded supports **note-tapping recording**, meaning you record note timing by tapping along with playback. This does **not** mean microphone or audio recording.

## When to Use It

Note-tapping recording is especially useful when:

- lyrics already exist but note timing is weak
- you want to re-time a phrase quickly by ear
- you want to reconstruct timing before refining pitch placement
- a stable absolute pitch view helps more than manual page-by-page editing

## Basic Workflow

1. Select the note or note range you want to record
2. Start the note-tapping recording mode
3. Tap note start and release for note end
4. Let Yass Reloaded place the tapped notes
5. Refine with editing tools or Align to Melody

## During Recording

The note-tapping recording UI can show:

- a fixed rolling time window
- a dedicated tap cursor
- a tap queue for upcoming syllables
- pitch lines as timing and pitch guidance
- committed taps moving away from the cursor as playback advances

## Typical Outcomes

After tapping, Yass Reloaded can:

- keep the recorded result
- continue from the first remaining untapped note
- discard the interrupted attempt

## Alignment After Tapping

Note-tapping recording is usually followed by note refinement:

- manual editing
- align to melody
- octave-aware cleanup in the absolute view

## Notes

- the visible behavior can differ between relative and absolute pitch view
- external pitch overlays depend on the selected track and available pitch analysis
- at the end of a song, the final tapped notes should still be committed even if playback stops naturally
- if this documentation says "recording" in this section, it always means note-tapping recording

## Useful Shortcuts

| Action | Shortcut |
| --- | --- |
| Start recording | `Ctrl-R` |
| Stop or cancel playback | `Esc` |
| Play selection | `Space` |
| Play page | `P` |
| Enable instrument | `Ctrl-B` |
| Toggle audio | `Ctrl-U` |

## Related Pages

- [Song Editor Overview](../editor/overview.md)
- [Note and Lyrics Editing](../editor/note-editing.md)
- [Absolute Pitch View](../editor/absolute-pitch-view.md)
- [Keyboard Shortcuts](../editor/keyboard-shortcuts.md)
