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
