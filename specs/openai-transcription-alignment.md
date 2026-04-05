# OpenAI Transcription Alignment

## Goal

Add an OpenAI-based alignment feature to Yass that uses an existing song audio file to generate timestamped transcription data and then repositions existing notes in time to better match the sung performance.

This is explicitly **not** a full lyrics generation feature. The premise is:

- the song already has notes
- the song already has lyrics
- Yass should use AI transcription timestamps to improve note timing

## Real Song Observations

I inspected two real song folders from the local UltraStar Deluxe library:

### `A Great Big World - Say Something (feat. Christina Aguilera)`

- two `.txt` variants exist in one folder: solo and duet
- the files already distinguish `#AUDIO`, `#VOCALS`, and `#INSTRUMENTAL`
- one version uses:
  - `#AUDIO` = vox track
  - `#INSTRUMENTAL` = instrumental
  - `#VOCALS` = full duet mix
- the duet version uses:
  - `#AUDIO` = full duet mix
  - `#INSTRUMENTAL` = instrumental
  - `#VOCALS` = vox track
- note timing is expressed in UltraStar beats, based on `#BPM` and `#GAP`

### `0-9/10cc - Dreadlock Holiday (Album Version)`

- one `.txt` file exists
- `#VOCALS` points to a `.webm` file, which is acceptable and may contain Opus audio
- `#AUDIO` points to the regular song mix
- `#INSTRUMENTAL` points to an instrumental-with-backing file
- this confirms the feature must accept more than just `mp3` and `wav`

## Product Intent

The feature should:

1. pick the best source audio
2. send it to OpenAI for timestamped transcription
3. compare the recognized text against the lyrics already in the song
4. map recognized words or phrases onto existing notes
5. move notes in time without unnecessarily changing pitches, syllables, or page breaks

## Recommended V1 Scope

I recommend a conservative V1:

- new settings under `External Tools > OpenAI`
- one editor action: `Tools -> Align Notes with Transcription`
- audio source priority:
  - `#VOCALS`
  - otherwise `#AUDIO`
- use OpenAI transcription with timestamps
- align existing notes in time
- preserve note text and pitches
- do not auto-rewrite lyrics in V1
- do not support duet track separation in V1

## Why Conservative V1

Even with good word timestamps, note timing alignment is fuzzy because:

- UltraStar notes are syllable-based, not word-based
- a single word may be split across many notes
- Yass lyrics may contain sustain fragments like `~`
- transcription returns words or segments, not note-level syllables
- punctuation, apostrophes, and repeated filler syllables differ
- duet files may contain multiple singers at once

So the safest first version is:

- match lyrics text against transcript text
- compute a better timing curve
- move note starts accordingly
- only move note ends in a very narrow class of simple songs
- keep the musical structure intact as much as possible

## Settings Proposal

Add a new panel under `Settings > External Tools`:

- `OpenAI`

Fields for V1:

- API key
- transcription model
- language override
- prompt / style hint
- timestamp granularity
- alignment aggressiveness

Recommended defaults:

- model: `whisper-1`
- language override: empty
- timestamp granularity: `word`
- aggressiveness: `conservative`

## Why `whisper-1` First

Recommendation: start with `whisper-1`.

Reason:

- it supports timestamped file transcription
- it supports word timestamps
- it is the most obvious fit for deterministic file-based alignment

Inference from official docs:

- OpenAI's transcription API supports file-based speech-to-text
- `whisper-1` supports `timestamp_granularities[]` including word-level timestamps

Source:

