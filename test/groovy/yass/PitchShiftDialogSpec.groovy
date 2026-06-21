package yass

import spock.lang.Specification

class PitchShiftDialogSpec extends Specification {

    def setupSpec() {
        I18.setDefaultLanguage()
    }

    def "formats cents with sign and semitone equivalent"() {
        expect:
        PitchShiftDialog.formatCentsAndSemitones(cents) == expected

        where:
        cents   || expected
        18.0d   || "+18.0 cents (+0.18 semitones)"
        -18.0d  || "-18.0 cents (-0.18 semitones)"
        0.0d    || "+0.0 cents (+0.00 semitones)"
        -10.87d || "-10.9 cents (-0.11 semitones)"
        100.0d  || "+100.0 cents (+1.00 semitones)"
    }
}
