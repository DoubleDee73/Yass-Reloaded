# Editor Insert Note

## Status

Implemented/current. This covers the editor Insert Note/Add Syllable action
used by `Shift+Enter`, `Ctrl+Enter`, the lyrics menu item, and the insert-note
toolbar button.

## Purpose

Yass Reloaded lets chart authors add a missing syllable or a small run of
lyrics while staying in the note editor. The action supports two related
workflows: inserting typed lyrics at the current editor/playback location, or
inserting one placeholder tilde note when no text is entered.

## User-Facing Behavior

- The legacy action is available from the editor lyrics menu as `edit_add`
  (`Add Syllable` in English), from the insert-note toolbar button, and through
  `Shift+Enter`.
- `Ctrl+Enter` opens the same text dialog, but uses a vocal-aware insertion
  path when the selected editor audio track is `#VOCALS` and loaded pitch data
  is available. Otherwise it falls back to the legacy action.
- Plain `Enter` is separate and toggles a page break.
- Running the action stops editor playback first, then opens the modal
  `edit_insert_notes_title` dialog.
- The dialog prompt asks for lyrics to insert. `OK` accepts the current text;
  `Cancel`, `Esc`, or closing the dialog leaves the song unchanged.
- Accepting non-blank text inserts generated note rows from that text at the
  current sheet/player position, or inside the local vocal-aware window for
  qualifying `Ctrl+Enter` inserts.
- Accepting a blank or whitespace-only value keeps the legacy placeholder
  behavior and inserts one note with text `~`.

## Core Rules

- Non-blank input goes through `YassTable.insertRowsAt(noteText, -1, true)`.
  With a live sheet, the insertion beat is derived from the current player
  position and the target row is the next note at that position, or the end row
  when there is no next note.
- Text input is converted through the normal lyric splitting path. Plain lyric
  text is split into UltraStar-style rows with `YassUtils.splitLyricsToLines`;
  tab-separated UltraStar rows are accepted directly.
- Inserted text rows replace the overlapping note/page-break range and then
  append the preserved remainder of the song. The end row is restored if needed.
- Blank input creates a single `:` note, never a page break.
- For blank input, the anchor row is the current selection. If there is no
  selection, the player cursor/next visible element is used when available.
- Blank insertion is blocked on comment rows and when there is no positive
  beat space before the next note or page break.
- The blank note beat is the maximum of the previous note end, previous page
  break second beat, and the player cursor beat when the cursor path is used.
- The blank note length defaults to four beats, is capped by the next note or
  page break, and is reduced by one beat when the available length is greater
  than one so the next row keeps a gap.
- The blank note pitch copies the previous note height when possible, otherwise
  the next note height, and otherwise uses `0`.
- After a successful insert, the new/updated table is selected or refreshed,
  player position is updated, the page zoom is refreshed, and absolute pitch
  view restores its vertical scroll position.

## Data And Configuration

- Inserted placeholder notes use UltraStar note type `:` and text `~`.
- Spaces in generated note text are stored as `YassRow.SPACE`.
- Dialog and action labels use these i18n keys:
  - `edit_add`
  - `edit_insert_notes_title`
  - `edit_insert_notes_prompt`
  - `edit_insert_notes_no_insertable_text`
  - `edit_insert_notes_no_space`
  - `edit_insert_notes_too_many_syllables`
  - `edit_insert_notes_no_pitch`
  - `edit_insert_notes_alignment_failed`
- The feature does not add persistent project configuration or song tags. It
  mutates the in-memory table rows that are later saved with the song.
- Timing calculations use the table BPM/GAP conversion path through the active
  sheet/player position.

## Code Entry Points

- `src/yass/YassActions.java`
  - `getEditorClipboardShortcutBindings()` binds `Ctrl+Enter` and
    `Shift+Enter`.
  - `insertNote` action stops playback and calls `YassTable.insertNote()`.
  - `insertNoteWithVocalPitch` checks the selected editor audio and loaded
    pitch data for `Ctrl+Enter`, then calls either
    `YassTable.insertNoteWithVocalPitch(...)` or the legacy `insertNote()`.
  - editor lyrics menu and toolbar wiring add the same action.
- `src/yass/YassTable.java`
  - `insertNote()`
  - `insertNoteWithVocalPitch(...)`
  - `insertLyricsWithVocalPitchAtBeat(...)`
  - `promptForInsertedLyrics()`
  - `insertNoteWithOptionalText(...)`
  - `insertRowsAt(...)`
  - `splitTextToLines(...)`
  - `AlignToMelodyContext.insertedLyrics()`
- `src/yass/YassUtils.java`
  - `splitLyricsToLines(...)` for plain lyric text.
- `src/yass/YassHyphenator.java`
  - hyphenation used by lyric splitting when available.

## Regression Coverage

