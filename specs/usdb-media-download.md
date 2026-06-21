# USDB Media Download

## Status

Implemented/current. Covers the audio/video/cover/background download stage of a
USDB song import as of 2026.6. Documents current behavior plus the known 403
failure mode that motivates moving the audio+video download to a single yt-dlp
invocation.

## Purpose

When a song is imported from USDB, its meta tag line references media sources
(YouTube IDs/URLs for audio and video, image URLs for cover and background).
This feature downloads those assets via yt-dlp into the staging directory and
wires the resulting filenames into the `YassTable` header tags
(`#MP3`/`#AUDIO`, `#VIDEO`, `#COVER`, `#BACKGROUND`), producing a playable
UltraStar song folder.

UltraStar songs reference a dedicated audio file *and* a separate video file, so
the importer must end up with both: an extracted audio file (in the configured
audio format) and a video file (in the configured codec/resolution).

## User-Facing Behavior

- Triggered as part of a USDB import (single import or queued import via
  `UsdbImportQueueService`).
- Progress is surfaced through `UsdbImportProgressListener`: general status
  ("Started video download", "Finished media download"), per-song status
  ("Downloading video and audio…"), and raw yt-dlp output lines via
  `onDetailLog`.
- Cover and background downloads are best-effort: failure logs at INFO and the
  import continues without that image (`awaitOptionalImageFuture`).
- Audio/video download failure is fatal to the song import: it propagates as an
  `IOException` and the queue marks the song failed
  (`UsdbImportQueueService.runImport`).
- On any failure the staging directory is deleted (`prepareImportedSong`'s
  `finally` / `deleteDirectory`), so partial downloads are not left behind.

## Core Rules

- **Media source resolution** (`startMediaImport`): if the meta tags carry a
  video source (`v`), both video and audio are downloaded from it. Otherwise, if
  only an audio source (`a`) exists, only audio is downloaded. If neither exists,
  no media download happens.
- **Source normalization** (`normalizeMediaSource`): a bare 11-char YouTube ID
  or a `v=<id>` fragment is expanded to
  `https://www.youtube.com/watch?v=<id>`; anything else is passed through
  trimmed.
- **Audio download** (`downloadAudio`): format `bestaudio`, plus
  `applyAudioExtractionOptions` (`--extract-audio`, `--audio-format` from
  settings) and `applyCommonOptions` (`--ffmpeg-location`). Output template
  `<baseName>.%(ext)s`.
- **Video download** (`downloadVideo`): format from
  `buildVideoOnlyFormatString` (`bestvideo` + optional codec + optional
  resolution), plus `--no-audio` and `applyCommonOptions`. Output template
  `<baseName>.%(ext)s`.
- **File detection** (`detectNewestMediaFile`): after a download, the newest
  regular file in the staging dir matching `<baseName>.*`, not ending in
  `.part`, modified at/after `started - 2000ms`, and on the correct side of the
  audio/video classification (`isAudio` by extension). Missing file →
  `IOException("Imported media file could not be detected.")`. Audio and video
  therefore must not collide on the same output extension.
- **Tag wiring**: audio filename → both `#MP3` and `#AUDIO`; video filename →
  `#VIDEO`. The optional `.usdb` syncer meta file records each resource's
  resolved source URL.

## Known Failure Mode (motivation for change)

`startVideoAndAudioImport` launches the audio download and the video download as
**two concurrent yt-dlp processes against the same YouTube URL**
(`audioFuture` + `videoFuture` via `CompletableFuture.supplyAsync` on a shared
pool, joined by `thenCombine`). YouTube rate-limits two simultaneous stream
pulls of the same video from one IP and rejects one leg with
`HTTP Error 403: Forbidden` on "unable to download video data". The metadata
fetch succeeds, only the media pull fails — the signature of self-inflicted
rate-limiting, not a stale yt-dlp or auth problem. A single manual
`yt-dlp -f bestaudio <url>` succeeds because it is one request.

