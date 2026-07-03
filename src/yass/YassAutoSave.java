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

package yass;

import javax.swing.SwingUtilities;
import java.util.Date;
import java.util.List;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

public class YassAutoSave extends TimerTask {
    private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private final YassTable yassTable;
    private volatile boolean locked;

    public YassAutoSave(YassTable yassTable) {
        this.yassTable = yassTable;
        locked = false;
    }

    @Override
    public void run() {
        if (yassTable.isSaved() || yassTable.isAutosaved() || locked) {
            return;
        }
        locked = true;
        try {
            // Run the write on the EDT so it cannot interleave with active edits.
            // The .bak path skips verify-reload and dialogs, so this stays cheap.
            SwingUtilities.invokeAndWait(this::saveBackup);
        } catch (Exception e) {
            LOGGER.log(Level.INFO, "Autosave failed: " + e.getMessage(), e);
        } finally {
            locked = false;
        }
    }

    private void saveBackup() {
        if (yassTable.isSaved() || yassTable.isAutosaved()) {
            return;
        }
        List<YassTable> saved = yassTable.getActions().mergeTableAndSave(yassTable, true);
        if (!saved.isEmpty()) {
            yassTable.setAutosaved(true);
            LOGGER.info(new Date() + ": Performed autosave " + saved.get(0).getDirFilename() + ".bak");
        }
    }
}
