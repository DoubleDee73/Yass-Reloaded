package yass

import spock.lang.Specification

import java.nio.file.Path

class YassActionsUsdbEditSpec extends Specification {

    def "USDB local save reload matching recognizes the open editor song file"() {
        expect:
        YassActions.isSameSongFileForUsdbReload(
                Path.of("C:/Songs/Artist - Title/Artist - Title.txt"),
                "C:\\Songs\\Artist - Title",
                "Artist - Title.txt")

        and:
        !YassActions.isSameSongFileForUsdbReload(
                Path.of("C:/Songs/Artist - Title/Artist - Title.txt"),
                "C:\\Songs\\Artist - Title",
                "Other.txt")
    }
}
