# Error Correction Panels

## Status

Implemented/current. This covers the error-correction settings panel
`yass.options.ErrorPanel`, the editor error dialog `YassErrors`, and the
library batch-correction flow that applies fixes to song `.txt` files.

## Purpose

Yass Reloaded checks UltraStar song files for invalid or uncommon rows, tags,
spacing, page breaks, apostrophes, capitalization, scoring, and related metadata
problems. The ErrorPanel settings do not correct files directly; they store the
policy values used by the editor and library correction flows when
`YassAutoCorrect` scans and mutates a `YassTable`.

## User-Facing Behavior

- The settings live in the modal Options dialog under Error Checks / Page
  breaks (`YassOptions` adds `ErrorPanel` under the checker tree).
- The panel contains:
  - `touching-syllables`: whether connected notes should be separated at the
    syllable level.
  - `correct-uncommon-pagebreaks`: radio value `true`, `false`, or `unknown`.
    `unknown` asks in the editor before trimming page breaks.
  - `correct-uncommon-pagebreaks-fix`: text field for a fixed minimum beat
    distance around page breaks; `0` means automatic placement.
  - `correct-uncommon-spacing`: radio value `after` or `before`, choosing
    trailing-space style or legacy leading-space style.
  - `typographic-apostrophes`: whether simple apostrophes are reported and
    converted to typographic apostrophes.
  - `capitalize-rows`: whether the first note after a page break should start
    with a capital letter.
- Options are stored only when the user accepts the Options dialog with OK.
  Cancel closes the dialog without copying the panel's temporary values back to
  `YassProperties`. The reset button restores defaults for the panel.
- If `correct-uncommon-spacing` is empty on startup, `YassMain` asks once which
  spacing convention to use. OK selects `after`; Cancel selects `before`.
- The editor's Errors action opens `YassErrors`, an always-on-top dialog that
  lists detected row messages. Selecting an error selects the corresponding
  table row, updates playback position, zooms the page, and recenters absolute
  pitch view when enabled.
- Supported errors show Correct, Correct All, and Cancel buttons. Correct
  applies the selected message to the selected row. Correct All applies the same
  message across the table, then the table is rechecked and the error selection
  is restored when possible.
- The editor also exposes direct correction actions for all safe errors,
  spacing, transposed notes, and page breaks.
- In the library detail view, Correct Songs can process selected or all songs
  and selected or all visible error classes. Batch correction loads each song,
  applies `YassAutoCorrect`, stores the `.txt` file back to disk, and refreshes
  the library row.

## Core Rules

- `YassAutoCorrect.checkData(...)` is the central scanner. It clears existing
  row messages, parses `correct-uncommon-pagebreaks-fix`, and then annotates
  `YassRow` instances plus the table-level message set.
- `autoCorrectAllSafe(...)` loops scan/correct cycles up to 20 times. Spacing
  correction loops up to 10 times. Page-break correction loops up to 20 times.
- `correct-uncommon-pagebreaks` does not control whether page-break issues are
  detected. It controls the editor `trimPageBreaks()` action:
  - `true` trims early, late, overlapping, and uncommon page breaks.
  - `false` trims early, late, and overlapping page breaks only.
  - `unknown` shows the editor prompt and can persist the user's yes/no choice.
- Page-break placement is computed by `YassAutoCorrect.getCommonPageBreak(...)`.
  With `correct-uncommon-pagebreaks-fix > 0`, the break is forced to that beat
  offset from the previous note, clamped inside the pause. With `0`, Yass uses
  pause-length heuristics based on BPM and available beats.
- Spacing detection depends on `correct-uncommon-spacing`:
  - `after` treats leading spaces as uncommon and expects a trailing space when
    the current note ends a word before another note/end/page break.
  - `before` treats trailing spaces as uncommon and moves the separator to the
    following note during correction.
- `touching-syllables` broadens note-touching checks. When enabled, adjacent
  note pairs without a free beat are reported whenever the previous note is
  longer than one beat; correction shortens the previous note by one beat.
- `typographic-apostrophes` gates both detection and correction of plain or
  alternate apostrophe characters. The scanner checks note text and title/artist
  comments; the corrector can update note rows or comment rows.
- `capitalize-rows` reports lowercase row starts after page breaks. Correction
  capitalizes that row, and page-break add/remove operations also use the
  setting to capitalize or uncapitalize adjacent lyric rows.
- Golden-note balance problems are conditionally correctable in the error
  dialog. The scanner reports `YassRow.UNCOMMON_GOLDEN`; `YassErrors` offers
  Correct/Correct All only when the table has fewer golden-note beats than the
  configured target. The correction delegates to `YassTable.suggestGoldenNotes()`
  and never demotes existing golden notes.

## Data And Configuration

- Properties are stored in the user properties file under the exact keys listed
  above. Defaults are defined in `YassProperties.setDefaultProperties(...)`.
- Relevant default values:
  - `touching-syllables=false`
  - `correct-uncommon-pagebreaks=unknown`
  - `correct-uncommon-pagebreaks-fix=0`
  - `correct-uncommon-spacing=` until the startup prompt chooses `after` or
    `before`
  - `typographic-apostrophes=false`
  - `capitalize-rows=false`
