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

package yass.alignment;

import yass.analysis.PitchDetector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds sustained, stable-pitch regions in a vocal pitch track that the current note layout does not
 * cover - the "unused signal" that should usually become (or extend) a note.
 *
 * <p>Pure and side-effect free so it can be unit-tested against fixture frames. The caller supplies
 * the note spans already placed on the table; this returns the runs worth reclaiming: a run of
 * in-band frames holding one pitch (+/-{@link #PITCH_TOLERANCE_SEMITONES}) for at least {@link
 * #MIN_RUN_MS}, that no note covers, and that sits within {@link #ADJACENCY_MS} of an existing note
 * (so the instrumental intro/outro is not mistaken for missed vocals).</p>
 */
public final class StablePitchRunDetector {

    private static final int MIN_RUN_MS = 120;
    private static final int MAX_RUN_GAP_MS = 60;
    private static final int PITCH_TOLERANCE_SEMITONES = 1;
    private static final int ADJACENCY_MS = 400;
    // A run only counts as sung (not breath / soft noise) when its mean energy reaches this fraction
    // of the song's loud reference. Disabled when frames carry no energy (loudReference == 0).
    private static final double MIN_SUNG_ENERGY_RATIO = 0.35d;

    private StablePitchRunDetector() {
    }

    /** A sustained region of roughly-constant pitch. */
    public record StableRun(int startMs, int endMs, int midiPitch) {
        public int durationMs() {
            return Math.max(0, endMs - startMs);
        }
    }

    /** A half-open note time span [startMs, endMs) on the table. */
    public record NoteSpan(int startMs, int endMs) {
    }

    /**
     * Returns the reclaimable stable-pitch runs. {@code frames} should already be band-filtered
     * (see {@link TranscriptTimingRefinementService#dominantPitchBandFilter}); only pitched frames
     * are considered. {@code noteSpans} are the note time windows currently on the table.
     */
    public static List<StableRun> findReclaimableRuns(List<PitchDetector.PitchData> frames,
                                                      List<NoteSpan> noteSpans) {
        List<StableRun> runs = detectStableRuns(frames);
        double loudReference = VocalEnergy.loudReference(frames);
        List<StableRun> reclaimable = new ArrayList<>();
        for (StableRun run : runs) {
            if (isCovered(run, noteSpans)) {
                continue;
            }
            if (!isAdjacentToNote(run, noteSpans)) {
                continue;
            }
            // Energy gate: skip runs that are too quiet to be sung (breath, soft noise).
            if (loudReference > 0d) {
                double energy = VocalEnergy.meanEnergy(frames, run.startMs(), run.endMs());
                if (energy < loudReference * MIN_SUNG_ENERGY_RATIO) {
                    continue;
                }
            }
            reclaimable.add(run);
        }
        return reclaimable;
    }

    static List<StableRun> detectStableRuns(List<PitchDetector.PitchData> frames) {
        if (frames == null || frames.isEmpty()) {
            return List.of();
        }
        List<PitchDetector.PitchData> pitched = new ArrayList<>();
        for (PitchDetector.PitchData frame : frames) {
            if (frame != null && frame.rawFrequency() > 0d) {
                pitched.add(frame);
            }
        }
        pitched.sort(Comparator.comparingInt(StablePitchRunDetector::frameStartMs));
        List<StableRun> runs = new ArrayList<>();
        Integer startMs = null;
        int lastMs = 0;
        int anchorPitch = 0;
        for (PitchDetector.PitchData frame : pitched) {
            int frameMs = frameStartMs(frame);
            int pitch = frame.pitch();
            if (startMs != null
                    && frameMs - lastMs <= MAX_RUN_GAP_MS
                    && Math.abs(pitch - anchorPitch) <= PITCH_TOLERANCE_SEMITONES) {
                lastMs = frameMs;
                continue;
            }
            if (startMs != null && lastMs - startMs >= MIN_RUN_MS) {
                runs.add(new StableRun(startMs, lastMs, anchorPitch));
            }
            startMs = frameMs;
            lastMs = frameMs;
            anchorPitch = pitch;
        }
        if (startMs != null && lastMs - startMs >= MIN_RUN_MS) {
            runs.add(new StableRun(startMs, lastMs, anchorPitch));
        }
        return runs;
    }

    private static boolean isCovered(StableRun run, List<NoteSpan> noteSpans) {
        for (NoteSpan span : noteSpans) {
            if (span.startMs() < run.endMs() && span.endMs() > run.startMs()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAdjacentToNote(StableRun run, List<NoteSpan> noteSpans) {
        for (NoteSpan span : noteSpans) {
            if (run.startMs() < span.endMs() + ADJACENCY_MS
                    && run.endMs() > span.startMs() - ADJACENCY_MS) {
                return true;
            }
        }
        return false;
    }

    private static int frameStartMs(PitchDetector.PitchData frame) {
        return Math.max(0, Math.round(frame.time() * 1000.0f));
    }
}
