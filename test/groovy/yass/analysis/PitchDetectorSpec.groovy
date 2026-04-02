package yass.analysis

import spock.lang.Specification

class PitchDetectorSpec extends Specification {

    def "analyzeTuningOffset detects positive global tuning drift from raw frequencies"() {
        given:
        def pitchFrames = buildFrames(440d, 18d, 40)

        when:
        def analysis = PitchDetector.analyzeTuningOffset(pitchFrames)

        then:
        analysis.available()
        Math.abs(analysis.estimatedOffsetCents() - 18d) < 0.5d
        Math.abs(analysis.suggestedCorrectionCents() + 18d) < 0.5d
        analysis.inlierCount() == 40
    }

    def "analyzeTuningOffset is robust against a few outlier frames"() {
        given:
        def pitchFrames = buildFrames(440d, -14d, 40)
        pitchFrames.add(new PitchDetector.PitchData(1.0f, 0, "C4", frequencyWithOffset(440d, 42d)))
        pitchFrames.add(new PitchDetector.PitchData(1.1f, 0, "C4", frequencyWithOffset(440d, -38d)))

        when:
        def analysis = PitchDetector.analyzeTuningOffset(pitchFrames)

        then:
        analysis.available()
        Math.abs(analysis.estimatedOffsetCents() + 14d) < 0.5d
        analysis.inlierCount() == 40
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
