package yass.analysis

import spock.lang.Specification

class PitchShiftRendererSpec extends Specification {

    def "ratio is 1.0 at zero cents and an octave at +-1200 cents"() {
        expect:
        PitchShiftRenderer.ratio(0d) == 1.0d
        Math.abs(PitchShiftRenderer.ratio(1200d) - 2.0d) < 1e-9
        Math.abs(PitchShiftRenderer.ratio(-1200d) - 0.5d) < 1e-9
    }

    def "no filter is produced for a zero shift so the conversion stays unchanged"() {
        expect:
        PitchShiftRenderer.audioFilter(0d, true) == ""
        PitchShiftRenderer.audioFilter(0d, false) == ""
    }

    def "rubberband backend emits a single pitch filter"() {
        when:
        def filter = PitchShiftRenderer.audioFilter(29.2d, true)

        then:
        filter.startsWith("rubberband=pitch=")
        // +29.2 cents raises pitch, ratio > 1
        def ratio = filter.replace("rubberband=pitch=", "") as double
        Math.abs(ratio - PitchShiftRenderer.ratio(29.2d)) < 1e-6
    }

    def "fallback backend resamples then restores duration with atempo"() {
        when:
        def filter = PitchShiftRenderer.audioFilter(29.2d, false)

        then: "asetrate scales by the ratio and atempo compensates by its inverse"
        filter.contains("asetrate=44100*")
        filter.contains("aresample=44100")
        filter.contains("atempo=")
        def atempo = (filter =~ /atempo=([0-9.]+)/)[0][1] as double
        Math.abs(atempo - 1.0d / PitchShiftRenderer.ratio(29.2d)) < 1e-6
    }
}
