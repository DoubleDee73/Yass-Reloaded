package yass.alignment

import spock.lang.Specification
import yass.alignment.StablePitchRunDetector.NoteSpan
import yass.analysis.PitchDetector

class StablePitchRunDetectorSpec extends Specification {

    // dense pitched frames (10ms grid) at a fixed semitone, from startMs to endMs
    private static List<PitchDetector.PitchData> run(int startMs, int endMs, int semitone) {
        return run(startMs, endMs, semitone, 1.0d)
    }

    private static List<PitchDetector.PitchData> run(int startMs, int endMs, int semitone, double energy) {
        double freq = 261.63d * Math.pow(2.0, semitone / 12.0)
        def frames = []
        for (int ms = startMs; ms < endMs; ms += 10) {
            frames.add(new PitchDetector.PitchData((ms / 1000f) as float, semitone, "n", freq, energy))
        }
        return frames
    }

    def "detects a sustained stable-pitch run and ignores a too-short blip"() {
        given:
        def frames = run(1000, 1400, -5) + run(2000, 2050, 0)   // 400ms run + 50ms blip

        when:
        def runs = StablePitchRunDetector.detectStableRuns(frames)

        then:
        runs.size() == 1
        runs[0].midiPitch() == -5
        Math.abs(runs[0].startMs() - 1000) <= 15
        runs[0].durationMs() >= 120
    }

    def "returns a run only when uncovered by notes and adjacent to an existing note"() {
        given:
        // Three runs: one covered by a note, one adjacent-and-uncovered (reclaimable), one far away.
        def frames = run(1000, 1400, -5) + run(2000, 2400, -5) + run(9000, 9400, -5)
        def notes = [
                new NoteSpan(950, 1450),    // covers run 1
                new NoteSpan(1600, 1800)    // run 2 (2000-2400) is within 400ms of this note's end
        ]

        when:
        def reclaimable = StablePitchRunDetector.findReclaimableRuns(frames, notes)

        then:
        // run 1 covered -> excluded; run 3 far from any note -> excluded; only run 2 survives
        reclaimable.size() == 1
        Math.abs(reclaimable[0].startMs() - 2000) <= 15
    }

    def "rejects intro-style runs that sit far from every note"() {
        given:
        def frames = run(1000, 1400, -5) + run(2000, 2400, -5)
        def notes = [new NoteSpan(30000, 30500)]   // notes only much later (the real song)

        expect:
        StablePitchRunDetector.findReclaimableRuns(frames, notes).isEmpty()
    }

    def "energy gate rejects a quiet (breath) run but keeps a loud (sung) one"() {
        given:
        // loud reference comes from the loud note; the breath run is at ~10% energy.
        def loudNote = run(1000, 1400, -5, 1.0d)     // covered by a note (sets loud reference)
        def breathRun = run(2000, 2400, -5, 0.10d)   // adjacent, uncovered, but quiet -> reject
        def loudRun = run(3000, 3400, -5, 1.0d)      // adjacent, uncovered, loud -> keep
        def frames = loudNote + breathRun + loudRun
        def notes = [
                new NoteSpan(950, 1450),    // covers loudNote; breathRun(2000) within 400ms of end
                new NoteSpan(1600, 1800),
                new NoteSpan(2700, 2900)    // makes loudRun(3000) adjacent so energy is the decider
        ]

        when:
        def reclaimable = StablePitchRunDetector.findReclaimableRuns(frames, notes)

        then:
        // breath run (2000) rejected by energy gate; loud run (3000) kept
        reclaimable.every { Math.abs(it.startMs() - 2000) > 100 }
        reclaimable.any { Math.abs(it.startMs() - 3000) <= 20 }
    }

    def "splits a run when the pitch changes by more than a semitone"() {
        given:
        def frames = run(1000, 1300, -5) + run(1300, 1600, 2)   // G3 then D4, contiguous

        when:
        def runs = StablePitchRunDetector.detectStableRuns(frames)

        then:
        runs.size() == 2
        runs*.midiPitch() == [-5, 2]
    }
}
