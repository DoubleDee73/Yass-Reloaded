package yass.wizard

import spock.lang.Specification
import yass.I18

class CreateSongWizardSpec extends Specification {

    def setupSpec() {
        I18.setDefaultLanguage()
    }

    def "resolveInitialLrcLibQuery prefers existing artist and cleaned title"() {
        when:
        def query = CreateSongWizard.resolveInitialLrcLibQuery(
                "Rick Astley",
                "Never Gonna Give You Up (Official Video)",
                "C:/temp/ignored.mp3",
                "")

        then:
        query.artist() == "Rick Astley"
        query.title() == "Never Gonna Give You Up"
    }

    def "resolveInitialLrcLibQuery falls back to filename metadata when wizard values are blank"() {
        when:
        def query = CreateSongWizard.resolveInitialLrcLibQuery(
                "",
                "",
                "C:/temp/The Proclaimers - I'm Gonna Be (500 Miles).mp3",
                "")

        then:
        query.artist() == "The Proclaimers"
        query.title() == "I'm Gonna Be (500 Miles)"
    }

    def "resolveInitialLrcLibQuery repairs common mojibake in suggestions"() {
        when:
        def query = CreateSongWizard.resolveInitialLrcLibQuery(
                "",
                "",
                "C:/temp/BeyoncÃ© - Halo (Official Video).mp3",
                "")

        then:
        query.artist() == "Beyoncé"
        query.title() == "Halo"
    }

    def "wizard dialog is centered relative to its owner instead of the default screen"() {
        given:
        String source = new File("src/yass/wizard/CreateSongWizard.java").text

        expect:
        !source.contains("getDialog().setLocationRelativeTo(null)")
        source.contains("YassUtils.resolveDialogOwner(parent)")
    }

    def "maps lyrics source choice dialog result"() {
        expect:
        CreateSongWizard.toLrcLyricsSourceAction(choice) == action

        where:
        choice || action
        0      || CreateSongWizard.LrcLyricsSourceAction.SEARCH_LRCLIB
        1      || CreateSongWizard.LrcLyricsSourceAction.IMPORT_LRC_FILE
        2      || null
        -1     || null
    }

    def "imports local lrc file as wizard transcription source"() {
        given:
        def lrc = File.createTempFile("wizard-import", ".lrc")
        lrc.deleteOnExit()
        lrc.text = "[00:01.00]First line\n[00:03.00]Second line"

        when:
        def result = CreateSongWizard.toImportedLrcTranscriptionResult(lrc)

        then:
        result.sourceTag == "#LRC"
        result.transcriptText == "First line\nSecond line"
        result.segments*.startMs == [1000, 3000]
        result.words*.text == ["First", "line", "Second", "line"]
    }
}
