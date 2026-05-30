# USDX Last Played Library Filter

This internal spec tracks a planned Library filter that shows songs played in
the last UltraStar Deluxe session, based on USDX `Error.log` playback markers.

## Purpose

UltraStar Deluxe logs many preview playback events while users browse songs.
That makes it hard to identify which local songs were actually started in a
singing session. Yass Reloaded should surface those played songs directly in
the Library so users can review or edit them without manually reading the log.

The first version targets UltraStar Deluxe / USDX only. Other UltraStar-family
games may use different log formats and should not be assumed compatible.

## Implementation Gap Assessment

Current implemented foundation:

- The Library already has a group dropdown backed by `YassFilter` plugins.
- `YassGroups.setFilter(...)` applies a group filter with
  `songList.setPreFilter(...)`, then refreshes and sorts the Library.
- `YassFilter.getSorting()` and `YassFilter.getExtraInfo()` already let a group
  choose its default sort column and visible extra information.
- `YassSongList.sortBy(...)` centralizes Library sorting.
- `YassSongList` already renders an extra line for tile mode, such as language,
  genre, edition, folder, year, album, duet singer, or length.
- USDX `src/base/ULog.pas` opens `Error.log` with `Rewrite(...)`, so the log is
  overwritten per game start rather than appended.
- Yass Reloaded already depends on `sqlite-jdbc`, so future DB enrichment could
  read `Ultrastar.db` without requiring an external SQLite CLI.

Missing for implementation:

- Automatic discovery of a readable USDX `Error.log`.
- A parser for USDX `Error.log` session start events.
- A session index that maps played events to Library songs.
- A dynamic `Last Played` group that is present only when the log is readable
  and at least one played event can be matched to a Library song.
- Temporary per-song last-played metadata for filtering, sorting, and display.
- A Library sort path for last-played timestamp.
- A Library extra-info display path for last-played clock time.
- Tests covering parsing, matching, dynamic group visibility, sorting across
  midnight, and repeated plays.

## User-Facing Behavior

The Library group dropdown gains a `Last Played` entry only when Yass Reloaded
can automatically find and parse a USDX `Error.log` with at least one matched
Library song. If no usable log exists, the dropdown looks unchanged.

Selecting `Last Played` in the group dropdown filters the Library to songs that
were started in the last USDX session. The first version should expose exactly
one rule below the dropdown, `All Last Played`, and apply that rule immediately
when the group is selected so users do not need a second click. The Library
remains song-based: each song appears at most once, even if it was played
multiple times.

For songs played more than once, Yass uses the latest play as the song's
session value. The extra line in tile mode shows the play clock time. Single
plays show only `HH:mm`; repeated plays append the count:

- `23:55`
- `00:05 (2x)`

The visible value may be only `HH:mm`, but sorting must use the full parsed date
and time. In a session that crosses midnight, `23:55` from the previous date
must sort before `00:05` from the next date when sorting chronologically.

The `Last Played` group defaults to chronological playback order. The initial
sort should be oldest-to-newest so the visible Library order follows the session
sequence. Users can still switch to the existing Artist or Title sorting through
the normal Library sort controls.

The feature does not add a separate dialog or an Extras menu action in the first
version.

## Log Parsing Rules

The parser should treat USDX `Error.log` as one session file. The first header
line identifies the session and should be used to reject clearly incompatible
files when possible.

The parser should identify actual song starts using the pattern observed in
USDX logs:

1. `STATUS: Begin [OnShow]`
2. `STATUS: End [OnShow]`
3. Within a short window after `End [OnShow]`, one or more
   `Using decoder FFmpeg_Decoder for "...\\songs\\..."`
   lines for the selected song.

Preview playback while browsing should not count as played. Preview lines often
appear without the `OnShow` transition and may be triggered by remote control,
search, left/right navigation, or song list movement.

If a start event is followed quickly by another song, it still counts as a
started song. The Library filter is about songs started in the session, not
only songs that reached a saved score.

## Matching Rules

The preferred matching key is the song text file path when it can be derived.
If the log only exposes media file paths, match by the song folder under
`songs/` and then resolve that folder to the Library song entry.

Matching must be conservative:

