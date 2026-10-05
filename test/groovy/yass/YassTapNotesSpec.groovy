package yass

import spock.lang.Specification

class YassTapNotesSpec extends Specification {

    def "recording from the first song note sets gap and anchors the first tapped note at beat zero"() {
        given:
        YassTable table = tableWithNotes()
        table.setGap(1000)
        table.setBPM(120)
        int firstNoteRow = noteRows(table)[0]
        table.setRowSelectionInterval(firstNoteRow, firstNoteRow)
        Vector<Long> taps = new Vector<>()
        taps.add(2_200_000L)
        taps.add(2_700_000L)
        taps.add(3_200_000L)
        taps.add(3_700_000L)

        when:
        int processed = YassTapNotes.evaluateTaps(table, taps, Collections.emptyList(), Timebase.NORMAL, firstNoteRow)

        then:
        processed == 2
        table.getGap() == 2200
        table.getCommentRow('GAP:').getHeaderComment() == '2200'
        List<YassRow> notes = noteRows(table).collect { table.getRowAt(it) }
        notes[0].getBeatInt() == 0
        notes[0].getLengthInt() == 4
        notes[1].getBeatInt() == 8
        notes[1].getLengthInt() == 4
        taps.isEmpty()
    }

    def "recording from a later song note keeps absolute timing relative to gap"() {
        given:
        YassTable table = tableWithNotes()
        table.setGap(1000)
        table.setBPM(120)
        int secondNoteRow = noteRows(table)[1]
        table.setRowSelectionInterval(secondNoteRow, secondNoteRow)
        Vector<Long> taps = new Vector<>()
        taps.add(2_200_000L)
        taps.add(2_700_000L)

        when:
        int processed = YassTapNotes.evaluateTaps(table, taps, Collections.emptyList(), Timebase.NORMAL, secondNoteRow)

        then:
        processed == 1
        table.getGap() == 1000
        YassRow secondNote = table.getRowAt(secondNoteRow)
        secondNote.getBeatInt() == 10
        secondNote.getLengthInt() == 4
    }

    private YassTable tableWithNotes() {
        I18.setDefaultLanguage()
        YassTableModel model = new YassTableModel()
        model.addRow(new YassRow(':', '12', '4', '10', 'One '))
        model.addRow(new YassRow(':', '20', '4', '10', 'two '))
        model.addRow(new YassRow('E', '', '', '', ''))
        YassTable table = new YassTable(model, Stub(YassProperties))
        table.setModel(model)
        table
    }

    private static List<Integer> noteRows(YassTable table) {
        (0..<table.getRowCount()).findAll { table.getRowAt(it).isNote() }
    }
}
