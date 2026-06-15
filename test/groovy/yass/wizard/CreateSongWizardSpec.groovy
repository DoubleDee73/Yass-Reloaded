package yass.wizard

import spock.lang.Specification
import yass.I18
import yass.integration.transcription.openai.OpenAiTranscriptSegment
import yass.integration.transcription.openai.OpenAiTranscriptWord
import yass.integration.transcription.openai.OpenAiTranscriptionResult

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

    def "adopts subtitle transcription result when the wizard has no transcript yet"() {
        given:
        def subtitleResult = transcriptResult("#SUBTITLES", "From captions")

        when:
        def state = CreateSongWizard.mergeLyricsTranscriptionResult(null, subtitleResult)

        then:
        state.transcriptionResult.is(subtitleResult)
    }

    def "keeps existing wizard transcript when subtitle lyrics are only a fallback"() {
        given:
        def existingResult = transcriptResult("#VOCALS", "From transcription")
        def subtitleResult = transcriptResult("#SUBTITLES", "From captions")
        def existingState = new WizardTranscriptionState(
                new File("run"),
                new File("source.mp3"),
                new File("source.wav"),
                null,
                existingResult)

        when:
        def state = CreateSongWizard.mergeLyricsTranscriptionResult(existingState, subtitleResult)

        then:
        state.is(existingState)
        state.transcriptionResult.is(existingResult)
    }

    def "preserves wizard assets when adding subtitle transcript to an existing state"() {
        given:
        def subtitleResult = transcriptResult("#SUBTITLES", "From captions")
        def runDirectory = new File("run")
        def sourceAudio = new File("source.mp3")
        def sourceConverted = new File("source.wav")
        def existingState = new WizardTranscriptionState(runDirectory, sourceAudio, sourceConverted, null, null)

        when:
        def state = CreateSongWizard.mergeLyricsTranscriptionResult(existingState, subtitleResult)

        then:
        state.runDirectory == runDirectory
        state.sourceAudioFile == sourceAudio
        state.sourceConvertedFile == sourceConverted
        state.transcriptionResult.is(subtitleResult)
    }

    private static OpenAiTranscriptionResult transcriptResult(String sourceTag, String text) {
        def word = new OpenAiTranscriptWord(text, text.toLowerCase(), 0, 1000)
        new OpenAiTranscriptionResult(
                null,
                null,
                sourceTag,
                text,
                [word],
                [new OpenAiTranscriptSegment(0, 1000, text, [word])],
                [],
                false,
                null)
    }
}