- `YassTableSpec`
  - `insertRowsAt should insert rows` covers direct UltraStar rows, plain
    lyric text conversion, hyphenation, overlap replacement, and preserving the
    remainder of the song.
- `EditorShortcutBindingsSpec`
  - `ctrl enter routes to vocal-aware insert while shift enter remains legacy
    insert` covers the shortcut split.
- `AlignToMelodySpec`
  - `inserted lyrics alignment uses generated-note octave handling` covers the
    `insertedLyrics()` alignment context.
- `YassTableSpec`
  - `insertLyricsWithVocalPitchAtBeat...` cases cover local replacement,
    capacity rejection, page-break preservation, and missing pitch data.
- `YassTableSpec`
  - blank insertion creates a `~` placeholder, inherits pitch from the previous
    note, caps length before the next note, blocks comment rows, and blocks
    zero-space insertions.
- `EditorShortcutBindingsSpec`
  - source-contract coverage asserts the modal dialog title/prompt, OK default
    button, focus handling, cancel/escape non-mutating paths, and runtime
    `Ctrl+Enter` fallback wiring for non-vocal audio or missing pitch data.
- Remaining direct runtime coverage gaps are the actual modal button event flow
  in a live Swing dialog and the full `YassActions` fallback branches with real
  `SongHeader`/`YassPlayer` instances.

## Extension Notes

- Keep the action distinct from `pasteNotes`/`insertNotesHere()`, which reads
  note rows from the system clipboard. `Ctrl+Enter` uses the dialog-based
  `insertNote()` path.
- Add focused tests before changing blank-note placement rules; small changes
  to beat, length, or inherited pitch are immediately visible in editor charts.
- If changing keyboard bindings, preserve the plain `Enter` page-break behavior
  and keep `Shift+Enter` on the legacy insert-note behavior.

## Vocal-Aware `Ctrl+Enter`

- `Shift+Enter` remains the legacy Insert Note action.
- `Ctrl+Enter` uses the vocal-aware path only when the editor's selected audio
  track is `#VOCALS` and loaded pitch data is available.
- If the selected track is not `#VOCALS`, or there is no pitch data at all,
  `Ctrl+Enter` falls back to the legacy Insert Note action.
- The vocal-aware path uses the already-loaded `mp3.getPitchDataList()` frames,
  applying `mp3.getPitchWaveformTranspose()` the same way existing pitch-aware
  editor actions do.
- The text dialog stays the same. Blank input keeps the legacy placeholder
  behavior; non-blank input enters the vocal-aware insertion flow.
- The cursor anchor is the current cursor beat. If that beat is inside an
  existing note rather than free space, that note is the local replacement
  target.
- The insertion window starts at the cursor beat and ends at the free beat just
  before the next existing note. If a page break lies between cursor and the
  next note, the page break is preserved and the end boundary is still based on
  the first note after that page break.
- Existing notes after the window are never shifted. `#GAP`, unrelated notes,
  and unrelated page breaks are not changed.
- Inserted syllables must fit in the local window with at least one beat of
  note length and one free beat between inserted notes and before the next
  existing note. For example, a window covering beats `100` through `103`
  before an existing note at `104` can hold at most two inserted notes: `100`,
  free `101`, `102`, free `103`.
- If the entered text produces more syllables than fit in the window, Yass
  shows a message and leaves the song unchanged.
- Syllable timing follows the synthetic transcription-segment approach locally:
  the dialog text is split through the same editor lyric splitting path, the
  syllable/word parts are distributed inside the insertion window as one-beat
  notes separated by one free beat, and those generated notes are aligned to
  the loaded vocal pitch.
- Generated rows align with
  `YassTable.AlignToMelodyContext.insertedLyrics()`. Its behavior is close to
  `createWizard()`, but the name keeps local editor insertions distinct from
  full song creation/rebuild flows.
- Alignment may improve timing and pitch only inside the computed local window.
  After alignment, the window bounds and the free beat before the next existing
  note remain mandatory. If the aligned result violates those bounds, Yass rolls
  back and shows a message.
- When `#VOCALS` and pitch data are present but the local window has no usable
  pitch frames, has sparse frames, or otherwise cannot produce a safe local
  insertion, Yass shows a message and leaves the song unchanged rather than
  falling back to coarse legacy insertion.

## Vocal-Aware Regression Notes

- The shortcut split is covered: `Shift+Enter` remains legacy, while
  `Ctrl+Enter` routes through the vocal-aware action.
- `insertedLyrics()` octave handling is covered in `AlignToMelodySpec`.
- Local insertion coverage includes replacing the local target note, preserving
  the next existing note, rejecting too many syllables, preserving a page break
  inside the scan range, and rejecting windows without usable pitch frames.
- Remaining direct coverage gaps are the modal message paths and higher-level
  `YassActions` integration tests with real non-vocal/no-pitch runtime state.
