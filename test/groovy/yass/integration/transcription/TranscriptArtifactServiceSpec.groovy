package yass.integration.transcription

import spock.lang.Specification
import yass.integration.transcription.openai.OpenAiTranscriptSegment
import yass.integration.transcription.openai.OpenAiTranscriptWord
import yass.integration.transcription.openai.OpenAiTranscriptionResult

import java.nio.file.Files

class TranscriptArtifactServiceSpec extends Specification {

    def "saves and loads canonical transcript artifact with separate text and timing sources"() {
        given:
        def songDir = Files.createTempDirectory("yass-song")
        def words = [
                new OpenAiTranscriptWord("I'll", "ill", 1000, 1400),
                new OpenAiTranscriptWord("go", "go", 1400, 1800)
        ]
        def result = new OpenAiTranscriptionResult(
                null,
                null,
                "#VOCALS",
                "I'll go",
                words,
                [new OpenAiTranscriptSegment(1000, 1800, "I'll go", words)],
                [],
                false,
                null,
                "#LRCLIB",
                "#VOCALS")

        when:
        def target = new TranscriptArtifactService().save(songDir.toFile(), result)
        def loaded = new TranscriptArtifactService().load(songDir.toFile())

        then:
        target.name == "yass-transcript.json"
        loaded.sourceTag == "#VOCALS"
        loaded.textSourceTag == "#LRCLIB"
        loaded.timingSourceTag == "#VOCALS"
        loaded.transcriptText == "I'll go"
        loaded.segments.size() == 1
        loaded.segments[0].startMs == 1000
        loaded.words*.text == ["I'll", "go"]

        cleanup:
        Files.deleteIfExists(songDir.resolve("yass-transcript.json"))
        Files.deleteIfExists(songDir)
    }

    def "load normalizes missing or broken normalizedText values from artifact words"() {
        given:
        def songDir = Files.createTempDirectory("yass-song")
        def artifact = songDir.resolve("yass-transcript.json")
        artifact.toFile().text = '''{
  "schemaVersion": 1,
  "sourceTag": "#SUBTITLES",
  "textSourceTag": "#SUBTITLES",
  "timingSourceTag": "#SUBTITLES",
  "transcriptText": "I can\\nthink",
  "words": [
    { "text": "I", "normalizedText": "", "startMs": 1000, "endMs": 1200 },
    { "text": "can", "startMs": 1200, "endMs": 1500 },
    { "text": "days", "normalizedText": "d", "startMs": 1500, "endMs": 1900 }
  ],
  "segments": []
}'''

        when:
        def loaded = new TranscriptArtifactService().load(songDir.toFile())

        then:
        loaded.words*.normalizedText == ["i", "can", "days"]

        cleanup:
        Files.deleteIfExists(artifact)
        Files.deleteIfExists(songDir)
    }
}
