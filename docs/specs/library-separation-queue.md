# Library Separation Queue

This internal spec tracks extending audio separation so multiple existing
library songs can be queued directly from the Library view.

## Purpose

Yass Reloaded can already run audio separation for a song opened in the editor,
and USDB Search can queue separation after importing songs. Existing local songs
should get the same queue-based workflow without requiring each song to be
opened in the editor first.

The main user need is batch processing: select several library songs, start
audio separation once, and let Yass work through the songs sequentially with
visible status, cancellation, and error reporting.

## Implementation Gap Assessment

Current implemented foundation:

- `specs/separation-feature.md` documents the current separation behavior.
- `YassSongList` already has a Library context-menu action named
  `edit_audio_separate`, but it only supports one selected song and opens the
  song in the editor before starting MVSEP separation.
- `YassActions.runQuietLocalSeparationForSongFile(...)` can load a song file,
  run the configured preferred separation provider, assign generated stems, and
  store the song file in the background.
- `UsdbImportQueueService` already owns a visible queue dialog, status logs,
  cancellation, and a single-thread separation executor for queued
  post-import separation.
- `UsdbImportQueueDialog` is currently tied to USDB import naming and job
  metadata, but its table/status/cancel UI is suitable for generic queued song
  work.

Missing for implementation:

- A Library action that accepts multiple selected songs.
- A direct menu/toolbar entry to open the queue without opening USDB Search.
- Separation-only queue jobs that do not require a `UsdbSongSummary`.
- Queue dialog labels and mode text that make sense for both import and
  separation-only jobs.
- Duplicate active-job detection for library song files.
- Detection and display for songs that already have separated stems assigned.
- A non-destructive tag assignment rule for quiet queued separation so existing
  valid `#VOCALS` and `#INSTRUMENTAL` values are not silently overwritten.
- Focused tests for queueing, state transitions, duplicate handling, and
  existing-tag preservation.

## Candidate User Flow

1. User opens the Library view.
2. User selects one or more songs.
3. User chooses `Audio separieren` from the Library context menu or an
   equivalent Library/Extras action.
4. Yass validates that at least one separation provider is configured.
5. Yass adds each selected song as a separate queue job.
6. The queue dialog opens immediately and shows the selected songs.
7. Jobs run one at a time through the existing separation executor.
8. Each successful job refreshes its Library entry.
9. Failed jobs stay visible with their error; later queued jobs continue.

The queue should also be openable directly through both the Library context menu
and the Library/Extras main menu as `Queue anzeigen` / `Open Queue`. This action
should not require USDB login or an active USDB Search dialog.

## Recommended Technical Direction

Prefer evolving the existing USDB import queue into a more generic song-work
queue rather than creating a second dialog. Keep the existing
`UsdbImportQueue*` class names for the first implementation to avoid broad
rename churn, but add an explicit job mode to the domain model:

- `IMPORT`
- `IMPORT_AND_SEPARATE`
- `SEPARATE_EXISTING_SONG`

For separation-only jobs, store:

- song text file path
- display name from the selected `YassSong`
- optional artist/title metadata for display and duplicate checks

USDB import jobs should keep their existing behavior. After import, jobs with
`IMPORT_AND_SEPARATE` should continue to use the same separation executor as
library separation jobs.

The Library action should call a new `YassActions` method such as
`enqueueLibrarySeparationForSelectedSongs()` or
`enqueueLibrarySongSeparation(List<YassSong>)`. `YassSongList` can keep the
context-menu wiring local but should delegate all queue and configuration logic
to `YassActions`.

## Queue Behavior Rules

- Separation-only jobs must not open songs in the editor.
- Jobs must run sequentially so local CPU/GPU work and MVSEP polling do not
  overlap unexpectedly.
- A failure for one song must not stop later queued songs.
- Cancel should stop queued or running separation where the provider supports
  interruption.
- Finished and canceled jobs should remain removable through the existing
  remove-finished action.
- Adding the same song while it already has a non-terminal queue job should be
  ignored or reported as already queued.
- Jobs for songs that already have separated stems assigned should remain
  queueable, but the queue row or detail log should show a clear hint before
  processing starts.
- Existing valid `#VOCALS` and `#INSTRUMENTAL` assignments should not be
  overwritten silently by quiet queued separation.

## UI Considerations

The Library context-menu item should allow multiple selected songs. It should be
enabled when:

- Library view is active
- one or more songs are selected
- at least one separation provider is configured

If no separation provider is configured, the action should show the same kind of
clear warning used by editor separation.

The direct queue-open action should be visible outside USDB Search in both the
Library context menu and the Library `Extras` main menu near USDB
Search/Compare. It should open the existing queue dialog even when the queue is
empty.

The queue dialog mode column should show:

- `Import`
- `Import + Separation`
- `Separation`

If the dialog title remains based on `lib_usdb_import_song`, add a generic title
such as `Song Queue` / `Song-Queue` so the dialog no longer appears USDB-only.

## Data And Configuration

Existing configuration should drive provider choice:

- `separation-preference`
- `mvsep-api-token`
- `mvsep-model`
- `mvsep-model-type`
- `mvsep-output-format`
- `mvsep-instrumental-default`
- `audiosep-python`
- `audiosep-model`
- `audiosep-model-dir`
- `audiosep-output-format`
- `audiosep-health-ok`

Affected song tags:

- `#VOCALS`
- `#INSTRUMENTAL`

The implementation should reuse the existing generated stem naming rules from
`SeparationRequest` and provider services.

## Regression Coverage

Add focused tests for:

- library selection with multiple songs enqueues one separation-only job per
  song
- no selected songs does not enqueue anything
- missing separation configuration shows a warning and does not enqueue jobs
- duplicate active song-file jobs are not added twice
- separation-only jobs use the single-thread separation executor
- failed separation marks only that job failed and lets following jobs continue
- successful separation refreshes the Library entry
- already separated songs get a visible queue hint before processing starts
- existing valid `#VOCALS` and `#INSTRUMENTAL` tags are preserved during quiet
  queued separation
- queue dialog mode text distinguishes `Import`, `Import + Separation`, and
  `Separation`
- direct queue-open action opens the queue dialog without USDB Search

## Decisions

- Songs that already have separated stems assigned remain queueable, but their
  queue entry should show a clear hint.
- The direct queue-open action should be available from both the Library context
  menu and the Library `Extras` main menu.
- Keep the existing `UsdbImportQueue*` class names during the first
  implementation.
