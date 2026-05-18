package yass.integration.transcription

import spock.lang.Specification
import yass.analysis.SubtitleParser

class SubtitleTranscriptionAdapterSpec extends Specification {

    def "converts subtitle cues into OpenAI-style transcript segments and words"() {
        given:
        def cues = [
                new SubtitleParser.SubtitleCue(1000, 3000, "Hello there"),
                new SubtitleParser.SubtitleCue(4000, 5000, "Again")
        ]

        when:
        def result = new SubtitleTranscriptionAdapter().fromCues(cues)

        then:
        result.sourceTag == "#SUBTITLES"
        result.textSourceTag == "#SUBTITLES"
        result.timingSourceTag == "#SUBTITLES"
        result.transcriptText == "Hello there\nAgain"
        result.segments*.text == ["Hello there", "Again"]
        result.words*.text == ["Hello", "there", "Again"]
        result.words[0].startMs == 1000
        result.words[0].endMs == 2000
        result.words[1].startMs == 2000
        result.words[1].endMs == 3000
    }
}
