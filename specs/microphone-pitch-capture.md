# Microphone Pitch Capture

## Status

Implemented/current. Editor feature: set the height (pitch) of the selected
note(s) by singing or humming into the configured microphone. Works on a
single note or a multi-note selection; no captured audio is stored.

## Purpose

Lets a user set a note's pitch by ear instead of dragging it, when they know the
intended melody but not the exact note. It is an input method for the selected
note's height — it does not record song audio or create notes (that is
Note-Tapping Recording).

## User-Facing Behavior

- Action **Set Pitch From Microphone** in the edit menu (next to the height
  actions), shortcut **Ctrl+Shift+M**.
- Enabled whenever a song is open (matching the other editor note actions, which
  gate on `isOpened` only because the table selection listener does not re-run
  `updateActions`). If triggered with no note selected, it shows
  `edit_mic_select_one_note` and does nothing.
- The microphone device is chosen in **Options > Editor > Control** ("Mic:"
  dropdown, `SketchPanel`), stored in property `control-mic`. Devices are
  enumerated at startup only.
- Triggering arms a **toggle listen mode**: a banner HUD is drawn into the
  visible grid (`edit_mic_listening`, then `edit_mic_detected <note>` once a
  stable pitch is found), the microphone opens, and all selected note(s) follow
  the detected pitch live (no undo entries during tracking). The banner is
  anchored to the scroll viewport and is visible during playback too.
- Playback and other editor shortcuts keep working while listening; the listen
  session's temporary key dispatcher only consumes **Up / Down** (octave-shift
  the detection window) and **Esc**.
- Acceptance is implicit:
  - **Toggling off** (re-pressing the shortcut/menu) accepts the detected pitch.
  - **Moving the selection to other note(s)** accepts the current note(s) and
    re-arms listening on the new selection ("walk down the line").
  - **Moving to a non-note / empty selection** accepts and turns the mode off.
  - **Esc** restores the original height(s) and turns the mode off.
  - In every case the banner disappears.
- Accept commits as undo step(s) (original height -> detected height). If no
  stable pitch was detected for a note group, it is left at its original height
  and no banner/message lingers (silent).
- With a multi-note selection the single detected pitch is applied to every
  selected note (like the Higher/Lower height actions).
- If no microphone is configured/available, the sheet shows `edit_mic_no_device`
  and nothing is mutated.

## Core Rules

- `YassCaptureAudio.calculatePitch` yields only a **pitch class 0-11 (0 = C)**;
  Yass note heights use the same convention (`Math.floorMod(height, 12)`,
  `YassSheet.getNoteName`). Octave is resolved against the active window, not the
  microphone.
- The detection window is one octave wide. Its low edge is derived from the
  current page's note range via `YassMicPitchCapture.octaveWindowLow(min, max)`:
  for a sub-octave range it pads symmetrically (e.g. page G4-D5 -> window F4-E5);
  for a range >= one octave it clamps the window low to the page minimum.
- `YassMicPitchCapture.resolveHeight(pitchClass, windowLow)` returns the unique
  height H in `[windowLow, windowLow+11]` with `Math.floorMod(H,12)==pitchClass`.
- Stable pitch is a circular (mod-12) mean over a ring buffer of recent non-noise
  readings (`RING_SIZE=12`), requiring at least `MIN_STABLE=4` readings; the
  circular mean treats pitch classes 11 and 0 as adjacent so octave-edge wrap is
  handled. `NOISE` (-1000) readings are discarded.
- Live tracking mutates with `YassTable.setPreventUndo(true)`; accept restores
  originals (undo-suppressed) then re-applies the final height with undo enabled
  plus `fireTableRowsUpdated`, so each accepted note group is one undo step.
- Re-arm on selection change is guarded by a `reArming` flag so the
  `fireTableRowsUpdated` from committing does not recursively re-trigger the
  selection listener.

## Data And Configuration

- Microphone device name from property `control-mic` (populated by
  `YassProperties.loadDevices`; device list in `control-mics`). `null` device
  auto-picks a mixer whose name contains `USBMIC`.
