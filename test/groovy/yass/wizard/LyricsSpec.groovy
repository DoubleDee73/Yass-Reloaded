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

    def "manual lyrics clear subtitle-derived source before table creation"() {
        given:
        I18.setDefaultLanguage()
        def properties = new YassProperties()
        def wizard = new com.nexes.wizard.Wizard()
        wizard.setValue("language", "English")
        wizard.setValue("bpm", "60")
        def lyrics = new Lyrics(wizard, properties)
        def subtitleFile = java.nio.file.Files.createTempFile("wizard-lyrics", ".vtt")
        java.nio.file.Files.writeString(subtitleFile, """WEBVTT

00:00:01.000 --> 00:00:02.000
Subtitle text
""")
        lyrics.setSubtitleFile(subtitleFile.toString())

        when:
        lyrics.lyricsArea.setText("Manual text")
        javax.swing.SwingUtilities.invokeAndWait({})

        then:
        lyrics.subtitleFileField.text == ""
        lyrics.getTable().contains("Ma")
        !lyrics.getTable().contains("Subtitle")

        cleanup:
        java.nio.file.Files.deleteIfExists(subtitleFile)
        wizard.dialog.dispose()
    }

    private static List<String> noteTexts(YassTable table) {
        (0..<table.rowCount)
                .collect { table.getRowAt(it) }
                .findAll { it?.note }
                .collect { it.trimmedText }
    }
}
