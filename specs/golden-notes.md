# Golden Notes

## Status

Implemented/current. The editor can suggest golden notes and can manually toggle
plain/golden and rap/rap-golden note types.

## Purpose

Golden notes provide the golden bonus portion of an UltraStar score. Yass
validates whether the current golden-note duration matches the configured target
and offers an editor action that marks suitable notes automatically.

## User-Facing Behavior

- Edit menu action: **Suggest Golden Notes**.
- Manual note-type shortcuts:
  - `G`: toggle selected notes between plain and golden.
  - `Shift+G`: toggle selected rap notes between rap and rap-golden.
- Suggestion stops playback, then delegates to `YassTable.suggestGoldenNotes()`.
- The suggestion action is ignored while lyrics are editable, the song list is
  editing, a filter is edited, or focus is inside the song header.
- The Errors dialog can report uncommon golden-note balance as
  `YassRow.UNCOMMON_GOLDEN`.
- The Errors dialog offers Correct/Correct All for `UNCOMMON_GOLDEN` only when
  the current golden-note duration is below the configured target. If the song
  already has enough or too many golden-note beats, the error remains visible
  without a correction button.

## Core Rules

- `YassAutoCorrect.checkData(...)` calculates target and current golden-note
  values before suggestion runs.
- Relevant settings:
  - `max-points`, default `7500`
  - `max-golden`, default `1250`
  - `max-linebonus`, default `1000`
  - `golden-allowed-variance`, default `250`
  - `freestyle-counts`, default `false`
- Candidate selection alternates between:
  - **Pitch Leaps**
  - **Long Words**
- Pitch leaps look for three consecutive regular notes forming two or three
  complete words with at least one third and one fifth interval.
- Long Words chooses the longest still-regular word that fits the remaining
  ideal golden duration.
- Candidates must fit into the remaining ideal golden duration to avoid
  overshooting.
- Only plain notes `:` and rap notes `R` are candidates.
- Marking converts `:` to `*` and `R` to `G`.
- Word boundaries honor the configured spacing mode.

## Data And Configuration

- Score/golden settings are read from `YassProperties`.
- `YassTable.setGoldenPoints(...)` stores calculated target/current values used
  by `suggestGoldenNotes()`.
- `UNCOMMON_GOLDEN` is a conditional auto-correction message. It is supported
  only through `YassAutoCorrect.autoCorrectionSupported(table, ...)` when the
  table is below the ideal golden-note duration.
- `UNCOMMON_GOLDEN` is absent from `YassAutoCorrect.isAutoCorrectionSafe(...)`,
  so all-safe and library batch correction do not distribute golden notes.
- No new file tags are created by the suggestion action.

## Code Entry Points

- `src/yass/YassTable.java`
  - `suggestGoldenNotes()`
  - golden target fields and `setGoldenPoints(...)`
- `src/yass/autocorrect/YassAutoCorrect.java`
  - `checkData(...)` calculates golden validation values
- `src/yass/YassActions.java`
  - `suggestGoldenNotes`, `golden`, and `rapgolden` actions

## Regression Coverage

- `YassTableSpec`
  - suggestion starts with Pitch Leaps before Long Words
  - suggestion alternates Pitch Leaps and Long Words
  - shorter Pitch Leap can fill remaining duration when Long Word no longer fits
  - rap Pitch Leap candidates are converted from `R` to `G`
  - freestyle and already-golden notes are not changed by suggestions
  - leading-space word boundaries are honored when trailing-space mode is off
- `YassAutoCorrectSpec`
  - `UNCOMMON_GOLDEN` correction support is offered only below the target
  - correction adds suggested golden notes below the target
  - correction does nothing when the current duration already reaches the target

## Extension Notes

- The suggestion action currently relies on `checkData(...)` having populated
  ideal/current golden values first.
- `UNCOMMON_GOLDEN` correction only adds golden notes. It does not demote or
  rebalance existing golden notes because that would overwrite manual authoring
  decisions.
- It does not optimize for chorus position, melodic salience beyond pitch leaps,
  phrase position, or user intent.
- If undo support is added, create an explicit undo snapshot before changing
  note types.
- Useful future tests: undo behavior and more mixed spacing/page-boundary
  examples around suggestion candidates.
