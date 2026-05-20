# Library Separation Queue

## Status

Implemented/current. Yass Reloaded can queue audio separation for multiple
existing Library songs and can open the shared song queue directly without
opening USDB Search.

## Purpose

The feature lets users batch-process existing local songs with the same visible
queue workflow used by USDB import. It avoids opening every song in the editor
just to start separation, keeps long-running separation work sequential, and
keeps per-song status, cancellation, and failures visible.

## User-Facing Behavior

- In the Library context menu, `Separate Audio` accepts one or more selected
  songs and queues each selected song as a separate separation job.
- The Library context menu also offers `Open Queue`.
- The Library `Extras` menu also offers `Open Queue`, independent of USDB
  login or the USDB Search dialog.
- The queue dialog uses the generic `Song Queue` title and shows a `Mode`
  column with:
  - `Import`
  - `Import + Separate`
  - `Separation`
- Separation-only jobs do not open songs in the editor.
- Jobs for songs that already have valid `#VOCALS` or `#INSTRUMENTAL`
  assignments remain queueable, but the queue detail log shows a hint that
  existing valid stem files will not be overwritten automatically.
- Canceling a waiting separation-only job cancels the job without offering to
  delete the local song folder.

## Core Rules

- The existing `UsdbImportQueue*` classes remain the shared queue
  implementation.
- Queue jobs have an explicit mode:
  - `IMPORT`
  - `IMPORT_AND_SEPARATE`
  - `SEPARATE_EXISTING_SONG`
- Import job lookup for USDB Search ignores separation-only jobs.
- Duplicate active separation jobs are detected by normalized song text file
  path and skipped.
- Separation work uses the queue's single-thread separation executor, so queued
  separation jobs run sequentially.
- A failed separation job marks only that job failed and does not stop later
  queued jobs.
- Quiet queued separation must not silently overwrite valid existing
  `#VOCALS` or `#INSTRUMENTAL` assignments that point to existing files.

## Data And Configuration

Provider choice and separation output continue to use the existing separation
configuration:

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

## Code Entry Points

- `src/yass/YassSongList.java`
  - Library context-menu actions for queueing separation and opening the queue.
- `src/yass/YassActions.java`
  - `enqueueLibrarySongSeparation(...)`
  - `showSongQueue()`
  - `hasExistingSeparatedStemAssignments(...)`
  - `shouldAssignQuietSeparatedTrack(...)`
  - `runQuietLocalSeparationForSongFile(...)`
- `src/yass/usdb/UsdbImportQueueJob.java`
  - queue job modes and separation-only job metadata
- `src/yass/usdb/UsdbImportQueueService.java`
  - library separation enqueueing, duplicate detection, cancellation, and
    separation execution
- `src/yass/usdb/UsdbImportQueueDialog.java`
  - generic queue title, mode text, and import-only match indicators

## Regression Coverage

- `UsdbImportQueueJobSpec`
  - import mode mapping
  - separation-only job metadata and existing-separation hint flag
- `UsdbImportQueueServiceSpec`
  - multiple selected Library songs enqueue one separation job each
  - duplicate active song-file jobs are skipped
  - waiting separation-only cancellation does not delete the song folder
  - already separated songs get a queue hint
  - USDB active-job lookup ignores separation-only jobs
- `UsdbImportQueueDialogSpec`
  - mode text distinguishes import, import+separation, and separation-only jobs
- `YassActionsLibrarySeparationSpec`
  - existing separated stem assignments are detected
  - quiet separation does not overwrite valid existing stem assignments

## Extension Notes

- The first implementation intentionally keeps the `UsdbImportQueue*` class
  names to avoid a broad rename. A future cleanup may rename them to a generic
  song queue once the shared behavior has settled.
- Queue status strings are still partly hard-coded in the service, matching the
  existing queue style. Broader localization can be done separately.
- The Library action delegates queue and configuration decisions to
  `YassActions`; keep `YassSongList` focused on selection and context-menu
  wiring.
