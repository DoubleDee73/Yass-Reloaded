package yass

import spock.lang.Specification
import yass.analysis.PitchDetector

import javax.swing.table.TableModel

class AlignToMelodySpec extends Specification {

    def 'alignToMelody trims weak trailing beats more aggressively than the main sung body'() {
        given:
        YassTableModel ytm = new YassTableModel()
        def note = new YassRow(':', '0', '4', '10', 'Test ')
        ytm.addRow(note)
        ytm.addRow(new YassRow('E', '', '', '', ''))

        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> true
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.setBPM(15d)
        yassTable.gap = 0
        yassTable.model = Stub(TableModel) {
            getRowCount() >> 2
        }
        def pitchData = [
                pd(0.10f, 10), pd(0.20f, 10), pd(0.30f, 10), pd(0.40f, 10), pd(0.50f, 10), pd(0.60f, 10),
                pd(1.10f, 10), pd(1.20f, 10), pd(1.30f, 10), pd(1.40f, 10), pd(1.50f, 10), pd(1.60f, 10),
                pd(2.10f, 10), pd(2.20f, 10), pd(2.30f, 10), pd(2.40f, 10), pd(2.50f, 10), pd(2.60f, 10),
                pd(3.10f, 10), pd(3.20f, 10)
        ]

        when:
        yassTable.alignToMelody([note], pitchData, YassTable.AlignToMelodyContext.manual())

        then:
        note.getBeatInt() == 0
        note.getLengthInt() == 3
    }

    def 'alignToMelody trims low-energy trailing beats even when pitch frame count stays high'() {
        given:
        YassTableModel ytm = new YassTableModel()
        def note = new YassRow(':', '0', '4', '10', 'Test ')
        ytm.addRow(note)
        ytm.addRow(new YassRow('E', '', '', '', ''))

        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> true
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.setBPM(15d)
        yassTable.gap = 0
        yassTable.model = Stub(TableModel) {
            getRowCount() >> 2
        }
        def pitchData = [
                pd(0.10f, 10, 1.0d), pd(0.20f, 10, 1.0d), pd(0.30f, 10, 1.0d), pd(0.40f, 10, 1.0d),
                pd(1.10f, 10, 1.0d), pd(1.20f, 10, 1.0d), pd(1.30f, 10, 1.0d), pd(1.40f, 10, 1.0d),
                pd(2.10f, 10, 1.0d), pd(2.20f, 10, 1.0d), pd(2.30f, 10, 1.0d), pd(2.40f, 10, 1.0d),
                pd(3.10f, 10, 0.08d), pd(3.20f, 10, 0.08d), pd(3.30f, 10, 0.08d), pd(3.40f, 10, 0.08d)
        ]

        when:
        yassTable.alignToMelody([note], pitchData, YassTable.AlignToMelodyContext.manual())

        then:
        note.getLengthInt() == 3
    }

    def 'alignToMelody does not expand left into a weak onset tail that is much quieter than the kept note body'() {
        given:
        YassTableModel ytm = new YassTableModel()
        def note = new YassRow(':', '21', '9', '10', 'days·')
        ytm.addRow(note)
        ytm.addRow(new YassRow('E', '', '', '', ''))

        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> true
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.setBPM(15d)
        yassTable.gap = 0
        yassTable.model = Stub(TableModel) {
            getRowCount() >> 2
        }
        def pitchData = []
        pitchData.addAll(framesForBeat(20, 11, -2, 0.0133d))
        pitchData.addAll(framesForBeat(21, 10, -2, 0.0552d))
        pitchData.addAll(framesForBeat(22, 11, -2, 0.0665d))
        pitchData.addAll(framesForBeat(23, 11, -2, 0.0491d))
        pitchData.addAll(framesForBeat(24, 10, -2, 0.0391d))
        pitchData.addAll(framesForBeat(25, 11, -2, 0.0358d))
        pitchData.addAll(framesForBeat(26, 10, -2, 0.0304d))
        pitchData.addAll(framesForBeat(27, 11, -2, 0.0280d))
        pitchData.addAll(framesForBeat(28, 11, -2, 0.0353d))
        pitchData.addAll(framesForBeat(29, 10, -2, 0.0357d))
        pitchData.addAll(framesForBeat(30, 11, 3, 0.0297d))

        when:
        yassTable.alignToMelody([note], pitchData, YassTable.AlignToMelodyContext.manual())

        then:
        note.getBeatInt() == 21
        note.getLengthInt() == 9
    }

