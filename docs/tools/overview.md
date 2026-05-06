---
title: External Tools
---

# External Tools

Yass Reloaded integrates with several external tools to extend song creation and editing workflows.

## Core Tools

### FFmpeg

Used for:

- audio conversion
- broad file format support
- preprocessing for analysis and transcription

### WhisperX

Used for:

- local transcription
- timestamped word alignment
- note alignment support
- isolated Python runtime when needed

### OpenAI

Used for:

- transcript-based note alignment workflows

### LrcLib

Used for:

- online lyric lookup in the song creation wizard
- importing ready-made lyric text and timed lyric lines when available
- providing a lightweight fallback before full transcription is needed

### MVSEP

Used for:

- cloud-based stem separation
- API-key based online vocal/instrumental separation
- choosing a Vocal Model and Audio Format for returned stems

### audio-separator

Used for:

- local stem separation
- optional model caching
- isolated Python runtime when needed

### fanart.tv

Used for:

- cover lookup workflows from the library
- fetching alternate cover candidates for a song folder

### USDB

Used for:

- direct song search against the live USDB service
- compare and review workflows against the current USDB version of a song
- importing songs from USDB into the local library
- pushing reviewed edits back to USDB when the current account has edit rights

### USDB Syncer

Used for:

- local USDB song search via the Syncer SQLite database
- refreshing the locally cached USDB song list
- supporting `.usdb`-based links between local songs and USDB entries

## Configuration

Most tool setup is available under:

- `Settings -> External Tools`

Python-based tools use a two-level runtime model:

- `Settings -> External Tools -> Locations` can define a shared `Default Python executable`
- WhisperX and audio-separator can each use their own tool-specific Python runtime
- if a tool-specific Python field is empty, Yass falls back to the shared default Python
- if `Update package` is used while the tool-specific Python field is still empty, Yass creates a dedicated virtual environment for that tool and stores its Python path automatically

The fanart.tv API key is configured in:

- `Settings -> Library -> Setup`

USDB account-related setup is configured in:

- `Settings -> Library -> Setup`

LrcLib does not require a dedicated API key or a separate setup page.

The wizard defaults for choosing local vs online engines are configured in:

- `Settings -> Wizard`

The USDB Syncer path is configured in:

- `Settings -> External Tools -> Locations`

## Health Checks

Several tool integrations provide health checks to verify:

- executable paths
- package availability
- FFmpeg presence
- WhisperX runtime suitability
- audio-separator package availability and script resolution

## Recommended Mindset

Not every workflow needs every tool:

- use FFmpeg as the baseline
- add WhisperX for local transcription and timing support
- use OpenAI for cloud transcription workflows
- use LrcLib first when you mainly want existing lyrics with minimal setup
- use MVSEP or audio-separator when you need stems
- use direct USDB integration when you want compare/import/edit workflows against the live song database
- use fanart.tv for library polish and media management
- use USDB Syncer when you want faster USDB search and a local DB-backed lookup path

## Goal of This Section

This section will gradually expand into dedicated setup guides for each supported integration.

## Related Pages

- [Installation](../installation.md)
- [Settings Overview](../settings/overview.md)
- [Wizard Overview](../wizard/overview.md)
- [Troubleshooting](../reference/troubleshooting.md)
