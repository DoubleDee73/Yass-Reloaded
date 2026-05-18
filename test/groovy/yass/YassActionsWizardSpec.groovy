package yass

import spock.lang.Specification
import yass.integration.separation.SeparationResult
import yass.integration.transcription.openai.OpenAiTranscriptionResult
import yass.wizard.WizardTranscriptionState

import java.nio.file.Files

class YassActionsWizardSpec extends Specification {

    def setupSpec() {
        I18.setDefaultLanguage()
    }

    def "offers post-wizard separation only when no separation result exists"() {
        expect:
        YassActions.shouldOfferSeparationAfterWizard(null)
        YassActions.shouldOfferSeparationAfterWizard(new WizardTranscriptionState(null, null, null, null, null))
        !YassActions.shouldOfferSeparationAfterWizard(new WizardTranscriptionState(
                null,
                null,
                null,
                new SeparationResult(null, null, null, null),
                null))
    }

    def "finds canonical transcript artifact next to a song file"() {
        given:
        def songDir = Files.createTempDirectory("yass-song")
        def songFile = songDir.resolve("Artist - Title.txt")
        Files.writeString(songFile, "#TITLE:Title\nE\n")
        def transcriptFile = songDir.resolve("yass-transcript.json")
        Files.writeString(transcriptFile, "{}")

        expect:
        YassActions.findTranscriptArtifactForSongFile(songFile.toString()) == transcriptFile.toFile()

        cleanup:
        Files.deleteIfExists(transcriptFile)
        Files.deleteIfExists(songFile)
        Files.deleteIfExists(songDir)
    }

    def "does not report transcript artifact when song folder has none"() {
        given:
        def songDir = Files.createTempDirectory("yass-song")
        def songFile = songDir.resolve("Artist - Title.txt")
        Files.writeString(songFile, "#TITLE:Title\nE\n")

        expect:
        YassActions.findTranscriptArtifactForSongFile(songFile.toString()) == null
        YassActions.findTranscriptArtifactForSongFile("") == null

        cleanup:
        Files.deleteIfExists(songFile)
        Files.deleteIfExists(songDir)
    }

    def "post wizard separation prompt mentions reusable transcript when artifact exists"() {
        expect:
        YassActions.buildPostWizardSeparationPrompt(false) == I18.get("create_song_open_separation_prompt")
        YassActions.buildPostWizardSeparationPrompt(true) ==
                I18.get("create_song_open_separation_prompt") + "\n\n" +
                I18.get("create_song_open_separation_transcript_hint")
    }
}
