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

import java.util.List;

public enum YtDlpAudioFormat implements YassEnum {
    M4A("m4a", "m4a", "AAC"),
    MP3("mp3", "mp3", "MPEG Layer 3"),
    OGG("vorbis", "ogg", "Ogg Vorbis"),
    OPUS("opus", "opus", "Ogg Opus");

    final String value;
    final String extension;
    final String description;

    YtDlpAudioFormat(String value, String extension, String description) {
        this.value = value;
        this.extension = extension;
        this.description = description;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public List<YassEnum> listElements() {
        return List.of();
    }

    @Override
    public String getLabel() {
        return "." + extension + " (" + description + ")";
    }
}
