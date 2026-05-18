# MVSEP Ogg Output

## Status

Implemented/current. MVSEP output choices include Ogg/Vorbis and Ogg/Opus as
local targets.

## Purpose

MVSEP does not need to provide Ogg variants directly. Yass can request WAV stems
from MVSEP and transcode them locally with FFmpeg so users can keep smaller open
audio files in song folders.

## User-Facing Behavior

- MVSEP output-format UI includes:
  - `Ogg/Vorbis`
  - `Ogg/Opus`
- Existing choices remain compatible:
  - `wav`
  - `flac`
  - `mp3`
- If FFmpeg is missing or transcoding fails, the separation is treated as failed
  and song tags are not updated to missing Ogg/Opus files.

## Core Rules

- `Ogg/Vorbis` stores property value `ogg-vorbis`.
- `Ogg/Opus` stores property value `ogg-opus`.
- For both Ogg targets, the remote MVSEP request uses WAV as the API output
  format.
- After download, Yass transcodes WAV stems to the requested local target.
- `SeparationResult` must point to the final Ogg/Opus files, not the
  intermediate WAV files.
- In editor mode, successful intermediate WAV files are moved into the editor
  audio cache using the same naming scheme as other playable/editable WAV
  transcodes.
- If moving an intermediate WAV into the cache fails, log a warning and leave it
  in place. The final Ogg/Opus result remains authoritative.
- Ogg/Vorbis uses `.ogg` and `libvorbis`.
- Ogg/Opus uses `.opus` and `libopus`.
- Encoding quality follows the existing yt-dlp audio quality settings.

## Data And Configuration

- Property: `mvsep-output-format`
- FFmpeg path: configured `ffmpegPath`, with existing fallback behavior.
- Local output extensions:
  - Ogg/Vorbis: `.ogg`
  - Ogg/Opus: `.opus`
- Editor WAV cache uses the existing `.yass/temp` audio-cache naming scheme.

## Code Entry Points

- `src/yass/integration/separation/mvsep/MvsepOutputFormat.java`
  - enum values, persisted values, API values, file extensions
- `src/yass/integration/separation/mvsep/MvsepSeparationService.java`
  - remote request output format
  - local FFmpeg transcode
  - intermediate WAV cache handling
- `src/yass/options/MvsepPanel.java`
  - settings UI
- `src/yass/integration/separation/mvsep/MvsepStartDialog.java`
  - per-job output-format UI

## Regression Coverage

- `MvsepOutputFormatSpec`
  - Ogg values parse correctly
  - Ogg values map to WAV for MVSEP API requests
  - extensions are `.ogg` and `.opus`
- `MvsepSeparationServiceSpec`
  - downloaded WAV files transcode to Ogg/Vorbis or Ogg/Opus
  - final `SeparationResult` points to Ogg/Opus files
  - FFmpeg quality follows yt-dlp settings
  - editor-mode intermediate WAV files move into the WAV cache naming scheme
  - missing/failed FFmpeg leaves song tags unchanged

## Extension Notes

- Keep remote and local format concepts separate. MVSEP API format can be WAV
  while the local target is Ogg/Opus.
- Do not add MVSEP-specific quality settings unless yt-dlp quality mapping
  proves insufficient.
- If MVSEP later supports Ogg directly, preserve the current local-transcode path
  as a fallback because it keeps behavior predictable.
