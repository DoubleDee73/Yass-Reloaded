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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum UltrastarHeaderTagVersion implements YassEnum {
    CLASSY("0.2.0", 0.2d),
    OLDY("0.3.0", 0.3d),
    UNITY("1.0.0", 1d),
    SHINY("1.1.0", 1.1d);
    final String version;
    final double numericVersion;

    UltrastarHeaderTagVersion(String version, double numericVersion) {
        this.version = version;
        this.numericVersion = numericVersion;
    }

    @Override
    public String getValue() {
        return version;
    }

    @Override
    public List<YassEnum> listElements() {
        return Arrays.stream(values()).collect(Collectors.toList());
    }

    @Override
    public String getLabel() {
        return this + " (" + version + ")";
    }

    public double getNumericVersion() {
        return numericVersion;
    }

    public static UltrastarHeaderTagVersion getFormatVersion(String version) {
        for (UltrastarHeaderTagVersion tagVersion : values()) {
            if (tagVersion.version.equals(version)) {
                return tagVersion;
            }
        }
        return UltrastarHeaderTagVersion.UNITY;
    }
}
