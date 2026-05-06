package yass.integration.lyrics.lrclib;

import org.apache.commons.lang3.StringUtils;

public record LrcLibSearchQuery(String artist, String title) {
    public LrcLibSearchQuery {
        artist = StringUtils.trimToEmpty(artist);
        title = StringUtils.trimToEmpty(title);
    }
}