- If exactly one Library song maps to the logged song folder, attach the event
  to that song.
- If multiple Library songs map to the same folder and the event cannot be
  disambiguated, skip the event rather than attaching it to the wrong song.
- If the logged song folder is not in the current Yass Library, skip it.

Song matching should not depend on display title normalization alone because
the database, log folder, and local `.txt` metadata can use slightly different
artist/title strings.

## Data Model

Add a small session model separate from persistent song metadata:

- `PlayedSongEvent`
  - start timestamp
  - logged media path
  - logged song folder
- `LastPlayedSongInfo`
  - latest start timestamp
  - play count
  - optional first start timestamp for diagnostics

The session model should live in memory and be rebuilt from the current
`Error.log` when the Library is loaded or manually refreshed. If the log
disappears or no longer has matched songs, the `Last Played` group should be
removed on the next Library/group rebuild. It must not write new tags or modify
song files.

`Ultrastar.db` should not be required in the first version. It may later enrich
the filter with score/completion metadata, but `us_scores.Date` appears to
represent result time rather than start time and is recorded per player.

## UI Integration

Prefer a new `YassLastPlayedFilter` plugin over a bespoke action. The filter
should be added to the group dropdown only when the session index is available.
Because the existing `filter-plugins` property is static, implementation may
need either:

- a dynamic registration hook for optional filters, or
- a filter plugin whose `getGenericRules(...)` returns no rules and is hidden
  when no usable session exists.

The group label should be localized:

- English: `Last Played`
- German: `Zuletzt gespielt`

When the `Last Played` group is active, the Library extra-info renderer should
display the session clock time instead of ordinary metadata. The renderer should
ask the session index for the visible value rather than storing this value in
the song file.

Because the current UI separates the group dropdown from the rule list, the
implementation must guarantee that selecting the `Last Played` group activates
its single rule. If the existing row-selection listener does not fire after the
group model is rebuilt, `YassGroups.setGroups(...)` or the new filter integration
should explicitly apply row zero for this group.

## Sorting

The `Last Played` default sort must use the full latest-start timestamp, not the
displayed clock text. This avoids lexical or time-only mistakes around
midnight.

Default order should be oldest-to-newest by latest-start timestamp:

- `2026-05-23 23:55` before `2026-05-24 00:05`

If the existing Library sorting is globally ascending-only, add a dedicated
last-played ordering that compares timestamps ascending. If reverse order is
introduced later, it must remain explicit and should not change the first
version's default behavior.

Artist and Title sorting remain available through the existing Library sort
controls.

## Candidate Implementation Slices

1. Add a parser and tests for USDX `Error.log` played-song start events.
2. Add a session index that maps parsed events to Library songs and keeps the
   latest timestamp plus play count per song.
3. Add the dynamic `Last Played` group/filter and visibility gating.
4. Add last-played sorting and tile extra-info rendering.
5. Add focused UI/source-contract coverage for dynamic group availability and
   sort/display behavior.

## Regression Coverage

When implemented, cover:

- `Error.log` opened by USDX is treated as a single last-session file.
- Preview decoder lines without `OnShow` are ignored.
- Decoder lines immediately after `End [OnShow]` produce played events.
- Short started songs still count as played.
- Repeated plays collapse to one Library song with the latest timestamp and
  incremented play count.
- A session crossing midnight sorts by full timestamp, so `23:55` comes before
  `00:05` when chronological sorting is active.
- The displayed extra info can be `HH:mm` while sorting still uses full date and
  time.
- Single plays display only `HH:mm`; repeated plays display `HH:mm (Nx)`.
- The `Last Played` group is absent when no readable/usable USDX `Error.log`
  exists.
- The `Last Played` group is absent when parsed events cannot be matched to
  current Library songs.
- Selecting the `Last Played` group applies its single rule without requiring a
  second click in the rule list.
- Ambiguous folder matches are skipped.
- Existing Artist and Title sorting still work after using `Last Played`.

## Extension Notes

- A later version may use `Ultrastar.db` to mark completed songs, scores, or
  players in Library details.
- A later version may support other UltraStar-family games, but only after their
  log format is inspected and covered by tests.