    def 'alignToMelody evaluates duration left to right and stops at a real internal voice gap instead of bridging it'() {
        given:
        YassTableModel ytm = new YassTableModel()
        def note = new YassRow(':', '203', '18', '10', 'I·')
        ytm.addRow(note)
        ytm.addRow(new YassRow('E', '', '', '', ''))

        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> true
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.setBPM(15d)
        yassTable.gap = 0
        yassTable.model = Stub(TableModel) {
            getRowCount() >> 2
        }
        def pitchData = []
        pitchData.addAll(framesForBeat(203, 11, 0, 0.0659d))
        pitchData.addAll(framesForBeat(204, 10, 0, 0.0940d))
        pitchData.addAll(framesForBeat(205, 11, 0, 0.0852d))
        pitchData.addAll(framesForBeat(206, 11, 0, 0.0684d))
        pitchData.addAll(framesForBeat(207, 10, -7, 0.0768d))
        pitchData.addAll(framesForBeat(208, 11, -7, 0.0854d))
        pitchData.addAll(framesForBeat(209, 10, -7, 0.1039d))
        pitchData.addAll(framesForBeat(210, 11, -7, 0.0806d))
        pitchData.addAll(framesForBeat(211, 11, -7, 0.0125d))
        pitchData.addAll(framesForBeat(212, 10, -7, 0.0259d))
        pitchData.addAll(framesForBeat(213, 11, 0, 0.0265d))
        pitchData.addAll(framesForBeat(214, 10, -7, 0.0701d))
        pitchData.addAll(framesForBeat(215, 11, -7, 0.0813d))
        pitchData.addAll(framesForBeat(216, 11, -7, 0.0192d))
        pitchData.addAll(framesForBeat(217, 10, -7, 0.0579d))
        pitchData.addAll(framesForBeat(218, 11, -7, 0.0891d))
        pitchData.addAll(framesForBeat(219, 10, -7, 0.0865d))
        pitchData.addAll(framesForBeat(220, 11, -7, 0.0935d))
        pitchData.addAll(framesForBeat(221, 10, -7, 0.0976d))

        when:
        yassTable.alignToMelody([note], pitchData, YassTable.AlignToMelodyContext.manual())

        then:
        note.getBeatInt() == 203
        note.getLengthInt() == 8
    }

    def 'alignToMelody moves octave-shifted notes onto the actually detected pitch line'() {
        given:
        YassTableModel ytm = new YassTableModel()
        def note = new YassRow(':', '0', '4', '10', 'Test ')
        ytm.addRow(note)
        ytm.addRow(new YassRow('E', '', '', '', ''))

        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> true
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.setBPM(15d)
        yassTable.gap = 0
        yassTable.model = Stub(TableModel) {
            getRowCount() >> 2
        }
        def pitchData = [
                pd(0.10f, -2), pd(0.20f, -2), pd(0.30f, -2), pd(0.40f, -2),
                pd(1.10f, -2), pd(1.20f, -2), pd(1.30f, -2), pd(1.40f, -2),
                pd(2.10f, -2), pd(2.20f, -2), pd(2.30f, -2), pd(2.40f, -2),
                pd(3.10f, -2), pd(3.20f, -2), pd(3.30f, -2), pd(3.40f, -2)
        ]

        when:
        yassTable.alignToMelody([note], pitchData, YassTable.AlignToMelodyContext.manual())

        then:
        note.getHeightInt() == -2
    }

    def 'alignNoteLength keeps pitch while adjusting note timing to the detected melody'() {
        given:
        YassTableModel ytm = new YassTableModel()
        def note = new YassRow(':', '0', '4', '10', 'Test ')
        ytm.addRow(note)
        ytm.addRow(new YassRow('E', '', '', '', ''))

        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> true
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.setBPM(15d)
        yassTable.gap = 0
        yassTable.model = Stub(TableModel) {
            getRowCount() >> 2
        }
        def pitchData = [
                pd(0.10f, -2, 1.0d), pd(0.20f, -2, 1.0d), pd(0.30f, -2, 1.0d), pd(0.40f, -2, 1.0d),
                pd(1.10f, -2, 1.0d), pd(1.20f, -2, 1.0d), pd(1.30f, -2, 1.0d), pd(1.40f, -2, 1.0d),
                pd(2.10f, -2, 1.0d), pd(2.20f, -2, 1.0d), pd(2.30f, -2, 1.0d), pd(2.40f, -2, 1.0d),
                pd(3.10f, -2, 0.08d), pd(3.20f, -2, 0.08d), pd(3.30f, -2, 0.08d), pd(3.40f, -2, 0.08d)
        ]

        when:
        yassTable.alignToMelody([note], pitchData, YassTable.AlignToMelodyContext.manual(),
                YassTable.AlignToMelodyMode.LENGTH_ONLY)

        then:
        note.getBeatInt() == 0
        note.getLengthInt() == 3
        note.getHeightInt() == 10
    }

