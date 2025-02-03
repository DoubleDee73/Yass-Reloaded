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

package yass.hyphenator;

import org.jetbrains.annotations.NotNull;

public class HyphenatedWord implements Comparable<HyphenatedWord> {
    private final String unHyphenated;
    private final String hyphenated;

    public HyphenatedWord(String unHyphenated, String hyphenated) {
        this.unHyphenated = unHyphenated;
        this.hyphenated = hyphenated;
    }

    public String getUnHyphenated() {
        return unHyphenated;
    }

    public String getHyphenated() {
        return hyphenated;
    }

    @Override
    public String toString() {
        return hyphenated;
    }

    @Override
    public int compareTo(@NotNull HyphenatedWord o) {
        return this.getHyphenated().compareTo(o.getHyphenated());
    }
}
