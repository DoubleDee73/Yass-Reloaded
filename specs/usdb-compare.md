# USDB Compare

## Status

Implemented/current.

## Purpose

The USDB compare flow lets maintainers compare a local TXT with the editable
TXT from USDB before saving the local file or submitting changes back to USDB.
It prevents accidental comparisons against a different song variant when the
library contains both solo and duet versions.

## User-Facing Behavior

`Compare with USDB` loads the local song, resolves a USDB song ID from the
`.usdb` metadata file or an exact artist/title search, then opens the diff
dialog.

If no suitable USDB ID can be resolved, Yass Reloaded opens the USDB search
dialog prefilled with the local artist and title. If the automatically resolved
USDB song is a solo/duet mismatch, Yass Reloaded also falls back to that search
dialog instead of opening a misleading diff.

When the user explicitly selects a mismatching result from the search dialog,
the compare is rejected with a clear duet/solo mismatch message.

## Core Rules

Local and remote TXT files must have the same duet structure before they are
compared. Solo songs may only be compared with solo songs, and duet songs may
only be compared with duet songs.

The remote TXT loaded from `UsdbSongEditService` is the source of truth for the
USDB song's duet status. Search results and syncer metadata do not currently
carry a reliable duet flag.

Duet detection treats `#DUETSINGERP...`, `#P1:` style singer headers, and `P1`
or `P 1` track switch lines as duet markers.

## Data And Configuration

The flow reads local TXT files as UTF-8 and normalizes line endings in memory.
USDB identity is discovered from `.usdb` metadata through
`UsdbSyncerMetaFileLoader` or from exact USDB artist/title search results.

Relevant message keys:

- `usdb_edit_compare`
- `usdb_edit_missing_meta`
- `usdb_edit_duet_mismatch`

## Code Entry Points

- `YassActions.compareAndSubmitUsdbSongEdit`
- `YassActions.hasUsdbCompareDuetMismatch`
- `YassActions.openCompareUsdbSearchDialog`
- `UsdbSearchDialog.compareSelectedSongWithCurrent`
- `UsdbSongEditService.loadEditableSong`
- `UsdbSyncerMetaFileLoader`

## Regression Coverage

- `YassActionsUsdbEditSpec` covers same-file reload detection and duet mismatch
  classification for local/remote TXT comparison.
- `UsdbSearchDialogSpec` covers compare search dialog behavior.
- `UsdbSongEditDiffDialogSpec` covers the diff dialog behavior.

## Extension Notes

If USDB search results eventually expose a trustworthy duet flag, the search
dialog can filter mismatching candidates earlier. Keep the TXT-based guard in
the compare path because it protects metadata and search-result edge cases.