    def 'alignPitch keeps timing while moving pitch to the detected melody line'() {
        given:
        YassTableModel ytm = new YassTableModel()
        def note = new YassRow(':', '0', '4', '10', 'Test ')
        ytm.addRow(note)
        ytm.addRow(new YassRow('E', '', '', '', ''))

        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> true
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.setBPM(15d)
        yassTable.gap = 0
        yassTable.model = Stub(TableModel) {
            getRowCount() >> 2
        }
        def pitchData = [
                pd(0.10f, -2), pd(0.20f, -2), pd(0.30f, -2), pd(0.40f, -2),
                pd(1.10f, -2), pd(1.20f, -2), pd(1.30f, -2), pd(1.40f, -2),
                pd(2.10f, -2), pd(2.20f, -2), pd(2.30f, -2), pd(2.40f, -2),
                pd(3.10f, -2), pd(3.20f, -2)
        ]

        when:
        yassTable.alignToMelody([note], pitchData, YassTable.AlignToMelodyContext.manual(),
                YassTable.AlignToMelodyMode.PITCH_ONLY)

        then:
        note.getBeatInt() == 0
        note.getLengthInt() == 4
        note.getHeightInt() == -2
    }

    def 'alignPitch snaps to the exact detected pitch line when pressed again after nearest-octave alignment'() {
        given:
        YassTableModel ytm = new YassTableModel()
        def note = new YassRow(':', '0', '4', '16', 'Test ')
        ytm.addRow(note)
        ytm.addRow(new YassRow('E', '', '', '', ''))

        and:
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> true
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.setBPM(15d)
        yassTable.gap = 0
        yassTable.model = Stub(TableModel) {
            getRowCount() >> 2
        }
        def pitchData = [
                pd(0.10f, 5), pd(0.20f, 5), pd(0.30f, 5), pd(0.40f, 5),
                pd(1.10f, 5), pd(1.20f, 5), pd(1.30f, 5), pd(1.40f, 5),
                pd(2.10f, 5), pd(2.20f, 5), pd(2.30f, 5), pd(2.40f, 5),
                pd(3.10f, 5), pd(3.20f, 5)
        ]

        when:
        yassTable.alignToMelody([note], pitchData, YassTable.AlignToMelodyContext.manual(),
                YassTable.AlignToMelodyMode.PITCH_ONLY)

        then:
        note.getHeightInt() == 17

        when:
        yassTable.alignToMelody([note], pitchData, YassTable.AlignToMelodyContext.manual(),
                YassTable.AlignToMelodyMode.PITCH_ONLY)

        then:
        note.getHeightInt() == 5
    }

    def 'alignToMelody uses free lead-in pitch frames for first note after a page break'() {
        given:
        def previous = new YassRow(':', '58', '23', '0', 'why ')
        def pageBreak = new YassRow('-', '91', '', '', '')
        def lateStart = new YassRow(':', '106', '8', '6', 'But ')
        def next = new YassRow(':', '122', '9', '-2', 'some')
        YassTable yassTable = tableWithRows(previous, pageBreak, lateStart, next)
        def pitchData = []
        pitchData.addAll(framesForBeat(58, 4, 0, 1.0d))
        pitchData.addAll(framesForBeat(90, 4, -4, 1.0d))
        pitchData.addAll(framesForBeat(91, 4, -4, 1.0d))
        pitchData.addAll(framesForBeat(106, 4, 1, 1.0d))
        pitchData.addAll(framesForBeat(122, 4, -2, 1.0d))

        when:
        yassTable.alignToMelody([lateStart], pitchData, YassTable.AlignToMelodyContext.createWizard())

        then:
        lateStart.getBeatInt() == 90
        lateStart.getLengthInt() == 2
        lateStart.getHeightInt() == -4
    }

