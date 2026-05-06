package yass.wizard

import spock.lang.Specification
import yass.I18
import yass.YassProperties
import yass.YassTable
import yass.integration.transcription.openai.OpenAiTranscriptSegment
import yass.integration.transcription.openai.OpenAiTranscriptWord
import yass.integration.transcription.openai.OpenAiTranscriptionResult

class LyricsSpec extends Specification {

    def "buildTableFromTranscriptionResult uses transcript rebuild and preserves syllable splitting"() {
        given:
        I18.setDefaultLanguage()
        def properties = new YassProperties()
        def result = new OpenAiTranscriptionResult(
                null,
                null,
                "#LRCLIB",
                "trying",
                [new OpenAiTranscriptWord("trying", "trying", 0, 3250)],
                [new OpenAiTranscriptSegment(0, 3250, "trying", [new OpenAiTranscriptWord("trying", "trying", 0, 3250)])],
                [],
                false,
                null)

        when:
        def tableText = Lyrics.buildTableFromTranscriptionResult(properties, "English", 60, result)
        def table = new YassTable()
        table.init(properties)
        table.setText(tableText)

        then:
        table.gap == 0
        noteTexts(table) == ["try", "ing"]
    }

    def "configureTranscriptHyphenator prepares an existing table for syllable splitting"() {
        given:
        I18.setDefaultLanguage()
        def properties = new YassProperties()
        def table = new YassTable()
        table.init(properties)
        table.setText("""
#TITLE:Unknown
#ARTIST:Unknown
#LANGUAGE:English
#GENRE:Unknown
#CREATOR:Unknown
#MP3:Unknown
#BPM:60
#GAP:0
: 0 4 0 test
E
""")
        def result = new OpenAiTranscriptionResult(
                null,
                null,
                "#LRCLIB",
                "trying",
                [new OpenAiTranscriptWord("trying", "trying", 0, 3250)],
                [new OpenAiTranscriptSegment(0, 3250, "trying", [new OpenAiTranscriptWord("trying", "trying", 0, 3250)])],
                [],
                false,
                null)

        when:
        Lyrics.configureTranscriptHyphenator(properties, table, "English")
        new yass.alignment.TranscriptNoteRebuildService().transcript(table, result)

        then:
        noteTexts(table) == ["try", "ing"]
    }

    private static List<String> noteTexts(YassTable table) {
        (0..<table.rowCount)
                .collect { table.getRowAt(it) }
                .findAll { it?.note }
                .collect { it.trimmedText }
    }
}
