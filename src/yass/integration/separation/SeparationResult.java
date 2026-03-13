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

package yass.integration.separation;

import org.apache.commons.lang3.StringUtils;

import java.io.File;

public class SeparationResult {
    private final File leadFile;
    private final File instrumentalFile;
    private final File instrumentalBackingFile;

    public SeparationResult(File leadFile, File instrumentalFile, File instrumentalBackingFile) {
        this.leadFile = leadFile;
        this.instrumentalFile = instrumentalFile;
        this.instrumentalBackingFile = instrumentalBackingFile;
    }

    public File getLeadFile() {
        return leadFile;
    }

    public File getVocalsFile() {
        return leadFile;
    }

    public File getInstrumentalFile() {
        return instrumentalFile;
    }

    public File getInstrumentalBackingFile() {
        return instrumentalBackingFile;
    }

    public File getPreferredInstrumentalFile(String defaultInstrumental) {
        if ("instrumental-backing".equalsIgnoreCase(StringUtils.defaultIfBlank(defaultInstrumental, "instrumental"))
                && instrumentalBackingFile != null) {
            return instrumentalBackingFile;
        }
        return instrumentalFile != null ? instrumentalFile : instrumentalBackingFile;
    }
}
