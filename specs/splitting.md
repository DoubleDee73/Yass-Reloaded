# Splitting

## Status

Implemented/current. The editor split action can use pitch data when available
and falls back to the legacy split behavior when pitch/text evidence is
ambiguous.

## Purpose

Pitch-guided splitting reduces manual cleanup when a single written syllable
spans multiple sung pitches. It helps with obvious held syllables and melismas
without replacing the standard split behavior.

## User-Facing Behavior

- The normal editor split action remains the entry point.
- If the selected note has usable detected pitch data, Yass may split by stable
  pitch segments.
- If pitch data is missing, sparse, unstable, or text distribution is ambiguous,
  Yass falls back to the legacy split.
- Existing continuation text such as `~n` stays on the right when split again.

## Core Rules

- Pitch-guided splitting is single-note oriented; it does not restructure a full
  phrase.
- A stable pitch segment needs at least three beats.
- Adjacent pitch changes within one semitone are treated as the same segment.
- Transition beats may remain attached to the previous segment.
- Non-final segments leave the usual one-beat gap when touching syllables are
  enabled.
- Explicit hyphenation or hyphenator syllables are preferred when available.
- Matching syllable and segment counts map one-to-one.
- With two pitch segments and more than two syllables, the first syllable maps
  to the first segment and the remaining syllables map to the second segment.
- For one unhyphenated word across multiple pitch segments, Final Consonant
  Split keeps the main word body first and moves trailing consonant clusters to
  the last segment as `~cluster`.
- Contraction apostrophes move from the apostrophe onward to the final segment.
- Two-letter words remain intact on the first segment.
- Punctuation and trailing spacing stay on the final segment.
- If pitch-guided splitting falls back but detects a sustained changed-pitch
  right side, the right-hand note still receives the changed pitch.

## Data And Configuration

- Input pitch data comes from `PitchDetector.PitchData`.
- Touching-syllable behavior follows the existing editor split settings.
- No persistent properties or song tags are changed beyond the split note rows.

## Code Entry Points

- `src/yass/YassActions.java`
  - split action loading pitch data and calling the table split path
- `src/yass/YassTable.java`
  - `splitRowsByPitch(...)`
  - legacy split implementation and text distribution helpers
- `src/yass/analysis/PitchDetector.java`
  - pitch data used by the split heuristics

## Regression Coverage

- `YassTableSpec`
  - stable pitch segment detection
  - transition beat handling
  - one-to-one syllable/segment mapping
  - first-syllable/rest mapping for two segments
  - final consonant cluster mapping
  - contraction apostrophe mapping
  - two-letter words staying intact
  - punctuation preservation
  - sustained-run fallback pitch assignment
  - repeated split of tilde continuations

## Extension Notes

- Keep this conservative. If segment detection or text mapping is uncertain,
  fall back to legacy split.
- Add tests before changing consonant cluster lists or syllable distribution
  rules; small text changes can produce very visible chart cleanup regressions.
- Keep debug logging useful but preferably at debug/fine level unless the user
  explicitly needs split diagnostics.
