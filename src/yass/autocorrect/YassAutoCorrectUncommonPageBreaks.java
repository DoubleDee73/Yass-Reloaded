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

package yass.autocorrect;

import yass.YassProperties;
import yass.YassRow;
import yass.YassTable;

public class YassAutoCorrectUncommonPageBreaks extends YassAutoCorrector{

    public YassAutoCorrectUncommonPageBreaks(YassProperties properties) {
        super(properties);
    }

    @Override
    public boolean autoCorrect(YassTable table, int currentRowIdx, int rowCount) {
        YassRow currentRow = table.getRowAt(currentRowIdx);
        YassRow previousRow = table.getRowAt(currentRowIdx - 1);
        YassRow nextRow = table.getRowAt(currentRowIdx + 1);
        int comm[] = new int[2];
        comm[0] = previousRow.getBeatInt() + previousRow.getLengthInt();
        comm[1] = nextRow.getBeatInt();
        boolean changed;
        int pause = YassAutoCorrect.getCommonPageBreak(comm, table.getBPM(), null);
        if (pause >= 0) {
            currentRow.setBeat(comm[0]);
            currentRow.setSecondBeat(comm[1]);
            changed = true;
        } else {
            changed = false;
        }
        return changed;
    }
}
