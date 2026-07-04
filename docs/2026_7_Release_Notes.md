# Yass Reloaded 2026.7 Release Notes

These release notes summarize the **user-facing changes** planned for `2026.7_release`.

## Highlights

- New **Pitch Shift** workflow for songs that are tuned slightly too high or too low
- New **Set Pitch From Microphone** action to set note heights by singing or humming
- Better **wizard subtitle and transcript handling**, especially for YouTube-based song creation
- More reliable **Align to Melody** and transcript-based note rebuilding
- New **Remove Reverb (MVSEP)** helper for existing `#VOCALS` tracks
- Better editor shortcuts on macOS and a setting for repeated `Shift-Up` / `Shift-Down` selection behavior
- Several UI, Preferences, USDB compare, autosave, and localization fixes

## Pitch Shift

- New **Pitch Shift...** dialog in the editor's Extras menu.
  - Open a song, choose `Extras -> Pitch Shift...`, then either analyze the tuning or enter a manual correction in cents.
  - Aubio is required for automatic analysis; manual corrections still work without Aubio.
  - Small offsets below 5 cents are reported as "no correction needed".
- Pitch correction is stored per song.
  - Yass writes the correction into the song's `#COMMENT` data as `pitchShiftCents`.
  - Playback uses a temporary corrected audio file, so the original files are not changed just by trying the feature.
  - When a non-zero correction is active, a small cents field appears in the editor toolbar for quick adjustments.
- The correction can also be made permanent.
  - Use **Render to Files...** in the Pitch Shift dialog.
  - FFmpeg is required for rendering.
  - Yass creates new shifted audio files, keeps the originals, updates the song to point to the new files, and removes the stored live correction so it is not applied twice.
- Pitch lines and melody alignment now stay in sync with pitch-shifted playback.
  - Previously, the detected pitch view could apply the correction twice in some cases.

## Set Pitch From Microphone

- New **Set Pitch From Microphone** action in the editor.
  - Configure a microphone first in `Extras -> Preferences... -> Editor -> Control`.
  - If the microphone is too quiet or too sensitive, adjust **Mic sensitivity** on the same settings page.
  - Devices are detected at startup, so reconnecting a microphone may require restarting Yass Reloaded before it appears.
  - If no microphone is configured yet, the on-screen hint now names this path and can be dismissed with `Esc`.
- To use it:
  - select one note or several notes
  - press `Ctrl-Shift-M` or choose the menu action
  - sing or hum the intended pitch
  - Yass applies the detected pitch to the selected note(s)
- While listening:
  - `Up` / `Down` shifts the detection window by octave
  - selecting another note accepts the current pitch and continues on the new selection
  - pressing `Ctrl-Shift-M` again or `Esc` accepts and turns listening off
  - use undo afterwards if you want to discard the accepted pitch
- Captured microphone audio is not stored. The feature only uses the detected pitch.

## Wizard, Subtitles, And Lyrics

- The Create Song Wizard can now use YouTube subtitles more reliably as transcript input.
  - Start from a YouTube URL as usual.
  - If subtitles are downloaded, Yass can keep the selected subtitle file for the final song creation step.
  - Manual subtitles are preferred over auto-generated subtitles when both are available.
- Subtitle data can be used as a fallback transcript.
  - This helps when no full OpenAI or WhisperX transcription was run.
  - The fallback transcript can still be saved as `yass-transcript.json` for later reuse.
- Subtitle, LRC, and LRCLib timing sources are handled more consistently.
  - All-caps lyric subtitles are normalized more cleanly.
  - Coarse line timings can be refined against separated vocals when available.
  - Wizard output can use the same transcript artifact model that editor transcription workflows already use.
- The wizard logs subtitle and transcript decisions more clearly, which should make missing transcript artifacts easier to diagnose.

## Align To Melody And Transcript Rebuilds

- **Align to Melody** and transcript-based note rebuilding are more vocal-aware.
  - This especially helps lyrics coming from subtitles, local `.lrc` files, LRCLib, OpenAI, or WhisperX.
  - Yass can use vocal pitch and energy data to place words closer to the actually sung timing.
