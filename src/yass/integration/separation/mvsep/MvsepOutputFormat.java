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

package yass.integration.separation.mvsep;

import yass.YassEnum;

import java.util.Arrays;
import java.util.List;

public enum MvsepOutputFormat implements YassEnum {
    FLAC("flac", "FLAC", "2", "flac"),
    WAV("wav", "WAV", "1", "wav"),
    MP3("mp3", "MP3", "0", "mp3"),
    M4A("m4a", "M4A", "3", "m4a");

    private final String value;
    private final String label;
    private final String apiValue;
    private final String extension;

    MvsepOutputFormat(String value, String label, String apiValue, String extension) {
        this.value = value;
        this.label = label;
        this.apiValue = apiValue;
        this.extension = extension;
    }

    public static MvsepOutputFormat fromValue(String value) {
        for (MvsepOutputFormat format : values()) {
            if (format.value.equalsIgnoreCase(value)) {
                return format;
            }
        }
        return FLAC;
    }

    public String getApiValue() {
        return apiValue;
    }

    public String getExtension() {
        return extension;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public List<YassEnum> listElements() {
        return Arrays.asList(values());
    }

    @Override
    public String getLabel() {
        return label;
    }
}
