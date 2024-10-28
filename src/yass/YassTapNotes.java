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

public class YassTapNotes {
    public static final double REACTION_TIME = 200;
    public static void evaluateTaps(YassTable table, Vector<Long> taps, List<Integer> pitches) {
        if (taps == null) return;
        int n = taps.size();
        if (n < 2) {
            taps.clear();
            return;
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
        int t = table.getSelectionModel().getMinSelectionIndex();
        if (t < 0) t = 0;
        while (t < tn) {
            YassRow r = table.getRowAt(t);
            if (r.isNote()) break;
            t++;
        }

        int k = 0;
        int i = 0;
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
                double ms = tapBeat / 1000.0 - gap - REACTION_TIME;
                double ms2 = tapBeat2 / 1000.0 - gap - REACTION_TIME;
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
            }
        }

        tm.fireTableDataChanged();
        table.addUndo();
        table.repaint();
        taps.clear();
    }
}