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

package com.doubledee.ytdlp;

import lombok.Getter;

import java.util.List;

@Getter
public class VttCue {
    private final long startMillis;
    private final long endMillis;
    private final List<String> words;

    public VttCue(long startMillis, long endMillis, List<String> words) {
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.words = words;
    }

    @Override
    public String toString() {
        return String.format("[%d --> %d] %s", startMillis, endMillis, String.join(" ", words));
    }
}
