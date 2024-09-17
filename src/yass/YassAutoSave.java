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

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.TimerTask;

public class YassAutoSave extends TimerTask {

    private YassTable yassTable;
    private boolean error;
    
    public YassAutoSave(YassTable yassTable) {
        this.yassTable = yassTable;
    }

    @Override
    public void run() {
        if (yassTable.isSaved()) {
            return;
        }
        File original = new File(yassTable.getDirFilename());
        File backup = new File(yassTable.getDirFilename() + ".bak");
        if (original.exists()) {
            try {
                FileUtils.copyFile(original, backup, StandardCopyOption.REPLACE_EXISTING);
                error = false;
            } catch (IOException e) {
                error = true;
                throw new RuntimeException(e);
            }
            System.out.println(new Date() + ": Autosaved " + yassTable.getDirFilename() + " / success " + !error);
        }
        yassTable.getActions().saveAllTables();
    }
}
