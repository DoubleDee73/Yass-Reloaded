/*
 * Yass - Karaoke Editor
 * Copyright (C) 2009 Saruta
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

package yass;

import java.util.List;
import java.util.Vector;
import yass.autocorrect.YassAutoCorrect;

public class YassTapNotes {

    public static int evaluateTaps(YassTable table, Vector<Long> taps, List<Integer> pitches, Timebase timebase) {
        return evaluateTaps(table, taps, pitches, timebase, -1);
    }

    public static int evaluateTaps(YassTable table, Vector<Long> taps, List<Integer> pitches, Timebase timebase, int startRow) {
        if (taps == null) return 0;
        int n = taps.size();
        if (n < 2) {
            taps.clear();
            return 0;
        }
        if (n % 2 == 1) {
            taps.removeElementAt(n - 1);
            n--;
        }

        int tn = table.getRowCount();
        YassTableModel tm = (YassTableModel) table.getModel();

        double gap = table.getGap();
        double bpm = table.getBPM();

        // get first note that follows selection
        int t = startRow >= 0 ? startRow : table.getSelectionModel().getMinSelectionIndex();
        if (t < 0) t = 0;
        while (t < tn) {
            YassRow r = table.getRowAt(t);
            if (r.isNote()) break;
            t++;
        }
        boolean anchorFirstTapToGap = t == getFirstNoteRow(table);
        if (anchorFirstTapToGap) {
            gap = toCompensatedTapMillis(taps.elementAt(0), timebase);
            table.setGap(gap);
        }

        int k = 0;
        int i = 0;
        int processedNotes = 0;
        while (k < n && t < tn) {
            YassRow r = table.getRowAt(t++);
            if (r.isNote()) {
                int note;
                if (i < pitches.size()) {
                    note = pitches.get(i++);
                } else {
                    note = Integer.MIN_VALUE;
                }
                long tapBeat = taps.elementAt(k++).longValue();
                long tapBeat2 = taps.elementAt(k++).longValue();
                double ms = toBeatRelativeMillis(tapBeat, gap, timebase);
                double ms2 = toBeatRelativeMillis(tapBeat2, gap, timebase);
                int beat = (int) Math.round((4 * bpm * ms / (60 * 1000)));
                int beat2 = (int) Math.round((4 * bpm * ms2 / (60 * 1000)));

                int length = beat2 - beat;

                if (length < 1) length = 1;
                r.setBeat(beat);
                r.setLength(length);
                if (note > Integer.MIN_VALUE + 10) {
                    r.setHeight(note);
                } else {
                    if (note == YassActions.FREESTYLE_NOTE) {
                        r.setType("F");
                    } else if (note == YassActions.RAP_NOTE) {
                        r.setType("R");
                    }
                }
                processedNotes++;
            }
        }

        YassAutoCorrect.repositionPageBreaks(table);
        tm.fireTableDataChanged();
        table.addUndo();
        table.repaint();
        taps.clear();
        return processedNotes;
    }

    private static int getFirstNoteRow(YassTable table) {
        int rowCount = table.getRowCount();
        for (int row = 0; row < rowCount; row++) {
            if (table.getRowAt(row).isNote()) {
                return row;
            }
        }
        return -1;
    }

    private static double toBeatRelativeMillis(long tapMicros, double gap, Timebase timebase) {
        return toCompensatedTapMillis(tapMicros, timebase) - gap;
    }

    private static double toCompensatedTapMillis(long tapMicros, Timebase timebase) {
        return Math.max(0d, tapMicros / 1000.0);
    }
}
