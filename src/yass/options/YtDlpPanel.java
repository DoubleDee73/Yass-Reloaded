/*
 * Yass - Karaoke Editor
 * Copyright (C) 2009 Saruta
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

package yass.options;

import org.apache.commons.lang3.StringUtils;
import yass.I18;
import yass.options.enums.YtDlpAudioBitrate;
import yass.options.enums.YtDlpAudioFormat;
import yass.options.enums.YtDlpVideoCodec;
import yass.options.enums.YtDlpVideoResolution;

/**
 * Wizard-Default settings panel
 *
 * @author DoubleDee
 */
public class YtDlpPanel extends OptionsPanel {

    private static final long serialVersionUID = 1L;
    public static final String YTDLP_AUDIO_FORMAT = "ytdlp-audio-format";
    public static final String YTDLP_AUDIO_BITRATE = "ytdlp-audio-bitrate";
    public static final String YTDLP_VIDEO_CODEC = "ytdlp-video-codec";
    public static final String YTDLP_VIDEO_RESOLUTION = "ytdlp-video-resolution";

    private String ytDlpVersion;

    public YtDlpPanel(String ytDlpVersion) {
        this.ytDlpVersion = ytDlpVersion;
    }

    /**
     * Gets the body attribute of the DirPanel object
     */
    public void addRows() {
        if (StringUtils.isNotEmpty(getYtDlpVersion())) {
            addComment(I18.get("options_wizard_ytdlp_version") + " " + getYtDlpVersion());
            addSeparator(I18.get("options_wizard_ytdlp_audio"));
            addChoice(I18.get("options_wizard_ytdlp_audio_format"), YtDlpAudioFormat.values(),
                      YTDLP_AUDIO_FORMAT, 100);
            addChoice(I18.get("options_wizard_ytdlp_audio_bitrate"), YtDlpAudioBitrate.values(),
                      YTDLP_AUDIO_BITRATE, 100);
            addSeparator();
            addSeparator(I18.get("options_wizard_ytdlp_video"));
            addChoice(I18.get("options_wizard_ytdlp_video_codec"), YtDlpVideoCodec.values(),
                      YTDLP_VIDEO_CODEC, 100);
            addChoice(I18.get("options_wizard_ytdlp_video_resolution"), YtDlpVideoResolution.values(),
                      YTDLP_VIDEO_RESOLUTION, 100);
        } else {
            addComment(I18.get("options_wizard_ytdlp_not_found"));
        }
    }

    public String getYtDlpVersion() {
        return ytDlpVersion;
    }

    public void setYtDlpVersion(String ytDlpVersion) {
        this.ytDlpVersion = ytDlpVersion;
    }
}
