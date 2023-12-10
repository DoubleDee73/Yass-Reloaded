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

import static yass.UltrastarHeaderTagVersion.*;

public enum UltrastarHeaderTag {
    VERSION(0),
    ARTIST(4),
    TITLE(5),
    EDITION(6, OLDY),
    GENRE(7, OLDY),
    LANGUAGE(8, OLDY),
    YEAR(9, OLDY),
    MP3(10),
    COVER(11, OLDY),
    BACKGROUND(12, OLDY),
    VIDEO(13, OLDY),
    VIDEOGAP(14, OLDY),
    START(15, OLDY),
    END(16, OLDY),
    RELATIVE(17, OLDY, CLASSY, UNITY),
    BPM(18),
    GAP(19, OLDY),
    ALBUM(23, OLDY, CLASSY, UNITY),
    LENGTH(24, OLDY, CLASSY, UNITY),
    ID(25, OLDY, CLASSY, UNITY),
    PREVIEWSTART(27, OLDY),
    MEDLEYSTARTBEAT(28, OLDY),
    MEDLEYENDBEAT(29, OLDY),
    ENCODING(30, OLDY, CLASSY, UNITY),
    CREATOR(31, OLDY),
    CALCMEDLEY(32, OLDY),
    P1(33, OLDY),
    P2(34, OLDY),
    DUETSINGERP1(35, OLDY, CLASSY, UNITY, P1),
    DUETSINGERP2(36, OLDY, CLASSY, UNITY, P2),
    COMMENT(37, OLDY),
    RESOLUTION(38, OLDY, CLASSY, UNITY),
    NOTESGAP(39, OLDY, CLASSY, UNITY),
    AUTHOR(40, OLDY, CLASSY, UNITY, CREATOR);

    final int index;
    final boolean mandatory;
    final UltrastarHeaderTagVersion added;
    final UltrastarHeaderTagVersion deprecation;
    final UltrastarHeaderTagVersion deleted;
    final UltrastarHeaderTag replacedBy;

    UltrastarHeaderTag(int index) {
        this.index = index;
        this.added = OLDY;
        this.mandatory = true;
        this.deprecation = null;
        this.deleted = null;
        this.replacedBy = null;
    }
    UltrastarHeaderTag(int index, UltrastarHeaderTagVersion added) {
        this.index = index;
        this.added = added;
        this.deprecation = null;
        this.deleted = null;
        this.replacedBy = null;
        this.mandatory = false;
    }

    UltrastarHeaderTag(int index,
                       UltrastarHeaderTagVersion added,
                       UltrastarHeaderTagVersion deprecation,
                       UltrastarHeaderTagVersion deleted) {
        this.index = index;
        this.added = added;
        this.deprecation = deprecation;
        this.deleted = deleted;
        this.replacedBy = null;
        this.mandatory = false;
    }

    UltrastarHeaderTag(int index,
                       UltrastarHeaderTagVersion added,
                       UltrastarHeaderTagVersion deprecation,
                       UltrastarHeaderTagVersion deleted,
                       UltrastarHeaderTag replacedBy) {
        this.index = index;
        this.added = added;
        this.deprecation = deprecation;
        this.deleted = deleted;
        this.replacedBy = replacedBy;
        this.mandatory = false;
    }

    public String getTagName() {
        return this + ":";
    }

    public static UltrastarHeaderTag getTag(String tagname) {
        for (UltrastarHeaderTag tag : values()) {
            if (tag.getTagName().equals(tagname)) {
                return tag;
            }
        }
        return null;
    }
    public static UltrastarHeaderTag getDeprecation(String tagname, UltrastarHeaderTagVersion version) {
        UltrastarHeaderTag tag = getTag(tagname);
        if (tag != null && tag.getTagName().equals(tagname)) {
            if (tag.deprecation == null) {
                return tag;
            }
            if (version.getNumericVersion() >= tag.deprecation.getNumericVersion()) {
                return tag.replacedBy;
            } else {
                return tag;
            }
        }
        return null;
    }

    public static List<String> deprecatedTags(UltrastarHeaderTagVersion version) {
        return Arrays.stream(values())
                     .filter(tag -> tag.deprecation != null)
                     .filter(tag -> tag.deprecation.getNumericVersion() <= version.numericVersion)
                     .map(Enum::toString)
                     .collect(Collectors.toList());
    }
}
