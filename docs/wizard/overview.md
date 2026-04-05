---
title: Wizard Overview
---

# Wizard Overview

The song creation wizard helps you build a new Yass Reloaded song step by step. Which pages appear depends on the available inputs and configured tools.

## Main Idea

The wizard is not one fixed path. It adapts to:

- whether `yt-dlp` is installed
- whether you already have a melody or MIDI file
- whether lyrics already exist
- whether separation and transcription tools are configured

## Typical Wizard Sections

### YouTube

This step is only available when `yt-dlp` is detected.

Use it when:

- you want to start from a YouTube URL
- you want the wizard to download source media
- you want the wizard to reuse YouTube metadata such as subtitles or video context

If `yt-dlp` is not installed, you cannot enter a YouTube URL in the wizard. In that case the wizard starts from local files instead.

### Melody

This step is where existing melody or MIDI-related input is introduced.

It matters when:

- you already have a MIDI file
- you want the wizard to use an existing melodic source instead of starting from lyrics only

Depending on your wizard defaults, MIDI handling may be used or skipped.

### Lyrics

This step is where lyrics are entered, pasted, or refined.

It is the central page when you create songs from text first.

This page can also expose additional helper actions when the environment is ready, for example a combined separation-and-transcription flow.

### Lyrics for MIDI

This step only matters in the MIDI-driven branch when lyrics are not already available in the MIDI-derived data.

If the imported MIDI already contains usable lyrics, this detour can be skipped.

### Audio

This step selects or confirms the source audio file for the song.

It is important because later helper features such as separation or transcription need a real audio file to work from.

### Header

This step defines the song header and basic metadata:

- artist
- title
- language
- genre
- year
- BPM
- creator

The wizard can also try to enrich this step from MusicBrainz if enough metadata is already known.

### Edition

This step helps place the song into the right folder or collection context.

### Tap

This final step is for note-tapping preparation and follow-up. In Yass Reloaded, "recording" in this context means **note-tapping recording**, not microphone or raw audio recording.

## Separation and Transcription in the Wizard

The lyrics page can offer a combined separation/transcription helper, but only when the prerequisites are met.

Typical prerequisites:

- a valid source audio file exists
- at least one separation backend is configured
- at least one transcription backend is configured

If these are not available, the feature may be disabled or not useful yet.

## When Steps Are Missing

The wizard intentionally hides or skips steps in some cases:

- no `yt-dlp` installed: no YouTube-start step
- no usable MIDI path: MIDI-related steps do not matter
- imported MIDI already has lyrics: the separate lyrics-for-MIDI step can be skipped
- no separation or transcription backend configured: advanced lyrics helper actions are limited

## Wizard Defaults Matter

The `Settings -> Wizard` section strongly influences the wizard experience:

- default creator name saves repetitive typing
- MIDI mode changes whether the wizard expects a MIDI-centered path
- preferred separation engine affects which backend is chosen first
- preferred transcription engine affects whether local or cloud transcription is preferred

## Related Pages

- [Settings Overview](../settings/overview.md)
- [Quick Start](../quick-start.md)
- [External Tools](../tools/overview.md)
