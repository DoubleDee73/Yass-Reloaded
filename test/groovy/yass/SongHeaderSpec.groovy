package yass

import spock.lang.Specification

class SongHeaderSpec extends Specification {

    def 'gap spinner duration stays editable when current gap is zero'() {
        expect:
        SongHeader.calculateGapSpinnerDurationMillis(0d, 120_000_000L) == 120_000
    }

    def 'gap spinner duration keeps room for an existing large gap'() {
        expect:
        SongHeader.calculateGapSpinnerDurationMillis(15_000d, 120_000_000L) == 150_000
    }

    def 'gap spinner duration has a useful fallback while audio duration is unknown'() {
        expect:
        SongHeader.calculateGapSpinnerDurationMillis(0d, 0L) == 10_000
    }

    def 'gap updates do not steal focus from song header controls'() {
        given:
        def source = new File('src/yass/YassActions.java').text
        def updateGapBpm = source.substring(source.indexOf('private void updateGapBpm() {'),
                source.indexOf('public void setVideoGap', source.indexOf('private void updateGapBpm() {')))

        expect:
        !updateGapBpm.contains('requestFocus()')
    }

    def 'song header updates fall back to the active table when no matching open table is found'() {
        given:
        YassTable activeTable = new YassTable(new YassTableModel(), Stub(YassProperties))
        Vector<YassTable> matchingTables = new Vector<>()

        when:
        Vector<YassTable> targetTables = YassActions.tablesForCurrentSongUpdate(matchingTables, activeTable)

        then:
        targetTables == [activeTable] as Vector<YassTable>
    }
}
