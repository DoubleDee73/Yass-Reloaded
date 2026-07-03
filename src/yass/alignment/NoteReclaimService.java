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

import org.apache.commons.lang3.StringUtils;
import yass.alignment.StablePitchRunDetector.NoteSpan;
import yass.alignment.StablePitchRunDetector.StableRun;
import yass.analysis.PitchDetector;

import java.util.ArrayList;
import java.util.List;

/**
 * Reclaims unused stable-pitch runs left by the initial alignment.
 *
 * <p>The initial rebuild sometimes places a note on a consonant transient and leaves the longer,
 * stable sung pitch right after it unused; or keeps a word whole ("forever") when the singer clearly
 * split it. This post-pass detects those unused stable-pitch runs (via {@link
 * StablePitchRunDetector}) and, per run, either:</p>
 * <ul>
 *   <li><b>splits</b> the adjacent word when the word has more hyphenation syllables than it has
 *       notes (the run becomes the extra syllable), or</li>
 *   <li><b>moves/retimes</b> the adjacent single-syllable note onto the run.</li>
 * </ul>
 *
 * <p>Operates on a small mutable {@link NoteLine} model so the decision logic can be unit-tested
 * without a full {@code YassTable}. A separate adapter maps the table to/from this model.</p>
 */
public class NoteReclaimService {

    private static final int MIN_MOVE_IMPROVEMENT_BEATS = 2;

    /** One note in beat space. text keeps its trailing/leading word-space markers. */
    public static final class Note {
        public int beat;
        public int length;
        public int height;
        public String text;
        /** Position in the original note list, or -1 for a note inserted by a split. */
        public int originIndex;
        /** For an inserted note, the originIndex of the note it was split from; else -1. */
        public int splitFromOrigin = -1;

        public Note(int beat, int length, int height, String text) {
            this(beat, length, height, text, -1);
        }

        public Note(int beat, int length, int height, String text, int originIndex) {
            this.beat = beat;
            this.length = length;
            this.height = height;
            this.text = text;
            this.originIndex = originIndex;
        }
    }

    /** The editable note sequence plus the beat<->ms mapping and a hyphenator hook. */
    public interface NoteLine {
        List<Note> notes();

        int msToBeat(double ms);

        double beatToMs(int beat);

        /** Hyphenates a bare word into syllables (no markers), or returns a single-element list. */
        List<String> hyphenate(String bareWord);
    }

    /**
     * Applies the reclaim pass. Returns the number of runs acted on. {@code frames} should be
     * band-filtered vocal pitch frames.
     */
    public int reclaim(NoteLine line, List<PitchDetector.PitchData> frames) {
        List<Note> notes = line.notes();
        if (notes.isEmpty()) {
            return 0;
        }
        List<NoteSpan> spans = new ArrayList<>(notes.size());
        for (Note note : notes) {
            spans.add(new NoteSpan((int) Math.round(line.beatToMs(note.beat)),
                    (int) Math.round(line.beatToMs(note.beat + note.length))));
        }
        List<StableRun> runs = StablePitchRunDetector.findReclaimableRuns(frames, spans);
        int applied = 0;
        for (StableRun run : runs) {
            if (applyRun(line, run)) {
                applied++;
            }
        }
        return applied;
    }

    private boolean applyRun(NoteLine line, StableRun run) {
        List<Note> notes = line.notes();
        int runStartBeat = line.msToBeat(run.startMs());
        int runEndBeat = line.msToBeat(run.endMs());
        if (runEndBeat <= runStartBeat) {
            runEndBeat = runStartBeat + 1;
        }
        Word word = wordAroundRun(notes, run, line);
        if (word == null) {
            return false;
        }
        int syllableCount = countSyllables(line, word);
        int noteCount = word.endIndex - word.startIndex + 1;
        if (syllableCount > noteCount) {
            return splitWordOntoRun(line, word, runStartBeat, runEndBeat);
        }
        return moveNoteOntoRun(line, word, run, runStartBeat, runEndBeat);
    }

