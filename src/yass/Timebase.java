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

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

public enum Timebase {
    NORMAL(1, "speedone24Icon", 1d, StringUtils.EMPTY),
    SLOWER(2, "speedtwo24Icon", 0.75d, "atempo=0.75"),
    SLOW(3, "speedthree24Icon", 0.5d, "atempo=0.5"),
    SLOWEST(4, "speedfour24Icon", 0.25d, "atempo=0.5,atempo=0.5");

    Timebase(int id, String icon, double timerate, String filter) {
        this.id = id;
        this.icon = icon;
        this.timerate = timerate;
        this.filter = filter;
    }
    
    final int id;
    final String icon;
    final double timerate;
    
    final String filter;

    public int getId() {
        return id;
    }
    public String getIcon() {
        return icon;
    }

    public double getTimerate() {
        return timerate;
    }

    public String getFilter() {
        return filter;
    }

    public double getMultiplier() {
        return 1 / timerate;
    }
    
    public static Timebase getTimebase(int id) {
        return Arrays.stream(values())
                     .filter(it -> it.getId() == id)
                     .findFirst()
                     .orElse(NORMAL);
    }
}