    def 'alignToMelody does not pull first note after a page break into previous page tail'() {
        given:
        def previous = new YassRow(':', '712', '16', '5', '~ ')
        def pageBreak = new YassRow('-', '734', '', '', '')
        def and = new YassRow(':', '736', '3', '23', 'And ')
        def next = new YassRow(':', '753', '3', '8', 'I ')
        YassTable yassTable = tableWithRows(previous, pageBreak, and, next)
        def pitchData = []
        pitchData.addAll(framesForBeat(712, 4, 5, 1.0d))
        pitchData.addAll(framesForBeat(730, 4, 23, 1.0d))
        pitchData.addAll(framesForBeat(731, 4, 23, 1.0d))
        pitchData.addAll(framesForBeat(736, 4, 8, 1.0d))
        pitchData.addAll(framesForBeat(753, 4, 8, 1.0d))

        when:
        yassTable.alignToMelody([and], pitchData, YassTable.AlignToMelodyContext.createWizard())

        then:
        and.getBeatInt() == 736
        and.getLengthInt() == 1
        and.getHeightInt() == 8
    }

    def 'manual alignToMelody keeps first note after page break on pitch prevalent inside original note'() {
        given:
        def previous = new YassRow(':', '1315', '5', '4', '~ng ')
        def pageBreak = new YassRow('-', '1320', '', '', '')
        def oh = new YassRow(':', '1322', '21', '7', 'Oh, ')
        def next = new YassRow(':', '1350', '5', '3', 'your ')
        YassTable yassTable = tableWithRows(previous, pageBreak, oh, next)
        def pitchData = []
        pitchData.addAll(framesForBeat(1315, 4, 4, 1.0d))
        pitchData.addAll(framesForBeat(1321, 4, 5, 1.0d))
        (1322..1342).each { beat ->
            pitchData.addAll(framesForBeat(beat, 4, 7, 1.0d))
        }
        pitchData.addAll(framesForBeat(1350, 4, 3, 1.0d))

        when:
        yassTable.alignToMelody([oh], pitchData, YassTable.AlignToMelodyContext.manual())

        then:
        oh.getBeatInt() == 1322
        oh.getLengthInt() == 21
        oh.getHeightInt() == 7
    }

    def 'recording alignment lowers C6-or-higher outliers when neighboring notes are much lower'() {
        given:
        def previous = new YassRow(':', '0', '4', '0', 'Prev ')
        def outlier = new YassRow(':', '4', '4', '0', 'High ')
        def next = new YassRow(':', '8', '4', '2', 'Next ')
        YassTable yassTable = tableWithRows(previous, outlier, next)
        def pitchData = []
        pitchData.addAll(framesForBeat(0, 4, 0, 1.0d))
        pitchData.addAll(framesForBeat(1, 4, 0, 1.0d))
        pitchData.addAll(framesForBeat(2, 4, 0, 1.0d))
        pitchData.addAll(framesForBeat(3, 4, 0, 1.0d))
        pitchData.addAll(framesForBeat(4, 4, 24, 1.0d))
        pitchData.addAll(framesForBeat(5, 4, 24, 1.0d))
        pitchData.addAll(framesForBeat(6, 4, 24, 1.0d))
        pitchData.addAll(framesForBeat(7, 4, 24, 1.0d))
        pitchData.addAll(framesForBeat(8, 4, 2, 1.0d))
        pitchData.addAll(framesForBeat(9, 4, 2, 1.0d))
        pitchData.addAll(framesForBeat(10, 4, 2, 1.0d))
        pitchData.addAll(framesForBeat(11, 4, 2, 1.0d))

        when:
        yassTable.alignToMelody([previous, outlier, next], pitchData,
                YassTable.AlignToMelodyContext.recording(), YassTable.AlignToMelodyMode.PITCH_ONLY)

        then:
        previous.getHeightInt() == 0
        outlier.getHeightInt() == 12
        next.getHeightInt() == 2
    }

