package yass

import spock.lang.Specification
import yass.autocorrect.YassAutoCorrect
import yass.autocorrect.YassAutoCorrectApostrophes
import yass.autocorrect.YassAutoCorrectLineCapitalization
import yass.autocorrect.YassAutoCorrectUncommonSpacing

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

    def 'safe correction policy includes focused correctors but excludes golden distribution'() {
        given:
        YassAutoCorrect autoCorrect = new YassAutoCorrect()

        expect:
        autoCorrect.isAutoCorrectionSafe(YassRow.UNCOMMON_SPACING)
        autoCorrect.isAutoCorrectionSafe(YassRow.LOWERCASE_ROWSTART)
        autoCorrect.isAutoCorrectionSafe(YassRow.BORING_APOSTROPHE)
        !autoCorrect.isAutoCorrectionSafe(YassRow.UNCOMMON_GOLDEN)
    }

    def 'apostrophe corrector respects the typographic apostrophes setting for notes and comments'() {
        given:
        YassTable table = tableWithRows([
                new YassRow('#', 'TITLE:', "I'm Here", '', ''),
                note(':', 0, 4, 0, "won't ")
        ])

        when:
        boolean disabledChanged = new YassAutoCorrectApostrophes(properties(false, true))
                .autoCorrect(table, 1, table.rowCount)

        then:
        !disabledChanged
        table.getRowAt(1).text.contains("'")

        when:
        boolean noteChanged = new YassAutoCorrectApostrophes(properties(true, true))
                .autoCorrect(table, 1, table.rowCount)
        boolean commentChanged = new YassAutoCorrectApostrophes(properties(true, true))
                .autoCorrect(table, 0, table.rowCount)

        then:
        noteChanged
        commentChanged
        !table.getRowAt(1).text.contains("'")
        !table.getRowAt(0).headerComment.contains("'")
    }

    def 'line capitalization corrector requires the setting and a preceding page break'() {
        given:
        YassTable table = tableWithRows([
                note(':', 0, 4, 0, 'first '),
                new YassRow('-', '5', '', '', ''),
                note(':', 6, 4, 0, 'second ')
        ])

        when:
        boolean disabledChanged = new YassAutoCorrectLineCapitalization(properties(false, true))
                .autoCorrect(table, 2, table.rowCount)

        then:
        !disabledChanged
        table.getRowAt(2).text == 'second' + YassRow.SPACE

        when:
        boolean changed = new YassAutoCorrectLineCapitalization(properties(true, true))
                .autoCorrect(table, 2, table.rowCount)

        then:
        changed
        table.getRowAt(2).text == 'Second' + YassRow.SPACE
    }

    def 'uncommon spacing corrector follows trailing and legacy spacing modes'() {
        given:
        YassTable trailingTable = tableWithRows([
                note(':', 0, 4, 0, ' one'),
                note(':', 5, 4, 0, 'two ')
        ])
        YassTable legacyTable = tableWithRows([
                note(':', 0, 4, 0, 'one '),
                note(':', 5, 4, 0, 'two')
        ])

        when:
        boolean trailingChanged = new YassAutoCorrectUncommonSpacing(properties(false, true))
                .autoCorrect(trailingTable, 0, trailingTable.rowCount)
        boolean legacyChanged = new YassAutoCorrectUncommonSpacing(properties(false, false))
                .autoCorrect(legacyTable, 0, legacyTable.rowCount)

        then:
        trailingChanged
        trailingTable.getRowAt(0).text == 'one'
        legacyChanged
        legacyTable.getRowAt(0).text == 'one'
        legacyTable.getRowAt(1).text == "${YassRow.SPACE}two"
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

    private YassTable tableWithRows(List<YassRow> rows) {
        I18.setDefaultLanguage()
        YassTableModel model = new YassTableModel()
        rows.each { model.addRow(it) }
        model.addRow(new YassRow('E', '', '', '', ''))
        YassTable table = new YassTable(model, Stub(YassProperties))
        table.setModel(model)
        table
    }

    private YassProperties properties(boolean enabled, boolean spacingAfter) {
        Stub(YassProperties) {
            getBooleanProperty(_ as String) >> enabled
            isUncommonSpacingAfter() >> spacingAfter
        }
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
