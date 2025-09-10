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

package yass.options.enums;

import yass.YassEnum;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum YtDlpAudioBitrate implements YassEnum {
    BITRATE_128(128),
    BITRATE_160(160),
    BITRATE_192(192),
    BITRATE_256(256),
    BITRATE_329(320);
    final int bitrate;
    YtDlpAudioBitrate(int bitrate) {
        this.bitrate = bitrate;
    }

    @Override
    public String getValue() {
        return "" + bitrate;
    }

    @Override
    public List<YassEnum> listElements() {
        return Arrays.stream(values()).collect(Collectors.toList());
    }

    @Override
    public String getLabel() {
        return bitrate + " kbps";
    }
}
