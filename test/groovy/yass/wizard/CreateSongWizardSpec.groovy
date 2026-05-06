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
}
