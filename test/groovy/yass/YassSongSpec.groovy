package yass

import spock.lang.Specification

class YassSongSpec extends Specification {

    def "toFilename removes Windows-invalid trailing spaces and dots"() {
        expect:
        YassSong.toFilename("Stevie Wonder - Send one your love ") ==
                "Stevie Wonder - Send one your love"
        YassSong.toFilename("Stevie Wonder - Send one your love ..opus") ==
                "Stevie Wonder - Send one your love.opus"
        YassSong.toFilename("...") == ""
    }

    def "toFilename removes Windows-invalid filename characters"() {
        expect:
        YassSong.toFilename('A/B\\C?D*E:F<G>H"I|J') == "ABCDEFGHIJ"
    }
}
