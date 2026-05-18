package yass

import spock.lang.Specification

import javax.swing.JViewport
import java.awt.image.BufferedImage
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

    def 'resize refresh skips rendering while no active table is attached'() {
        given:
        def sheet = new YassSheet()
        def viewport = new JViewport()
        viewport.setExtentSize(new Dimension(400, 240))
        viewport.setSize(400, 240)
        viewport.setView(sheet)
        sheet.setSize(400, 240)
        setPrivateField(sheet, 'image', new BufferedImage(400, 240, BufferedImage.TYPE_INT_ARGB))

        when:
        sheet.refreshImage()

        then:
        noExceptionThrown()
    }

    def 'undo restores absolute pitch window while keeping the current viewport'() {
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
        table.addUndo()

        when:
        sheet.setAbsolutePitchWindow(0, 36)
        sheet.setViewPosition(new Point(654, (int) sheet.getViewPosition().y))
        int currentViewX = sheet.getViewPosition().x
        table.addUndo()
        table.undoRows()

        then:
        sheet.getAbsolutePitchWindowStart() == originalWindowStart
        sheet.getAbsolutePitchWindowSpan() == originalWindowSpan
        sheet.getViewPosition().x == currentViewX
    }

    def 'recording rolling mode temporarily forces absolute pitch view and restores relative view afterwards'() {
        given:
        def sheet = new YassSheet()
        sheet.enablePan(true)
        sheet.setAbsolutePitchViewEnabled(false)

        expect:
        !sheet.isAbsolutePitchViewEnabled()
        sheet.isPanEnabled()

        when:
        sheet.setRecordingRollingMode(true)

        then:
        sheet.isAbsolutePitchViewEnabled()
        sheet.isPanEnabled()

        when:
        sheet.setRecordingRollingMode(false)

        then:
        !sheet.isAbsolutePitchViewEnabled()
        sheet.isPanEnabled()
    }

    private static void setPrivateField(Object target, String fieldName, Object value) {
        def field = target.class.getDeclaredField(fieldName)
        field.accessible = true
        field.set(target, value)
    }
}
