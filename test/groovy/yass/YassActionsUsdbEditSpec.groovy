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

    def "USDB compare rejects duet mismatch between local and remote txt"() {
        given:
        String solo = """#TITLE:Song
#ARTIST:Artist
: 0 4 0 Hello
E
"""
        String duet = """#TITLE:Song
#ARTIST:Artist
#DUETSINGERP1:Alice
#DUETSINGERP2:Bob
P1
: 0 4 0 Hello
P2
: 4 4 0 There
E
"""

        expect:
        YassActions.hasUsdbCompareDuetMismatch(solo, duet)
        YassActions.hasUsdbCompareDuetMismatch(duet, solo)
        !YassActions.hasUsdbCompareDuetMismatch(duet, duet)
        !YassActions.hasUsdbCompareDuetMismatch(solo, solo)
    }
}