    /** The word whose note span is nearest the run (its last note ends closest to the run start). */
    private Word wordAroundRun(List<Note> notes, StableRun run, NoteLine line) {
        List<Word> words = groupWords(notes);
        Word best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Word word : words) {
            double wordStartMs = line.beatToMs(notes.get(word.startIndex).beat);
            double wordEndMs = line.beatToMs(notes.get(word.endIndex).beat + notes.get(word.endIndex).length);
            double distance = Math.min(Math.abs(wordEndMs - run.startMs()), Math.abs(wordStartMs - run.endMs()));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = word;
            }
        }
        return best;
    }

    private boolean splitWordOntoRun(NoteLine line, Word word, int runStartBeat, int runEndBeat) {
        List<Note> notes = line.notes();
        // Only handle the common case: a single-note word that hyphenates into >=2 syllables, and the
        // run sits after it. Split into first syllable (kept in place, shortened) + remaining syllable
        // placed on the run.
        if (word.startIndex != word.endIndex) {
            return false;
        }
        Note note = notes.get(word.startIndex);
        String bare = bareText(note.text);
        List<String> syllables = line.hyphenate(bare);
        if (syllables.size() < 2) {
            return false;
        }
        if (runStartBeat <= note.beat) {
            return false;
        }
        boolean leadingSpace = note.text.startsWith(String.valueOf(yass.YassRow.SPACE));
        boolean trailingSpace = note.text.endsWith(String.valueOf(yass.YassRow.SPACE));
        // First syllable keeps the note position, shortened so it ends before the run.
        int firstLength = Math.max(1, Math.min(note.length, runStartBeat - note.beat - 1));
        String firstSyllable = syllables.get(0);
        String remainder = String.join("", syllables.subList(1, syllables.size()));
        note.length = firstLength;
        note.text = (leadingSpace ? String.valueOf(yass.YassRow.SPACE) : "") + firstSyllable;
        int secondLength = Math.max(1, runEndBeat - runStartBeat);
        String secondText = remainder + (trailingSpace ? String.valueOf(yass.YassRow.SPACE) : "");
        Note added = new Note(runStartBeat, secondLength, note.height, secondText);
        added.splitFromOrigin = note.originIndex;
        notes.add(word.startIndex + 1, added);
        return true;
    }

    private boolean moveNoteOntoRun(NoteLine line, Word word, StableRun run, int runStartBeat, int runEndBeat) {
        List<Note> notes = line.notes();
        // Move the note nearest the run (the word's last note) onto the run when that is a clear
        // forward improvement (the run starts at least a couple beats past the note's current start).
        Note note = notes.get(word.endIndex);
        if (runStartBeat < note.beat + MIN_MOVE_IMPROVEMENT_BEATS) {
            return false;
        }
        if (!fitsBetweenNeighbours(notes, word.endIndex, runStartBeat, runEndBeat)) {
            return false;
        }
        note.beat = runStartBeat;
        note.length = Math.max(1, runEndBeat - runStartBeat);
        return true;
    }

    private boolean fitsBetweenNeighbours(List<Note> notes, int index, int startBeat, int endBeat) {
        if (index > 0) {
            Note prev = notes.get(index - 1);
            if (startBeat <= prev.beat + prev.length) {
                return false;
            }
        }
        if (index + 1 < notes.size()) {
            Note next = notes.get(index + 1);
            if (endBeat >= next.beat) {
                return false;
            }
        }
        return true;
    }

    private int countSyllables(NoteLine line, Word word) {
        List<Note> notes = line.notes();
        StringBuilder bare = new StringBuilder();
        for (int index = word.startIndex; index <= word.endIndex; index++) {
            bare.append(bareText(notes.get(index).text));
        }
        List<String> syllables = line.hyphenate(bare.toString());
        return Math.max(1, syllables.size());
    }

    private List<Word> groupWords(List<Note> notes) {
        List<Word> words = new ArrayList<>();
        boolean startNew = true;
        for (int index = 0; index < notes.size(); index++) {
            Note note = notes.get(index);
            if (startNew) {
                words.add(new Word(index, index));
            } else {
                words.get(words.size() - 1).endIndex = index;
            }
            startNew = note.text.endsWith(String.valueOf(yass.YassRow.SPACE));
        }
        return words;
    }

    private String bareText(String text) {
        return StringUtils.defaultString(text)
                .replace(String.valueOf(yass.YassRow.SPACE), "")
                .replace("~", "")
                .trim();
    }

    private static final class Word {
        int startIndex;
        int endIndex;

        Word(int startIndex, int endIndex) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }
}
