package yass.integration.transcription

import spock.lang.Specification
import yass.integration.transcription.openai.OpenAiTranscriptionResult

class TranscriptSourceCommentSpec extends Specification {

    def "does not add transcript provenance to comment"() {
        given:
        def result = new OpenAiTranscriptionResult(
                null,
                null,
                "#VOCALS",
                "lyrics",
                [],
                [],
                [],
                false,
                null,
                "#LRCLIB",
                "#VOCALS")

        expect:
        TranscriptSourceComment.merge("", result) == ""
    }

    def "preserves unrelated comment entries and removes stale transcript source entries"() {
        given:
        def result = new OpenAiTranscriptionResult(
                null,
                null,
                "#SUBTITLES",
                "lyrics",
                [],
                [],
                [],
                false,
                null,
                "#SUBTITLES",
                "#SUBTITLES")

        expect:
        TranscriptSourceComment.merge(
                "v=abc,key=Am,transcriptText=#LRCLIB,transcriptTiming=#VOCALS,transcriptSource=#VOCALS",
                result) == "v=abc,key=Am"
    }

    def "leaves comment unchanged when transcript result is missing"() {
        expect:
        TranscriptSourceComment.merge("v=abc,key=Am", null) == "v=abc,key=Am"
    }
}
