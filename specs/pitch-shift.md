# Pitch Shift

## Status

Implemented/current. Song-level global pitch-shift correction covering metadata
storage, tuning analysis, a dialog UI, non-destructive live playback preview, and
an optional permanent bake into audio files.

## Purpose

Some recordings are tuned slightly above or below concert pitch while remaining
internally consistent. Yass estimates that global tuning offset from Aubio pitch
data and lets the user apply a controlled, song-local correction so that audio
playback, detected pitch lines, and melody alignment all sit on the chromatic
grid. Cents are the precise unit; semitones are the musician-friendly display
(`100 cents = 1 semitone`, negative = source is flat, positive = source is sharp).

## User-Facing Behavior

- Entry point: `Extras > Pitch Shift…` (the `pitchShift` action), discoverable for
  any open song. Context-sensitive buttons communicate when an action is
  unavailable instead of hiding it.
- **Analyze** is enabled when Aubio is configured and any audio track is loaded.
  It runs on whichever track the header's audio selector points at, preferring the
  declared `#INSTRUMENTAL` track (the most reliable tuning reference). When Aubio
  is unavailable the button is disabled with an explanatory tooltip, but
  view/edit/apply/remove of a manual value still works.
- The dialog shows any stored correction, the detected source offset
  (`±N cents (±0.NN semitones)`), and a low-confidence note when the estimate is
  marginal. **Apply recommended** stores the suggested correction; offsets below
  5 cents report "no correction needed".
- **Render to Files…** (the permanent bake) is enabled only with a stored non-zero
  correction and an available FFmpeg backend; otherwise disabled with a tooltip.
- Loading audio never auto-scans for an offset. Analysis is always an explicit
  user action.

## Core Rules

- **Apply exactly once.** A correction is applied either live (comment present,
  source files untouched) or baked into the files (comment absent), never both.
- **Audio is the single source of truth for the corrected pitch view.** The
  correction is baked into the playback temp WAV via an FFmpeg filter. Pitch
  detection then runs on that already-shifted temp WAV, so the detected pitch
  list is *already corrected*. Therefore the renderer and melody alignment must
  **not** re-apply the cents shift — they consume the detected list as-is (only
  the unrelated whole-semitone display transpose is composed in). Re-applying
  cents on top of audio-derived data is a double application and is the bug this
  rule exists to prevent.
- Analysis uses the **raw** pitch frames (`detectPitchWithRaw` →
  `analyzeTuningOffset`), never the Viterbi-smoothed list. The smoothed list snaps
  frequencies onto the chromatic grid, which reads ~0 cents for every frame and
  destroys the tuning signal.
- The offset is a **circular mean** of each frame's within-semitone phase, with the
  resultant length R reported as `concentration` (0..1). Two thresholds govern R:
  - Below `PitchDetector.MIN_TUNING_CONCENTRATION` (**0.15**) → returned
    `unavailable` ("too inconsistent"); no offset reported.
  - Between 0.15 and `PitchShiftMetadata.CONFIDENCE_CONCENTRATION_THRESHOLD`
    (**0.25**) → an offset is returned with a low-confidence note.
  - Above 0.25 → shown without caveat.
  These thresholds are empirical (calibrated on AC/DC vocal vs instrumental), not
  derived.
- A global pitch shift can only fix a uniform tuning offset. Relative/drifting
  detuning produces a flat offset distribution with low R and is correctly
  reported as "no reliable offset" — out of scope.
- Offsets below 5 cents do not recommend or auto-persist a correction (gates
  recommendation only, not manual storage).

## Data And Configuration

- **Song metadata:** `#COMMENT:pitchShiftCents=-18.0` (pseudo tag inside
  `#COMMENT`). Other comment keys (`key=`, `v=`, etc.) are preserved. The live
  preview and corrected display read this value but never change it; only a
  successful bake removes it.
- **External tools:** Aubio (analysis), FFmpeg (live preview + bake). The filter
  prefers `rubberband=pitch=<ratio>` when present, else a duration-preserving
  `asetrate`/`aresample`/`atempo` chain. `ratio = 2^(cents/1200)`.
- **Temp-WAV cache:** the cents value is folded into `buildTempAudioHash` /
  `resolveTempFilename` **only when non-zero**, so un-corrected songs keep their
  exact prior cache key and byte-identical conversion.
