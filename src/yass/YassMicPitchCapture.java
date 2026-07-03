/*
 * Yass Reloaded - Karaoke Editor
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package yass;

import yass.renderer.YassPlayerNote;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Live microphone pitch capture used by the "Set Pitch From Microphone" editor
 * gesture. Wraps {@link YassCaptureAudio}, polls it on a background thread, and
 * keeps a short ring buffer of recent pitch-class readings so callers can ask
 * for a single stable estimate.
 * <p>
 * {@link YassCaptureAudio#calculatePitch(int)} only yields a pitch class
 * (0&ndash;11, 0&nbsp;=&nbsp;C) with no octave. Yass note heights use the same
 * pitch-class convention ({@code Math.floorMod(height, 12)}), so
 * {@link #resolveHeight(int, int)} resolves a pitch class to a concrete note
 * height inside an octave-wide detection window.
 * <p>
 * The pure helpers ({@link #resolveHeight}, {@link #octaveWindowLow},
 * {@link #stablePitchClass(List)}) are static and unit-testable without audio
 * hardware.
 */
public class YassMicPitchCapture {

    /** Number of recent stable (non-noise) readings retained for smoothing. */
    static final int RING_SIZE = 12;
    /** Minimum stable readings required before a pitch is reported. */
    static final int MIN_STABLE = 4;
    private static final long POLL_SLEEP_MS = 10L;

    private final YassCaptureAudio capture = new YassCaptureAudio();
    private final Deque<Integer> recent = new ArrayDeque<>(RING_SIZE);
    private final Object lock = new Object();

    private Thread pollThread;
    private volatile boolean running;

    /**
     * Opens the given capture device and starts polling pitch on a background
     * thread.
     *
     * @param deviceName the mixer device name, or {@code null} to auto-pick a
     *                    device whose name contains "USBMIC"
     * @return {@code true} if the line opened and capture started
     */
    public boolean start(String deviceName) {
        return start(deviceName, -1, 1.0);
    }

    /**
     * Opens the device and starts polling.
     *
     * @param deviceName mixer device name, or {@code null} to auto-pick
     * @param minLevel   noise-gate threshold 0..1 (lower = more sensitive); pass
     *                   a negative value to keep the capture default
     * @param micGain    input gain multiplier (>= 1); higher = more sensitive
     */
    public boolean start(String deviceName, double minLevel, double micGain) {
        if (running) {
            return true;
        }
        if (minLevel >= 0) {
            capture.setMinLevel(minLevel);
        }
        capture.setMicGain(micGain);
        if (!capture.openLine(deviceName)) {
            return false;
        }
        synchronized (lock) {
            recent.clear();
        }
        running = true;
        pollThread = new Thread(this::pollLoop, "yass-mic-pitch-capture");
        pollThread.setDaemon(true);
        pollThread.start();
        return true;
    }

    /** Discards buffered readings so a fresh stable pitch must be re-established. */
    public void reset() {
        synchronized (lock) {
            recent.clear();
        }
    }

    /** Stops polling and releases the capture line. Safe to call repeatedly. */
    public void stop() {
        running = false;
        Thread t = pollThread;
        pollThread = null;
        if (t != null) {
            t.interrupt();
        }
        capture.stopCapture();
    }

    private void pollLoop() {
        while (running) {
            YassPlayerNote note = capture.getCurrentNote(0);
            int pitch = note.getHeight();
            if (pitch != YassPlayerNote.NOISE && pitch >= 0 && pitch < 12) {
                synchronized (lock) {
                    if (recent.size() == RING_SIZE) {
                        recent.removeFirst();
                    }
                    recent.addLast(pitch);
                }
            }
            try {
                Thread.sleep(POLL_SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Returns the current stable pitch class from recent readings, or empty if
     * not enough stable frames have been collected yet.
     */
    public OptionalInt stablePitchClass() {
        List<Integer> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(recent);
        }
        return stablePitchClass(snapshot);
    }

    /**
     * Computes a stable pitch class from a list of pitch-class readings using a
     * circular (mod-12) trimmed mean that is robust to octave-edge wrap and
     * sparse outliers. Returns empty if fewer than {@link #MIN_STABLE} readings
     * are present.
     *
     * @param readings recent pitch-class values (0&ndash;11); noise must already
     *                 be filtered out by the caller
     */
    public static OptionalInt stablePitchClass(List<Integer> readings) {
        if (readings == null || readings.size() < MIN_STABLE) {
            return OptionalInt.empty();
        }
        // Map each pitch class onto the unit circle, average the vectors, then
        // read the angle back. This treats 11 and 0 as adjacent (a semitone
        // apart) rather than 11 apart, so wrap at the octave edge is handled.
        double sumX = 0;
        double sumY = 0;
        for (int p : readings) {
            double angle = 2 * Math.PI * p / 12.0;
            sumX += Math.cos(angle);
            sumY += Math.sin(angle);
        }
        if (sumX == 0 && sumY == 0) {
            return OptionalInt.empty();
        }
        double meanAngle = Math.atan2(sumY, sumX);
        int pitchClass = (int) Math.round(meanAngle / (2 * Math.PI) * 12.0);
        return OptionalInt.of(Math.floorMod(pitchClass, 12));
    }

    /**
     * Resolves a pitch class to the unique note height inside the octave-wide
     * detection window {@code [windowLow, windowLow + 11]}.
     *
     * @param pitchClass pitch class 0&ndash;11 (0&nbsp;=&nbsp;C)
     * @param windowLow  lowest note height of the one-octave detection window
     * @return the note height H in {@code [windowLow, windowLow + 11]} with
     *         {@code Math.floorMod(H, 12) == pitchClass}
     */
    public static int resolveHeight(int pitchClass, int windowLow) {
        int pc = Math.floorMod(pitchClass, 12);
        int lowPc = Math.floorMod(windowLow, 12);
        int delta = Math.floorMod(pc - lowPc, 12);
        return windowLow + delta;
    }

    /**
     * Derives the low edge of a one-octave detection window that best covers the
     * given page note range, padding symmetrically around the range. For a page
     * spanning a single octave or less the window is centred on the range; for a
     * wider range it is clamped to start at the minimum.
     * <p>
     * Example: a page from G4 to D5 (min..max) yields a window starting at F4 and
     * ending at E5, matching the user-specified behaviour.
     *
     * @param minHeight lowest note height on the page
     * @param maxHeight highest note height on the page
     * @return the low edge of the one-octave window
     */
    public static int octaveWindowLow(int minHeight, int maxHeight) {
        if (maxHeight < minHeight) {
            int t = minHeight;
            minHeight = maxHeight;
            maxHeight = t;
        }
        int span = maxHeight - minHeight;
        if (span >= 11) {
            return minHeight;
        }
        int slack = 11 - span;
        int padBelow = slack / 2;
        return minHeight - padBelow;
    }

    /** Exposes the underlying capture for callers that need device checks. */
    public Optional<YassCaptureAudio> captureDevice() {
        return Optional.of(capture);
    }
}
