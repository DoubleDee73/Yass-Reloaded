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

public enum YtDlpVideoCodec implements YassEnum {
    BEST("", I18.get("options_wizard_ytdlp_video_codec_best")),
    MP4_BEST("[ext=mp4]",              I18.get("options_wizard_ytdlp_video_codec_mp4_best")),
    MP4_AV1("[vcodec^=av01[ext=mp4]",  I18.get("options_wizard_ytdlp_video_codec_mp4_av1")),
    MP4_AVC("[vcodec^=avc1][ext=mp4]", I18.get("options_wizard_ytdlp_video_codec_mp4_avc")),
    MP4_VP9("[vcodec^=vp9][ext=mp4]",  I18.get("options_wizard_ytdlp_video_codec_mp4_vp9")),
    WEBM_VP9("[vcodec^=vp9][ext=webm]",I18.get( "options_wizard_ytdlp_video_codec_webm_vp9"));
    final String value;
    final String label;

    YtDlpVideoCodec(String value, String label) {
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
