package yass.usdb

import spock.lang.Specification

class UsdbSongEditDiffDialogSpec extends Specification {

    def "adjacent header and note changes stay separate diff blocks"() {
        given:
        String usdbText = """#TITLE:Remote Title
#ARTIST:Remote Artist
: 0 4 0 Hello
E"""
        String localText = """#TITLE:Local Title
#ARTIST:Local Artist
: 1 4 0 Hello
E"""

        expect:
        UsdbSongEditDiffDialog.describeDiffBlocksForTest(usdbText, localText) == [
                "HEADER:L0-1:R0-1",
                "BODY:L2-2:R2-2"
        ]
    }
}
