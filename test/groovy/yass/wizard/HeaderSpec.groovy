package yass.wizard

import com.nexes.wizard.Wizard
import spock.lang.Specification
import yass.I18

class HeaderSpec extends Specification {

    def setupSpec() {
        I18.setDefaultLanguage()
    }

    def "artist and title getters trim manual wizard input"() {
        given:
        Header header = new Header(new Wizard())

        when:
        header.setArtist(" Stevie Wonder ")
        header.setTitle(" Send one your love ")

        then:
        header.artist == "Stevie Wonder"
        header.title == "Send one your love"
    }
}
