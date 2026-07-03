# Autosave

## Status

Implemented/current. A per-song background timer writes a `.bak` backup of the
open song at a fixed interval. Autosave never mutates the live table model and
never shows blocking dialogs, so it does not interrupt active editing.

## Purpose

Editing sessions can be long, and a crash, power loss, or accidental close would
otherwise lose unsaved work. Autosave periodically writes a recoverable `.bak`
copy next to the song file without requiring the user to save manually.

## User-Facing Behavior

- Autosave runs silently in the background; there is no toolbar/menu action.
- The interval is configured in the Sketch options panel
  (`options_autosave_interval`, in seconds). `0` disables autosave.
- On opening a song, if a newer `.bak` than the song file exists, the editor
  asks whether to restore it (`edit_autosave_hint` / `edit_autosave_title`).
  - Restore: the current file is copied to `.old`, then the `.bak` overwrites
    the song file.
  - Decline: the `.bak` is deleted.
- A `.bak` that is older than the song file, or whose table has been saved
  normally, is cleaned up automatically when the song set is disposed.
- Autosave does not steal focus, move the selection or caret, change scroll
  position, or pop a dialog during normal operation. Failures are logged only.

## Core Rules

- **Never touch the live model off the EDT.** The autosave write executes on
  the Swing EDT (via `SwingUtilities.invokeAndWait` from the timer thread) so it
  cannot interleave with active edits. This guarantees a race-free, consistent
  snapshot without duplicating `storeFile`'s relative-format serialization. The
  background timer thread itself must never read or mutate the live table.
- **No verify-reload, no dialog for `.bak` writes.** Autosave does not re-read
  the written file and compare it against the live table; that comparison races
  with concurrent edits and previously produced spurious "could not be verified"
  popups. Verification and user-facing error dialogs remain only on the explicit
  user-initiated save path.
- **Skip when nothing changed.** Autosave is a no-op when the table is already
  saved (`isSaved()`) or already autosaved (`isAutosaved()`).
- **State flag lifecycle:**
  - `setSaved(true)` implies autosaved as well; `setSaved(false)` (any edit)
    clears both, re-arming autosave.
  - A successful autosave sets `autosaved = true` so it does not rewrite the
    same content until the next real edit.
  - `saved`, `autosaved`, and the autosave re-entry guard are visible across
    threads (EDT writer / timer reader).
- **One timer per song table.** `initAutoSave()` starts it when the song opens;
  `removeAutoSave()` cancels it and marks the table autosaved when the song
  closes. A skipped or overlapping tick must never queue a second concurrent
  write.
- **`.bak` writes skip version/dedup handling.** Backup files intentionally do
  not run `setVersion()`, `handleDeprecations()`, or `handleAudio()`; only the
  real save path does.

## Data And Configuration

- Property: `options_autosave_interval` (seconds; default `300`, `0` disables).
  Interval is clamped to a maximum of `600` seconds. First tick fires 30s after
  the song opens.
- Generated files:
  - `<song>.txt.bak` — autosave backup written next to the song file.
  - `<song>.txt.old` — copy of the previous song file, created only when the
    user accepts a `.bak` restore on open.
- Duet songs: the `.bak` is produced by merging open tracks
  (`YassTable.mergeTables`) into a single merged table before writing.

## Code Entry Points

- `src/yass/YassAutoSave.java`
  - `TimerTask.run()` — the autosave tick; gating, snapshot, write.
- `src/yass/YassTable.java`
  - `initAutoSave()` / `removeAutoSave()` — timer lifecycle.
  - `storeFile(String)` — file serialization (shared with the normal save).
  - `isSaved()` / `isAutosaved()` / `setSaved(boolean)` / `setAutosaved(boolean)`
    — state flags.
  - table model listener (`addTableModelListener`) — clears saved/autosaved on
    every edit.
- `src/yass/YassActions.java`
  - `mergeTableAndSave(YassTable, boolean)` — chooses `.bak` vs real save and
    handles duet merge.
  - `checkAutosaveBackup(String)` — restore-on-open prompt.
  - `disposeAutosaveFiles(Vector<YassTable>)` — cleanup of stale `.bak`.

## Regression Coverage

- No dedicated spec exists yet. Behavior is exercised indirectly through
  `YassTableSpec` (table model / save) and manual testing.
- Recommended scenarios to add: edit during an in-flight autosave does not throw
  or corrupt the `.bak`; autosave is a no-op when already saved; `.bak` restore
  prompt fires only for a newer backup; duet merge produces a loadable `.bak`.

## Extension Notes

- The expensive part of autosave is full-table serialization plus disk I/O. Keep
  the EDT snapshot cheap (string/row copy) and do everything else off-EDT.
- Do not reintroduce a verify-reload or any dialog on the autosave path — that
  was the primary source of mid-edit interruption.
- If autosave ever needs to report a failure to the user, debounce it and route
  it through the EDT; never block the timer thread on a modal dialog.
- Consider fixed-delay scheduling (`Timer.schedule`) over fixed-rate
  (`scheduleAtFixedRate`) so a slow write cannot cause back-to-back catch-up
  ticks.
