---
title: Installation
---

# Installation

Yass Reloaded runs on Windows, macOS, and Linux.

## Recommended Approach

For most users, the easiest option is to use the packaged release from the GitHub releases page instead of launching the JAR manually.

## Windows

The recommended option is the Windows installer from the [Releases](https://github.com/DoubleDee73/Yass-Reloaded/releases) page.

- Java is bundled
- no manual runtime setup is usually required

## macOS

Use the matching DMG from the release page:

- Apple Silicon: `arm64`
- Intel: `x64`

If macOS blocks the app, use right click -> Open once to confirm the launch.

## Linux

Depending on the release, you can either:

- install the `.deb` package
- or run the fat JAR directly

FFmpeg should be installed through the system package manager.

## Java

If you run the JAR manually, Java 21 is required.

Example:

```bash
java -jar Yass-Reloaded.jar
```

## FFmpeg

FFmpeg is required for broad audio format support and several editor and wizard features.

Typical installation:

- Windows: install FFmpeg and make sure `ffmpeg` and `ffprobe` are available
- macOS: `brew install ffmpeg`
- Linux: install via your package manager

If Yass Reloaded cannot find FFmpeg, check:

- PATH configuration
- `External Tools -> Locations`
- your `user.xml` in `~/.yass`

FFmpeg is especially important for:

- audio conversion
- waveform generation
- transcription preparation
- stem separation workflows
- broad format support for imported media

## First Setup

After the first start, configure:

1. your song library folder
2. playlist and cover directories if needed
3. FFmpeg path if auto-detection did not work

The basic directory setup can be found in:

- `Settings -> Library -> Setup`

## Useful Follow-Up Pages

- [Quick Start](quick-start.md)
- [External Tools](tools/overview.md)
- [Troubleshooting](reference/troubleshooting.md)
