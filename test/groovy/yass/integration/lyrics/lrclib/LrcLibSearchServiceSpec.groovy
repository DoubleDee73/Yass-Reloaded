package yass.integration.lyrics.lrclib

import spock.lang.Specification

class LrcLibSearchServiceSpec extends Specification {

    def "keeps empty timed LRCLib lines as timing boundary segments"() {
        given:
        def candidate = new LrcLibCandidate(
                1L,
                "Song",
                "Artist",
                "Album",
                30,
                false,
                "First line\nNext line",
                "[00:10.00] First line\n[00:12.00] \n[00:20.00] Next line")

        when:
        def result = new LrcLibSearchService().toTranscriptionResult(candidate)

        then:
        result.segments.size() == 3
        result.segments[0].text == "First line"
        result.segments[0].startMs == 10000
        result.segments[0].endMs == 12000
        result.segments[1].text == ""
        result.segments[1].startMs == 12000
        result.segments[1].endMs == 20000
        result.segments[1].words.empty
        result.segments[2].text == "Next line"
        result.words*.text == ["First", "line", "Next", "line"]
    }
}
