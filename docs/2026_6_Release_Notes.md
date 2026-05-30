# Yass Reloaded 2026.6 Release Notes

These release notes summarize the **user-facing changes** planned for `2026.6_release`.

## Highlights

- More reliable **keyboard shortcut handling** across keyboard layouts
- New **Align Timing** and **Align Pitch** variants for melody-based alignment
- Cleaner editor menu organization around alignment actions
- Broader **song queue** support for library audio separation and USDB imports
- Stronger **transcription and lyrics workflows**, including reusable transcript artifacts and local `.lrc` import
- Safer editor cleanup for timing tags, insert-note behavior, splitting, tapping, and error correction

## Keyboard Handling

- Improved editor shortcut handling for keyboard-layout dependent keys
  - `Split Rows` now reacts to the typed `-` character instead of relying only on a physical key code
  - `Join Rows` now reacts to the typed `+` character instead of relying only on a physical key code
  - `Insert Minus` now reacts to the typed `_` character instead of relying only on a physical key code
  - the leading-tilde toggle now reacts to the typed `~` character, with dead-key fallbacks kept for layouts that emit dead tilde/circumflex key presses
  - this makes symbol shortcuts more reliable on layouts where the visible character is not produced by the same physical key, such as UK keyboards
- Shortcut handling is more context-aware
  - editor shortcuts are ignored while focus is in song header fields
  - shortcuts can still be used from the editor surface and lyrics view when lyrics are not actively being edited
- The keyboard dispatcher now keeps character-based shortcuts separate from key-code based shortcuts, which should make future layout-sensitive shortcuts easier to support.

## Alignment

- **Align to Melody** remains available on `M`
  - this keeps the existing behavior: align selected notes to both detected pitch and detected timing
- New **Align Timing** action on `Ctrl+M`
  - adjusts selected note timing and length against the detected melody
  - keeps the current note pitch unchanged
- New **Align Pitch** action on `Shift+M`
  - adjusts selected note pitch against the detected melody
  - keeps note start and length unchanged
  - follows the same octave behavior as **Align to Melody**, including the repeated-align behavior for snapping to the exact pitch line
- The **Edit** menu now exposes all three melody alignment variants next to each other:
  - **Align to Melody**
  - **Align Timing**
  - **Align Pitch**

## Song Queue And Separation

- Existing Library songs can now be queued for audio separation from the Library context menu.
  - multiple selected songs are queued as separate jobs
  - `Open Queue` is available from Library actions without opening USDB Search first
  - the shared queue distinguishes import, import + separation, and separation-only jobs
- Separation-only queue jobs run sequentially and can be cancelled while waiting.
- Existing valid `#VOCALS` and `#INSTRUMENTAL` assignments are not silently overwritten by quiet separation.
- MVSEP output settings now include local Ogg targets:
  - `Ogg/Vorbis`
  - `Ogg/Opus`
  - Yass requests WAV from MVSEP when needed and transcodes locally with FFmpeg
- USDB Search supports selecting multiple search results and queueing them for import together.
  - songs already in the active import queue are skipped
  - importing more than 10 selected songs asks for confirmation
- Local USDB edit saves refresh the open editor song when the saved file is the song currently being edited.
- The USDB edit diff keeps adjacent header changes and note changes as separate diff blocks, making review/navigation clearer.

## Transcription And Lyrics

- The Create Song Wizard can separate vocals and transcribe them during the lyrics step when the configured tools are available.
  - generated stems and transcript artifacts are carried into the final song folder
  - completed wizard runs can be reused when their metadata still matches the current song
- Yass now uses `yass-transcript.json` as the reusable canonical transcript artifact.
  - wizard output can save it into the song folder
  - editor transcription alignment can reuse it later
  - post-wizard prompts mention reusable transcript data when present
- LRCLib lyrics can be compared against transcript text before applying them.
- The LRCLib lyrics action can now import a local `.lrc` file instead of searching online.
  - synced `.lrc` lines are converted into the same segment and word-timing model used by other transcript sources
  - imported local LRC data is tagged as `#LRC`
- Manual lyrics in the Create Song Wizard can use explicit `+` or `•` separators to mark note boundaries.
  - ordinary lines keep the existing dictionary-backed splitting behavior
  - separator markup is not written into generated lyric text
- Clipboard lyrics can be pasted directly into the wizard.
  - when transcript data exists, Yass opens a comparison dialog before applying the clipboard text
  - applying corrected lyrics preserves transcript timing where possible
- OpenAI and WhisperX remain the shared transcription-alignment engines.
  - cached transcription data and `yass-transcript.json` can be reused
  - WhisperX health checks recommend runtime settings from available hardware
  - WhisperX can retry with CPU/int8 after float16 backend failures

## Editor Authoring

- Insert Note/Add Syllable now uses a modal text prompt with clearer OK, Cancel, Escape, and focus behavior.
- Blank Insert Note input creates a single `~` placeholder note when there is safe beat space.
- `Ctrl+Enter` can use a vocal-aware insert path when the selected editor audio is `#VOCALS` and pitch data is loaded.
  - `Shift+Enter` keeps the legacy Insert Note behavior
  - non-vocal or no-pitch cases fall back to the legacy path
- Editor split can use detected pitch data to split obvious multi-pitch syllables more accurately.
  - ambiguous cases fall back to the legacy split
  - continuation text such as `~n` remains stable on repeated splits
- Tapping recording behavior is more predictable.
  - recording can start from the first processable selected note
  - interrupted sessions with completed taps can still be applied
  - recording from the first song note anchors the first tapped note at beat `0`
  - optional post-recording melody alignment preserves the detected octave
- Golden-note suggestions no longer create Pitch Leap candidates across page breaks.
- Unsafe timing tags are cleaned more conservatively when songs are opened or after touched editor sessions.
  - this covers tags such as `#START`, `#END`, `#GAP`, `#VIDEOGAP`, `#PREVIEWSTART`, and medley beat tags
  - changed songs remain marked modified so the user can save through the normal flow

## Library, Search, And Corrections

- Library search/filter matching now folds more Latin letter variants consistently, so names such as `Bløf` can match plain `blof` searches.
- Error correction settings and correction behavior are documented and covered more explicitly.
  - safe batch correction excludes golden-note distribution
  - typographic apostrophe, capitalization, and spacing corrections follow their settings more consistently
  - the Error Checks panel declares all persisted correction settings
- Options and related dialogs use shared owner/placement helpers more consistently, reducing awkward dialog placement on multi-window setups.