- [OpenAI Speech-to-Text Guide](https://platform.openai.com/docs/guides/speech-to-text)

## UX Proposal

### Entry Point

Editor menu only in V1:

- `Tools -> Align Notes with Transcription`

Rationale:

- alignment affects the currently open song
- the editor already owns the active table and waveform context

### Preconditions

The action is enabled only if:

- an OpenAI API key is configured
- a song is open
- a usable source audio file exists
- the table contains note rows with lyrics
- the song is not a duet

### Start Dialog

The dialog should explain the operation clearly:

- source file used
- model
- language override
- estimated behavior: "Existing lyrics will be matched against the transcription. Notes may be moved in time, but lyrics and pitches will remain unchanged."

Suggested actions:

- `Analyze and Apply`
- `Cancel`

### Progress Dialog

Like MVSEP, but simpler:

- uploading audio
- waiting for transcription
- matching transcript to lyrics
- calculating note timing changes
- applying changes

### Result Summary

Recommendation: apply directly, then show a compact summary dialog.

Show at least:

- matched words/segments count
- unmatched lyric lines
- notes moved count
- average timing delta
- warning if confidence is poor
- warning if filler words were detected but ignored

## Audio Source Selection

Recommended source selection order:

1. existing `#VOCALS` file, if present and readable
2. otherwise existing `#AUDIO`

Reason:

- vocals-only tracks usually improve transcription quality
- several real library songs already have separate vocal files

Accepted formats should include at least:

- `mp3`
- `wav`
- `m4a`
- `opus`
- `webm`
- `flac`

## Alignment Strategy

### Data We Already Have

From the song:

- note rows with beat-based start and duration
- note text fragments
- `#GAP`
- `#BPM`

From OpenAI:

- transcript text
- word or segment timestamps in milliseconds

### Internal Normalization

Before matching, normalize both sources:

- lowercase
- normalize apostrophes
- strip punctuation
- collapse whitespace
- strip or collapse sustain-only fragments like `~` for matching purposes
- optionally map Unicode variants
- keep original text for final display, but match on normalized text

### Matching Unit

Recommendation: match at **word-group / lyric-chunk** level first, not individual notes.

Practical approach:

1. group consecutive notes into lyric chunks
2. derive normalized chunk text from note syllables
3. merge sustain fragments like `~` back into their surrounding lyric tokens for matching
4. align those chunks against transcript words or segments
5. only then distribute timing back down to notes

This avoids overfitting noisy word timestamps to tiny syllables.

### Tilde and Syllable Handling

This is a key Yass-specific rule.

A single sung word can be encoded over many note fragments, for example:

- `Ye`
- `~`
- `~`
- `~s`
- `ter`
- `~`
- `day`
- `~`

For matching, this should be reconstructed into the lexical word, effectively `yesterday`.

Recommendation for V1:

- build a lyric-token layer above raw note text
- treat isolated `~` notes as sustain extensions, not standalone tokens
- merge fragments like `~s` into the current word token
- keep the raw note rows untouched until timing is finally applied

### Chunk Building

A chunk could be:

- one lyric word
- one short phrase until page break
- or one existing line between page breaks

Recommendation for V1:

- use phrase chunks bounded by existing page breaks
- within a chunk, further align by words where possible

This keeps the page structure stable.

### Timing Redistribution

Once a chunk has a new target time span:

- always move note starts conservatively
- preserve the original relative spacing inside the chunk where possible
- keep existing note durations by default
- only scale note durations in a special "simple new song" case

A song counts as a simple new song for duration scaling only if:

- all notes use one constant pitch, and
- note durations are effectively uniform across the chart, and
- the chart does not already show meaningful rhythmic shaping

If these conditions are not met, note lengths stay unchanged.

Guardrails:

- apply alignment only to well-matched regions
- leave unmatched regions untouched
- adjusted regions must not overlap untouched neighboring regions
- if a proposed shift would create overlap, clamp or skip that region
- do not move a chunk more than a configurable max offset unless user confirms
- do not create negative overlaps across neighboring chunks
- if no reliable anchor exists, leave the affected notes untouched

## Filler Words and Non-Lyric Syllables

The canonical lyrics in the `.txt` are assumed to be more trustworthy than the transcription.

That means V1 should treat transcript-only fillers conservatively, especially:

- `oh`
- `ah`
- `ooh`
- `uh`
- `yeah`
- `hey`
- `woo`

Recommendation for V1:

- do not insert new lyric text
- do not create new notes
- do not rewrite existing syllables based on fillers
- if transcript-only fillers appear between otherwise good anchors, ignore them for alignment
- report them in the result summary as ignored fillers

This keeps the alignment useful without polluting the curated lyrics.

## Recommended Matching Algorithm

### Phase 1

Find anchor matches between existing lyrics and transcript:

- exact normalized phrase matches
- exact word sequence matches
- strong fuzzy matches only for nearby alternatives

### Phase 2

Build an alignment path:

- dynamic programming or sequence alignment over lyric tokens vs transcript tokens
- reward exact matches
- mildly reward fuzzy matches
- penalize skips and reorderings heavily
- treat filler-only transcript tokens as ignorable noise

### Phase 3

Extract timing anchors:

- start timestamp for first matched token in a chunk
- end timestamp for last matched token in a chunk

### Phase 4

Reposition notes:

- convert timestamps in ms to beats using current `#BPM` and `#GAP`
- map lyric-token timing back onto the underlying note fragments
- move note starts
- move note ends only for simple new songs
- preserve pitch values

## Important Technical Detail

The feature should **not** change `#BPM` or `#GAP` in V1.

Recommendation:

- keep global `#BPM` and `#GAP`
- move note beat positions only

Reason:

- changing `#GAP` or `#BPM` globally would affect the whole chart
- local note movement is safer and reversible

## Duets and Multi-Version Folders

This is one of the main complexity points.

Observed reality:

- one folder can contain multiple `.txt` variants
- a duet version may use different audio tags than a solo version

Decision for V1:

- operate only on the currently open `.txt`
- do not try to infer sibling versions automatically
- duet songs are blocked in V1 with a clear message

Suggested message:

- "Transcription alignment is currently only available for non-duet songs."

## Service Architecture Proposal

Mirror the MVSEP structure:

- `src/yass/options/OpenAiPanel.java`
- `src/yass/integration/transcription/openai/OpenAiTranscriptionService.java`
- `src/yass/integration/transcription/openai/OpenAiTranscriptionRequest.java`
- `src/yass/integration/transcription/openai/OpenAiTranscriptionResult.java`
- `src/yass/integration/transcription/openai/OpenAiTranscriptWord.java`
- `src/yass/alignment/LyricsAlignmentService.java`
- `src/yass/alignment/LyricsAlignmentPreview.java`

This keeps concerns separate:

- API communication
- transcript parsing
- lyric/token matching
- note timing updates

## UI Integration Proposal

### Settings

`Settings > External Tools > OpenAI`

### Editor

`Tools -> Align Notes with Transcription`

### Phase 2 Candidates

- trigger after MVSEP vocal download
- trigger from library context menu for the current song
- batch analysis without auto-apply

## Logging

Like MVSEP, log on `INFO` and `FINE`.

`INFO`:

- selected source file
- chosen model and language
- transcript duration and token count
- matching summary
- number of notes changed

`FINE`:

- raw API response summary
- normalized token lists
- anchor matches
- rejected matches
- chunk timing deltas
- ignored filler tokens
- lyric-token to note-fragment mappings

Important:

- never log the API key
- avoid logging full lyrics or transcript bodies at `INFO`

## Error Handling

Expected failure cases:

- missing API key
- unsupported or too-large audio file
- transcription request fails
- transcript contains too little matching text
- song is a duet
- noisy backing vocals produce low-confidence alignment

Recommendation:

- fail with clear user-facing messages
- do not apply partial note changes unless enough anchors were found

## Caching

Decision for V1:

- cache the last transcript result per source file in the song folder as sidecar JSON

Possible filename:

- `<audio-basename>.openai-transcript.json`

Reason:

- lets the user iterate on alignment without retranscribing every time
- reduces API cost
- makes the cache transparent and portable with the song

## Privacy / Cost

The feature uploads user audio to OpenAI.

That should be made explicit in:

- the settings panel
- the start dialog

Recommendation:

- add a short privacy/cost note
- allow the user to clear cached transcripts

## Implementation Roadmap

### Slice 1

- settings panel under `External Tools > OpenAI`
- API key storage
- basic service for transcription
- menu action in editor
- source file selection
- transcription request
- compact post-apply summary dialog

### Slice 2

- normalization and tokenization
- lyric chunk extraction from `YassTable`
- tilde-aware lyric-token builder
- alignment engine
- direct note-start alignment

### Slice 3

- guarded note-duration alignment for simple new songs only
- overlap protection between matched and unmatched regions
- undo-safe integration
- logging and summary
- transcript caching

### Slice 4

- filler detection and reporting
- reuse of cached transcript
- optional library integration

## Decisions

The agreed decisions are:

- direct apply is fine, no preview required in V1
- note starts should be aligned
- note lengths should only be adjusted for very simple new songs
- duet songs are blocked in V1
- cached transcripts live in the song folder
- lyrics remain authoritative; transcript-only fillers are ignored, not inserted
- only well-matched regions are adjusted
- matched regions must not overlap untouched regions
- Yass-specific `~` sustain fragments must be merged back into lexical lyric tokens before matching