The accepted fix is to download audio and video in a **single yt-dlp
invocation** (download `bestvideo+bestaudio` and keep the muxed video while
extracting the audio track), eliminating the concurrent second process. The
song wizard already uses a single combined call as precedent
(`YouTube.buildDownloadRequest`, `YtDlpSupport.buildCombinedVideoFormatString`).

### Quality constraints the single-call path must preserve

- Must still produce **two files**: an extracted audio file in the configured
  audio format (`YTDLP_AUDIO_FORMAT`, optional `YTDLP_AUDIO_BITRATE`) and a
  video file honoring the configured codec/resolution (`YTDLP_VIDEO_CODEC`,
  `YTDLP_VIDEO_RESOLUTION`).
- `detectNewestMediaFile` must still resolve the audio file and the video file
  unambiguously (distinct extensions; `isAudio` classification holds).
- `#MP3`/`#AUDIO` point at the audio file, `#VIDEO` at the video file — unchanged
  from today.
- Audio-only imports (no `v` tag) keep their existing single `bestaudio` path.

## Data And Configuration

- Settings (in `~/.yass/user.xml`, edited via `YtDlpPanel`):
  - `ytdlp-audio-format` (`YtDlpAudioFormat`: m4a/AAC, ogg/Vorbis, opus)
  - `ytdlp-audio-bitrate` (`YtDlpAudioBitrate`)
  - `ytdlp-video-codec` (`YtDlpVideoCodec`: best, mp4 best, mp4 AVC)
  - `ytdlp-video-resolution` (`YtDlpVideoResolution`)
  - `ytdlpPath` (explicit yt-dlp binary; falls back to `yt-dlp` on PATH),
    `ytdlp-version` (remembered), `ffmpegPath`.
- UltraStar tags written: `#MP3`, `#AUDIO`, `#VIDEO`, `#COVER`, `#BACKGROUND`.
- External tools: yt-dlp (required for media), ffmpeg (required for
  extraction/muxing; located via `ffmpegPath`).

## Code Entry Points

- `yass.usdb.UsdbSongImportService`
  - `prepareImportedSong` — orchestrates media/cover/background stages on a
    4-thread daemon pool.
  - `startMediaImport` / `startVideoAndAudioImport` — **the concurrent
    audio+video download to collapse into one call.**
  - `downloadAudio`, `downloadVideo` — per-asset yt-dlp requests.
  - `executeYtDlp` — runs yt-dlp async, captures stderr → `IOException`.
  - `detectNewestMediaFile`, `isAudio`, `normalizeMediaSource`.
- `yass.YtDlpSupport` — `applyCommonOptions`, `applyAudioExtractionOptions`,
  `buildVideoOnlyFormatString`, `buildCombinedVideoFormatString`,
  `ensureExecutableAvailable`.
- `yass.wizard.YouTube.buildDownloadRequest` — existing single-combined-call
  precedent, including a fallback ladder (codec → no codec → `best`).
- `yass.usdb.UsdbImportQueueService.runImport` — consumes failures.

## Regression Coverage

- `UsdbSongImportServiceSpec` — currently only covers `buildFolderName` for
  illegal-character titles. The download flow (source normalization, audio/video
  format selection, file detection, tag wiring) is **not** yet covered.
- `UsdbImportQueueServiceSpec`, `UsdbImportQueueJobSpec` — queue-level behavior.

## Extension Notes

- Gap: no automated coverage of the media-download path. Any change to the
  audio+video download should add `UsdbSongImportService`-level tests for the
  yt-dlp request that gets built (format string, extract-audio + keep-video
  options) and for `detectNewestMediaFile` distinguishing the two outputs.
- The importer has **no fallback ladder** like the wizard; a single format
  failure is fatal. Consider mirroring the wizard's fallback if reliability
  needs to improve further.
- Keep the audio-only branch (no `v` tag) on its dedicated `bestaudio` path —
  collapsing only applies when both video and audio come from one source.
- Do not reintroduce a second concurrent yt-dlp process against the same URL;
  that is the documented cause of the 403 failures.
