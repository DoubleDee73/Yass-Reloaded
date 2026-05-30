# Wizard Manual Lyrics Separators

## Status

Implemented/current. The Create Song Wizard's manual lyrics path can use explicit
separator markup when no transcription result, subtitle timing map, or MIDI
lyrics source is active.

## Purpose

Manual wizard lyrics often already contain the user's intended syllable or note
boundaries. Respecting explicit separators lets Yass Reloaded create provisional
note rows from that markup without asking the dictionary or hyphenator to infer
a different split.

## User-Facing Behavior

- In the Create Song Wizard lyrics page, manually entered plain lyrics can use
  `•` or `+` as explicit note boundaries.
- A manual line without `•` or `+` keeps the existing dictionary-backed word and
  syllable splitting behavior.
- A manual line containing at least one `•` or `+` is split only by those
  separators.
- The separator characters are markup and do not appear in generated lyric
  text.
- Spaces around a separator are ignored for the boundary. Spaces inside a
  resulting fragment remain part of that lyric fragment.
- Song-part marker lines such as `[chorus]` are still skipped before lyric
  splitting.

## Core Rules

- Recognized explicit separators are `•` and `+`; both have the same meaning.
- Explicit-separator handling is opt-in in `YassUtils.splitLyricsToLines(...)`.
  The default overload keeps legacy behavior for editor insertion and subtitle
  paths.
- Empty fragments from leading, trailing, or repeated separators are ignored.
  A literal `~` fragment is retained as an explicit empty syllable marker.
- Punctuation stays attached to the fragment where the user entered it.
- Normal spacing mode prefixes the first generated explicit fragment for the
  line. Spacing-after mode appends spacing to the final generated explicit
  fragment, matching the existing generated-row convention.
- Transcript, subtitle-backed wizard, LRCLIB-with-transcript-structure, editor
  insert-note, and editor rehyphenation flows do not opt in to this behavior.

## Data And Configuration

- No persistent properties or UltraStar tags are added.
- Generated rows still use the current default manual wizard note length and
  pitch.
- The behavior uses the existing `isUncommonSpacingAfter()` setting when writing
  generated row text.

## Code Entry Points

- `src/yass/wizard/Lyrics.java`
  - `getTable()` enables explicit separators only for the manual no-subtitles
    path.
- `src/yass/YassUtils.java`
  - `splitLyricsToLines(String[], int, boolean)`
  - `hasExplicitWizardSeparators(String)`
  - explicit separator row creation helpers near `createRowsFromLine(...)`
- `src/yass/YassTable.java`
  - editor insertion still calls the default `splitLyricsToLines(String[], int)`
    overload.

## Regression Coverage

- `YassUtilsSpec`
  - mixed `•` and `+` separators split in input order without hyphenator lookup
  - `+` separators bypass dictionary syllabification
  - ordinary opt-in lines still use dictionary-backed syllable splitting
  - punctuation and `~` fragments survive while accidental empty fragments are
    ignored
  - default `splitLyricsToLines(...)` behavior remains unchanged for non-wizard
    callers

## Extension Notes

- Keep this behavior scoped to the manual wizard path unless a future spec
  deliberately expands explicit separator support to subtitles, transcript
  alignment, or editor workflows.
- If word-boundary spacing rules are expanded later, test raw lyric cell text in
  addition to trimmed text so UltraStar spacing behavior remains intentional.