- Error classes are defined as `YassRow` message constants such as
  `UNCOMMON_SPACING`, `NOTES_TOUCHING`, `UNCOMMON_PAGE_BREAK`,
  `LOWERCASE_ROWSTART`, and `BORING_APOSTROPHE`.
- Dialog labels and error messages come from `src/yass/resources/i18/yass_*.properties`.
  The capitalization key is misspelled as `options_errors_captilization` in the
  current resource files and code.
- Library batch correction writes the modified song back through
  `YassTable.storeFile(...)`. The old commented AUTO-version backup flow is not
  active; `YassSongList.batchProcess(...)` currently sets `backup=false`.

## Code Entry Points

- `src/yass/options/ErrorPanel.java`
  - Builds the six visible settings rows.
- `src/yass/options/YassOptions.java`
  - Hosts `ErrorPanel`, stores panel properties on OK, and discards them on
    Cancel.
- `src/yass/YassProperties.java`
  - Defines defaults and helper methods such as `isUncommonSpacingAfter()`.
- `src/yass/YassMain.java`
  - Prompts for the spacing convention when no value has been chosen yet.
- `src/yass/YassErrors.java`
  - Editor error dialog, correction buttons, error selection, and message
    rendering.
- `src/yass/autocorrect/YassAutoCorrect.java`
  - Main scanner, safe-correction loop, page-break heuristics, and message
    dispatch.
- `src/yass/autocorrect/YassAutoCorrectUncommonSpacing.java`
- `src/yass/autocorrect/YassAutoCorrectUncommonPageBreaks.java`
- `src/yass/autocorrect/YassAutoCorrectApostrophes.java`
- `src/yass/autocorrect/YassAutoCorrectLineCapitalization.java`
  - Focused correctors for settings-driven message classes.
- `src/yass/YassActions.java`
  - Editor actions for showing errors, trimming page breaks, correcting spacing,
    and opening Options.
- `src/yass/YassSongList.java`
  - Library detail scanning and batch correction of song files.
- `src/yass/YassTable.java`
  - Uses these settings in page-break toggling, BPM recalculation, apostrophe
    toggling, and table rechecks.

## Regression Coverage

- `ErrorPanelSpec` covers that the panel declares all six persisted correction
  settings.
- `YassTableSpec` covers some downstream behavior, including page-break
  insertion/removal under spacing modes and capitalization when toggling a page
  break.
- Existing coverage does not directly assert Options dialog persistence,
  `YassErrors` Correct/Correct All behavior, library batch correction writes,
  page-break-fix parsing, or startup spacing prompt behavior.
- `YassAutoCorrectSpec` covers that `UNCOMMON_GOLDEN` is supported only when
  the current golden duration is below the target, adds golden notes in that
  case, and does nothing when the target is already reached or exceeded.
- `YassAutoCorrectSpec` also covers the safe-correction policy for focused
  correctors, typographic apostrophe correction for notes/comments, line
  capitalization gating after page breaks, and trailing/legacy spacing
  correction modes.
- Additional focused unit coverage should be added as more direct correction
  branches move into dedicated `YassAutoCorrector` implementations.

## Extension Notes

- Naming is easy to confuse: `ErrorPanel` is the settings panel, while
  `YassErrors` is the editor correction dialog. Future UI or spec work should
  keep those roles separate.
- `correct-uncommon-pagebreaks-fix` is a free text field. `checkData(...)`
  parses it with `Integer.parseInt(...)`, so non-numeric values can abort the
  check path and show the generic parse-error dialog. The UI text says the
  value should be a minimum beat count and the code comments assume values up
  to 10, but the panel does not validate or clamp the input.
- The library "Correct Page Breaks" action only queues early, late, and
  overlapping page-break messages. Uncommon page breaks are handled by the full
  safe-correction path or by selecting that error class explicitly.
- `UNCOMMON_GOLDEN` is not safe-batch-correctable. It is intentionally absent
  from `isAutoCorrectionSafe(...)`, so library auto-correction does not
  distribute golden notes unless a user explicitly triggers the supported error
  correction in an editor context.
- When there are too many golden-note beats, the error remains a manual finding.
  Yass does not remove or rebalance existing golden notes automatically.
- `YassAutoCorrect` still contains several direct correction branches. Future
  refactors should move those methods toward the `YassAutoCorrectApostrophes`
  pattern: a focused class per message family that extends
  `YassAutoCorrector`, is registered in `initAutoCorrectors()`, and carries its
  own targeted tests.
- The editor page-break trim path calls the all-rows correction flow, but the
  `PAGE_OVERLAP`, `EARLY_PAGE_BREAK`, `LATE_PAGE_BREAK`, and
  `SHORT_PAGE_BREAK` switch branch still checks whether exactly one table row is
  selected before using the page-break corrector. That selection dependency is
  worth regression testing before relying on bulk trim behavior.
- `correct-uncommon-spacing` affects display/error text as well as mutation.
  When changing it from Options, open tables are refreshed but existing song
  rows are not rewritten until a correction action is run.
