/*
 * Yass Reloaded - Karaoke Editor
 * Copyright (C) 2009-2023 Saruta
 * Copyright (C) 2023 DoubleDee
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package yass

import net.davidashen.text.Hyphenator
import spock.lang.Specification
import yass.analysis.PitchDetector
import yass.autocorrect.YassAutoCorrect

import javax.swing.*
import javax.swing.table.TableModel 

class YassTableSpec extends Specification {

    private final static List<YassRow> LEADING_SPACE_END_TILDE_SONG = initSong1()
    private final static List<YassRow> TRAILING_SPACE_END_TILDE_SONG = initSong2()
    private final static List<YassRow> LEADING_SPACE_END_WORD_SONG = initSong3()
    private final static List<YassRow> TRAILING_SPACE_END_WORD_SONG = initSong4()
    private final static List<YassRow> TILDE_SONG = initSong5()

    def 'selectPrevBeat extends a single-note selection upward'() {
        given:
        YassTable yassTable = tableWithNotes()
        yassTable.setRowSelectionInterval(1, 1)

        when:
        yassTable.selectPrevBeat()

        then:
        yassTable.getSelectedRows() == [0, 1] as int[]
    }

    def 'selectNextBeat extends a single-note selection downward'() {
        given:
        YassTable yassTable = tableWithNotes()
        yassTable.setRowSelectionInterval(1, 1)

        when:
        yassTable.selectNextBeat()

        then:
        yassTable.getSelectedRows() == [1, 2] as int[]
    }

    def 'splitRowsByPitch splits a held syllable at the first stable pitch segment change'() {
        given:
        YassTable yassTable = tableWithSingleNote(690, 14, 1, 'down ')
        List<PitchDetector.PitchData> pitchData = pitchFramesByBeat(yassTable, 690,
                [4, 4, 4, 4, 1, 0, 1, 1, 0, 1, 2, 0, 1, 1])

        when:
        yassTable.splitRowsByPitch(pitchData)

        then:
        yassTable.getRowAt(1).getBeatInt() == 690
        yassTable.getRowAt(1).getLengthInt() == 3
        yassTable.getRowAt(1).getHeightInt() == 4
        yassTable.getRowAt(1).getText() == 'dow'
        yassTable.getRowAt(2).getBeatInt() == 694
        yassTable.getRowAt(2).getLengthInt() == 10
        yassTable.getRowAt(2).getHeightInt() == 1
        yassTable.getRowAt(2).getText() == '~n' + YassRow.SPACE
    }

    def 'splitRowsByPitch waits for a stable target pitch and ignores transition beats'() {
        given:
        YassTable yassTable = tableWithSingleNote(1801, 15, 1, 'round ')
        List<PitchDetector.PitchData> pitchData = pitchFramesByBeat(yassTable, 1801,
                [4, 4, 4, 4, 3, 0, 1, 1, 1, 2, 1, 1, 1, 1, 1])

        when:
        yassTable.splitRowsByPitch(pitchData)

        then:
        yassTable.getRowAt(1).getBeatInt() == 1801
        yassTable.getRowAt(1).getLengthInt() == 5
        yassTable.getRowAt(1).getHeightInt() == 4
        yassTable.getRowAt(1).getText() == 'rou'
        yassTable.getRowAt(2).getBeatInt() == 1807
        yassTable.getRowAt(2).getLengthInt() == 9
        yassTable.getRowAt(2).getHeightInt() == 1
        yassTable.getRowAt(2).getText() == '~nd' + YassRow.SPACE
    }

    def 'splitRowsByPitch maps matching syllable and pitch segment counts one to one'() {
        given:
        YassTable yassTable = tableWithSingleNote(100, 6, 4, 'pudding ', 'pud\u00ADding')
        List<PitchDetector.PitchData> pitchData = pitchFramesByBeat(yassTable, 100,
                [4, 4, 4, 7, 7, 7])

        when:
        yassTable.splitRowsByPitch(pitchData)

        then:
        yassTable.getRowAt(1).getBeatInt() == 100
        yassTable.getRowAt(1).getLengthInt() == 2
        yassTable.getRowAt(1).getHeightInt() == 4
        yassTable.getRowAt(1).getText() == 'pud'
        yassTable.getRowAt(2).getBeatInt() == 103
        yassTable.getRowAt(2).getLengthInt() == 3
        yassTable.getRowAt(2).getHeightInt() == 7
        yassTable.getRowAt(2).getText() == 'ding' + YassRow.SPACE
    }

    def 'splitRowsByPitch maps the first syllable to the first segment and the remaining syllables to the second segment'() {
        given:
        YassTable yassTable = tableWithSingleNote(200, 7, 2, 'amazing ', 'a\u00ADma\u00ADzing')
        List<PitchDetector.PitchData> pitchData = pitchFramesByBeat(yassTable, 200,
                [2, 2, 2, 5, 5, 5, 5])

        when:
        yassTable.splitRowsByPitch(pitchData)

        then:
        yassTable.getRowAt(1).getBeatInt() == 200
        yassTable.getRowAt(1).getLengthInt() == 2
        yassTable.getRowAt(1).getHeightInt() == 2
        yassTable.getRowAt(1).getText() == 'a'
        yassTable.getRowAt(2).getBeatInt() == 203
        yassTable.getRowAt(2).getLengthInt() == 4
        yassTable.getRowAt(2).getHeightInt() == 5
        yassTable.getRowAt(2).getText() == 'mazing' + YassRow.SPACE
    }

    def 'splitRowsByPitch moves a final consonant cluster to the last pitch segment'() {
        given:
        YassTable yassTable = tableWithSingleNote(300, 6, 4, text)
        List<PitchDetector.PitchData> pitchData = pitchFramesByBeat(yassTable, 300,
                [4, 4, 4, 7, 7, 7])

        when:
        yassTable.splitRowsByPitch(pitchData)

        then:
        yassTable.getRowAt(1).getBeatInt() == 300
        yassTable.getRowAt(1).getLengthInt() == 2
        yassTable.getRowAt(1).getHeightInt() == 4
        yassTable.getRowAt(1).getText() == expectedLeft
        yassTable.getRowAt(2).getBeatInt() == 303
        yassTable.getRowAt(2).getLengthInt() == 3
        yassTable.getRowAt(2).getHeightInt() == 7
        yassTable.getRowAt(2).getText() == expectedRight + YassRow.SPACE

        where:
        text       || expectedLeft | expectedRight
        'Ground '  || 'Grou'       | '~nd'
        'Point '   || 'Poi'        | '~nt'
        'jump '    || 'ju'         | '~mp'
    }

    def 'splitRowsByPitch keeps a two letter word intact before the tilde continuation'() {
        given:
        YassTable yassTable = tableWithSingleNote(400, 6, 4, 'is ')
        List<PitchDetector.PitchData> pitchData = pitchFramesByBeat(yassTable, 400,
                [4, 4, 4, 7, 7, 7])

        when:
        yassTable.splitRowsByPitch(pitchData)

        then:
        yassTable.getRowAt(1).getText() == 'is'
        yassTable.getRowAt(2).getText() == '~' + YassRow.SPACE
    }

    def 'splitRowsByPitch moves contraction endings to the last pitch segment'() {
        given:
        YassTable yassTable = tableWithSingleNote(450, 6, 4, text)
        List<PitchDetector.PitchData> pitchData = pitchFramesByBeat(yassTable, 450,
                [4, 4, 4, 7, 7, 7])

        when:
        yassTable.splitRowsByPitch(pitchData)

        then:
        yassTable.getRowAt(1).getText() == expectedLeft
        yassTable.getRowAt(2).getText() == expectedRight + YassRow.SPACE

        where:
        text          || expectedLeft | expectedRight
        "I'll "       || 'I'          | "~'ll"
        "won't "      || 'won'        | "~'t"
        "I\u2019ll "  || 'I'          | "~\u2019ll"
    }

    def 'splitRowsByPitch puts final consonant clusters on the last of several pitch segments'() {
        given:
        YassTable yassTable = tableWithSingleNote(500, 9, 16, 'friend ')
        List<PitchDetector.PitchData> pitchData = pitchFramesByBeat(yassTable, 500,
                [16, 16, 16, 14, 14, 14, 11, 11, 11])

        when:
        yassTable.splitRowsByPitch(pitchData)

        then:
        yassTable.getRowAt(1).getText() == 'frie'
        yassTable.getRowAt(2).getText() == '~'
        yassTable.getRowAt(3).getText() == '~nd' + YassRow.SPACE
    }

    def 'splitRowsByPitch keeps final consonant cluster and punctuation when pitch tail is not stable enough'() {
        given:
        YassTable yassTable = tableWithSingleNote(17, 7, 11, 'wind, ')
        List<PitchDetector.PitchData> pitchData = pitchFramesByBeat(yassTable, 17,
                [11, 11, 11, 11, 9, 8, 7])

        when:
        yassTable.splitRowsByPitch(pitchData)

        then:
        yassTable.getRowAt(1).getBeatInt() == 17
        yassTable.getRowAt(1).getLengthInt() == 3
        yassTable.getRowAt(1).getHeightInt() == 11
        yassTable.getRowAt(1).getText() == 'wi'
        yassTable.getRowAt(2).getBeatInt() == 21
        yassTable.getRowAt(2).getLengthInt() == 3
        yassTable.getRowAt(2).getHeightInt() == 11
        yassTable.getRowAt(2).getText() == '~nd,' + YassRow.SPACE
    }

    def 'splitRowsByPitch applies the changed pitch when legacy sustained-run fallback is used'() {
        given:
        YassTable yassTable = tableWithSingleNote(316, 12, 6, 'sound ')
        List<PitchDetector.PitchData> pitchData = pitchFramesByBeat(yassTable, 316,
                [31, 5, 6, 6, 5, 6, 8, 8, 8, 8, 7, 6])

        when:
        yassTable.splitRowsByPitch(pitchData)

        then:
        yassTable.getRowAt(1).getBeatInt() == 316
        yassTable.getRowAt(1).getLengthInt() == 5
        yassTable.getRowAt(1).getHeightInt() == 6
        yassTable.getRowAt(1).getText() == 'sou'
        yassTable.getRowAt(2).getBeatInt() == 322
        yassTable.getRowAt(2).getLengthInt() == 6
        yassTable.getRowAt(2).getHeightInt() == 8
        yassTable.getRowAt(2).getText() == '~nd' + YassRow.SPACE
    }

    def 'splitRows keeps a tilde continuation on the right when splitting it again'() {
        given:
        YassTable yassTable = tableWithSingleNote(600, 6, 4, '~n ')

        when:
        yassTable.splitRows()

        then:
        yassTable.getRowAt(1).getBeatInt() == 600
        yassTable.getRowAt(1).getLengthInt() == 2
        yassTable.getRowAt(1).getHeightInt() == 4
        yassTable.getRowAt(1).getText() == '~'
        yassTable.getRowAt(2).getBeatInt() == 603
        yassTable.getRowAt(2).getLengthInt() == 3
        yassTable.getRowAt(2).getHeightInt() == 4
        yassTable.getRowAt(2).getText() == '~n' + YassRow.SPACE
    }

    def 'shiftEndingLeft keeps a tilde in the source note when moving its only letter'() {
        given:
        YassTable yassTable = tableWithTwoNotes('O', 'f')
        yassTable.setRowSelectionInterval(0, 1)

        when:
        yassTable.shiftEndingLeft()

        then:
        yassTable.getRowAt(0).getText() == 'Of'
        yassTable.getRowAt(1).getText() == '~'
    }

    def 'shiftEnding keeps a tilde in the source note when moving its only letter'() {
        given:
        YassTable yassTable = tableWithTwoNotes('g', 'h')
        yassTable.setRowSelectionInterval(0, 1)

        when:
        yassTable.shiftEnding()

        then:
        yassTable.getRowAt(0).getText() == '~'
        yassTable.getRowAt(1).getText() == '~gh'
    }

    def 'toggleTilde removes a leading tilde before a sung vowel fragment'() {
        given:
        YassTable yassTable = tableWithSingleNote(497, 2, 11, '~o')

        when:
        yassTable.toggleTilde()

        then:
        yassTable.getRowAt(1).getText() == 'o'
    }

    def 'suggestGoldenNotes starts with pitch leaps before long words'() {
        given:
        YassTable yassTable = tableForGoldenSuggestions([
                note(':', 0, 2, 0, 'risk '),
                note(':', 3, 2, 7, 'it '),
                note(':', 6, 2, 3, 'all '),
                note(':', 9, 5, 3, 'later ')
        ])
        yassTable.setGoldenPoints(0, 1250, 250, 0, 6, '+6')

        when:
        yassTable.suggestGoldenNotes()

        then:
        yassTable.getRowAt(0).getType() == '*'
        yassTable.getRowAt(1).getType() == '*'
        yassTable.getRowAt(2).getType() == '*'
        yassTable.getRowAt(3).getType() == ':'
    }

    def 'suggestGoldenNotes alternates pitch leaps and long words'() {
        given:
        YassTable yassTable = tableForGoldenSuggestions([
                note(':', 0, 2, 0, 'risk '),
                note(':', 3, 2, 7, 'it '),
                note(':', 6, 2, 3, 'all '),
                note(':', 9, 5, 3, 'later ')
        ])
        yassTable.setGoldenPoints(0, 1250, 250, 0, 11, '+11')

        when:
        yassTable.suggestGoldenNotes()

        then:
        (0..3).collect { yassTable.getRowAt(it).getType() } == ['*', '*', '*', '*']
    }

    def 'suggestGoldenNotes uses a shorter pitch leap when a long word no longer fits'() {
        given:
        YassTable yassTable = tableForGoldenSuggestions([
                note(':', 0, 2, 0, 'risk '),
                note(':', 3, 2, 7, 'it '),
                note(':', 6, 2, 3, 'all '),
                note(':', 9, 5, 3, 'later '),
                note(':', 15, 1, 2, 'ri'),
                note(':', 17, 1, 9, '~sk '),
                note(':', 19, 2, 5, 'it ')
        ])
        yassTable.setGoldenPoints(0, 1250, 250, 0, 10, '+10')

        when:
        yassTable.suggestGoldenNotes()

        then:
        (0..2).collect { yassTable.getRowAt(it).getType() } == ['*', '*', '*']
        yassTable.getRowAt(3).getType() == ':'
        (4..6).collect { yassTable.getRowAt(it).getType() } == ['*', '*', '*']
    }

    def 'suggestGoldenNotes converts rap pitch leaps to rap golden notes'() {
        given:
        YassTable yassTable = tableForGoldenSuggestions([
                note('R', 0, 2, 0, 'risk '),
                note('R', 3, 2, 7, 'it '),
                note('R', 6, 2, 3, 'all ')
        ])
        yassTable.setGoldenPoints(0, 1250, 250, 0, 6, '+6')

        when:
        yassTable.suggestGoldenNotes()

        then:
        (0..2).collect { yassTable.getRowAt(it).getType() } == ['G', 'G', 'G']
    }

    def 'suggestGoldenNotes ignores freestyle and already golden notes'() {
        given:
        YassTable yassTable = tableForGoldenSuggestions([
                note('*', 0, 5, 0, 'golden '),
                note('F', 6, 5, 0, 'free '),
                note(':', 12, 2, 0, 'risk '),
                note(':', 15, 2, 7, 'it '),
                note(':', 18, 2, 3, 'all ')
        ])
        yassTable.setGoldenPoints(0, 1250, 250, 0, 6, '+6')

        when:
        yassTable.suggestGoldenNotes()

        then:
        (0..4).collect { yassTable.getRowAt(it).getType() } == ['*', 'F', '*', '*', '*']
    }

    def 'suggestGoldenNotes honors leading-space word boundaries'() {
        given:
        YassTable yassTable = tableForGoldenSuggestions([
                note(':', 0, 2, 0, ' risk'),
                note(':', 3, 2, 7, ' it'),
                note(':', 6, 2, 3, ' all'),
                note(':', 9, 5, 3, ' later')
        ], false)
        yassTable.setGoldenPoints(0, 1250, 250, 0, 6, '+6')

        when:
        yassTable.suggestGoldenNotes()

        then:
        (0..2).collect { yassTable.getRowAt(it).getType() } == ['*', '*', '*']
        yassTable.getRowAt(3).getType() == ':'
    }

    def 'suggestGoldenNotes does not build pitch leap candidates across page breaks'() {
        given:
        YassTable yassTable = tableForGoldenSuggestions([
                note(':', 0, 2, 0, 'not '),
                note(':', 3, 2, 7, 'across '),
                new YassRow('-', '5', '', '', ''),
                note(':', 6, 2, 0, 'page '),
                note(':', 9, 2, 0, 'risk '),
                note(':', 12, 2, 7, 'it '),
                note(':', 15, 2, 3, 'all ')
        ])
        yassTable.setGoldenPoints(0, 1250, 250, 0, 6, '+6')

        when:
        yassTable.suggestGoldenNotes()

        then:
        noteTypes(yassTable) == [':', ':', ':', '*', '*', '*']
    }

    def 'isSongWithTrailingSpaces should check, if a song has trailing spaces'() {
        given:
        YassTableModel ytm = new YassTableModel()
        LEADING_SPACE_END_TILDE_SONG.each { row ->
            ytm.addRow(row)
        }
        when:
        YassProperties props = Stub(YassProperties)
        YassTable yassTable = new YassTable(ytm, props)

        then:
        !yassTable.isSongWithTrailingSpaces()

        when:
        ytm = new YassTableModel()
        TRAILING_SPACE_END_TILDE_SONG.each { row ->
            ytm.addRow(row)
        }
        and:
        yassTable = new YassTable(ytm, props)

        then:
        yassTable.isSongWithTrailingSpaces()
    }

    def 'rollRight applied to a song with leading spaces ending with ~. Legacy spacing'() {
        given:
        YassTableModel ytm = new YassTableModel()
        LEADING_SPACE_END_TILDE_SONG.each { row ->
            ytm.addRow(row)
        }

        and:
        I18.setDefaultLanguage()

        and:
        ListSelectionModel selectionModel = Stub(ListSelectionModel) {
            getMinSelectionIndex() >> rowNum
        }
        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> false
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.selectionModel = selectionModel
        yassTable.model = Stub(TableModel) {
            getRowCount() >> LEADING_SPACE_END_TILDE_SONG.size()
        }

        when:
        yassTable.rollRight(splitCode as char, splitPos)

        then:
        verifyExpectation(yassTable, expectation)

        where:
        rowNum | splitCode | splitPos || expectation
        0      | '$'       | 0        || ['~', 'One', '~', ' two', '_', 'three', ' Four', ' five']
        0      | ' '       | 1        || ['O', ' ne', '~', ' two', '_', 'three', ' Four', ' five']
        0      | '-'       | 1        || ['O', 'ne', '~', ' two', '_', 'three', ' Four', ' five']
        1      | '$'       | 0        || ['One', '~', '~', ' two', '_', 'three', ' Four', ' five']
        2      | '$'       | 0        || ['One', '~', '~', ' two', '_', 'three', ' Four', ' five']
        2      | ' '       | 2        || ['One', '~', ' t', ' wo', '_', 'three', ' Four', ' five']
        3      | '$'       | 0        || ['One', '~', ' two', '~', '_', ' three', ' Four', ' five'] // why?
        5      | '$'       | 0        || ['One', '~', ' two', ' three', '_', '~', 'Four', ' five']
        5      | '-'       | 2        || ['One', '~', ' two', ' three', '_', 'Fo', 'ur', ' five']
        6      | '$'       | 0        || ['One', '~', ' two', ' three', '_', 'Four', '~', ' five']
        6      | ' '       | 3        || ['One', '~', ' two', ' three', '_', 'Four', ' fi', ' ve']
        6      | '-'       | 3        || ['One', '~', ' two', ' three', '_', 'Four', ' fi', 've']
        7      | '$'       | 0        || ['One', '~', ' two', ' three', '_', 'Four', ' five', '~']
    }

    def 'rollRight applied to a song with leading spaces ending with ~. New spacing'() {
        given:
        YassTableModel ytm = new YassTableModel()
        TRAILING_SPACE_END_TILDE_SONG.each { row ->
            ytm.addRow(row)
        }

        and:
        I18.setDefaultLanguage()

        and:
        ListSelectionModel selectionModel = Stub(ListSelectionModel) {
            getMinSelectionIndex() >> rowNum
        }
        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> true
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.selectionModel = selectionModel
        yassTable.model = Stub(TableModel) {
            getRowCount() >> TRAILING_SPACE_END_TILDE_SONG.size()
        }

        when:
        yassTable.rollRight(splitCode as char, slitPos) // We are substracting this in the code again

        then:
        verifyExpectation(yassTable, expectation)

        where:
        rowNum | splitCode | slitPos || expectation
        0      | '$'       | 0       || ['~', 'One', '~ ', 'two ', '_', 'three ', 'Four ', 'five ']
        0      | ' '       | 1       || ['O ', 'ne', '~ ', 'two ', '_', 'three ', 'Four ', 'five ']
        0      | ' '       | 2       || ['On ', 'e', '~ ', 'two ', '_', 'three ', 'Four ', 'five ']
        0      | '-'       | 1       || ['O', 'ne', '~ ', 'two ', '_', 'three ', 'Four ', 'five ']
        1      | '$'       | 0       || ['One', '~', '~ ', 'two ', '_', 'three ', 'Four ', 'five ']
        2      | '$'       | 0       || ['One', '~', '~ ', 'two ', '_', 'three ', 'Four ', 'five ']
        2      | ' '       | 2       || ['One', '~ ', 'tw ', 'o ', '_', 'three ', 'Four ', 'five ']
        3      | '$'       | 0       || ['One', '~ ', 'two', '~ ', '_', 'three ', 'Four ', 'five ']
        3      | ' '       | 3       || ['One', '~ ', 'two ', 'thr ', '_', 'ee ', 'Four ', 'five ']
        5      | '$'       | 0       || ['One', '~ ', 'two ', 'three ', '_', '~', 'Four ', 'five ']
        5      | '-'       | 2       || ['One', '~ ', 'two ', 'three ', '_', 'Fo', 'ur ', 'five ']
        6      | '$'       | 0       || ['One', '~ ', 'two ', 'three ', '_', 'Four', '~ ', 'five ']
        6      | ' '       | 2       || ['One', '~ ', 'two ', 'three ', '_', 'Four ', 'fi ', 've ']
        6      | '-'       | 2       || ['One', '~ ', 'two ', 'three ', '_', 'Four ', 'fi', 've ']
        7      | '$'       | 0       || ['One', '~ ', 'two ', 'three ', '_', 'Four ', 'five', '~ ']
    }

    def 'rollRight applied to a song with leading spaces ending with word. Legacy spacing'() {
        given:
        YassTableModel ytm = new YassTableModel()
        LEADING_SPACE_END_WORD_SONG.each { row ->
            ytm.addRow(row)
        }

        and:
        I18.setDefaultLanguage()

        and:
        ListSelectionModel selectionModel = Stub(ListSelectionModel) {
            getMinSelectionIndex() >> rowNum
        }
        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> false
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.selectionModel = selectionModel
        yassTable.model = Stub(TableModel) {
            getRowCount() >> LEADING_SPACE_END_WORD_SONG.size()
        }

        when:
        yassTable.rollRight(splitCode as char, slitPos)

        then:
        verifyExpectation(yassTable, expectation)

        where:
        rowNum | splitCode | slitPos || expectation
        0      | '$'       | 0       || ['~', 'One', '~', ' two', '_', 'three', ' Four', ' five six']
        0      | ' '       | 1       || ['O', ' ne', '~', ' two', '_', 'three', ' Four', ' five six']
        0      | '-'       | 1       || ['O', 'ne', '~', ' two', '_', 'three', ' Four', ' five six']
        1      | '$'       | 0       || ['One', '~', '~', ' two', '_', 'three', ' Four', ' five six']
        2      | '$'       | 0       || ['One', '~', '~', ' two', '_', 'three', ' Four', ' five six']
        2      | ' '       | 2       || ['One', '~', ' t', ' wo', '_', 'three', ' Four', ' five six']
        3      | '$'       | 0       || ['One', '~', ' two', '~', '_', ' three', ' Four', ' five six'] // why?
        5      | '$'       | 0       || ['One', '~', ' two', ' three', '_', '~', 'Four', ' five six']
        5      | '-'       | 2       || ['One', '~', ' two', ' three', '_', 'Fo', 'ur', ' five six']
        6      | '$'       | 0       || ['One', '~', ' two', ' three', '_', 'Four', '~', ' five']
        6      | ' '       | 3       || ['One', '~', ' two', ' three', '_', 'Four', ' fi', ' ve']
        6      | '-'       | 3       || ['One', '~', ' two', ' three', '_', 'Four', ' fi', 've']
        7      | '$'       | 0       || ['One', '~', ' two', ' three', '_', 'Four', ' five', ' six']
    }

    def 'rollRight applied to a song with leading spaces ending with word. New spacing'() {
        given:
        YassTableModel ytm = new YassTableModel()
        TRAILING_SPACE_END_WORD_SONG.each { row ->
            ytm.addRow(row)
        }

        and:
        I18.setDefaultLanguage()

        and:
        ListSelectionModel selectionModel = Stub(ListSelectionModel) {
            getMinSelectionIndex() >> rowNum
        }
        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> true
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.selectionModel = selectionModel
        yassTable.model = Stub(TableModel) {
            getRowCount() >> TRAILING_SPACE_END_WORD_SONG.size()
        }

        when:
        yassTable.rollRight(splitCode as char, slitPos)

        then:
        verifyExpectation(yassTable, expectation)

        where:
        rowNum | splitCode | slitPos || expectation
        0      | '$'       | 0       || ['~', 'One', '~ ', 'two ', '_', 'three ', 'Four ', 'five six ']
        0      | ' '       | 1       || ['O ', 'ne', '~ ', 'two ', '_', 'three ', 'Four ', 'five six ']
        0      | '-'       | 1       || ['O', 'ne', '~ ', 'two ', '_', 'three ', 'Four ', 'five six ']
        1      | '$'       | 0       || ['One', '~', '~ ', 'two ', '_', 'three ', 'Four ', 'five six ']
        2      | '$'       | 0       || ['One', '~', '~ ', 'two ', '_', 'three ', 'Four ', 'five six ']
        2      | ' '       | 2       || ['One', '~ ', 'tw ', 'o ', '_', 'three ', 'Four ', 'five six ']
        3      | '$'       | 0       || ['One', '~ ', 'two', '~ ', '_', 'three ', 'Four ', 'five six ']
        5      | '$'       | 0       || ['One', '~ ', 'two ', 'three ', '_', '~ ', 'Four ', 'five six ']
        5      | '-'       | 2       || ['One', '~ ', 'two ', 'three ', '_', 'Fo', 'ur ', 'five six ']
        6      | '$'       | 0       || ['One', '~ ', 'two ', 'three ', '_', 'Four', '~ ', 'five six ']
        6      | ' '       | 2       || ['One', '~ ', 'two ', 'three ', '_', 'Four ', 'fi ', 've six ']
        6      | '-'       | 2       || ['One', '~ ', 'two ', 'three ', '_', 'Four ', 'fi', 've six ']
        7      | '$'       | 0       || ['One', '~ ', 'two ', 'three ', '_', 'Four ', 'five', 'six ']
    }

    // ----------

    def 'rollLeft applied to a song with leading spaces ending with ~. Legacy spacing'() {
        given:
        YassTableModel ytm = new YassTableModel()
        LEADING_SPACE_END_TILDE_SONG.each { row ->
            ytm.addRow(row)
        }

        and:
        I18.setDefaultLanguage()

        and:
        ListSelectionModel selectionModel = Stub(ListSelectionModel) {
            getMinSelectionIndex() >> rowNum
        }
        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> false
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.selectionModel = selectionModel
        yassTable.model = Stub(TableModel) {
            getRowCount() >> LEADING_SPACE_END_TILDE_SONG.size()
        }

        when:
        yassTable.rollLeft()

        then:
        verifyExpectation(yassTable, expectation)

        where:
        rowNum || expectation
        0      || ['One~', ' two', ' three', ' Four', '_', 'five', '~', '~']
        1      || ['One', ' two', ' three', ' Four', '_', 'five', '~', '~']
        2      || ['One', '~', ' two three', ' Four', '_', 'five', '~', '~']
        3      || ['One', '~', ' two', ' three Four', '_', 'five', '~', '~']
        5      || ['One', '~', ' two', ' three', '_', 'Four five', '~', '~']
        6      || ['One', '~', ' two', ' three', '_', 'Four', '~', '~']
        7      || ['One', '~', ' two', ' three', '_', 'Four', ' five', '~']
    }

    def 'rollLeft applied to a song with leading spaces ending with ~. New spacing'() {
        given:
        YassTableModel ytm = new YassTableModel()
        TRAILING_SPACE_END_TILDE_SONG.each { row ->
            ytm.addRow(row)
        }

        and:
        I18.setDefaultLanguage()

        and:
        ListSelectionModel selectionModel = Stub(ListSelectionModel) {
            getMinSelectionIndex() >> rowNum
        }
        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> true
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.selectionModel = selectionModel
        yassTable.model = Stub(TableModel) {
            getRowCount() >> TRAILING_SPACE_END_TILDE_SONG.size()
        }

        when:
        yassTable.rollLeft()

        then:
        verifyExpectation(yassTable, expectation)

        where:
        rowNum || expectation
        0      || ['One ', 'two ', 'three ', 'Four ', '_', 'five', '~', '~ ']
        1      || ['One ', 'two ', 'three ', 'Four ', '_', 'five', '~', '~ ']
        2      || ['One', '~ ', 'two three ', 'Four ', '_', 'five', '~', '~ ']
        3      || ['One', '~ ', 'two ', 'three Four ', '_', 'five', '~', '~ ']
        5      || ['One', '~ ', 'two ', 'three ', '_', 'Four five', '~', '~ ']
        6      || ['One', '~ ', 'two ', 'three ', '_', 'Four ', '~', '~ ']
        7      || ['One', '~ ', 'two ', 'three ', '_', 'Four ', 'five', '~ ']
    }

    def 'rollLeft applied to a song with leading spaces ending with word. Legacy spacing'() {
        given:
        YassTableModel ytm = new YassTableModel()
        LEADING_SPACE_END_WORD_SONG.each { row ->
            ytm.addRow(row)
        }

        and:
        I18.setDefaultLanguage()

        and:
        ListSelectionModel selectionModel = Stub(ListSelectionModel) {
            getMinSelectionIndex() >> rowNum
        }
        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> false
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.selectionModel = selectionModel
        yassTable.model = Stub(TableModel) {
            getRowCount() >> LEADING_SPACE_END_WORD_SONG.size()
        }

        when:
        yassTable.rollLeft()

        then:
        verifyExpectation(yassTable, expectation)

        where:
        rowNum || expectation
        0      || ['One~', ' two', ' three', ' Four', '_', 'five', ' six', '~']
        1      || ['One', ' two', ' three', ' Four', '_', 'five', ' six', '~']
        2      || ['One', '~', ' two three', ' Four', '_', 'five', ' six', '~']
        3      || ['One', '~', ' two', ' three Four', '_', 'five', ' six', '~']
        5      || ['One', '~', ' two', ' three', '_', 'Four five', ' six', '~']
        6      || ['One', '~', ' two', ' three', '_', 'Four', ' six', '~']
        7      || ['One', '~', ' two', ' three', '_', 'Four', ' five', ' six']
    }

    def 'rollLeft applied to a song with leading spaces ending with word. New spacing'() {
        given:
        YassTableModel ytm = new YassTableModel()
        TRAILING_SPACE_END_WORD_SONG.each { row ->
            ytm.addRow(row)
        }

        and:
        I18.setDefaultLanguage()

        and:
        ListSelectionModel selectionModel = Stub(ListSelectionModel) {
            getMinSelectionIndex() >> rowNum
        }

        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> true
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.selectionModel = selectionModel
        yassTable.model = Stub(TableModel) {
            getRowCount() >> TRAILING_SPACE_END_WORD_SONG.size()
        }

        when:
        yassTable.rollLeft()

        then:
        verifyExpectation(yassTable, expectation)

        where:
        rowNum || expectation
        0      || ['One ', 'two ', 'three ', 'Four ', '_', 'five ', 'six', '~ ']
        1      || ['One ', 'two ', 'three ', 'Four ', '_', 'five ', 'six', '~ ']
        2      || ['One', '~ ', 'two three ', 'Four ', '_', 'five ', 'six', '~ ']
        3      || ['One', '~ ', 'two ', 'three Four ', '_', 'five ', 'six', '~ ']
        5      || ['One', '~ ', 'two ', 'three ', '_', 'Four five ', 'six', '~ ']
        6      || ['One', '~ ', 'two ', 'three ', '_', 'Four ', 'six', '~ ']
        7      || ['One', '~ ', 'two ', 'three ', '_', 'Four ', 'five', '~ ']
    }

    // ----------

    def 'insertPageBreakAt applied to a song with leading spaces ending with ~. Legacy spacing'() {
        given:
        YassTableModel ytm = new YassTableModel()
        LEADING_SPACE_END_TILDE_SONG.each { row ->
            ytm.addRow(row)
        }

        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> false
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.model = Stub(TableModel) {
            getRowCount() >> TRAILING_SPACE_END_WORD_SONG.size()
        }
        when:
        yassTable.insertPageBreakAt(rowNum)

        then:
        verifyExpectation(yassTable, expectation)

        where:
        rowNum || expectation
        0      || ['One', '_', '~', ' two', ' three', '_', 'Four', ' five', '~']
        1      || ['One', '~', '_', 'two', ' three', '_', 'Four', ' five', '~']
        2      || ['One', '~', ' two', '_', 'three', '_', 'Four', ' five', '~']
        3      || ['One', '~', ' two', ' three', '_', 'Four', ' five', '~']
        5      || ['One', '~', ' two', ' three', '_', 'Four', '_', 'five', '~']
        6      || ['One', '~', ' two', ' three', '_', 'Four', ' five', '_', '~']
    }

    def 'insertPageBreakAt applied to a song with trailing spaces ending with ~. New spacing'() {
        given:
        YassTableModel ytm = new YassTableModel()
        TRAILING_SPACE_END_TILDE_SONG.each { row ->
            ytm.addRow(row)
        }

        and:
        I18.setDefaultLanguage()

        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> true
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.model = Stub(TableModel) {
            getRowCount() >> TRAILING_SPACE_END_TILDE_SONG.size()
        }

        when:
        yassTable.insertPageBreakAt(rowNum)

        then:
        verifyExpectation(yassTable, expectation)

        where:
        rowNum || expectation
        0      || ['One', '_', '~ ', 'two ', 'three ', '_', 'Four ', 'five', '~ ']
        1      || ['One', '~ ', '_', 'two ', 'three ', '_', 'Four ', 'five', '~ ']
        2      || ['One', '~ ', 'two ', '_', 'three ', '_', 'Four ', 'five', '~ ']
        3      || ['One', '~ ', 'two ', 'three ', '_', 'Four ', 'five', '~ ']
        5      || ['One', '~ ', 'two ', 'three ', '_', 'Four ', '_', 'five', '~ ']
        6      || ['One', '~ ', 'two ', 'three ', '_', 'Four ', 'five', '_', '~ ']
    }

    def 'togglePageBreak publishes capitalized lyrics in the table model event'() {
        given:
        I18.setDefaultLanguage()
        YassTableModel ytm = new YassTableModel()
        ytm.addRow(new YassRow(':', '0', '4', '10', 'hello '.replace(' ' as char, YassRow.SPACE)))
        ytm.addRow(new YassRow(':', '5', '4', '10', 'world '.replace(' ' as char, YassRow.SPACE)))
        ytm.addRow(new YassRow('E', '', '', '', ''))

        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> true
            getBooleanProperty('capitalize-rows') >> true
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.setModel(ytm)
        yassTable.setRowSelectionInterval(1, 1)
        List<String> lyricsSnapshots = []
        ytm.addTableModelListener { lyricsSnapshots << yassTable.getText() }

        when:
        yassTable.togglePageBreak()

        then:
        yassTable.getRowAt(2).getText() == 'World' + YassRow.SPACE
        lyricsSnapshots == ['hello\nWorld']
    }

    def 'getText retrieves the text of a song with Legacy spacing'() {
        given:
        YassTableModel ytm = new YassTableModel()
        LEADING_SPACE_END_TILDE_SONG.each { row ->
            ytm.addRow(row)
        }

        and:
        I18.setDefaultLanguage()

        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> false
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.model = Stub(TableModel) {
            getRowCount() >> LEADING_SPACE_END_TILDE_SONG.size()
        }

        when:
        String text = yassTable.getText()

        then:
        verifyExpectation(yassTable, ['One', '~', ' two', ' three', '_', 'Four', ' five', '~'])
        text == 'One-~ two three\nFour five-~'
    }

    def 'getText retrieves the text of a song with regular spacing'() {
        given:
        YassTableModel ytm = new YassTableModel()
        TRAILING_SPACE_END_TILDE_SONG.each { row ->
            ytm.addRow(row)
        }

        and:
        I18.setDefaultLanguage()

        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> true
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.model = Stub(TableModel) {
            getRowCount() >> TRAILING_SPACE_END_TILDE_SONG.size()
        }

        when:
        String text = yassTable.getText()

        then:
        verifyExpectation(yassTable, ['One', '~ ', 'two ', 'a ', '_', 'Four ', 'five', '~ '])
        text == 'One-~ two a\nFour five-~'
    }

    def 'insertRowsAt should insert rows'() {
        given:
        YassTableModel ytm = new YassTableModel()
        TRAILING_SPACE_END_TILDE_SONG.each { row ->
            ytm.addRow(row)
        }

        and:
        I18.setDefaultLanguage()

        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> true
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.setBPM(240d)
        yassTable.gap = 1000
        yassTable.model = Stub(TableModel) {
            getRowCount() >> TRAILING_SPACE_END_TILDE_SONG.size()
        }
        YassHyphenator hyphenator = new YassHyphenator(null)
        hyphenator.hyphenator = new Hyphenator()
        hyphenator.setFallbackHyphenations(['hello': 'he•lo'])
        yassTable.hyphenator = hyphenator
        yassTable.yassUtils = new YassUtils(hyphenator: hyphenator)

        when:
        yassTable.insertRowsAt(textToInsert, startRow, true)

        then:
        verifyExpectation(yassTable, expectation)

        where:
        textToInsert                  | startRow || expectation
        ':\t0\t4\t20\tHello '         | 7        || ['One', '~ ', 'two ', 'a ', '_', 'Hello ', '_', 'five', '~ ']
        ':\t0\t8\t20\tHello '         | 7        || ['One', '~ ', 'two ', 'a ', '_', 'Hello ', '_', '~ ']
        ':\t0\t2\t20\ta \n' +
                ':\t3\t2\t20\tb \n' +
                ':\t6\t2\t20\tc \n' +
                ':\t9\t3\t20\td \n' +
                ':\t14\t2\t20\te \n'  | 7        || ['One', '~ ', 'two ', 'a ', '_', 'a ', 'b ', 'c ', 'd ', 'e ']
        ':\t0\t2\t20\ta \n' +
                ':\t3\t2\t20\tb \n' +
                ':\t6\t2\t20\tc \n' +
                ':\t9\t3\t20\td \n' +
                ':\t14\t2\t20\te \n'  | 5        || ['One', '~ ', 'two ', 'a ', 'b ', 'c ', 'd ', 'e ', '_', 'Four ', 'five', '~ ']
        'hello b c d\ne f g h\n'      | 5        || ['One', '~ ', 'two ', 'he', 'lo ', 'b ', 'c ', 'd ', '_', 'e ', 'f ', 'g ', 'h ', '_', 'Four ', 'five', '~ ']
    }

    def 'blank insert note creates a tilde placeholder with inherited pitch and capped length'() {
        given:
        YassTable yassTable = tableForInsertedLyrics([
                note(':', 0, 4, 7, 'one '),
                note(':', 9, 4, 12, 'two ')
        ], [:])
        int firstNoteRow = firstRowIndex(yassTable) { it.note }
        yassTable.setRowSelectionInterval(firstNoteRow, firstNoteRow)

        when:
        invokeInsertNoteWithOptionalText(yassTable, '')

        then:
        noteTexts(yassTable) == ['one ', '~', 'two ']
        noteBeats(yassTable) == [0, 4, 9]
        noteLengths(yassTable) == [4, 3, 4]
        notePitches(yassTable) == [7, 7, 12]
        yassTable.getRowAt(yassTable.selectedRow).text == '~'
    }

    def 'blank insert note is blocked on comment rows'() {
        given:
        YassTable yassTable = tableForInsertedLyrics([
                new YassRow('#', 'COMMENT:', 'key=C', '', ''),
                note(':', 0, 4, 7, 'one ')
        ], [:])
        int commentRow = firstRowIndex(yassTable) { it.comment && it.headerCommentTag == 'COMMENT:' }
        int originalRowCount = yassTable.rowCount
        yassTable.setRowSelectionInterval(commentRow, commentRow)

        when:
        invokeInsertNoteWithOptionalText(yassTable, '')

        then:
        yassTable.rowCount == originalRowCount
        noteTexts(yassTable) == ['one ']
    }

    def 'blank insert note is blocked when no beat space remains before the next note'() {
        given:
        YassTable yassTable = tableForInsertedLyrics([
                note(':', 0, 4, 7, 'one '),
                note(':', 4, 4, 12, 'two ')
        ], [:])
        int firstNoteRow = firstRowIndex(yassTable) { it.note }
        yassTable.setRowSelectionInterval(firstNoteRow, firstNoteRow)

        when:
        invokeInsertNoteWithOptionalText(yassTable, '')

        then:
        noteTexts(yassTable) == ['one ', 'two ']
        noteBeats(yassTable) == [0, 4]
    }

    def 'insertLyricsWithVocalPitchAtBeat replaces the local note with aligned syllables inside the next-note gap'() {
        given:
        YassTable yassTable = tableForInsertedLyrics([
                note(':', 100, 4, 0, 'old '),
                note(':', 104, 4, 0, 'next ')
        ], ['hello': 'hel\u00ADlo'])
        List<PitchDetector.PitchData> pitchData = pitchFramesByBeat(yassTable, 100, [5, 5, 7, 7])

        when:
        def result = yassTable.insertLyricsWithVocalPitchAtBeat('hello', 100, pitchData)

        then:
        result.status() == YassTable.InsertedLyricsStatus.INSERTED
        noteTexts(yassTable) == ['hel', 'lo ', 'next ']
        noteBeats(yassTable) == [100, 102, 104]
        noteLengths(yassTable) == [1, 1, 4]
        notePitches(yassTable)[0..1] == [5, 7]
    }

    def 'insertLyricsWithVocalPitchAtBeat rejects text with more syllables than the local gap can hold'() {
        given:
        YassTable yassTable = tableForInsertedLyrics([
                note(':', 100, 4, 0, 'old '),
                note(':', 104, 4, 0, 'next ')
        ], [
                'hello'   : 'hel\u00ADlo',
                'darkness': 'dark\u00ADness'
        ])
        List<PitchDetector.PitchData> pitchData = pitchFramesByBeat(yassTable, 100, [5, 5, 7, 7])

        when:
        def result = yassTable.insertLyricsWithVocalPitchAtBeat('hello darkness', 100, pitchData)

        then:
        result.status() == YassTable.InsertedLyricsStatus.TOO_MANY_SYLLABLES
        result.syllableCount() == 4
        result.capacity() == 2
        noteTexts(yassTable) == ['old ', 'next ']
        noteBeats(yassTable) == [100, 104]
    }

    def 'insertLyricsWithVocalPitchAtBeat preserves a page break while using the first note after it as boundary'() {
        given:
        YassTable yassTable = tableForInsertedLyrics([
                note(':', 100, 1, 0, 'old '),
                new YassRow('-', '103', '', '', ''),
                note(':', 108, 4, 0, 'next ')
        ], ['hello': 'hel\u00ADlo'])
        List<PitchDetector.PitchData> pitchData = pitchFramesByBeat(yassTable, 100, [5, 5, 7, 7, 7, 7, 7, 7])

        when:
        def result = yassTable.insertLyricsWithVocalPitchAtBeat('hello', 100, pitchData)

        then:
        result.status() == YassTable.InsertedLyricsStatus.INSERTED
        pageBreakBeats(yassTable) == [103]
        noteTexts(yassTable) == ['hel', 'lo ', 'next ']
        noteBeats(yassTable) == [100, 102, 108]
    }

    def 'insertLyricsWithVocalPitchAtBeat rejects windows without usable pitch frames'() {
        given:
        YassTable yassTable = tableForInsertedLyrics([
                note(':', 100, 4, 0, 'old '),
                note(':', 104, 4, 0, 'next ')
        ], ['hello': 'hel\u00ADlo'])

        when:
        def result = yassTable.insertLyricsWithVocalPitchAtBeat('hello', 100, [])

        then:
        result.status() == YassTable.InsertedLyricsStatus.NO_USABLE_PITCH_DATA
        noteTexts(yassTable) == ['old ', 'next ']
        noteBeats(yassTable) == [100, 104]
    }

    def 'calculateNewGap should add a Gap'() {
        expect:
        YassTable.calculateNewGap(gap, currentGap) == expected

        where:
        currentGap | gap   || expected
        1234       | 10    || 1240
        1240       | 10    || 1250
        1234       | -10   || 1230
        1230       | -10   || 1220
        1234       | 1000  || 2234
        1234       | -1000 || 234
        234        | -1000 || 0
    }

    def 'calculateNewBpm should add a BPM'() {
        expect:
        YassTable.calculateNewBpm(bpm, currentBpm) == expected

        where:
        currentBpm | bpm   || expected
        123.45d    | 0.1d  || 123.5d
        123.4d     | 0.1d  || 123.5d
        123.45d    | -0.1d || 123.4d
        123.4d     | -0.1d || 123.3d
        123.4d     | 1d    || 124d
        123.45d    | 1d    || 124d
        123d       | 1d    || 124d
        123.4d     | -1d   || 123d
        123.45d    | -1d   || 123d
        123d       | -200d || 0
        299.9d     | 0.1d  || 300d
    }

    def 'checkShiftEndingConditions'() {
        given:
        YassTableModel ytm = new YassTableModel()
        song.each { row ->
            ytm.addRow(row)
        }

        and:
        I18.setDefaultLanguage()

        and:
        ListSelectionModel selectionModel = Stub(ListSelectionModel) {
            getSelectedIndices() >> {
                List<Integer> indices = []
                if (minRow < maxRow) {
                    (minRow..maxRow).each {
                        indices.add(it)
                    }
                } else {
                    indices = [minRow]
                }
                indices
            }
            getMinSelectionIndex() >> minRow
            getMaxSelectionIndex() >> maxRow
        }
        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> true
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.selectionModel = selectionModel
        yassTable.model = Stub(TableModel) {
            getRowCount() >> song.size()
        }

        when:
        boolean result = yassTable.checkShiftEndingConditions(toleft)

        then:
        result == expectation

        where:
        song                         | minRow | maxRow | toleft || expectation
        TRAILING_SPACE_END_WORD_SONG | 0      | 1      | false  || true
        TRAILING_SPACE_END_WORD_SONG | 0      | 2      | false  || false
        TRAILING_SPACE_END_WORD_SONG | 0      | 0      | false  || false
        TRAILING_SPACE_END_WORD_SONG | 2      | 3      | false  || false
        TILDE_SONG                   | 0      | 2      | true   || false
        TRAILING_SPACE_END_WORD_SONG | 4      | 5      | false  || false
        TRAILING_SPACE_END_WORD_SONG | 5      | 6      | false  || false
    }

    def 'shiftEnding'() {
        given:
        YassTableModel ytm = new YassTableModel()
        song.each { row ->
            ytm.addRow(row)
        }

        and:
        I18.setDefaultLanguage()

        and:
        ListSelectionModel selectionModel = Stub(ListSelectionModel) {
            getSelectedIndices() >> {
                List<Integer> indices = []
                if (minRow < maxRow) {
                    (minRow..maxRow).each {
                        indices.add(it)
                    }
                } else {
                    indices = [minRow]
                }
                indices
            }
            getMinSelectionIndex() >> minRow
            getMaxSelectionIndex() >> maxRow
        }
        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> true
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.selectionModel = selectionModel
        yassTable.model = Stub(TableModel) {
            getRowCount() >> song.size()
        }

        when:
        yassTable.shiftEnding()

        then:
        verifyExpectation(yassTable, expectation)

        where:
        song       | minRow | maxRow || expectation
        TILDE_SONG | 0      | 3      || ['On', '~', '~', '~e.', 'SKIPALL']
        TILDE_SONG | 9      | 11     || ['One', '~', '~', '~.', '_', 'T', '~', '~est', '_', 'Te', '~', '~st']
    }


    def 'shiftEndingLeft'() {
        given:
        YassTableModel ytm = new YassTableModel()
        song.each { row ->
            ytm.addRow(row)
        }

        and:
        I18.setDefaultLanguage()

        and:
        ListSelectionModel selectionModel = Stub(ListSelectionModel) {
            getSelectedIndices() >> {
                List<Integer> indices = []
                if (minRow < maxRow) {
                    (minRow..maxRow).each {
                        indices.add(it)
                    }
                } else {
                    indices = [minRow]
                }
                indices
            }
            getMinSelectionIndex() >> minRow
            getMaxSelectionIndex() >> maxRow
        }
        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> true
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.selectionModel = selectionModel
        yassTable.model = Stub(TableModel) {
            getRowCount() >> song.size()
        }

        when:
        yassTable.shiftEndingLeft()

        then:
        verifyExpectation(yassTable, expectation)

        where:
        song       | minRow | maxRow || expectation
        TILDE_SONG | 5      | 7      || ['One', '~', '~', '~.', '_', 'Te', '~', '~st', '_', 'Tes', '~', 't']
        TILDE_SONG | 9      | 11     || ['One', '~', '~', '~.', '_', 'T', '~', '~est', '_', 'Test', '~', '~']
    }

    private boolean verifyExpectation(YassTable yassTable, List<String> expectation) {
        int offset = 0
        YassRow yassRow = yassTable.getRowAt(offset)
        while (yassRow.isComment()) {
            yassRow = yassTable.getRowAt(++offset)
        }
        expectation.eachWithIndex { String entry, int i ->
            if ('SKIPALL'.equals(entry)) {
                return true
            } else if ('SKIP'.equals(entry)) {
                assert true
            } else {
                yassRow = yassTable.getRowAt(i + offset)
                if (yassRow.isPageBreak()) {
                    assert entry == '_'
                } else {
                    String actual = yassRow.getText().replace(YassRow.SPACE, ' ' as char)
                    assert actual == entry
                }
            }
        }
    }

    private YassTable tableWithNotes() {
        I18.setDefaultLanguage()
        YassTableModel ytm = new YassTableModel()
        ytm.addRow(new YassRow(':', '0', '4', '10', 'One '))
        ytm.addRow(new YassRow(':', '4', '4', '10', 'two '))
        ytm.addRow(new YassRow(':', '8', '4', '10', 'three '))
        ytm.addRow(new YassRow('E', '', '', '', ''))
        YassTable yassTable = new YassTable(ytm, Stub(YassProperties))
        yassTable.setModel(ytm)
        yassTable
    }

    private YassTable tableWithSingleNote(int beat, int length, int pitch, String text,
                                          String hyphenated = null) {
        I18.setDefaultLanguage()
        YassTableModel ytm = new YassTableModel()
        ytm.addRow(new YassRow(':', beat as String, length as String, pitch as String,
                text.replace(' ' as char, YassRow.SPACE)))
        ytm.addRow(new YassRow('E', '', '', '', ''))
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> true
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.setModel(ytm)
        yassTable.setBPM(236d)
        yassTable.setAutoCorrect(Stub(YassAutoCorrect) {
            isTouchingSyllables() >> true
        })
        yassTable.setHyphenator(Stub(YassHyphenator) {
            hyphenateWord(_ as String) >> { String word -> hyphenated == null ? word : hyphenated }
        })
        yassTable.setRowSelectionInterval(1, 1)
        yassTable
    }

    private YassTable tableWithTwoNotes(String firstText, String secondText) {
        I18.setDefaultLanguage()
        YassTableModel ytm = new YassTableModel()
        ytm.addRow(new YassRow(':', '0', '4', '10', firstText.replace(' ' as char, YassRow.SPACE)))
        ytm.addRow(new YassRow(':', '5', '4', '10', secondText.replace(' ' as char, YassRow.SPACE)))
        ytm.addRow(new YassRow('E', '', '', '', ''))
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> true
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.setModel(ytm)
        yassTable
    }

    private YassTable tableForGoldenSuggestions(List<YassRow> rows, boolean uncommonSpacingAfter = true) {
        I18.setDefaultLanguage()
        YassTableModel ytm = new YassTableModel()
        rows.each { ytm.addRow(it) }
        ytm.addRow(new YassRow('E', '', '', '', ''))
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> uncommonSpacingAfter
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.setModel(ytm)
        yassTable
    }

    private static YassRow note(String type, int beat, int length, int pitch, String text) {
        new YassRow(type, beat as String, length as String, pitch as String,
                text.replace(' ' as char, YassRow.SPACE))
    }

    private YassTable tableForInsertedLyrics(List<YassRow> rows, Map<String, String> hyphenations) {
        I18.setDefaultLanguage()
        YassTableModel ytm = new YassTableModel()
        rows.each { ytm.addRow(it) }
        ytm.addRow(new YassRow('E', '', '', '', ''))
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> true
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.setModel(ytm)
        yassTable.setBPM(60d)
        yassTable.gap = 0
        YassHyphenator hyphenator = Stub(YassHyphenator) {
            hyphenateWord(_ as String) >> { String word -> hyphenations.get(word, word) }
        }
        yassTable.setHyphenator(hyphenator)
        yassTable.yassUtils = new YassUtils(hyphenator: hyphenator)
        yassTable.yassUtils.setDefaultLength(1)
        yassTable.yassUtils.setSpacingAfter(true)
        yassTable
    }

    private static void invokeInsertNoteWithOptionalText(YassTable table, String noteText) {
        def method = YassTable.getDeclaredMethod('insertNoteWithOptionalText', String)
        method.accessible = true
        method.invoke(table, noteText)
    }

    private static int firstRowIndex(YassTable table, Closure<Boolean> predicate) {
        for (int index = 0; index < table.rowCount; index++) {
            if (predicate.call(table.getRowAt(index))) {
                return index
            }
        }
        throw new AssertionError('No matching row found')
    }

    private static List<String> noteTexts(YassTable table) {
        (0..<table.rowCount)
                .collect { table.getRowAt(it) }
                .findAll { it?.note }
                .collect { it.text.replace(YassRow.SPACE, ' ' as char) }
    }

    private static List<Integer> noteBeats(YassTable table) {
        (0..<table.rowCount)
                .collect { table.getRowAt(it) }
                .findAll { it?.note }
                .collect { it.beatInt }
    }

    private static List<Integer> noteLengths(YassTable table) {
        (0..<table.rowCount)
                .collect { table.getRowAt(it) }
                .findAll { it?.note }
                .collect { it.lengthInt }
    }

    private static List<Integer> notePitches(YassTable table) {
        (0..<table.rowCount)
                .collect { table.getRowAt(it) }
                .findAll { it?.note }
                .collect { it.heightInt }
    }

    private static List<String> noteTypes(YassTable table) {
        (0..<table.rowCount)
                .collect { table.getRowAt(it) }
                .findAll { it?.note }
                .collect { it.type }
    }

    private static List<Integer> pageBreakBeats(YassTable table) {
        (0..<table.rowCount)
                .collect { table.getRowAt(it) }
                .findAll { it?.pageBreak }
                .collect { it.beatInt }
    }

    private static List<PitchDetector.PitchData> pitchFramesByBeat(YassTable table, int startBeat, List<Integer> pitches) {
        List<PitchDetector.PitchData> frames = []
        pitches.eachWithIndex { int pitch, int index ->
            double time = (table.beatToMs(startBeat + index) + 1d) / 1000d
            frames.add(new PitchDetector.PitchData(time as float, pitch, 'A', 440d))
        }
        frames
    }

    private static List<YassRow> initSong1() {
        List<YassRow> rows = [
                new YassRow(':', '0', '5', '10', 'One'),
                new YassRow(':', '6', '5', '10', '~'),
                new YassRow(':', '12', '5', '10', ' two'),
                new YassRow(':', '18', '5', '10', ' three'),
                new YassRow('-', '24', '', '', ''),
                new YassRow(':', '26', '5', '10', 'Four'),
                new YassRow(':', '32', '5', '10', ' five'),
                new YassRow(':', '38', '5', '10', '~'),
                new YassRow('E', '', '', '', '')
        ]
        rows.each { row ->
            row.setText(row.getText().replace(' ' as char, YassRow.SPACE))
        }
        rows
    }

    private static List<YassRow> initSong2() {
        List<YassRow> rows = [
                new YassRow(':', '0', '5', '10', 'One'),
                new YassRow(':', '6', '5', '10', '~ '),
                new YassRow(':', '12', '5', '10', 'two '),
                new YassRow(':', '18', '5', '10', 'a '),
                new YassRow('-', '124', '', '', ''),
                new YassRow(':', '126', '5', '10', 'Four '),
                new YassRow(':', '132', '5', '10', 'five'),
                new YassRow(':', '138', '5', '10', '~ '),
                new YassRow('E', '', '', '', '')
        ]
        rows.each { row ->
            row.setText(row.getText().replace(' ' as char, YassRow.SPACE))
        }
        rows
    }

    private static List<YassRow> initSong3() {
        List<YassRow> rows = [
                new YassRow(':', '0', '5', '10', 'One'),
                new YassRow(':', '6', '5', '10', '~'),
                new YassRow(':', '12', '5', '10', ' two'),
                new YassRow(':', '18', '5', '10', ' three'),
                new YassRow('-', '24', '', '', ''),
                new YassRow(':', '26', '5', '10', 'Four'),
                new YassRow(':', '32', '5', '10', ' five'),
                new YassRow(':', '38', '5', '10', ' six'),
                new YassRow('E', '', '', '', '')
        ]
        rows.each { row ->
            row.setText(row.getText().replace(' ' as char, YassRow.SPACE))
        }
        rows
    }

    private static List<YassRow> initSong4() {
        List<YassRow> rows = [
                new YassRow(':', '0', '5', '10', 'One'),
                new YassRow(':', '6', '5', '10', '~ '),
                new YassRow(':', '12', '5', '10', 'two '),
                new YassRow(':', '18', '5', '10', 'three '),
                new YassRow('-', '24', '', '', ''),
                new YassRow(':', '26', '5', '10', 'Four '),
                new YassRow(':', '32', '5', '10', 'five '),
                new YassRow(':', '38', '5', '10', 'six '),
                new YassRow('E', '', '', '', '')
        ]
        rows.each { row ->
            row.setText(row.getText().replace(' ' as char, YassRow.SPACE))
        }
        rows
    }

    private static List<YassRow> initSong5() {
        List<YassRow> rows = [
                new YassRow(':', '0', '5', '10', 'One'),
                new YassRow(':', '6', '5', '10', '~'),
                new YassRow(':', '12', '5', '10', '~'),
                new YassRow(':', '18', '5', '10', '~.'),
                new YassRow('-', '24', '', '', ''),
                new YassRow(':', '26', '5', '10', 'T'),
                new YassRow(':', '32', '5', '10', '~'),
                new YassRow(':', '38', '5', '10', '~est'),
                new YassRow('-', '44', '', '', ''),
                new YassRow(':', '46', '5', '10', 'Tes'),
                new YassRow(':', '52', '5', '10', '~'),
                new YassRow(':', '58', '5', '10', 't'),
                new YassRow('E', '', '', '', '')
        ]
        rows.each { row ->
            row.setText(row.getText().replace(' ' as char, YassRow.SPACE))
        }
        rows
    }
}