    def 'create wizard alignment lowers C6-or-higher outliers when neighboring notes are much lower'() {
        given:
        def previous = new YassRow(':', '0', '4', '0', 'Prev ')
        def outlier = new YassRow(':', '4', '4', '0', 'High ')
        def next = new YassRow(':', '8', '4', '2', 'Next ')
        YassTable yassTable = tableWithRows(previous, outlier, next)
        def pitchData = []
        pitchData.addAll(framesForBeat(0, 4, 0, 1.0d))
        pitchData.addAll(framesForBeat(1, 4, 0, 1.0d))
        pitchData.addAll(framesForBeat(2, 4, 0, 1.0d))
        pitchData.addAll(framesForBeat(3, 4, 0, 1.0d))
        pitchData.addAll(framesForBeat(4, 4, 24, 1.0d))
        pitchData.addAll(framesForBeat(5, 4, 24, 1.0d))
        pitchData.addAll(framesForBeat(6, 4, 24, 1.0d))
        pitchData.addAll(framesForBeat(7, 4, 24, 1.0d))
        pitchData.addAll(framesForBeat(8, 4, 2, 1.0d))
        pitchData.addAll(framesForBeat(9, 4, 2, 1.0d))
        pitchData.addAll(framesForBeat(10, 4, 2, 1.0d))
        pitchData.addAll(framesForBeat(11, 4, 2, 1.0d))

        when:
        yassTable.alignToMelody([previous, outlier, next], pitchData,
                YassTable.AlignToMelodyContext.createWizard(), YassTable.AlignToMelodyMode.PITCH_ONLY)

        then:
        previous.getHeightInt() == 0
        outlier.getHeightInt() == 12
        next.getHeightInt() == 2
    }

    def 'inserted lyrics alignment uses generated-note octave handling'() {
        given:
        def previous = new YassRow(':', '0', '4', '0', 'Prev ')
        def outlier = new YassRow(':', '4', '4', '0', 'High ')
        def next = new YassRow(':', '8', '4', '2', 'Next ')
        YassTable yassTable = tableWithRows(previous, outlier, next)
        def pitchData = []
        pitchData.addAll(framesForBeat(0, 4, 0, 1.0d))
        pitchData.addAll(framesForBeat(1, 4, 0, 1.0d))
        pitchData.addAll(framesForBeat(2, 4, 0, 1.0d))
        pitchData.addAll(framesForBeat(3, 4, 0, 1.0d))
        pitchData.addAll(framesForBeat(4, 4, 24, 1.0d))
        pitchData.addAll(framesForBeat(5, 4, 24, 1.0d))
        pitchData.addAll(framesForBeat(6, 4, 24, 1.0d))
        pitchData.addAll(framesForBeat(7, 4, 24, 1.0d))
        pitchData.addAll(framesForBeat(8, 4, 2, 1.0d))
        pitchData.addAll(framesForBeat(9, 4, 2, 1.0d))
        pitchData.addAll(framesForBeat(10, 4, 2, 1.0d))
        pitchData.addAll(framesForBeat(11, 4, 2, 1.0d))

        when:
        yassTable.alignToMelody([previous, outlier, next], pitchData,
                YassTable.AlignToMelodyContext.insertedLyrics(), YassTable.AlignToMelodyMode.PITCH_ONLY)

        then:
        previous.getHeightInt() == 0
        outlier.getHeightInt() == 12
        next.getHeightInt() == 2
    }

    def 'recording alignment lowers very high outliers repeatedly until they land below C6'() {
        given:
        def previous = new YassRow(':', '0', '4', '0', 'Prev ')
        def outlier = new YassRow(':', '4', '4', '0', 'VeryHigh ')
        def next = new YassRow(':', '8', '4', '2', 'Next ')
        YassTable yassTable = tableWithRows(previous, outlier, next)
        def pitchData = []
        pitchData.addAll(framesForBeat(0, 4, 0, 1.0d))
        pitchData.addAll(framesForBeat(1, 4, 0, 1.0d))
        pitchData.addAll(framesForBeat(2, 4, 0, 1.0d))
        pitchData.addAll(framesForBeat(3, 4, 0, 1.0d))
        pitchData.addAll(framesForBeat(4, 4, 41, 1.0d))
        pitchData.addAll(framesForBeat(5, 4, 41, 1.0d))
        pitchData.addAll(framesForBeat(6, 4, 41, 1.0d))
        pitchData.addAll(framesForBeat(7, 4, 41, 1.0d))
        pitchData.addAll(framesForBeat(8, 4, 2, 1.0d))
        pitchData.addAll(framesForBeat(9, 4, 2, 1.0d))
        pitchData.addAll(framesForBeat(10, 4, 2, 1.0d))
        pitchData.addAll(framesForBeat(11, 4, 2, 1.0d))

        when:
        yassTable.alignToMelody([previous, outlier, next], pitchData,
                YassTable.AlignToMelodyContext.recording(), YassTable.AlignToMelodyMode.PITCH_ONLY)

        then:
        previous.getHeightInt() == 0
        outlier.getHeightInt() == 17
        next.getHeightInt() == 2
    }