- Audio capture format is fixed by `YassCaptureAudio`: **16-bit signed mono @
  48 kHz** (`AudioFormat(48000, 16, 1, true, false)`). `getDeviceNames` lists any
  mixer that advertises a 16-bit signed mono (or unspecified-channel) capture
  line; the raw buffer is decoded down to the 8-bit numeric range in
  `decodeSamples` so the legacy autocorrelation thresholds stay calibrated.
  Pitch is mono only (no left/right channels).
- `YassProperties.load()` calls `loadDevices()` on **both** the existing-`user.xml`
  path and the first-run fallback, so the device scan runs on every startup.
  Devices are enumerated at startup only.
- No captured audio is persisted; at most high-level debug logging.
- i18n keys: `edit_pitch_from_microphone`, `edit_mic_listening`,
  `edit_mic_detected`, `edit_mic_hint`, `edit_mic_no_device`,
  `edit_mic_select_one_note` (in all `yass_*.properties`). `edit_mic_no_pitch`
  is retained but currently unused (no-stable-pitch is silent).
- Device selection UI: `src/yass/options/SketchPanel.java` ("Mic:" choice bound
  to `control-mic` / `control-mics`).

## Code Entry Points

- `src/yass/YassMicPitchCapture.java` — capture lifecycle and pure pitch/height
  math (`start`, `stop`, `stablePitchClass`, `resolveHeight`, `octaveWindowLow`).
- `src/yass/YassActions.java` — `setPitchFromMicrophone` action,
  `toggleMicPitchCapture` / `onMicPitchTick` / `applyMicPitchHeight` /
  `commitMicPitchSelection` / `acceptAndStopMicPitchCapture` /
  `cancelMicPitchCapture` / `bindMicPitchSelection` /
  `installMicPitchSelectionListener` / `installMicPitchKeyDispatcher` /
  `selectedNoteRows` / `pageHeightRange`, inner `MicPitchSession` (holds the row
  set, original heights, window, timer, key + selection listeners), keybinding in
  `getEditorToggleShortcutBindings`, gating in `updateActions`.
- `src/yass/YassCaptureAudio.java` — `openLine` / `getCurrentNote` /
  `stopCapture`, `calculatePitch` / `autocorrelate` / `calculateLevel` (mono
  16-bit), `decodeSamples`, `getDeviceNames`, `logAvailableCaptureFormats`
  (startup mixer/format diagnostic).
- `src/yass/YassProperties.java` — `loadDevices` (runs on every startup; stores
  `control-mic` / `control-mics`).
- `src/yass/YassSheet.java` — `setMessage` / `setErrorMessage` set the `message`
  field; `paintMessage` draws the banner anchored to the scroll viewport (called
  unconditionally from `paintComponent`, so it shows during playback);
  `formatHeightName` formats a height as a note name; `getNoteName` and the
  pitch-class note tables.

## Regression Coverage

- `test/groovy/yass/YassMicPitchCaptureSpec.groovy` — `resolveHeight`
  octave-correctness and window containment, ±12 window shift, `octaveWindowLow`
  (G4-D5 -> F4-E5, octave clamping, reversed args), `stablePitchClass`
  trimmed/circular mean including the C/B octave-edge case and the
  too-few-readings empty case.
- Live microphone behavior (device open, live tracking, HUD, commit/cancel undo)
  is verified manually; it cannot be exercised in headless tests.

## Extension Notes

- Multi-note selections all receive the same detected pitch. Per-note distinct
  pitches (e.g. tracking a sung phrase across consecutive notes) would be a
  larger enhancement; `resolveHeight` is per-note pure to allow it.
- Listen mode coexists with playback and other shortcuts by design; only
  Up/Down/Esc are consumed while armed. If new global key handling is added,
  ensure listen-mode teardown still runs on focus loss / song close.
- The temporary `KeyEventDispatcher` and the table `ListSelectionListener` are
  both removed in `teardownMicPitchSession`; any new exit path must call it.
- `YassActions.initMic()` remains a disabled legacy live-session path and is not
  used by this feature.
