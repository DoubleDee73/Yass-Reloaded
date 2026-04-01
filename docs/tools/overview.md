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

### OpenAI

Used for:

- transcript-based note alignment workflows

### MVSEP

Used for:

- cloud-based stem separation

### audio-separator

Used for:

- local stem separation

### fanart.tv

Used for:

- cover lookup workflows from the library
- fetching alternate cover candidates for a song folder

## Configuration

Most tool setup is available under:

- `Settings -> External Tools`

The fanart.tv API key is configured in:

- `Settings -> Library -> Setup`

The wizard defaults for choosing local vs online engines are configured in:

- `Settings -> Wizard`

## Health Checks

Several tool integrations provide health checks to verify:

- executable paths
- package availability
- FFmpeg presence
- WhisperX runtime suitability

## Recommended Mindset

Not every workflow needs every tool:

- use FFmpeg as the baseline
- add WhisperX for local transcription and timing support
- use OpenAI for cloud transcription workflows
- use MVSEP or audio-separator when you need stems
- use fanart.tv for library polish and media management

## Goal of This Section

This section will gradually expand into dedicated setup guides for each supported integration.

## Related Pages

- [Installation](../installation.md)
- [Settings Overview](../settings/overview.md)
- [Wizard Overview](../wizard/overview.md)
- [Troubleshooting](../reference/troubleshooting.md)
