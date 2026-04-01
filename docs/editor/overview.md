---
title: Song Editor Overview
---

# Song Editor Overview

The song editor is the central workspace for creating and refining UltraStar songs.

## At a Glance

The editor combines metadata editing, lyric editing, pitch-aware note editing, waveform display, and playback controls in one place.

## Main Areas

### Song Header

The header contains high-level song metadata and track selection, including:

- audio selection
- gap / start / end
- language, genre, year, and tags
- quick access to alternate tracks such as `#VOCALS`

### Lyrics Panel

The lyrics area is used for:

- lyric editing
- note row selection
- navigating to phrases and pages
- jumping the editor focus to a word or phrase

### Sheet

The sheet displays:

- note rectangles
- timing grid
- waveform
- pitch lines
- page structure

Depending on the mode, the sheet can use:

- relative pitch view
- absolute pitch view

### Timeline and Navigation

The editor provides:

- page navigation
- cursor positioning
- zoomed and one-page views
- horizontal overview / transport-style navigation
- sticky helper controls in advanced absolute workflows

## Common Editing Tasks

- move notes
- resize notes
- select multiple notes
- edit lyrics
- place page breaks
- align notes to grid
- align notes to melody
- jump to a precise cursor time
- move all following notes to a new timestamp

## Important Modes

### Relative Pitch View

This mode adapts the visible pitch range to the current page or note context.

### Absolute Pitch View

This mode shows notes in fixed pitch space and is especially useful for:

- pitch analysis
- recording/tapping workflows
- octave-aware editing
- comparing note placement against detected pitch lines

## Navigation Basics

The classic navigation model is still important:

- `Left` / `Right` selects neighboring notes
- `Up` / `Down` switches pages
- `Page Up` / `Page Down` changes the number of visible pages
- `Ctrl-Shift-Page Up` or `Ctrl-Numpad0` returns to one-page view

## Editing Basics

Some of the most used editing shortcuts are:

| Action | Shortcut |
| --- | --- |
| Play selection | `Space` |
| Play page | `P` |
| Move note left/right | `Shift-Left` / `Shift-Right` |
| Move note pitch | `Ctrl-Up` / `Ctrl-Down` |
| Resize note | `Ctrl-Alt-Left` / `Ctrl-Alt-Right` |
| Toggle page break | `Enter` |
| Add note | `Ctrl-Enter` |
| Align to melody | `M` |
| Toggle absolute view | `L` |

## Related Pages

- [Quick Start](../quick-start.md)
- [Absolute Pitch View](absolute-pitch-view.md)
- [Keyboard Shortcuts](keyboard-shortcuts.md)
- [Recording and Tapping](../recording/tapping.md)
