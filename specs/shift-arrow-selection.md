# Shift-Arrow Note Selection

## Status

Implemented/current. Covers the editor's keyboard multi-note selection via
`Shift-Up` / `Shift-Down`, including the repeated-press escalation to word and
page boundaries. Single-press extend-by-one is the base behavior; the 2x/3x
escalation is timing-based and can be toggled on/off per user (default on) via
the `F8` key or the Edit-menu checkbox. When disabled, `Shift-Arrow` always
extends by a single note.

## Purpose

Lets users grow a contiguous note selection from the keyboard without the mouse.
The repeated-press escalation makes selecting whole words or whole pages fast
(e.g. selecting several pages to move or retime them), while a single press
keeps fine-grained note-by-note control.

## User-Facing Behavior

All shortcuts apply when the editor (sheet or lyrics view) has focus and the
song header is not being edited. Selection is always a single contiguous range
anchored at one end; there is no keyboard way to build a discontiguous
selection.

`Shift-Down` (extend toward the end of the song):

- **1 press** — extend the selection to the next note (`selectNextBeat`).
- **2 presses within 350 ms** — extend to the end of the current word
  (`selectToEndOfCurrentWord` via `extendSelectionDownToWordBoundary`).
- **3 presses within 350 ms** — extend to the end of the current page
  (`selectToEndOfCurrentPage` via `extendSelectionDownToPageBreak`).

`Shift-Up` mirrors this toward the start of the song:

- **1 press** — extend to the previous note (`selectPrevBeat`).
- **2 presses** — extend to the start of the current word.
- **3 presses** — extend to the start of the current page.

Escalation toggle:

- `F8`, the Edit menu -> "Extend Selection by Word/Page" checkbox, or clicking
  the **"Shift Acc" status indicator** in the bottom info bar all toggle the
  multi-press escalation. All three reflect the current state. State persists in
  `shift-arrow-escalation` (default `true`).

Bottom info-bar status indicators (`YassSheetInfo`, active track only). Each
shows a colored dot + label; the bordered hover box appears only while the mouse
is over the indicator (hand cursor), like the Errors area:

- **"Shift Acc"** — green dot when escalation is on, dim when off. Clicking calls
  `YassActions.setShiftArrowEscalation`.
- **"Mic"** — red dot/label while a microphone pitch session is listening, dim
  otherwise. Clicking calls `YassActions.toggleMicPitch` (same as `Ctrl-Shift-M`:
  arms the session on the current note selection, or commits/stops if already
  listening — so when no note is selected the existing "select a note" error
  shows). The light refreshes via `sheet.firePropsChanged()` on session
  start/stop.
- When **off**, the dispatcher caps the effective press count at 1, so the 2x/3x
  word/page bindings are never reached and `Shift-Up`/`Shift-Down` always extend
  by a single note. The base single-note bindings are unaffected either way.

Timing and repeat rules:

- The press count is the number of consecutive identical `Shift-Up`/`Shift-Down`
  strokes seen within a rolling 350 ms window. A pause longer than 350 ms resets
  the count back to a single-note extend.
- **Holding the key** (OS auto-repeat) is detected and forced to `pressCount = 1`,
  so holding `Shift-Down` keeps extending note-by-note instead of jumping to word
  then page. Escalation requires deliberate, separate taps.
- If a given press count has no binding, the registry falls back to the highest
  configured count less than or equal to the actual count.

Side effects after any extend:

- `adjustMultiSize()` switches the sheet to a multi-page zoom when the selection
  spans page breaks, and back to single-page zoom otherwise.
- The player position and scroll position follow the moving edge of the
  selection.

## Core Rules

- Selection extension only ever lands on note rows; intermediate non-note rows
  (page breaks, etc.) are skipped, but a spanned page break still counts toward
  `adjustMultiSize()` page math.
- `nextBeat(add=true)` always anchors at `rows[0]` (the top of the current
  selection) and grows downward to the next note.
- `prevBeat(add=true)` is asymmetric by design of the current code:
  - With a **single** row selected it grows upward (anchor becomes the bottom).
  - With **multiple** rows selected it *contracts* from the tail
    (`rows[length-2]`) rather than growing past the top anchor. This means
    `Shift-Up` after a `Shift-Down` run shrinks the selection instead of
    reversing symmetrically. See Extension Notes — this is the most common
    source of "selection behaves weirdly" reports.
- Word-boundary extends only commit if the newly reached note is actually a word
  start/end (`isWordStartAtSelectionTail` / `isWordEndAtSelectionHead`);
  otherwise the single-note extend from the first sub-press stands.
- Page-boundary extends snap to the next/previous page break, and if the head/
  tail is already immediately at a boundary they jump to the following page's
  boundary so a 3rd press keeps making progress.

## Data And Configuration

- Press-count window: `editorKeySequenceTracker = new KeySequenceTracker(350L)`
  in `YassActions` (the 350 ms is hard-coded, not a user property).