- Better handling of song starts and page boundaries.
  - Songs with `#GAP:0` can scan for the first real vocal entry instead of anchoring to intro dialog or bleed.
  - The first note after a page break can use a nearby unused vocal pitch island instead of leaving that sung bit unassigned.
- Better handling of sustained and melismatic vocals.
  - Notes can be extended when the vocal energy clearly continues.
  - Extra stable pitch runs can be reclaimed instead of being dropped.
  - Long sung blocks are less likely to steal timing from the following words.
- Manual editor alignment stays more predictable.
  - When you align an existing selected note, the pitch decision is based on that note's original range rather than being pulled by a nearby lead-in beat.

## MVSEP And Audio Tools

- New **Remove Reverb (MVSEP)** action.
  - Configure your MVSEP API token first.
  - Open a song that already has a usable `#VOCALS` file.
  - Choose `Extras -> Remove Reverb (MVSEP)`.
  - Yass uploads the current vocals track, downloads the de-reverbed result into the song folder, and asks whether it should become the new `#VOCALS` file.
- Reverb Removal is kept separate from the normal MVSEP stem-separation model list, so it no longer appears as if it were a normal separation preset.
- The reverb-removal status dialog supports progress output and cancellation in the same style as other separation actions.

## Editor Shortcuts And Selection

- macOS has dedicated editor shortcut mappings for arrow-key editing.
  - This avoids several combinations that macOS normally intercepts for Mission Control, Spaces, or window navigation.
  - The GitHub Pages shortcut reference now lists Windows/Linux and macOS variants side by side.
- Repeated `Shift-Up` / `Shift-Down` selection behavior is configurable.
  - With the option enabled, repeated presses can extend the selection to the current word or page boundary.
  - With the option disabled, `Shift-Up` and `Shift-Down` stay note-by-note.
  - Configure this in `Extras -> Preferences... -> Editor -> Keyboard`.
- The shortcut reference was updated for current note movement, resizing, alignment, microphone pitch, and lyrics actions.

## USDB And Library Workflows

- USDB compare/edit now detects solo-vs-duet mismatches.
  - If the local song and the selected USDB song are different solo/duet variants, Yass no longer tries to compare incompatible text.
  - Forced mismatches show a clear message.
  - Automatic compare can fall back to search so the correct variant can be selected.
- The USDB Syncer metadata dialog is easier to use for long fields.
  - It is scrollable/resizable enough to show rows such as Video URL.
- Background image refreshes are more consistent after metadata changes.

## Preferences, Autosave, And UI Fixes

- French and Spanish Preferences no longer crash when opening `Extras -> Preferences`.
  - Fixes [GitHub issue #51](https://github.com/DoubleDee73/Yass-Reloaded/issues/51).
  - The issue was a duplicated translated language-list key that overwrote the internal language choices.
- Autosave is less intrusive during active editing.
  - The backup write path no longer performs a verify-reload step that could interrupt editing with a dialog.
  - The autosave interval is still configured in `Extras -> Preferences... -> Editor -> Control`.
  - `0` disables autosave; the default remains 300 seconds.
- Tag chips in the song header look cleaner.
  - The remove button now stays next to the tag label instead of drifting far away.
- The absolute pitch view recenters more sensibly after transcript rebuilds.
- The transcript overwrite prompt no longer mentions WhisperX when the transcript came from another source.
- New translated UI text was added for pitch shift, microphone pitch capture, mic sensitivity, MVSEP reverb removal, and related prompts.

## Documentation

- GitHub Pages now includes a dedicated **Pitch Shift** page.
- The keyboard shortcut page now documents macOS-specific editor shortcuts.
- The settings, editor, wizard, and tools pages were updated for:
  - Set Pitch From Microphone
  - mic sensitivity
  - autosave interval
  - repeated `Shift-Up` / `Shift-Down` selection behavior
  - MVSEP Reverb Removal
  - YouTube subtitle transcript fallback
