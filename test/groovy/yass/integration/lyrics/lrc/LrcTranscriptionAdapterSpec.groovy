package yass.integration.lyrics.lrc

import spock.lang.Specification

class LrcTranscriptionAdapterSpec extends Specification {

    def "converts synced lrc lines into transcript segments and word timings"() {
        given:
        def adapter = new LrcTranscriptionAdapter()
        def lrc = """[00:01.000]Hello world
[00:03.500]Again"""

        when:
        def result = adapter.fromLrcText(lrc, 6)

        then:
        result.sourceTag == "#LRC"
        result.textSourceTag == "#LRC"
        result.timingSourceTag == "#LRC"
        result.transcriptText == "Hello world\nAgain"
        result.segments*.startMs == [1000, 3500]
        result.segments*.endMs == [3500, 6000]
        result.words*.text == ["Hello", "world", "Again"]
        result.words*.startMs == [1000, 2250, 3500]
        result.words*.endMs == [2250, 3500, 6000]
    }

    def "ignores untimed lines and repeated timestamps create separate segments"() {
        given:
        def adapter = new LrcTranscriptionAdapter()
        def lrc = """[00:00.50][00:02.00]Echo
untimed metadata
[00:04.00]Tail"""

        when:
        def result = adapter.fromLrcText(lrc, 5)

        then:
        result.transcriptText == "Echo\nEcho\nTail"
        result.segments*.startMs == [500, 2000, 4000]
        result.segments*.endMs == [2000, 4000, 5000]
    }
}
