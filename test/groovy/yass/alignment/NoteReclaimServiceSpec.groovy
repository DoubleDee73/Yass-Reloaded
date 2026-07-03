package yass.alignment

import spock.lang.Specification
import yass.YassRow
import yass.alignment.NoteReclaimService.Note
import yass.alignment.NoteReclaimService.NoteLine
import yass.analysis.PitchDetector

class NoteReclaimServiceSpec extends Specification {

    static final int GAP = 30000
    static final double BPM = 290

    // A test NoteLine backed by an ArrayList, with a fixed-dictionary hyphenator.
    static class TestLine implements NoteLine {
        List<Note> notes
        Map<String, List<String>> dict

        TestLine(List<Note> notes, Map<String, List<String>> dict) {
            this.notes = notes; this.dict = dict
        }
        List<Note> notes() { notes }
        int msToBeat(double ms) { (int) Math.ceil((ms - GAP) * 4 * BPM / 60000) }
        double beatToMs(int beat) { 1000 * 60 * beat / (4 * BPM) + GAP }
        List<String> hyphenate(String w) { dict.getOrDefault(w.toLowerCase(), [w]) }
    }

    private static List<PitchDetector.PitchData> runFrames(int startMs, int endMs, int semitone) {
        double freq = 261.63d * Math.pow(2.0, semitone / 12.0)
        def frames = []
        for (int ms = startMs; ms < endMs; ms += 10) {
            frames.add(new PitchDetector.PitchData((ms / 1000f) as float, semitone, "n", freq, 1.0d))
        }
        return frames
    }

    private static String sp(String s) { s + YassRow.SPACE }   // trailing word-space marker

    def "splits a whole word when it has spare hyphenation syllables (Forget -> For/get)"() {
        given:
        // One note "Forget" (whole word). A stable run sits shortly after it, unused.
        def forgetBeat = 10
        def note = new Note(forgetBeat, 4, -5, sp("Forget"))
        def line = new TestLine([note], ["forget": ["For", "get"]])
        // note span ~ beat 10-14; place a stable run clearly after it (beat ~20+)
        int runStart = (int) line.beatToMs(20)
        int runEnd = (int) line.beatToMs(28)
        def frames = runFrames(runStart, runEnd, -5)

        when:
        int applied = new NoteReclaimService().reclaim(line, frames)

        then:
        applied == 1
        line.notes.size() == 2
        // first syllable kept, second syllable placed on the run
        line.notes[0].text.replace(YassRow.SPACE as String, "") == "For"
        line.notes[1].text.replace(YassRow.SPACE as String, "") == "get"
        line.notes[1].beat >= 20
    }

    def "does not split a single-syllable word (Choke stays one note)"() {
        given:
        def note = new Note(10, 4, -5, sp("Choke"))
        // Choke hyphenates to itself (1 syllable)
        def line = new TestLine([note], ["choke": ["Choke"]])
        int runStart = (int) line.beatToMs(20)
        def frames = runFrames(runStart, (int) line.beatToMs(28), -5)

        when:
        int applied = new NoteReclaimService().reclaim(line, frames)

        then:
        // With no spare syllable, it must not split; at most it moves the note onto the run.
        line.notes.every { it.text.replace(YassRow.SPACE as String, "") == "Choke" }
        line.notes.size() == 1
    }

    def "moves a single-syllable note onto the stable run when the note sits early on a transient"() {
        given:
        // "the" placed early (beat 10, len 1) on a transient; the real stable pitch is at beat ~14,
        // a couple beats later and within the adjacency window.
        def note = new Note(10, 1, -5, sp("the"))
        def line = new TestLine([note], ["the": ["the"]])
        int runStart = (int) line.beatToMs(14)
        int runEnd = (int) line.beatToMs(22)
        def frames = runFrames(runStart, runEnd, -5)

        when:
        int applied = new NoteReclaimService().reclaim(line, frames)

        then:
        applied == 1
        line.notes.size() == 1
        line.notes[0].beat >= 14
    }
}