    def 'recording alignment lowers notes above C6 even when neighbors are not more than twenty semitones lower'() {
        given:
        def previous = new YassRow(':', '0', '4', '11', 'Prev ')
        def outlier = new YassRow(':', '4', '4', '30', 'Meet ')
        def next = new YassRow(':', '8', '4', '11', 'Next ')
        YassTable yassTable = tableWithRows(previous, outlier, next)
        def pitchData = []
        pitchData.addAll(framesForBeat(0, 4, 11, 1.0d))
        pitchData.addAll(framesForBeat(1, 4, 11, 1.0d))
        pitchData.addAll(framesForBeat(2, 4, 11, 1.0d))
        pitchData.addAll(framesForBeat(3, 4, 11, 1.0d))
        pitchData.addAll(framesForBeat(4, 4, 29, 1.0d))
        pitchData.addAll(framesForBeat(5, 4, 29, 1.0d))
        pitchData.addAll(framesForBeat(6, 4, 29, 1.0d))
        pitchData.addAll(framesForBeat(7, 4, 29, 1.0d))
        pitchData.addAll(framesForBeat(8, 4, 11, 1.0d))
        pitchData.addAll(framesForBeat(9, 4, 11, 1.0d))
        pitchData.addAll(framesForBeat(10, 4, 11, 1.0d))
        pitchData.addAll(framesForBeat(11, 4, 11, 1.0d))

        when:
        yassTable.alignToMelody([previous, outlier, next], pitchData,
                YassTable.AlignToMelodyContext.recording(), YassTable.AlignToMelodyMode.PITCH_ONLY)

        then:
        previous.getHeightInt() == 11
        outlier.getHeightInt() == 17
        next.getHeightInt() == 11
    }

    def 'recording alignment raises below-C3 outliers when neighboring notes are much higher'() {
        given:
        def previous = new YassRow(':', '0', '4', '8', 'Prev ')
        def outlier = new YassRow(':', '4', '4', '0', 'Low ')
        def next = new YassRow(':', '8', '4', '10', 'Next ')
        YassTable yassTable = tableWithRows(previous, outlier, next)
        def pitchData = []
        pitchData.addAll(framesForBeat(0, 4, 8, 1.0d))
        pitchData.addAll(framesForBeat(1, 4, 8, 1.0d))
        pitchData.addAll(framesForBeat(2, 4, 8, 1.0d))
        pitchData.addAll(framesForBeat(3, 4, 8, 1.0d))
        pitchData.addAll(framesForBeat(4, 4, -13, 1.0d))
        pitchData.addAll(framesForBeat(5, 4, -13, 1.0d))
        pitchData.addAll(framesForBeat(6, 4, -13, 1.0d))
        pitchData.addAll(framesForBeat(7, 4, -13, 1.0d))
        pitchData.addAll(framesForBeat(8, 4, 10, 1.0d))
        pitchData.addAll(framesForBeat(9, 4, 10, 1.0d))
        pitchData.addAll(framesForBeat(10, 4, 10, 1.0d))
        pitchData.addAll(framesForBeat(11, 4, 10, 1.0d))

        when:
        yassTable.alignToMelody([previous, outlier, next], pitchData,
                YassTable.AlignToMelodyContext.recording(), YassTable.AlignToMelodyMode.PITCH_ONLY)

        then:
        previous.getHeightInt() == 8
        outlier.getHeightInt() == -1
        next.getHeightInt() == 10
    }

