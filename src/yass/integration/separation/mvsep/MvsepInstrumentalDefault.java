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
import java.util.Map;

public enum MvsepInstrumentalDefault implements YassEnum {
    INSTRUMENTAL("instrumental", "Instrumental"),
    INSTRUMENTAL_BACKING("instrumental-backing", "Instrumental + Backing");

    private final String value;
    private final String label;

    MvsepInstrumentalDefault(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public static MvsepInstrumentalDefault fromValue(String value) {
        for (MvsepInstrumentalDefault option : values()) {
            if (option.value.equalsIgnoreCase(value)) {
                return option;
            }
        }
        return INSTRUMENTAL;
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
