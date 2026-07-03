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

import yass.alignment.NoteReclaimService.Note;
import yass.alignment.NoteReclaimService.NoteLine;
import yass.analysis.PitchDetector;

import java.util.List;

/**
 * Lengthens notes that end too early into the strong, stable vocal signal that continues right after
 * them. The initial rebuild often cuts a note at its onset while the sung note holds on; this grows
 * the note's length to where that trailing signal ends, always stopping a beat short of the next
 * note.
 *
 * <p>Only ever increases a note's length toward real signal - it never moves a note, changes its
 * pitch/text, reorders, or crosses into the next note - so it is the safest of the post-passes.
 * Operates on the shared {@link NoteLine} model for unit-testability.</p>
 */
public class NoteExtensionService {

    private static final int NEXT_NOTE_BUFFER_BEATS = 1;
    private static final int GAP_SAMPLE_MS = 20;
    private static final double MIN_SIGNAL_COVERAGE = 0.40d;
    private static final int SIGNAL_TAIL_GUARD_MS = 40;
    // Energy gate: a run only counts as "sung" (not breath / soft noise) when its mean energy is at
    // least this fraction of the song's loud reference. Breath and mic noise sit well below this,
    // while sustained sung notes sit above it. See VocalEnergy for the reference computation.
    private static final double MIN_SUNG_ENERGY_RATIO = 0.35d;

    /**
     * Extends notes into their trailing stable signal. {@code frames} should be band-filtered vocal
     * pitch frames. Returns the number of notes lengthened.
     */
    public int extend(NoteLine line, List<PitchDetector.PitchData> frames) {
        List<Note> notes = line.notes();
        if (notes.size() < 1 || frames == null || frames.isEmpty()) {
            return 0;
        }
        double loudReference = VocalEnergy.loudReference(frames);
        int extended = 0;
        for (int index = 0; index < notes.size(); index++) {
            Note note = notes.get(index);
            int noteEndBeat = note.beat + note.length;
            // Upper bound: the next note's start, or open-ended for the last note (bounded by signal).
            Integer nextStartBeat = index + 1 < notes.size() ? notes.get(index + 1).beat : null;
            double noteEndMs = line.beatToMs(noteEndBeat);
            double limitMs = nextStartBeat != null
                    ? line.beatToMs(nextStartBeat)
                    : noteEndMs + 4000; // scan up to 4s past the last note

            double coverage = signalCoverage(frames, noteEndMs, limitMs - SIGNAL_TAIL_GUARD_MS);
            if (coverage < MIN_SIGNAL_COVERAGE) {
                continue;
            }
            // Energy gate: only extend into signal that is actually sung, not a quiet breath in the
            // gap. Disabled when no energy info is available (loudReference == 0).
            if (loudReference > 0d) {
                double gapEnergy = VocalEnergy.meanEnergy(frames, noteEndMs, limitMs - SIGNAL_TAIL_GUARD_MS);
                if (gapEnergy < loudReference * MIN_SUNG_ENERGY_RATIO) {
                    continue;
                }
            }
            Double lastSignalMs = lastSignalTime(frames, noteEndMs, limitMs - SIGNAL_TAIL_GUARD_MS);
            if (lastSignalMs == null) {
                continue;
            }
            int targetEndBeat = line.msToBeat(lastSignalMs);
            if (nextStartBeat != null) {
                targetEndBeat = Math.min(targetEndBeat, nextStartBeat - NEXT_NOTE_BUFFER_BEATS);
            }
            int newLength = targetEndBeat - note.beat;
            if (newLength > note.length) {
                note.length = newLength;
                extended++;
            }
        }
        return extended;
    }

    /** Fraction of {@value #GAP_SAMPLE_MS}ms buckets in [fromMs, toMs) that contain a pitched frame. */
    private double signalCoverage(List<PitchDetector.PitchData> frames, double fromMs, double toMs) {
        if (toMs <= fromMs) {
            return 0d;
        }
        int bucketCount = Math.max(1, (int) ((toMs - fromMs) / GAP_SAMPLE_MS));
        boolean[] filled = new boolean[bucketCount];
        for (PitchDetector.PitchData frame : frames) {
            if (frame == null || frame.rawFrequency() <= 0d) {
                continue;
            }
            double ms = frame.time() * 1000.0;
            if (ms < fromMs || ms >= toMs) {
                continue;
            }
            int bucket = (int) ((ms - fromMs) / GAP_SAMPLE_MS);
            if (bucket >= 0 && bucket < bucketCount) {
                filled[bucket] = true;
            }
        }
        int hits = 0;
        for (boolean b : filled) {
            if (b) {
                hits++;
            }
        }
        return (double) hits / bucketCount;
    }

    private Double lastSignalTime(List<PitchDetector.PitchData> frames, double fromMs, double toMs) {
        Double last = null;
        for (PitchDetector.PitchData frame : frames) {
            if (frame == null || frame.rawFrequency() <= 0d) {
                continue;
            }
            double ms = frame.time() * 1000.0;
            if (ms >= fromMs && ms < toMs) {
                last = ms;
            }
        }
        return last;
    }
}
