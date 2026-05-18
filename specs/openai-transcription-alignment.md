# OpenAI Transcription Alignment

## Status

Implemented/current. OpenAI is one transcription engine for the shared
transcription-alignment workflow.

## Purpose

OpenAI transcription alignment uses an existing song audio file to generate
timestamped transcript data and then improves existing note timing or rebuilds
notes from transcript timing. It is not a general lyrics-generation feature:
the normal premise is that the song already has lyrics or receives curated
lyrics through wizard/LRCLib flows.

## User-Facing Behavior

- Editor action: **Align Notes with Transcription**.
- The action can use OpenAI or WhisperX depending on transcription engine
  settings and availability.
- If a cached transcription or `yass-transcript.json` exists, Yass can ask the
  user whether to reuse it.
- After transcription, the user can apply alignment to existing notes or rebuild
  notes from transcript timing when that flow is available.
- OpenAI settings live under External Tools / Transcription:
  - API key
  - transcription model
  - optional language
- Progress and failure messages are shown in the alignment workflow; raw
  exceptions should be wrapped in useful IO messages.

## Core Rules

- Audio source priority for editor alignment is `#VOCALS` first, then `#AUDIO`
  or the selected/source audio fallback used by the request builder.
- Requests ask OpenAI for timestamped transcription data.
- Returned data is converted into `OpenAiTranscriptionResult`.
- Existing-note alignment tokenizes current Yass lyrics and transcript words,
  finds safe anchors, then redistributes timing without casually changing pitch
  or text.
- Rebuild mode deletes/replaces note rows from transcript timing and then may
  align rebuilt notes to melody when pitch data is available.
- Filler words and non-lyric syllables are handled conservatively. The feature
  should not invent note text that the chart did not ask for.
- Duet/multi-singer ambiguity remains a high-risk area; do not broaden support
  without explicit tests.

## Data And Configuration

- Properties:
  - `openai-api-key`
  - `openai-model`
  - `openai-language`
  - transcription engine selection properties in the shared transcription panel
- OpenAI raw/cache responses are stored in the configured transcript cache
  folder.
- Canonical reusable artifact: `yass-transcript.json`.

## Code Entry Points

- `src/yass/integration/transcription/openai/OpenAiTranscriptionService.java`
  - request creation
  - multipart upload
  - response parsing
  - cache handling
- `src/yass/integration/transcription/openai/OpenAiTranscriptionResult.java`
  - canonical transcript model
- `src/yass/alignment/LyricsAlignmentService.java`
  - existing-note alignment
- `src/yass/alignment/LyricsAlignmentTokenizer.java`
  - Yass lyric tokenization
- `src/yass/alignment/TranscriptNoteRebuildService.java`
  - rebuild notes from transcript timing
- `src/yass/alignment/TranscriptTimingRefinementService.java`
  - optional vocal-aware timing refinement
- `src/yass/YassActions.java`
  - editor action, prompts, reuse, apply/rebuild flow
- `src/yass/options/OpenAiPanel.java`
  - settings UI

## Regression Coverage

- `LyricsAlignmentServiceSpec`
  - transcript alignment and rebuild behavior with real transcript fixtures
- `TranscriptNoteRebuildServiceSpec`
  - phrase grouping and note rebuild rules
- `TranscriptTimingRefinementServiceSpec`
  - vocal-aware timing refinement before alignment
- `TranscriptArtifactServiceSpec`
  - reusable transcript artifact persistence
- OpenAI-specific network calls are not integration-tested against the live API;
  keep parsing and request construction testable without network access.

## Extension Notes

- Keep OpenAI and WhisperX converging into `OpenAiTranscriptionResult`; do not
  fork alignment logic per engine unless absolutely necessary.
- Preserve existing lyrics unless the user explicitly chooses a rewrite/rebuild
  path.
- If adding new OpenAI models or response formats, update parsing and cache
  compatibility together.
- Be careful with multipart error handling. Server-side errors can be plain text
  or HTML, not only JSON.