    def 'recording alignment raises very low outliers repeatedly until they land at C3 or above'() {
        given:
        def previous = new YassRow(':', '0', '4', '8', 'Prev ')
        def outlier = new YassRow(':', '4', '4', '0', 'VeryLow ')
        def next = new YassRow(':', '8', '4', '10', 'Next ')
        YassTable yassTable = tableWithRows(previous, outlier, next)
        def pitchData = []
        pitchData.addAll(framesForBeat(0, 4, 8, 1.0d))
        pitchData.addAll(framesForBeat(1, 4, 8, 1.0d))
        pitchData.addAll(framesForBeat(2, 4, 8, 1.0d))
        pitchData.addAll(framesForBeat(3, 4, 8, 1.0d))
        pitchData.addAll(framesForBeat(4, 4, -37, 1.0d))
        pitchData.addAll(framesForBeat(5, 4, -37, 1.0d))
        pitchData.addAll(framesForBeat(6, 4, -37, 1.0d))
        pitchData.addAll(framesForBeat(7, 4, -37, 1.0d))
        pitchData.addAll(framesForBeat(8, 4, 10, 1.0d))
        pitchData.addAll(framesForBeat(9, 4, 10, 1.0d))
        pitchData.addAll(framesForBeat(10, 4, 10, 1.0d))
        pitchData.addAll(framesForBeat(11, 4, 10, 1.0d))

        when:
        yassTable.alignToMelody([previous, outlier, next], pitchData,
                YassTable.AlignToMelodyContext.recording(), YassTable.AlignToMelodyMode.PITCH_ONLY)

        then:
        previous.getHeightInt() == 8
        outlier.getHeightInt() == -1
        next.getHeightInt() == 10
    }

    def 'recording alignment keeps C6 notes when neighboring notes are not far enough below'() {
        given:
        def previous = new YassRow(':', '0', '4', '5', 'Prev ')
        def high = new YassRow(':', '4', '4', '0', 'High ')
        def next = new YassRow(':', '8', '4', '5', 'Next ')
        YassTable yassTable = tableWithRows(previous, high, next)
        def pitchData = []
        pitchData.addAll(framesForBeat(0, 4, 5, 1.0d))
        pitchData.addAll(framesForBeat(1, 4, 5, 1.0d))
        pitchData.addAll(framesForBeat(2, 4, 5, 1.0d))
        pitchData.addAll(framesForBeat(3, 4, 5, 1.0d))
        pitchData.addAll(framesForBeat(4, 4, 24, 1.0d))
        pitchData.addAll(framesForBeat(5, 4, 24, 1.0d))
        pitchData.addAll(framesForBeat(6, 4, 24, 1.0d))
        pitchData.addAll(framesForBeat(7, 4, 24, 1.0d))
        pitchData.addAll(framesForBeat(8, 4, 5, 1.0d))
        pitchData.addAll(framesForBeat(9, 4, 5, 1.0d))
        pitchData.addAll(framesForBeat(10, 4, 5, 1.0d))
        pitchData.addAll(framesForBeat(11, 4, 5, 1.0d))

        when:
        yassTable.alignToMelody([previous, high, next], pitchData,
                YassTable.AlignToMelodyContext.recording(), YassTable.AlignToMelodyMode.PITCH_ONLY)

        then:
        previous.getHeightInt() == 5
        high.getHeightInt() == 24
        next.getHeightInt() == 5
    }

    private YassTable tableWithRows(YassRow... notes) {
        YassTableModel ytm = new YassTableModel()
        notes.each { ytm.addRow(it) }
        ytm.addRow(new YassRow('E', '', '', '', ''))
        YassProperties props = Stub(YassProperties) {
            isUncommonSpacingAfter() >> true
        }
        YassTable yassTable = new YassTable(ytm, props)
        yassTable.setBPM(15d)
        yassTable.gap = 0
        yassTable.model = Stub(TableModel) {
            getRowCount() >> ytm.getRowCount()
        }
        yassTable
    }

    private static PitchDetector.PitchData pd(float time, int pitch) {
        new PitchDetector.PitchData(time, pitch, "A", 440d)
    }

    private static PitchDetector.PitchData pd(float time, int pitch, double energy) {
        new PitchDetector.PitchData(time, pitch, "A", 440d, energy)
    }

    private static List<PitchDetector.PitchData> framesForBeat(int beat, int frameCount, int pitch, double energy) {
        double beatLengthSeconds = 1.0d
        double beatStart = beat * beatLengthSeconds
        (0..<frameCount).collect { index ->
            float time = (float) (beatStart + 0.05d + (index * 0.08d))
            pd(time, pitch, energy)
        }
    }
}
