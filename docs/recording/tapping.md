---
title: Recording and Tapping
---

# Recording and Tapping

Yass Reloaded supports timing notes by tapping along with playback.

## Basic Workflow

1. Select the note or note range you want to record
2. Start the recording / tapping mode
3. Tap note start and release for note end
4. Let Yass place the tapped notes
5. Refine with editing tools or Align to Melody

## During Recording

The recording UI can show:

- a fixed rolling time window
- a dedicated tap cursor
- a tap queue for upcoming syllables
- pitch lines as timing and pitch guidance

## Typical Outcomes

After tapping, Yass can:

- keep the recorded result
- continue from the first remaining untapped note
- discard the interrupted attempt

## Alignment After Tapping

Recording and tapping are usually followed by note refinement:

- manual editing
- [Align to Melody](../editor/overview.md)

## Notes

- the visible behavior can differ between relative and absolute pitch view
- external pitch overlays depend on the selected track and available pitch analysis
- at the end of a song, the final tapped notes should still be committed even if playback stops naturally
