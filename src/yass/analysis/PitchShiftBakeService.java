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

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestrates the optional offline bake of a song's global pitch-shift correction into new
 * audio files (slice 4b). This is the permanent counterpart to the live playback preview
 * (slice 4a): it writes shifted copies of every audio file the song references and reports a
 * tag-to-new-filename mapping the caller applies to the song model.
 * <p>
 * The bake is <b>all-or-nothing at the song-metadata level</b>. Renders go to temporary
 * candidate files; only when every required render succeeds are the candidates promoted to
 * their final names and the mapping returned. If the user cancels or any render fails, all
 * candidates are deleted and the {@link Outcome} carries no tag updates, so the caller leaves
 * the source files, audio tags, and the {@code pitchShiftCents} comment untouched.
 * <p>
 * If several tags point to the same source file, that file is rendered once and every tag that
 * referenced it is mapped to the single rendered copy, so the tags stay consistent.
 * <p>
 * Rendering is injected as a {@link Renderer} so the commit/cleanup logic is unit-tested
 * without invoking FFmpeg; the production renderer is {@link PitchShiftRenderer#renderToFile}.
 */
public final class PitchShiftBakeService {

    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    /** Marker inserted into rendered filenames; also used to detect already-baked sources. */
    public static final String SHIFTED_MARKER = "(Pitch Shifted)";

    /** Renders {@code source} to {@code target}, shifting pitch by {@code cents}. Throws on failure. */
    @FunctionalInterface
    public interface Renderer {
        void render(File source, File target, double cents) throws Exception;
    }

    /** Polled between renders so a long bake can be cancelled cooperatively. */
    @FunctionalInterface
    public interface CancelCheck {
        boolean isCancelled();
    }

    /**
     * Result of a bake attempt.
     *
     * @param success    whether every render succeeded and candidates were promoted
     * @param reasonKey  an i18n key describing why a bake did not run/succeed (or did succeed)
     * @param tagUpdates ordered map of audio tag (e.g. {@code "VOCALS:"}) to the new filename to
     *                   assign; empty unless {@code success} is true
     */
    public record Outcome(boolean success, String reasonKey, Map<String, String> tagUpdates) {
        static Outcome failure(String reasonKey) {
            return new Outcome(false, reasonKey, Map.of());
        }
    }

    private PitchShiftBakeService() {
    }

    /**
     * Bakes the correction into new audio files.
     *
     * @param dir       the song directory containing the source audio files and receiving the copies
     * @param audioTags ordered map of audio tag to source filename (e.g. {@code AUDIO: -> "song.ogg"});
     *                  multiple tags may share a source filename
     * @param cents     the correction to apply, in cents (must be non-zero)
     * @param renderer  the render backend; {@code null} signals an unavailable backend
     * @param cancel    cooperative cancel check, polled before each render; may be {@code null}
     * @return the {@link Outcome}; on failure no final files are left behind and no tag updates are returned
     */
    public static Outcome bake(File dir,
                               Map<String, String> audioTags,
                               double cents,
                               Renderer renderer,
                               CancelCheck cancel) {
        if (renderer == null) {
            return Outcome.failure("pitch_shift_bake_no_backend");
        }
        if (cents == 0.0) {
            return Outcome.failure("pitch_shift_bake_no_correction");
        }
        if (dir == null || !dir.isDirectory()) {
            return Outcome.failure("pitch_shift_bake_no_dir");
        }
        if (audioTags == null || audioTags.isEmpty()) {
            return Outcome.failure("pitch_shift_bake_no_audio");
        }

        // Unique source filenames, preserving first-seen order so output naming is deterministic.
        Set<String> uniqueSources = new LinkedHashSet<>();
        for (String fileName : audioTags.values()) {
            if (fileName != null && !fileName.isBlank()) {
                uniqueSources.add(fileName);
            }
        }
        if (uniqueSources.isEmpty()) {
            return Outcome.failure("pitch_shift_bake_no_audio");
        }

        // Validate every source exists before rendering anything (no partial work on a bad input).
        for (String sourceName : uniqueSources) {
            File source = new File(dir, sourceName);
            if (!source.isFile()) {
                LOGGER.warning("[PitchShiftBake] missing source file: " + source.getAbsolutePath());
                return Outcome.failure("pitch_shift_bake_source_missing");
            }
        }

        // Reserve collision-safe final names up front so two sources never resolve to the same target.
        Set<String> reserved = new LinkedHashSet<>();
        Map<String, String> sourceToFinalName = new LinkedHashMap<>();
        for (String sourceName : uniqueSources) {
            String finalName = collisionSafeName(dir, sourceName, reserved);
            reserved.add(finalName.toLowerCase(Locale.ROOT));
            sourceToFinalName.put(sourceName, finalName);
        }

        // Render each unique source to a temporary candidate; promote only after all succeed.
        Map<String, File> sourceToCandidate = new LinkedHashMap<>();
        try {
            for (String sourceName : uniqueSources) {
                if (cancel != null && cancel.isCancelled()) {
                    return cleanupAndFail(sourceToCandidate, "pitch_shift_bake_cancelled");
                }
                File source = new File(dir, sourceName);
                File candidate = new File(dir, sourceToFinalName.get(sourceName) + ".part");
                candidate.delete();
                try {
                    renderer.render(source, candidate, cents);
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "[PitchShiftBake] render failed for " + sourceName, ex);
                    return cleanupAndFail(sourceToCandidate, "pitch_shift_bake_render_failed");
                }
                if (!candidate.isFile() || candidate.length() == 0) {
                    candidate.delete();
                    return cleanupAndFail(sourceToCandidate, "pitch_shift_bake_render_failed");
                }
                sourceToCandidate.put(sourceName, candidate);
            }

            // Final cancel check before the irreversible promote step.
            if (cancel != null && cancel.isCancelled()) {
                return cleanupAndFail(sourceToCandidate, "pitch_shift_bake_cancelled");
            }

            // Promote candidates to their final names.
            for (Map.Entry<String, File> entry : sourceToCandidate.entrySet()) {
                File candidate = entry.getValue();
                File finalFile = new File(dir, sourceToFinalName.get(entry.getKey()));
                if (!candidate.renameTo(finalFile)) {
                    LOGGER.warning("[PitchShiftBake] could not promote candidate " + candidate.getAbsolutePath());
                    // Promotion failed: remove whatever we already promoted and the remaining candidates.
                    for (String done : sourceToCandidate.keySet()) {
                        if (done.equals(entry.getKey())) {
                            break;
                        }
                        new File(dir, sourceToFinalName.get(done)).delete();
                    }
                    return cleanupAndFail(sourceToCandidate, "pitch_shift_bake_render_failed");
                }
            }
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "[PitchShiftBake] unexpected failure", ex);
            return cleanupAndFail(sourceToCandidate, "pitch_shift_bake_render_failed");
        }

        // Map every tag (including those sharing a source) to its source's rendered copy.
        Map<String, String> tagUpdates = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : audioTags.entrySet()) {
            String sourceName = entry.getValue();
            if (sourceName != null && sourceToFinalName.containsKey(sourceName)) {
                tagUpdates.put(entry.getKey(), sourceToFinalName.get(sourceName));
            }
        }
        return new Outcome(true, "pitch_shift_bake_done", tagUpdates);
    }

    private static Outcome cleanupAndFail(Map<String, File> candidates, String reasonKey) {
        for (File candidate : candidates.values()) {
            if (candidate != null) {
                candidate.delete();
            }
        }
        return Outcome.failure(reasonKey);
    }

    /**
     * Computes a collision-safe rendered filename for a source: inserts the {@link #SHIFTED_MARKER}
     * before the extension and, if that name already exists on disk or is already reserved, appends
     * an incrementing counter. Comparison is case-insensitive to be safe on case-insensitive
     * filesystems (macOS/Windows).
     */
    static String collisionSafeName(File dir, String sourceName, Set<String> reserved) {
        int dot = sourceName.lastIndexOf('.');
        String base = dot >= 0 ? sourceName.substring(0, dot) : sourceName;
        String ext = dot >= 0 ? sourceName.substring(dot) : "";
        String candidate = base + " " + SHIFTED_MARKER + ext;
        int counter = 2;
        while (isTaken(dir, candidate, reserved)) {
            candidate = base + " " + SHIFTED_MARKER + " " + counter + ext;
            counter++;
        }
        return candidate;
    }

    private static boolean isTaken(File dir, String name, Set<String> reserved) {
        if (reserved.contains(name.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return new File(dir, name).exists();
    }
}
