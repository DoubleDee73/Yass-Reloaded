---
title: Note and Lyrics Editing
---

# Note and Lyrics Editing

Yass Reloaded combines note timing, pitch editing, lyrics editing, and page structure in one editor workflow.

## Note Editing Basics

You can edit notes by keyboard, mouse, or both:

- move notes horizontally
- move notes vertically
- resize from left or right
- split and join notes
- add and delete notes

Frequently used shortcuts:

| Action | Shortcut |
| --- | --- |
| Move note left / right | `Shift-Left` / `Shift-Right` |
| Move note pitch | `Ctrl-Up` / `Ctrl-Down` |
| Resize note | `Ctrl-Alt-Left` / `Ctrl-Alt-Right` |
| Split / join note | `-` / `+` |
| Delete note | `Delete` |
| Add note | `Ctrl-Enter` or `Shift-Enter` |

## Copy and Paste Workflows

Copy and paste are central to fast editing in Yass Reloaded, especially for repeated melodies and repeated rhythmic structures.

Useful shortcuts:

| Action | Shortcut |
| --- | --- |
| Copy rows | `Ctrl-C` |
| Cut / remove and copy | `Ctrl-X` |
| Paste rows | `Ctrl-V` |
| Paste notes | `Ctrl-Shift-V` |
| Paste note heights | `Ctrl-Shift-Alt-V` |
| Show copied melody helper | `V` |
| Toggle copied melody behavior | `C` |

Typical use cases:

- repeat a chorus phrase quickly
- reuse the rhythm of a previous phrase
- reuse pitch only
- reuse timing only and then refine the lyric mapping

The exact best choice depends on what should stay the same:

- use row copy/paste when the whole phrase structure repeats
- use pitch- or note-oriented paste variants when only part of the musical information should be reused
- use the copied-melody helper as a visual aid when you want to align a later phrase against an earlier one instead of pasting blindly

## Lyrics Editing

Lyrics editing is closely tied to note editing:

- edit the lyric text
- roll lyrics left or right
- add or remove trailing spaces
- mark notes as golden, freestyle, rap, or plain

Useful shortcuts:

| Action | Shortcut |
| --- | --- |
| Edit lyrics | `F4` |
| Roll lyrics right / left | `R` / `Shift-R` |
| Mark as golden / freestyle / plain | `G` / `F` |
| Mark as Golden Rap / Rap / Plain | `Shift-G` / `Shift-F` |
| Add trailing space | `Ctrl-Alt-Space` |
| Remove trailing space | `Ctrl-Alt-Backspace` |

In practice, lyrics editing is often interleaved with note editing rather than done in a completely separate pass.

## Page Breaks

Page breaks control phrase boundaries and visible page structure.

You can:

- toggle page breaks manually
- remove them again
- let Yass Reloaded auto-correct them
- shift notes around them until the phrase reads naturally

Relevant shortcuts:

| Action | Shortcut |
| --- | --- |
| Toggle page break | `Enter` |
| Remove page break | `Backspace` |
| Auto-correct page breaks | `Ctrl-T` |

## Page Trimming Logic

Yass Reloaded inherits the classic idea that pauses between phrases can be trimmed automatically. In practice this means:

- short pauses break very close to the next phrase
- longer pauses can leave a more readable gap
- extremely long pauses are shortened to keep the page flow usable

Even with auto-trimming, manual cleanup is still often the final step for natural-looking pages.

## Copied Melody

Repeated melodic structures are common in songs. The classic copied-melody helper is still useful when a phrase repeats with similar note movement.

In the older help this was exposed as a toggleable "Copied Melody" aid. In Yass Reloaded, the broader idea still matters:

- reuse repeating note shapes where appropriate
- compare later phrases against earlier melodic material
- combine copy/paste with melody alignment for faster cleanup

This is one of the places where older Yass workflows still transfer very well into Yass Reloaded.

## Related Pages

- [Song Editor Overview](overview.md)
- [Absolute Pitch View](absolute-pitch-view.md)
- [Keyboard Shortcuts](keyboard-shortcuts.md)
