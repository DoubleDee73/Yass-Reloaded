---
title: Settings Overview
---

# Settings Overview

Yass Reloaded groups its settings into several areas in the options dialog. This page explains what each area is for and when a setting actually matters.

## How the Settings Dialog Is Structured

The left tree is divided into these top-level groups:

- `Library`
- `Metadata`
- `Error Checking`
- `Editor`
- `Wizard`
- `External Tools`
- `Advanced`

Not every user needs every area. If you mainly edit existing songs, the most important sections are usually `Library`, `Editor`, and `External Tools`.

## Library

### Setup

This section defines the practical base environment for Yass Reloaded:

- default programs
- song directory
- playlist directory
- cover directory
- import directory
- fanart.tv API key
- interface language

Use this area when:

- you set up Yass Reloaded on a new machine
- your library moved to a different folder
- you want fanart.tv-based cover search to work

### Groups (1) and Groups (2)

These sections control how songs are grouped in the library.

Use them when:

- you browse by language, edition, folder, or other metadata
- you want custom grouping behavior in the library

### Sorting

Sorting affects how artists, titles, and metadata are ordered in the song library.

This is useful for:

- moving articles like "The" to the end
- tuning how library browsing behaves
- making grouped views feel more consistent

### Printer

This area matters when you print song lists or export printable overviews.

### Filetypes

This area controls how text files and encodings are handled.

It becomes important when:

- you work with older song files
- you migrate songs between systems
- you need to force UTF-8 behavior

## Metadata

### Languages

Configure the known language values used throughout Yass Reloaded.

This matters for:

- library filtering
- metadata cleanup
- language-aware sorting and grouping

### Editions

Configure reusable edition values for songs and collections.

### Genres

Configure the genre vocabulary used in metadata tagging.

This is useful when:

- you want genre suggestions to stay consistent
- you use MusicBrainz or other metadata helpers and still want normalized values

## Error Checking

These sections define how Yass Reloaded checks songs for common problems.

### Tags

Checks metadata consistency and missing or invalid values.

### Images

Checks cover, background, and media-related expectations.

### Text

Covers font and text-related issues.

### Page Breaks

Checks page break and phrase-structure related problems.

### Score

Deals with score-related validation such as golden-note assumptions or bonus-related checks.

These sections matter most if you use the library as a batch quality-control tool.

## Editor

### Design

Controls the editor look and visual behavior.

Useful when:

- you want a clearer sheet view
- you need better readability on your display
- you switch often between relative and absolute pitch workflows

### Control

This section controls mouse-gesture and sketch-related behavior.

It matters if you prefer gesture-heavy editing or want to tune how direct note manipulation feels.

### Keyboard

Configure keyboard-related behavior such as the virtual piano layout and shortcut-oriented control preferences.

Useful when:

- you use note-tapping heavily
- you use QWERTY, QWERTZ, or AZERTY layouts
- you depend on keyboard-driven editing

### Spelling

This area is relevant for hyphenation and spelling support in lyrics editing.

## Wizard

### Song Creation Defaults

This section controls how the create wizard behaves by default.

Important settings include:

- default creator name
- MIDI handling strategy
- preferred separation engine
- preferred transcription engine

These defaults matter because the wizard adapts to what is actually configured.

### yt-dlp

This section is where YouTube download preferences are configured.

When `yt-dlp` is available, you can configure:

- audio format
- audio bitrate
- video codec
- video resolution

If `yt-dlp` is not installed or not detected, this page becomes informational rather than actionable. In that case Yass Reloaded cannot offer the YouTube-driven wizard start in the same way.

## External Tools

This is the most important settings group for advanced workflows.

### Locations

This page defines tool paths and cache locations.

It includes:

- song list cache
- playlist cache
- cover image cache
- FFmpeg path
- yt-dlp path
- Aubio path

Use this page when:

- FFmpeg is not detected automatically
- yt-dlp should be used from a custom location
- Aubio is installed separately and should be picked up for pitch analysis

### MVSEP

This page configures the cloud separation service MVSEP.

Relevant settings include:

- API token
- separation model
- model type where applicable
- output format
- instrumental default preference
- polling interval

If no API token is configured, MVSEP cannot be used even though the page is still visible.

### audio-separator

This page configures the local Python-based stem separation workflow.

Relevant settings include:

- Python executable
- separation model
- model directory
- output format
- health check
- install/update button

If Python is missing or the health check fails, the page still exists, but the useful actions are reduced. In particular, model discovery and updates depend on a working Python environment.

### Transcription

This page combines OpenAI-based transcription and local WhisperX configuration.

OpenAI section:

- API key
- transcription model

WhisperX section:

- Python executable
- module invocation vs direct command
- command path
- default model
- device
- compute type
- transcript cache folder
- health check and update button

Important behavior:

- if model, device, or compute type are set to `Auto`, Yass Reloaded can apply recommended runtime values after a health check
- if you choose a manual value, Yass Reloaded should not silently overwrite it
- the update button is only useful once a valid Python-based installation exists

## Advanced

### Audio/Video

Advanced audio behavior and low-level playback options live here.

### Debug

This area is intended for troubleshooting and development-oriented diagnostics.

Most users can ignore it until something behaves unexpectedly.

## Related Pages

- [Wizard Overview](../wizard/overview.md)
- [External Tools](../tools/overview.md)
- [Library Overview](../library/overview.md)