- `shift-arrow-escalation` (boolean, default `true`) in `~/.yass/user.xml` gates
  the multi-press escalation. Seeded via `putIfAbsent` in `YassProperties`
  alongside `mouseover`/`sketching`. Toggled by `toggleShiftArrowEscalation`
  (`F8` / Edit-menu checkbox), read at dispatch time.
- i18n label key: `edit_shift_arrow_escalation` (in all six `yass_*.properties`).

## Code Entry Points

- `YassActions.getEditorMovementShortcutBindings()` (~`:628-633`) — base
  `Shift-Up`/`Shift-Down` -> `selectPrevBeat`/`selectNextBeat` bindings.
- `YassActions` ~`:13054-13069` — `bindEditorPressCountShortcut` registrations
  for the 2x/3x word and page escalations.
- `YassActions` actions `selectNextBeat`, `selectPrevBeat`, `selectCurrentWord`,
  `selectCurrentWordUp`, `selectToEndOfCurrentPage`, `selectToStartOfCurrentPage`
  (~`:3867-3906`).
- `yass.input.EditorKeyDispatcher.dispatchKeyEvent` — counts presses, suppresses
  auto-repeat (`pressedKeyCodes`), and also caps the count to 1 when its
  `multiPressEscalationEnabled` `BooleanSupplier` returns false. Routes to the
  registry. `isTrackedMultiPressStroke` marks the two escalating chords.
- `YassActions.toggleShiftArrowEscalation` action + `shiftArrowEscalationCBI`
  checkbox (Edit menu, next to `selectNextBeat`/`selectPrevBeat`) + the `F8`
  `EditorShortcutBinding` in `getEditorGlobalShortcutBindings()`. The dispatcher's
  supplier is wired in `initEditorKeyDispatcher()`
  (`() -> prop == null || prop.getBooleanProperty("shift-arrow-escalation")`).
- `YassActions.setShiftArrowEscalation` (shared by F8/menu/info-bar; persists,
  syncs the checkbox, fires `sheet.firePropsChanged()`),
  `YassActions.isShiftArrowEscalationEnabled`, `YassActions.isMicPitchActive`,
  `YassActions.toggleMicPitch` (wraps the `Ctrl-Shift-M` action).
- `YassSheetInfo.paintStatusIndicators` draws both indicators; `escalationHitRect`
  / `micHitRect` + the `SHOW_ESCALATION` / `SHOW_MIC` hilite cues +
  `toggleEscalation()` / `toggleMic()` handle the click/hover (hover box only).
  Mic session start/stop calls `firePropsChanged()` in `YassActions` (mic-pitch
  start path and `teardownMicPitchSession`).
- `yass.input.KeySequenceTracker` — rolling-window consecutive-stroke counter.
- `yass.input.EditorKeyBindingRegistry` — `(KeyStroke, pressCount) -> command`
  map with the highest-≤ fallback in `get`.
- `YassTable.nextBeat` / `prevBeat` (~`:4057`, `:3992`) — core single-step
  extend logic and the `prevBeat` contract-vs-grow branch.
- `YassTable.extendSelectionDownToWordBoundary` / `...UpToWordBoundary` /
  `...DownToPageBreak` / `...UpToPageBreak` (~`:4160-4276`) — boundary jumps.
- `YassTable.adjustMultiSize` (~`:4127`) — multi-page zoom side effect.

## Regression Coverage

- `test/groovy/yass/YassTableSpec.groovy`:
  - `selectPrevBeat extends a single-note selection upward`
  - `selectNextBeat extends a single-note selection downward`
- `test/groovy/yass/input/EditorKeyDispatcherSpec.groovy`:
  - `does not treat held shift-down repeats as multi-press escalation`
    (auto-repeat suppression)
  - `counts shift-down as true double press only after release and second press`
  - `keeps shift-down single-note when multi-press escalation is disabled`
    (the `shift-arrow-escalation` off path)
- **Gaps:** the `YassTable`-level word/page boundary jumps
  (`extendSelection*ToWordBoundary` / `*ToPageBreak`) and the `prevBeat`
  multi-row contract branch still have no direct Spock coverage.

## Extension Notes

- **Known feedback split (addressed):** some users dislike the timing-based
  escalation and want plain single-note `Shift-Arrow`; the maintainer values the
  fast multi-page selection. Resolved with the `shift-arrow-escalation` toggle
  (default on) — when off, the dispatcher caps the press count at 1 so
  `Shift-Arrow` is always note-by-note. The toggle is implemented at the
  dispatcher level (count cap) rather than by un-registering bindings, so the
  word/page commands stay registered and instantly re-enable when toggled back.
- Considered but not chosen: moving word/page escalation onto distinct modifier
  chords (e.g. `Ctrl-Shift-Arrow` = page). Rejected because the Mac arrow+modifier
  namespace is effectively full (see `remapForPlatform`) and it would cost
  current users' muscle memory. The on/off toggle was preferred.
- If the 350 ms window is ever exposed, route it through `KeySequenceTracker`'s
  constructor; do not scatter literals.
- The `prevBeat` asymmetry (contract-on-multi-row) is worth treating as a
  separate bug investigation; symmetric reverse-extend would likely match user
  expectation better but may change selection semantics other code relies on.
