package yass.usdb

import spock.lang.Specification

class UsdbSearchDialogSpec extends Specification {

    def 'shows compare with usdb only for exact and artist-title matches'() {
        expect:
        !UsdbSearchDialog.shouldShowCompareWithUsdb(UsdbSearchDialog.MatchStatus.NONE)
        UsdbSearchDialog.shouldShowCompareWithUsdb(UsdbSearchDialog.MatchStatus.TITLE_ARTIST)
        UsdbSearchDialog.shouldShowCompareWithUsdb(UsdbSearchDialog.MatchStatus.EXACT)
        !UsdbSearchDialog.shouldShowCompareWithUsdb(UsdbSearchDialog.MatchStatus.QUEUED)
    }
}