- **Pitch-line detection gate:** the editor only populates the detected pitch list
  (and therefore draws audio-derived pitch lines) when the `debug-waveform`
  property is enabled and the selected track is `#VOCALS`.
- **Baked filenames:** collision-safe `<name> (Pitch Shifted).<ext>`; originals are
  never overwritten. Only `#AUDIO`/`#VOCALS`/`#INSTRUMENTAL` are baked; the legacy
  `#MP3` tag is intentionally not baked.

## Code Entry Points

- `src/yass/analysis/PitchShiftMetadata.java` — `parse`/`upsert`/`remove`,
  cents↔semitone conversion, confidence thresholds. `YassTable` delegates via
  `getPitchShiftCents`/`setPitchShiftCents`/`removePitchShiftCents`.
- `src/yass/analysis/PitchDetector.java` — `detectPitchWithRaw`,
  `analyzeTuningOffset`, `TuningOffsetAnalysis`. `applyPitchShift`/`correctedPitch`
  still exist and compose the integer display transpose; the render/align callers
  now pass `0.0` cents (transpose-only) because the audio path already applies the
  shift.
- `src/yass/PitchShiftDialog.java` — the modal UI, wired via the `pitchShift`
  action.
- `src/yass/YassPlayer.java` — `generateTemp` / `buildPlaybackAudioFilter` inject
  the pitch-shift filter; `buildTempAudioHash` folds cents into the cache key;
  `setPitchShiftCents`/`getPitchShiftCents`.
- `src/yass/analysis/PitchShiftRenderer.java` — `audioFilter(cents)` (shared by
  preview and bake) and `renderToFile`.
- `src/yass/analysis/PitchShiftBakeService.java` — all-or-nothing bake
  orchestration with an injected `Renderer`.
- `src/yass/YassActions.java` — `applyPitchShiftToPlayback` (re-converts on value
  change), `bakePitchShiftToAudioFiles`/`applyPitchShiftBakeResult`,
  `currentPitchShiftCents` (now drives only the audio path), and the
  alignment/split call sites that pass `0.0` cents.
- `src/yass/SongHeader.java` — `determinePitches` runs detection on the shifted
  temp WAV (`player.getTempFile()`) under `debug-waveform`.
- `src/yass/YassSheet.java` — `paintPitchWaveform` / `formatPitchDistribution`
  render the detected list directly (transpose only, no cents).

## Regression Coverage

- `PitchShiftMetadataSpec` — cents↔semitone round-trip, parse/upsert preserving
  other comment keys, idempotent upsert, remove-only-pitchShift, `key=` does not
  bias, sub-5-cents ⇒ no recommendation, confidence threshold.
- `PitchDetectorSpec` — positive drift, outlier robustness, too-few-frames,
  raw-vs-smoothed tuning analysis.
- `PitchShiftRendererSpec` — filter string for a known cents value (rubberband vs
  fallback); cents=0 produces no filter; cache key differs by cents.
- `PitchShiftBakeServiceSpec` — cancel leaves tags/files/comment unchanged; full
  success retags and removes only `pitchShiftCents`; failed partial render rolls
  back; empty candidate counts as failure; collision-safe naming; missing
  FFmpeg/backend message; shared source rendered once.

## Extension Notes

- **Do not re-introduce a cents shift in the renderer or alignment.** The audio
  path (detection on the shifted temp WAV) is the single point where the
  correction enters the visual/alignment data. The earlier design applied cents a
  second time via `correctedPitch`, which double-corrected; it was masked only
  because Viterbi grid-snapping flattened `rawFrequency` to a ~0 residual, so the
  lines barely moved while the audio was shifted. If detection-on-temp is ever
  bypassed, revisit this decision deliberately rather than sprinkling cents back
  into both consumers.
- Pitch lines are only drawn from audio under `debug-waveform`; the corrected-view
  behavior is therefore only observable with that flag on.
- Threshold values (0.15 / 0.25 / 5 cents) are empirical; recalibrate against new
  material if songs land in the 0.10–0.15 gap. `analyzeCurrentAudioTuningOffset`
  logs an INFO `[PitchShift]` line (offset/concentration/MAD) for every analyzed
  song, including rejected ones, to support recalibration.
- Re-detection cost: changing the cents value re-runs the whole-file FFmpeg
  conversion (and, with `debug-waveform`, re-detection). This is "apply then hear",
  not real-time scrubbing; the temp cache means an unchanged value replays
  instantly.
