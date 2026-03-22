# Wizard Separation + Transcription

## Goal

The song creation flow should optionally automate:

1. audio download via `yt-dlp`
2. vocal separation via MVSEP
3. local transcription via WhisperX
4. immediate lyric population in the wizard
5. later note generation from transcript timing instead of from plain split lyrics

The feature should still support the current conservative alignment path for existing songs, but offer a stronger "transcript as truth" mode where appropriate.

## Observed Current Flow

- `YouTube` already downloads audio and subtitles into `temp-dir`.
- `Lyrics` currently owns the text entry area and can already generate a dummy `melodytable` from lyrics or subtitles.
- `MP3` and final wizard completion move audio/video files from temp into the song folder.
- `Tap` displays the generated `melodytable`.
- `Align Notes with Transcription` already exists for existing songs and can consume OpenAI or WhisperX transcripts.

This means the cleanest integration point is:

- wizard-side transcript generation on the `Lyrics` step
- note generation changes inside the lyrics-to-table path
- a separate, explicit "overwrite current notes from transcript" option in the editor flow

## Product Direction

### Phase 1: Editor-Side Rebuild Option

When `Align Notes with Transcription` is triggered and a WhisperX transcript is available:

- ask: `Overwrite current notes with detected transcription?`
- `Yes`:
  - delete existing notes/pagebreaks
  - rebuild notes from transcript timing and transcript text
  - treat transcript timestamps as truth
- `No`:
  - keep the current alignment path
  - use transcript only to move existing notes

This is the smallest valuable slice because it reuses the existing editor and lets us validate the note generation strategy before embedding it into the wizard.

### Phase 2: Wizard Button On Lyrics Step

If all of the following are true:

- audio was already downloaded into temp by `yt-dlp`
- MVSEP is configured
- WhisperX is configured and has passed health check in this session

then the `Lyrics` wizard step shows a button at bottom-left:

- `Separate Vocals + Transcribe`

Pressing it does:

1. resolve wizard audio source from downloaded temp audio
2. convert to wav if needed
3. submit to MVSEP
4. store stems in temp workspace
5. run WhisperX on the selected vocals stem
6. load transcript text into the lyrics text area
7. store transcript JSON as wizard temp state

### Phase 3: Wizard Finish Uses Transcript-As-Truth

If wizard temp state contains a transcript generated during the lyrics step:

- copy transcript JSON into the song folder cache structure
- copy generated stems into the song folder with final filenames
- generate initial notes from transcript timing instead of the old uniform-length lyric split

## Availability Rules

### MVSEP availability

MVSEP is available if:

- API token exists

### WhisperX availability

WhisperX is available if:

- configuration is present
- last health check in this session succeeded

Recommendation:

- store a lightweight session flag such as `whisperx-available-session=true`
- set it after successful health check
- clear it on startup
- do not persist it as a long-lived truth, because the local environment can change

### Wizard button enablement

The wizard button should be:

- hidden or disabled when prerequisites are missing
- preferably visible but disabled, with a short hint why

Recommendation:

- show disabled button plus tooltip/reason text

## Temp Workspace Design

Use a wizard-specific temp subfolder under `temp-dir`, for example:

- `<temp-dir>/wizard-separation/`

Per run:

- source audio
- converted wav
- MVSEP results
- WhisperX transcript
- wizard state metadata

Recommendation:

- create a run-specific subfolder
- e.g. `<temp-dir>/wizard-separation/<timestamp-or-random-id>/`

This avoids collisions when the user restarts the wizard or runs multiple attempts.

## Final Song Folder Structure

On wizard finish, copy artifacts into the song folder:

- final audio
- final stems
- transcript cache

Recommendation:

- reuse the same cache convention as editor-side transcription:
  - `.yass-cache/whisperx/vocals-transcript.json`
- if MVSEP is used:
  - copy final vocal/instrumental stems with normal song naming

## Transcript Editing Model

User request:

- when lyrics are corrected in the wizard text area, the transcript JSON should reflect those edits

This is desirable, but needs a careful scope.

### Safe V1 approach

