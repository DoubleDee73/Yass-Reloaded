package yass.analysis

import spock.lang.Specification

class PitchDetectorSpec extends Specification {

    def "uses no private waveform nested class for frame energy helpers"() {
        expect:
        !PitchDetector.declaredClasses*.simpleName.contains('Waveform')
    }

    def "analyzeTuningOffset detects positive global tuning drift from raw frequencies"() {
        given:
        def pitchFrames = buildFrames(440d, 18d, 40)

        when:
        def analysis = PitchDetector.analyzeTuningOffset(pitchFrames)

        then:
        analysis.available()
        Math.abs(analysis.estimatedOffsetCents() - 18d) < 0.5d
        Math.abs(analysis.suggestedCorrectionCents() + 18d) < 0.5d
        // perfectly consistent frames concentrate fully on one tuning
        analysis.concentration() > 0.99d
    }

    def "analyzeTuningOffset is robust against a few outlier frames"() {
        given:
        def pitchFrames = buildFrames(440d, -14d, 40)
        pitchFrames.add(new PitchDetector.PitchData(1.0f, 0, "C4", frequencyWithOffset(440d, 42d)))
        pitchFrames.add(new PitchDetector.PitchData(1.1f, 0, "C4", frequencyWithOffset(440d, -38d)))

        when:
        def analysis = PitchDetector.analyzeTuningOffset(pitchFrames)

        then: "the circular mean stays near the dominant -14c despite two scattered frames"
        analysis.available()
        Math.abs(analysis.estimatedOffsetCents() + 14d) < 2.0d
        analysis.concentration() > 0.9d
    }

    def "analyzeTuningOffset reports low confidence and unavailable for scattered offsets"() {
        given: "frames spread uniformly across the whole semitone (no dominant tuning)"
        def pitchFrames = (0..<60).collect { idx ->
            new PitchDetector.PitchData(
                    (idx / 20.0f) as float, 0, "A4",
                    frequencyWithOffset(440d, -50d + idx * (100d / 60d)))
        }

        when:
        def analysis = PitchDetector.analyzeTuningOffset(pitchFrames)

        then: "concentration is near zero and no reliable offset is offered"
        !analysis.available()
        analysis.concentration() < 0.25d
        analysis.reason().contains("too inconsistent")
    }

    def "analyzeTuningOffset returns unavailable for too few frames"() {
        given:
        def pitchFrames = buildFrames(440d, 9d, 8)

        when:
        def analysis = PitchDetector.analyzeTuningOffset(pitchFrames)

        then:
        !analysis.available()
        analysis.reason().contains("Not enough stable pitch frames")
    }

    def "applyPitchShift is a no-op when there is no correction and no transpose"() {
        given:
        def frames = buildFrames(440d, 18d, 4)

        expect: "the same list instance is returned unchanged so existing behavior is preserved"
        PitchDetector.applyPitchShift(frames, 0d, 0).is(frames)
    }

    def "applyPitchShift applies only the integer transpose when correction is zero"() {
        given: "a frame whose detected pitch is 0 (C4) and is dead-on its grid note"
        def frames = [new PitchDetector.PitchData(0.0f, 0, "C4", 440d)]

        when:
        def shifted = PitchDetector.applyPitchShift(frames, 0d, 3)

        then: "only the transpose moves the pitch; raw frequency and other fields are preserved"
        shifted.size() == 1
        shifted[0].pitch() == 3
        shifted[0].rawFrequency() == 440d
    }

    def "applyPitchShift nudges a frame across the chromatic grid boundary exactly once"() {
        given: "a frame sitting +45 cents sharp of its nearest note (pitch already rounded to that note)"
        def freq = frequencyWithOffset(440d, 45d)
        def frames = [new PitchDetector.PitchData(0.0f, 0, "A4", freq)]

        when: "a +10 cent correction pushes the residual (45c) past the +50c boundary"
        def shifted = PitchDetector.applyPitchShift(frames, 10d, 0)

        then: "the integer pitch moves up by exactly one semitone"
        shifted[0].pitch() == 1
    }

    def "applyPitchShift leaves a frame on its note when the correction stays within the grid cell"() {
        given: "a frame +10 cents sharp"
        def freq = frequencyWithOffset(440d, 10d)
        def frames = [new PitchDetector.PitchData(0.0f, 0, "A4", freq)]

        when: "a +20 cent correction keeps the residual (30c) below the +50c boundary"
        def shifted = PitchDetector.applyPitchShift(frames, 20d, 0)

        then: "the integer pitch does not flip"
        shifted[0].pitch() == 0
    }

    def "correctedPitch scalar agrees with the list producer for the same frame"() {
        given:
        def frame = new PitchDetector.PitchData(0.0f, 5, "F4", frequencyWithOffset(440d, 40d))

        expect: "the hot-loop scalar form and the list form produce identical pitches"
        PitchDetector.correctedPitch(frame, 15d, 2) ==
                PitchDetector.applyPitchShift([frame], 15d, 2)[0].pitch()
    }

    private List<PitchDetector.PitchData> buildFrames(double baseFrequency, double centsOffset, int count) {
        return (0..<count).collect { idx ->
            new PitchDetector.PitchData(
                    (idx / 20.0f) as float,
                    PitchDetector.frequencyToNote(baseFrequency),
                    "A4",
                    frequencyWithOffset(baseFrequency, centsOffset)
            )
        }
    }

    private double frequencyWithOffset(double frequency, double centsOffset) {
        return frequency * Math.pow(2d, centsOffset / 1200d)
    }
}
