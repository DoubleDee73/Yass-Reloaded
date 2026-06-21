package yass.analysis

import spock.lang.Specification

class PitchShiftMetadataSpec extends Specification {

    def "parse returns empty for null, blank, or absent key"() {
        expect:
        PitchShiftMetadata.parse(input).empty

        where:
        input << [null, "", "   ", "key=Am", "key=Am,v=123"]
    }

    def "parse reads the pitchShiftCents value"() {
        expect:
        PitchShiftMetadata.parse(comment).asDouble == expected

        where:
        comment                              || expected
        "pitchShiftCents=-18.0"              || -18.0d
        "key=Am,pitchShiftCents=12.5"        || 12.5d
        "pitchShiftCents=0.0,key=Am"         || 0.0d
        " pitchShiftCents=7.0 , key=Am"      || 7.0d
    }

    def "parse returns empty for an unparseable value"() {
        expect:
        PitchShiftMetadata.parse("pitchShiftCents=abc").empty
    }

    def "upsert appends the key when no comment exists"() {
        expect:
        PitchShiftMetadata.upsert(null, -18.0d) == "pitchShiftCents=-18.0"
        PitchShiftMetadata.upsert("", 12.5d) == "pitchShiftCents=12.5"
    }

    def "upsert preserves other comment properties when appending"() {
        expect:
        PitchShiftMetadata.upsert("key=Am,v=123", -18.0d) == "key=Am,v=123,pitchShiftCents=-18.0"
    }

    def "upsert replaces an existing value in place without destroying other keys"() {
        when:
        def updated = PitchShiftMetadata.upsert("key=Am,pitchShiftCents=5.0,v=123", -18.0d)

        then:
        updated == "key=Am,pitchShiftCents=-18.0,v=123"
        PitchShiftMetadata.parse(updated).asDouble == -18.0d
    }

    def "upsert is idempotent for the same value"() {
        given:
        def once = PitchShiftMetadata.upsert("key=Am", -18.0d)

        expect:
        PitchShiftMetadata.upsert(once, -18.0d) == once
    }

    def "remove deletes only the pitchShiftCents property"() {
        expect:
        PitchShiftMetadata.remove("key=Am,pitchShiftCents=-18.0,v=123") == "key=Am,v=123"
        PitchShiftMetadata.remove("pitchShiftCents=-18.0") == ""
        PitchShiftMetadata.remove("key=Am") == "key=Am"
        PitchShiftMetadata.remove(null) == null
    }

    def "remove leaves an existing key= property untouched"() {
        when:
        def removed = PitchShiftMetadata.remove("key=Am,pitchShiftCents=-18.0")

        then:
        removed == "key=Am"
        PitchShiftMetadata.parse(removed).empty
    }

    def "cents convert to semitones and back"() {
        expect:
        PitchShiftMetadata.centsToSemitones(100.0d) == 1.0d
        PitchShiftMetadata.centsToSemitones(50.0d) == 0.5d
        PitchShiftMetadata.centsToSemitones(-18.0d) == -0.18d
        PitchShiftMetadata.semitonesToCents(0.5d) == 50.0d
    }

    def "offsets below 5 cents are not recommended"() {
        expect:
        PitchShiftMetadata.isCorrectionRecommended(cents) == recommended

        where:
        cents  || recommended
        0.0d   || false
        4.9d   || false
        -4.9d  || false
        5.0d   || true
        -18.0d || true
    }

    def "estimates below the concentration threshold are flagged as low confidence"() {
        expect:
        PitchShiftMetadata.isEstimateConfident(concentration) == confident

        where:
        concentration || confident
        1.0d          || true
        0.5d          || true
        0.25d         || true
        0.24d         || false
        0.1d          || false
        0.0d          || false
    }

    def "an existing key= property does not affect pitch-shift parsing"() {
        expect:
        PitchShiftMetadata.parse("key=Am").empty
        PitchShiftMetadata.parse("key=C#m,pitchShiftCents=-3.0").asDouble == -3.0d
    }
}
