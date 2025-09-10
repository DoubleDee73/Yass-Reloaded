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

import yass.I18;
import yass.YassEnum;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum YtDlpVideoResolution implements YassEnum {
    RESOLUTION_2160("[height<=2160]", I18.get("options_wizard_ytdlp_video_resolution_2160")),
    RESOLUTION_1440("[height<=1440]", I18.get("options_wizard_ytdlp_video_resolution_1440")),
    RESOLUTION_1080("[height<=1080]", I18.get("options_wizard_ytdlp_video_resolution_1080")),
    RESOLUTION_720("[height<=720]", I18.get("options_wizard_ytdlp_video_resolution_720")),
    RESOLUTION_480("[height<=480]", I18.get("options_wizard_ytdlp_video_resolution_480")),
    RESOLUTION_360("[height<=360]", I18.get("options_wizard_ytdlp_video_resolution_360"));
    final String value;
    final String label;

    YtDlpVideoResolution(String value, String label) {
        this.value = value;
        this.label = label;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public List<YassEnum> listElements() {
        return Arrays.stream(values()).collect(Collectors.toList());
    }

    @Override
    public String getLabel() {
        return label;
    }
}