When transcript text is loaded into the lyrics area:

- keep the original timestamp structure
- maintain a mutable token list in wizard state
- when the user edits lyrics, update token text only where a stable token mapping exists

What should not happen in V1:

- re-time words because the user changed text
- freely insert or delete timestamps without a clear mapping

Recommendation:

- treat transcript timing as immutable
- allow text replacement on matched token positions
- if the edit is too structurally different, mark transcript as `text diverged`
- then fall back to current plain-lyrics note generation unless the user explicitly accepts degraded timing fidelity

## Transcript-As-Truth Note Generation

This is different from alignment.

### Alignment mode

- existing notes stay
- transcript moves note starts conservatively

### Rebuild mode

- existing notes are discarded
- transcript words become the source of timing and lyric content

For rebuild mode:

1. use transcript word timestamps as anchors
2. split transcript words into Yass syllables
3. distribute each word's time range across its syllables
4. create notes with those start/length values
5. derive pagebreaks from transcript segments or pauses

Recommendation:

- use a constant pitch and simple note type in V1 rebuild mode
- keep pitch generation separate from timing generation

This matches the user's goal:

- the auto-generated notes are only an initial scaffold for manual refinement

## Rebuild Heuristic

For `Overwrite current notes with detected transcription`:

- source of truth is transcript timing, not existing notes
- use transcript text unless the user already edited synced transcript text
- generate note starts and lengths from transcript timing

Suggested V1 rebuild rules:

- each transcript word becomes one or more notes
- syllabification uses the existing `YassHyphenator`
- word duration is divided across syllables proportionally
  - simple equal distribution is acceptable in V1
- insert a pagebreak when:
  - transcript segment boundary exists
  - or inter-word silence exceeds threshold

Guardrails:

- minimum note length in beats
- minimum inter-note gap
- no overlap
- monotonic timestamps only

## Wizard UI Proposal

### Lyrics step additions

- bottom-left button: `Separate Vocals + Transcribe`
- status text area or inline label:
  - `Separating vocals...`
  - `Transcribing vocals with WhisperX...`
  - `Transcript loaded`

Optional later:

- small source indicator:
  - `Transcript source: WhisperX on MVSEP vocals`

### Editor alignment dialog additions

If a WhisperX transcript is available:

- ask before apply:
  - `Overwrite current notes with detected transcription?`

Buttons:

- `Yes` = rebuild notes
- `No` = current alignment path
- `Cancel` = abort

## Implementation Roadmap

### Slice 1: Rebuild Existing Notes From Transcript

- extend current alignment action
- detect available WhisperX transcript
- add overwrite prompt
- implement transcript-to-note-table builder
- reuse existing test fixture approach

### Slice 2: Wizard Temp Transcript State

- add wizard temp run context
- add lyrics-step button
- run MVSEP + WhisperX from wizard
- populate lyrics area
- store transcript temp JSON

### Slice 3: Wizard Finish Artifact Copy

- copy stems to final song folder
- copy transcript cache to final cache folder
- use transcript builder instead of plain lyric splitter when transcript exists

### Slice 4: Editable Transcript Text Synchronization

- track editable transcript token list
- reflect simple lyric corrections into transcript text
- detect divergence and fall back safely when edits are too structural

## Recommendations

- build the editor-side rebuild option first
- keep transcript-as-truth separate from alignment
- keep transcript timing immutable in V1
- use wizard temp subfolders per run
- persist only finalized artifacts into the song folder on finish
- only expose the wizard button when both MVSEP and WhisperX are practically usable

## Open Questions

1. If the wizard user edits transcript text heavily, should we:
   - a) keep transcript timings and only update visible lyric text where mapping is obvious
   - b) discard transcript timing and fall back to plain lyric note generation

Recommendation:

- a) first, and automatically fall back to b) on structural divergence

2. In rebuild mode, should we derive pagebreaks only from pauses, or also from WhisperX segments?

Recommendation:

- use WhisperX segments first, pauses second

3. For wizard-side MVSEP, should the default vocal source be `Vocals-full` if available, otherwise `Lead`?

Recommendation:

- yes
