# WhisperX Alignment

## Goal

Add support for using a locally installed WhisperX as an alternative transcription engine for note alignment.

The feature should produce the same kind of output as the existing OpenAI-based flow:
- word timestamps
- a reusable cached JSON transcript
- alignment against existing Yass notes and lyrics
- `#GAP` and note timing updates as a starting point for manual refinement

WhisperX is optional. Yass must not try to install it automatically.

## Product Intent

This feature is meant to improve the practical usability of note alignment:
- no API costs
- potentially better alignment-quality timestamps
- usable offline when WhisperX is already installed

The resulting alignment still does not need to be perfect.
The goal is a plausible, non-overlapping starting point for manual cleanup.

## Scope

### In scope for V1

- New `External Tools > WhisperX` settings panel
- Local environment validation for WhisperX prerequisites
- New transcription engine option alongside OpenAI
- Shared editor entry point for note alignment
- Reuse-or-regenerate prompt if a WhisperX JSON cache already exists
- Cache transcripts inside the song folder in a dedicated subfolder
- Parse WhisperX word timestamps into the existing alignment model
- Reuse the existing alignment heuristics

### Out of scope for V1

- Automatic WhisperX installation
- Package management from within Yass
- Advanced GPU diagnostics beyond lightweight detection
- Multi-engine transcript comparison UI
- Duet support

## User Experience

### Settings

Add a new panel under `External Tools > WhisperX`.

Suggested fields:
- `Python executable`
- `Use module invocation`
- `Model`
- `Language`
- `Device`
- `Compute type`
- `Cache folder name`
- `Default engine` should stay in the shared OpenAI/WhisperX alignment settings area or be added later

Suggested actions:
- `Test configuration`

### Validation output

The test action should show a compact status block:
- Python found / not found
- WhisperX callable / not callable
- FFmpeg found / not found
- GPU available / not available / unknown
- version info when cheaply available

### Alignment entry point

Keep one shared editor action:
- `Align Notes with Transcription`

Menu behavior:
- disabled if neither OpenAI nor WhisperX is usable
- enabled if at least one configured engine is usable
- execution uses the configured default engine

Future option:
- submenu for explicit engine selection

### Cache behavior

Use a song-local cache subfolder, for example:
- `<song-folder>/.yass-cache/openai/...`
- `<song-folder>/.yass-cache/whisperx/...`

If a WhisperX transcript JSON already exists:
- prompt the user like OpenAI does now
- reuse the cache or regenerate it

## Functional Requirements

### Environment detection

Yass should attempt the following resolution order:
1. configured Python executable
2. `py`
3. `python`

WhisperX invocation should prefer:
- `python -m whisperx`

Reason:
- more robust across Windows installations than assuming `whisperx.exe` is on PATH

### Audio source selection

Use the same source rules as the OpenAI alignment flow:
- use the currently selected editor audio source
- if `Vocals` is selected and valid, use that file
- if `Audio` is selected, use that file
- if `Instrumental` is selected, block the action and ask the user to switch first
- if needed, reuse existing Yass temp-conversion logic for unsupported input containers

### WhisperX execution

Yass should run WhisperX as an external process.

Expected flow:
1. resolve source audio file
2. resolve Python and WhisperX command
3. prepare output folder under `.yass-cache/whisperx`
4. execute WhisperX
5. locate or collect JSON output
6. parse word timestamps
7. pass result into the existing alignment service

### GPU handling

Support both CPU and GPU in V1, but keep detection lightweight.

Suggested detection order:
1. if a configured device is explicit, trust it
2. otherwise, try a tiny Python probe for `torch.cuda.is_available()`
3. if probe fails, fall back to `unknown`

Behavior:
- no hard failure if GPU cannot be detected
- CPU remains valid
- laptop/dev environments without GPU must still work

### Transcript format integration

WhisperX output must be mapped into the same internal structures used by OpenAI alignment where possible.

Needed minimum data:
- transcript text
- ordered words
- start time in ms
- end time in ms

If WhisperX returns segments plus words, Yass should only depend on the word-level subset needed by alignment.

## Architecture Recommendation

Introduce or evolve a shared abstraction for transcription engines.

Suggested shape:
- `TranscriptionService` or similar interface
- `OpenAiTranscriptionService`
- `WhisperXTranscriptionService`

This should keep the following shared:
- cache checks
- result model where practical
- source resolution rules
- summary building patterns
- downstream alignment flow

WhisperX should not duplicate the alignment logic.
It should only provide transcript data in a compatible model.

## Error Handling

User-facing failure messages should clearly distinguish:
- Python not found
- WhisperX not installed or not callable
- FFmpeg missing
- transcription process failed
- cached JSON unreadable

If WhisperX fails but OpenAI is configured, fallback is a product choice for a later phase.
For V1, keep failures explicit and do not silently switch engines.

## Data Storage

Recommended cache structure:
- `.yass-cache/whisperx/transcript.json`
- optionally include source-derived names later if multiple sources per song become common

V1 recommendation:
- one cache file per engine per song per active source role
- simple naming is fine as long as it is deterministic

Possible naming examples:
- `.yass-cache/whisperx/audio-transcript.json`
- `.yass-cache/whisperx/vocals-transcript.json`

## Interaction With Existing OpenAI Flow

WhisperX should follow the same UX conventions as the OpenAI implementation:
- ask before reusing cached transcript
- show progress while transcribing
- apply alignment directly
- update UI immediately after applying `#GAP` and note timing

The user should not need to learn a second workflow.

## Recommendations

### Recommended V1 defaults

- engine invocation: `python -m whisperx`
- cache location: `.yass-cache/whisperx`
- menu model: one shared action, enabled only when at least one engine is configured and usable
- explicit no-install policy
- CPU and GPU both allowed, with lightweight GPU detection only

### Recommended implementation slices

1. Settings panel and health check
2. WhisperX process runner and JSON cache handling
3. Mapping WhisperX JSON into internal transcript words
4. Hook WhisperX into the shared editor alignment action
5. Polish status and error messages

## Open Questions

1. Where should the default engine setting live?
Recommendation: in a shared transcription/alignment settings area, or temporarily in WhisperX settings until OpenAI/WhisperX settings are unified.

2. Should Yass allow a manual override of the engine for a single run?
Recommendation: not in V1, use the configured default engine.

3. Should Yass keep separate caches for `Audio` and `Vocals` selections?
Recommendation: yes, because they can produce materially different transcripts.

4. Should WhisperX version be shown in the settings panel if detectable?
Recommendation: yes, when cheap to query.