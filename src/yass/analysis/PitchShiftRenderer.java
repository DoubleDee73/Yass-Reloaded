/*
 * Yass Reloaded - Karaoke Editor
 * Copyright (C) 2009-2023 Saruta
 * Copyright (C) 2024-2025 DoubleDee
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

package yass.analysis;

import org.apache.commons.lang3.StringUtils;
import yass.ffmpeg.FFMPEGLocator;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Builds the FFmpeg audio filter string that pitch-shifts a song by a fixed cents amount, used both
 * for the non-destructive live preview (the playback temp WAV) and the optional permanent bake.
 * <p>
 * Two backends:
 * <ul>
 *   <li><b>rubberband</b> — a true pitch shifter that preserves duration and sounds best, but is an
 *       optional FFmpeg filter that may be absent from the user's build. Probed once and cached.</li>
 *   <li><b>asetrate/atempo fallback</b> — always available. {@code asetrate} resamples (shifting
 *       pitch and duration together), then {@code atempo} restores the original duration. Good
 *       enough for the small sub-semitone corrections this feature targets.</li>
 * </ul>
 * This class is free of UI and song-model state so the filter math is testable in isolation.
 */
public final class PitchShiftRenderer {

    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    /** Output sample rate of the playback temp WAV; the asetrate fallback is expressed against it. */
    public static final int SAMPLE_RATE = 44100;

    private static volatile Boolean rubberbandAvailable;

    private PitchShiftRenderer() {
    }

    /**
     * The linear frequency ratio for a cents offset: {@code 2^(cents/1200)}. A positive correction
     * (raising a flat recording) yields a ratio &gt; 1.
     */
    public static double ratio(double cents) {
        return Math.pow(2.0, cents / 1200.0);
    }

    /**
     * Returns the FFmpeg {@code -af} filter string that shifts pitch by {@code cents}, or an empty
     * string when no shift is needed (so callers can skip adding a filter and keep byte-identical
     * output to the un-corrected path).
     *
     * @param cents          the correction in cents (positive raises pitch)
     * @param useRubberband  whether the rubberband filter may be used
     */
    public static String audioFilter(double cents, boolean useRubberband) {
        if (cents == 0.0) {
            return "";
        }
        double ratio = ratio(cents);
        if (useRubberband) {
            return String.format(Locale.ROOT, "rubberband=pitch=%.8f", ratio);
        }
        // asetrate scales pitch and speed together; atempo (1/ratio) restores the original duration.
        return String.format(Locale.ROOT, "asetrate=%d*%.8f,aresample=%d,atempo=%.8f",
                             SAMPLE_RATE, ratio, SAMPLE_RATE, 1.0 / ratio);
    }

    /** Convenience overload that auto-detects the rubberband backend. */
    public static String audioFilter(double cents) {
        return audioFilter(cents, isRubberbandAvailable());
    }

    /**
     * Whether the located FFmpeg build exposes the {@code rubberband} filter. Probed once via
     * {@code ffmpeg -filters} and cached; defaults to {@code false} (use the always-available
     * fallback) if the probe cannot run.
     */
    public static boolean isRubberbandAvailable() {
        Boolean cached = rubberbandAvailable;
        if (cached != null) {
            return cached;
        }
        synchronized (PitchShiftRenderer.class) {
            if (rubberbandAvailable == null) {
                rubberbandAvailable = probeRubberband();
            }
            return rubberbandAvailable;
        }
    }

    /**
     * Whether an FFmpeg binary can be located for an offline bake. The bake button stays disabled
     * (with an explanatory tooltip) when this is false.
     */
    public static boolean isFfmpegAvailable() {
        return FFMPEGLocator.getInstance().hasFFmpeg();
    }

    /** Resolves the ffmpeg executable, honoring the located install path or falling back to PATH. */
    private static String resolveFfmpegBinary() {
        String ffmpegPath = FFMPEGLocator.getInstance().getPath();
        return StringUtils.isNotBlank(ffmpegPath)
                ? new File(ffmpegPath, "ffmpeg").getAbsolutePath()
                : "ffmpeg";
    }

    /**
     * Renders {@code source} to {@code target}, shifting pitch by {@code cents} via the best
     * available backend. Used as the production {@link PitchShiftBakeService.Renderer}. Throws on a
     * non-zero ffmpeg exit, a timeout, or a missing output so the bake's all-or-nothing logic can
     * abort and clean up. The output container/codec is inferred by FFmpeg from {@code target}'s
     * extension, so a {@code .ogg} source yields a {@code .ogg} copy.
     */
    public static void renderToFile(File source, File target, double cents) throws Exception {
        String ffmpeg = resolveFfmpegBinary();
        String filter = audioFilter(cents);
        if (StringUtils.isBlank(filter)) {
            throw new IllegalArgumentException("No pitch shift to render (cents=" + cents + ").");
        }
        List<String> command = new ArrayList<>(List.of(
                ffmpeg, "-hide_banner", "-y",
                "-i", source.getAbsolutePath(),
                "-af", filter,
                "-vn",
                target.getAbsolutePath()));
        LOGGER.fine("[PitchShiftBake] ffmpeg " + String.join(" ", command));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        if (!process.waitFor(10, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IllegalStateException("ffmpeg pitch-shift render timed out for " + source.getName());
        }
        int exit = process.exitValue();
        if (exit != 0) {
            throw new IllegalStateException("ffmpeg pitch-shift render failed (exit " + exit + ") for "
                    + source.getName() + ":\n" + output);
        }
        if (!target.isFile() || target.length() == 0) {
            throw new IllegalStateException("ffmpeg produced no output for " + source.getName());
        }
    }

    private static boolean probeRubberband() {
        String ffmpeg = resolveFfmpegBinary();
        try {
            Process process = new ProcessBuilder(ffmpeg, "-hide_banner", "-filters")
                    .redirectErrorStream(true)
                    .start();
            boolean found = false;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("rubberband")) {
                        found = true;
                    }
                }
            }
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                LOGGER.fine("rubberband probe timed out; using asetrate/atempo fallback.");
                return false;
            }
            LOGGER.fine("rubberband filter " + (found ? "available" : "not available")
                    + "; pitch-shift backend = " + (found ? "rubberband" : "asetrate/atempo"));
            return found;
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Could not probe for rubberband; using asetrate/atempo fallback.", ex);
            return false;
        }
    }
}
