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

    def 'bulk import only asks for confirmation above ten selected songs'() {
        expect:
        !UsdbSearchDialog.shouldConfirmBulkImport(10)
        UsdbSearchDialog.shouldConfirmBulkImport(11)
    }

    def 'bulk import candidates skip songs already queued'() {
        given:
        def queued = summary(1, 'Artist', 'Queued')
        def exact = summary(2, 'Artist', 'Existing')
        def fresh = summary(3, 'Artist', 'Fresh')

        expect:
        UsdbSearchDialog.importCandidates([queued, exact, fresh], { song ->
            song == queued ? UsdbSearchDialog.MatchStatus.QUEUED :
                    song == exact ? UsdbSearchDialog.MatchStatus.EXACT :
                            UsdbSearchDialog.MatchStatus.NONE
        }) == [exact, fresh]
    }

    private static UsdbSongSummary summary(int id, String artist, String title) {
        new UsdbSongSummary(id, artist, title, '', '', '', '', false)
    }
}
