package yass.alignment

import spock.lang.Specification
import yass.YassRow
import yass.alignment.NoteReclaimService.Note
import yass.alignment.NoteReclaimService.NoteLine
import yass.analysis.PitchDetector

class NoteExtensionServiceSpec extends Specification {

    static final int GAP = 30000
    static final double BPM = 290

    static class TestLine implements NoteLine {
        List<Note> notes
        TestLine(List<Note> notes) { this.notes = notes }
        List<Note> notes() { notes }
        int msToBeat(double ms) { (int) Math.ceil((ms - GAP) * 4 * BPM / 60000) }
        double beatToMs(int beat) { 1000 * 60 * beat / (4 * BPM) + GAP }
        List<String> hyphenate(String w) { [w] }
    }

    private static List<PitchDetector.PitchData> runFrames(int startMs, int endMs, int semitone) {
        return runFrames(startMs, endMs, semitone, 1.0d)
    }

    private static List<PitchDetector.PitchData> runFrames(int startMs, int endMs, int semitone, double energy) {
        double freq = 261.63d * Math.pow(2.0, semitone / 12.0)
        def frames = []
        for (int ms = startMs; ms < endMs; ms += 10) {
            frames.add(new PitchDetector.PitchData((ms / 1000f) as float, semitone, "n", freq, energy))
        }
        return frames
    }

    def "extends a short note into the trailing stable signal, stopping a beat before the next note"() {
        given:
        // "the" at beat 10 len 2 (too short); strong signal continues through ~beat 20; next note at 24.
        def theNote = new Note(10, 2, -5, "the" + YassRow.SPACE)
        def next = new Note(24, 4, -5, "giant" + YassRow.SPACE)
        def line = new TestLine([theNote, next])
        int sigStart = (int) line.beatToMs(12)
        int sigEnd = (int) line.beatToMs(20)
        def frames = runFrames(sigStart, sigEnd, -5)

        when:
        int extended = new NoteExtensionService().extend(line, frames)

        then:
        extended == 1
        theNote.length > 2
        // ends at least a beat before the next note (beat 24)
        theNote.beat + theNote.length <= 23
        theNote.beat + theNote.length >= 18
    }

    def "does not extend when the gap after the note is mostly silent"() {
        given:
        def note = new Note(10, 3, -5, "let" + YassRow.SPACE)
        def next = new Note(24, 4, -5, "the" + YassRow.SPACE)
        def line = new TestLine([note, next])
        // only a tiny blip of signal in the gap
        def frames = runFrames((int) line.beatToMs(13), (int) line.beatToMs(14), -5)

        when:
        int extended = new NoteExtensionService().extend(line, frames)

        then:
        extended == 0
        note.length == 3
    }

    def "energy gate blocks extension into a quiet breath even when pitch coverage is high"() {
        given:
        // "let" note; the gap after it is full of a *quiet* breath (pitched but low energy). A loud
        // note elsewhere sets the loud reference so the gate can compare.
        def letNote = new Note(10, 3, -5, "let" + YassRow.SPACE)
        def next = new Note(24, 4, -5, "the" + YassRow.SPACE)
        def line = new TestLine([letNote, next])
        // loud reference from the "next" note region; breath fills the gap at ~10% energy
        def loudRef = runFrames((int) line.beatToMs(24), (int) line.beatToMs(28), -5, 1.0d)
        def breath = runFrames((int) line.beatToMs(13), (int) line.beatToMs(22), -5, 0.10d)
        def frames = breath + loudRef

        when:
        int extended = new NoteExtensionService().extend(line, frames)

        then:
        // high pitch coverage in the gap, but too quiet to be sung -> not extended
        extended == 0
        letNote.length == 3
    }

    def "never shortens a note nor crosses into the next note"() {
        given:
        // note already long; signal continues but the next note starts right after
        def note = new Note(10, 10, -5, "in" + YassRow.SPACE)
        def next = new Note(21, 4, -5, "Se" + YassRow.SPACE)
        def line = new TestLine([note, next])
        def frames = runFrames((int) line.beatToMs(20), (int) line.beatToMs(30), -5)

        when:
        int extended = new NoteExtensionService().extend(line, frames)

        then:
        // cannot grow past beat 20 (next-1), so it stays at its current length (no shortening)
        note.length >= 10
        note.beat + note.length <= 20
    }
}
