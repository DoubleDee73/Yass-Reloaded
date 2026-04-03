# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Yass Reloaded is a Java 21 karaoke editor for UltraStar/UltraStar Deluxe song files. It provides a GUI (Swing + JavaFX) for editing notes, timing, and lyrics, managing song libraries, and detecting errors in karaoke files. It is a fork of Yass 2.4.3, modernized from Java 8 to Java 21.

## Build Commands

```bash
# Full build (fat JAR with all dependencies)
mvn clean package

# Build without running tests
mvn clean package -DskipTests

# Run tests only
mvn test

# Run a single test class
mvn test -Dtest=YassTableSpec

# Run the application (after building)
java -jar target/Yass-Reloaded.jar
```

The build produces `target/Yass-Reloaded.jar` as a fat JAR containing all dependencies.

Tests are written in Groovy using the Spock framework and live in `test/groovy/yass/`. There are currently 3 spec files: `YassTableSpec`, `YassRowSpec`, and `TitleCaseConverterSpec`.

## Source Structure

```
src/
  yass/               # Main application code (all primary logic)
    analysis/         # Pitch detection, musical key analysis
    autocorrect/      # Auto-correction logic for karaoke files
    extras/           # Miscellaneous utilities
    ffmpeg/           # FFmpeg integration and locators (FfmpegDownloader)
    filter/           # Song library filtering
    hyphenator/       # Text hyphenation support
    keyboard/         # Keyboard input handling
    logger/           # Logging utilities
    musicalkey/       # Musical key enums and detection
    musicbrainz/      # MusicBrainz metadata integration
    options/          # App configuration, preferences, enums
    print/            # PDF printing support
    renderer/         # YassSheet rendering (visual note display)
    resources/        # Bundled assets: i18n strings, icons, fonts, hyphenation data
    stats/            # Song statistics
    suggest/          # Suggestions/autocomplete
    titlecase/        # Title case conversion
    video/            # Video playback support
    wizard/           # Song creation wizard
  com/
    doubledee/ytdlp/  # yt-dlp download wrapper
    jfposton/ytdlp/   # yt-dlp Java interface
    nexes/wizard/     # Robert Eckstein's wizard framework
    graphbuilder/     # Graph builder desktop utilities
  plugins/            # Plugin framework
  themes/star/        # Star theme assets
  unicode/            # Unicode utilities
test/groovy/yass/     # Spock test specs
```

## Architecture

**GUI Framework:** Java Swing is the primary UI framework. JavaFX is used for media playback integration (javafx-media, javafx-swing bridge).

**Key classes:**
- `YassMain` — Application entry point and main frame
- `YassActions` — Swing actions wired to menu/toolbar items
- `YassSheet` — The visual note editor canvas (renders notes, playhead, lyrics)
- `YassTable` / `YassRow` — Model for UltraStar song data (table rows = song notes/lyrics lines)
- `YassPlayer` — Audio playback, FFmpeg integration
- `YassSongList` / `YassGroups` — Song library management
- `YassFile` / `UsdbFile` — UltraStar file I/O
- `YassProperties` — User configuration (stored in `~/.yass/user.xml`)
- `FfmpegDownloader` — Auto-download/install FFmpeg if not found

**Data model:** UltraStar songs are represented as `YassTable` (a `DefaultTableModel` subclass) containing `YassRow` objects. Each row represents a note, lyric line, pause, or header tag.

**Configuration:** User settings are persisted to `~/.yass/user.xml`. FFmpeg path can be set via `ffmpegPath` entry or auto-detected from PATH.

**Localization:** i18n string resources are in `src/yass/resources/i18/` (English, German, French, Spanish, Polish, Hungarian).

**External tool dependencies (runtime, not bundled):**
- FFmpeg — required for audio format support (AAC, OGG, OPUS, etc.)
- yt-dlp — optional, for YouTube downloads
- aubio — optional, for BPM detection and pitch visualization

**Local JARs in `lib/`:** Several legacy dependencies (JMF, texhyphj, jinput DLLs) are not available in Maven Central and are loaded from `lib/` as `system`-scoped dependencies in pom.xml.

## Versioning

The version is managed in `resources/version.properties` and filtered into the build via Maven resource filtering. Current release branch convention: `YYYY.N_release` (e.g., `2026.4_release`).
