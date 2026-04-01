---
title: Absolute Pitch View
---

# Absolute Pitch View

Absolute pitch view shows notes in a fixed pitch space instead of dynamically adapting the visible range to the current page.

## What Makes It Different

Compared to the relative view, the absolute view is designed for octave-aware work:

- notes stay on their actual pitch rows
- pitch lines can be compared directly against note placement
- a vertical scrollbar lets you inspect a wider MIDI-like pitch range
- the visible content can be centered without changing the underlying note positions

## Typical Use Cases

Absolute view is especially useful for:

- checking whether notes sit on the detected melody
- recording and tapping workflows
- investigating octave mistakes
- navigating across pages without losing the pitch context

## UI Elements You Will Notice

Depending on the workflow and zoom level, the absolute view can show:

- a continuous pitch grid
- octave labels
- a piano-style pitch strip on the left
- sticky timeline and navigation elements
- waveform and detected pitch overlays

## Navigation Behavior

In the absolute view, horizontal and vertical navigation are both important:

- horizontal navigation follows timing and pages
- vertical navigation follows pitch range
- one-page view should keep the active page centered both horizontally and vertically

This makes it much easier to keep your bearings when switching between pages or aligning notes to pitch data.

## Recording and Tapping

The absolute view works especially well with tapping because it can keep a stable vertical reference while timing is recorded.

This is helpful when you want to:

- tap note timing
- compare the result against pitch lines
- refine note heights afterward

## Related Pages

- [Song Editor Overview](overview.md)
- [Recording and Tapping](../recording/tapping.md)
- [Keyboard Shortcuts](keyboard-shortcuts.md)
