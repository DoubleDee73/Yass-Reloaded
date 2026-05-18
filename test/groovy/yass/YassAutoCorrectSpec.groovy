package yass

import spock.lang.Specification
import yass.autocorrect.YassAutoCorrect

class YassAutoCorrectSpec extends Specification {

    def 'uncommon golden correction is supported only when golden duration is below target'() {
        given:
        YassAutoCorrect autoCorrect = new YassAutoCorrect()

        expect:
        !autoCorrect.autoCorrectionSupported(YassRow.UNCOMMON_GOLDEN)
        autoCorrect.autoCorrectionSupported(tableForGoldenCorrection(0, 6), YassRow.UNCOMMON_GOLDEN)
        !autoCorrect.autoCorrectionSupported(tableForGoldenCorrection(6, 6), YassRow.UNCOMMON_GOLDEN)
        !autoCorrect.autoCorrectionSupported(tableForGoldenCorrection(8, 6), YassRow.UNCOMMON_GOLDEN)
    }

    def 'uncommon golden correction suggests additional golden notes when below target'() {
        given:
        YassAutoCorrect autoCorrect = new YassAutoCorrect()
        YassTable table = tableForGoldenCorrection(0, 6)

        when:
        boolean changed = autoCorrect.autoCorrect(table, false, YassRow.UNCOMMON_GOLDEN)

        then:
        changed
        noteTypes(table) == ['*', '*', '*']
    }

    def 'uncommon golden correction does nothing when current duration already reaches target'() {
        given:
        YassAutoCorrect autoCorrect = new YassAutoCorrect()
        YassTable table = tableForGoldenCorrection(8, 6)

        when:
        boolean changed = autoCorrect.autoCorrect(table, false, YassRow.UNCOMMON_GOLDEN)

        then:
        !changed
        noteTypes(table) == [':', ':', ':']
    }

    private YassTable tableForGoldenCorrection(int currentGoldenDuration, int idealGoldenBeats) {
        I18.setDefaultLanguage()
        YassTableModel model = new YassTableModel()
        model.addRow(note(':', 0, 2, 0, 'One '))
        model.addRow(note(':', 3, 2, 4, 'two '))
        model.addRow(note(':', 6, 2, 11, 'three '))
        model.addRow(new YassRow('E', '', '', '', ''))

        YassProperties properties = Stub(YassProperties) {
            isUncommonSpacingAfter() >> true
        }
        YassTable table = new YassTable(model, properties)
        table.setModel(model)
        table.setGoldenPoints(0, 1250, 250, currentGoldenDuration, idealGoldenBeats,
                (idealGoldenBeats - currentGoldenDuration) as String)
        table
    }

    private static List<String> noteTypes(YassTable table) {
        (0..<table.rowCount)
                .collect { table.getRowAt(it) }
                .findAll { it?.note }
                .collect { it.type }
    }

    private static YassRow note(String type, int beat, int length, int pitch, String text) {
        new YassRow(type, beat as String, length as String, pitch as String,
                text.replace(' ' as char, YassRow.SPACE))
    }
}
