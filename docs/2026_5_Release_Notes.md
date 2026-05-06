# Yass Reloaded 2026.5 Release Notes

These release notes summarize the **user-facing changes** between `v2026.4` and `2026.5_release`.

## Highlights

- New **USDB integration** for search, compare, import, and direct editing workflows
- New **LrcLib integration** in the create-song wizard
- Smarter **wizard reuse and metadata flow** for lyrics, separation, and transcription
- Improved **fanart.tv cover picker** with better search and preview handling
- More reliable **Align to Melody** behavior and several editor stability fixes
- Cleaner setup for **Python-based tools**, `yt-dlp`, and external-tool health checks

## USDB Integration

- New **Search USDB** workflow in the library
  - result markers show whether a song is unmatched, already present locally, already linked by `.usdb`, or already queued
- New **Compare with USDB** workflow
  - available from the library and editor for matched songs
  - diff dialog with stronger change highlighting
  - selective copy from USDB to local song
  - save reviewed changes locally or submit them back to USDB
- New **USDB import queue**
  - non-blocking import workflow
  - progress and detail view per job
  - better handling of follow-up steps such as images and optional separation
- New **USDB login and session support**
  - stored credentials and browser-cookie based login flows
  - distinction between normal access and direct-edit capable accounts
- Optional **USDB Syncer bridge**
  - can use the local Syncer database first for faster search
  - supports cached song-list refresh and `.usdb`-based song relationships

## Create Song Wizard and Lyrics

- New **LrcLib search** directly from the lyrics step
  - can import plain lyrics or timed lyric lines when available
- The wizard now asks for **Artist** and **Title** earlier in the lyrics flow
  - suggested values are derived from YouTube metadata or filenames
  - the confirmed values are reused later for LrcLib, MusicBrainz, and naming logic
- Better **wizard reuse** of previous work
  - existing separation or transcription data can be reused instead of always starting over
- Better **separation follow-up**
  - if the wizard finishes without separation, Yass can still offer separation once the song opens in the editor
- Improved lyric transfer and transcript application
  - corrected lyrics are applied more consistently
  - lyric-driven rebuild paths behave more predictably
- Better handling of YouTube-derived metadata and subtitle inputs in the wizard

## Editor and Align to Melody

- **Align to Melody** was refined in several ways
  - better handling of octave placement
  - more cautious handling of weak tails and short gaps
  - stronger heuristics for deciding where a sung note really starts and ends
  - additional debug logging to explain alignment decisions
- Better subtitle cleanup for imported rolling captions
  - overlapping YouTube-style subtitle lines are collapsed more cleanly before they reach later workflows
- Improved **Undo/Redo** behavior in absolute pitch workflows
  - the visible octave window is restored more reliably
- Fixed early-sheet stability issues
  - safer handling when editor geometry is not initialized yet
- Improved keyboard behavior
  - held keys no longer interfere as easily with multi-press shortcuts
  - menu navigation keeps `Up` and `Down` when a Swing menu is open

## fanart.tv Cover Workflow

- Improved **fanart.tv search flow**
  - stronger MusicBrainz matching
  - better album-title resolution
  - more detailed logging for debugging lookup problems
- Improved **cover picker dialog**
  - clearer visual selection state
  - gray tile backgrounds and stronger highlight for the selected tile
  - `Enter` in the search field jumps through matches instead of triggering an immediate download
  - repeated `Enter` cycles through all matches
- Improved preview loading
  - lazy loading of visible tiles
  - fallback from preview URL to original asset URL
  - connection-failure limits to avoid noisy repeated errors
- Preview URLs now prefer `assets.fanart.tv` handling over the older `images.fanart.tv` path

## External Tools and Runtime Setup

- Cleaner handling of **Python-based tools**
  - shared default Python runtime in `External Tools -> Locations`
  - tool-specific Python overrides for WhisperX and audio-separator
  - `Update package` can create and persist dedicated virtual environments automatically
- Expanded health-check and update flows for:
  - WhisperX
  - audio-separator
  - FFmpeg-related setup helpers
- Improved `yt-dlp` integration
  - configured executable path can be used more directly
  - better handling in wizard and download-related flows
- Additional startup and setup helpers were added to make missing-tool situations easier to diagnose

## Documentation and Settings

- Expanded **GitHub Pages documentation**
  - library, tools, settings, wizard, and editor pages now cover more of the current workflows
- Added tutorial video links to the README and documentation start page
- Settings pages now document:
  - USDB and Syncer setup
  - default Python runtime behavior
  - LrcLib availability
  - WhisperX/audio-separator runtime fallback behavior

## Bug Fixes and Stability

- Multiple fixes in wizard transitions and reuse logic
- More robust handling of fanart and subtitle edge cases
- Better resilience in editor navigation and playback-related shortcuts
- Various polish and consistency improvements across dialogs, settings, and workflow integration
