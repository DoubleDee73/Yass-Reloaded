package yass

import spock.lang.Specification

import javax.swing.JViewport
import java.awt.Dimension
import java.awt.Point

class YassSheetSpec extends Specification {

    def 'returns no visible note when rectangles are not initialized yet'() {
        given:
        def sheet = new YassSheet()

        expect:
        sheet.firstVisibleNote() == -1
        sheet.nextNote(0) == -1
        sheet.firstVisibleNote(0) == -1
        sheet.nextNote(0, 0) == -1
    }

    def 'undo restores absolute pitch window instead of falling back to a different octave'() {
        given:
        I18.setDefaultLanguage()

        and:
        def table = new YassTable()
        table.addRow(new YassRow(':', '0', '8', '-5', 'One '))
        table.addRow(new YassRow(':', '8', '8', '7', 'Two '))
        table.addRow(new YassRow('E', '', '', '', ''))
        def sheet = new YassSheet()
        def viewport = new JViewport()
        viewport.setExtentSize(new Dimension(1200, 420))
        viewport.setSize(1200, 420)
        viewport.setView(sheet)
        sheet.setSize(3000, 420)
        sheet.addTable(table)
        table.setSheet(sheet)
        sheet.setActiveTable(table)

        and:
        table.setRowSelectionInterval(0, 1)
        sheet.setAbsolutePitchViewEnabled(true)
        sheet.setAbsolutePitchWindow(12, 12)
        int originalWindowStart = sheet.getAbsolutePitchWindowStart()
        int originalWindowSpan = sheet.getAbsolutePitchWindowSpan()
        sheet.setViewPosition(new Point(321, (int) sheet.getViewPosition().y))
        int originalViewX = sheet.getViewPosition().x
        table.addUndo()

        when:
        sheet.setAbsolutePitchWindow(0, 36)
        sheet.setViewPosition(new Point(654, (int) sheet.getViewPosition().y))
        table.addUndo()
        table.undoRows()

        then:
        sheet.getAbsolutePitchWindowStart() == originalWindowStart
        sheet.getAbsolutePitchWindowSpan() == originalWindowSpan
        sheet.getViewPosition().x == originalViewX
    }
}
