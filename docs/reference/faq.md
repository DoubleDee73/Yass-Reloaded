---
title: FAQ
---

# FAQ

## What is Yass Reloaded for?

Yass Reloaded is an editor for UltraStar songs. It helps with lyrics, notes, timing, metadata, page breaks, playback, and media management.

## Should I use relative or absolute pitch view?

Use the relative view for fast page-focused editing. Use the absolute view when octave consistency, pitch inspection, vertical scrolling, or recording workflows matter more.

## Do I need a `#VOCALS` track?

Not always, but many pitch-aware workflows become much more useful with a clean vocals track. This includes melody inspection and some alignment scenarios.

## Can I time notes by ear?

Yes. The recording and tapping workflow is designed exactly for that, and you can refine the result afterward.

## Where do I configure fanart.tv?

The fanart.tv API key is configured in:

- `Settings -> Library -> Setup`

## Where are WhisperX and audio-separator configured?

These are configured in:

- `Settings -> External Tools -> Transcription`
- `Settings -> External Tools -> audio-separator`

They can also fall back to:

- `Settings -> External Tools -> Locations -> Default Python executable`

If the tool-specific Python field is empty and you use `Update package`, Yass can create a dedicated virtual environment for WhisperX or audio-separator automatically.

## Where is USDB configured?

USDB-related settings are split across two places:

- `Settings -> Library -> Setup` for the USDB user name and account-oriented behavior
- `Settings -> External Tools -> Locations` for the optional USDB Syncer path

The Syncer path improves search and refresh workflows, but the direct live USDB integration can still work without it.

## Does Yass Reloaded support LrcLib?

Yes.

LrcLib is available as an online lyrics source in the song creation wizard. It does not require a separate API key or its own settings page. When timed lyrics are available, Yass can use them as structured lyric input before or instead of a full transcription workflow.

## Is the GitHub Wiki still relevant?

Yes. The wiki is still useful, especially for older notes and gradually evolving content. The GitHub Pages site is intended to become the better structured main documentation.

## Is Yass Reloaded a fork of the original Yass?

Yes. Yass Reloaded builds on the original Yass project and extends it with modern Java, improved workflows, and active maintenance.

## Why does this documentation keep saying "Yass Reloaded" instead of just "Yass"?

To avoid confusion. Some older tutorials, screenshots, and help texts still refer to the original Yass project, but this documentation is specifically about Yass Reloaded unless stated otherwise.

## Which older Yass topics are still relevant for Yass Reloaded?

A number of classic concepts still carry over well:

- library maintenance and error-driven editing
- page trimming and phrase layout
- note, lyrics, and page-break editing basics
- copied melody and repeating phrase workflows
- keyboard-driven editing and virtual piano usage

## Does it support UltraStar Deluxe songs?

Yes. Yass Reloaded is designed for UltraStar-style song files and typical UltraStar Deluxe workflows.

## Which operating systems are supported?

Windows, macOS, and Linux.

## Do I need FFmpeg?

Usually yes, especially if you work with a broad set of audio formats or use advanced wizard and analysis features.

## Does Yass Reloaded support transcription and stem separation?

Yes, through external integrations such as:

- WhisperX
- OpenAI-based transcription
- MVSEP
- audio-separator

## Where should I report bugs or request features?

Use the project repository:

- [Issues](https://github.com/DoubleDee73/Yass-Reloaded/issues)
