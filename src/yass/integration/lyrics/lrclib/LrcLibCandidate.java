package yass.integration.lyrics.lrclib;

import org.apache.commons.lang3.StringUtils;

public class LrcLibCandidate {
    private final long id;
    private final String trackName;
    private final String artistName;
    private final String albumName;
    private final int durationSeconds;
    private final boolean instrumental;
    private final String plainLyrics;
    private final String syncedLyrics;

    public LrcLibCandidate(long id,
                           String trackName,
                           String artistName,
                           String albumName,
                           int durationSeconds,
                           boolean instrumental,
                           String plainLyrics,
                           String syncedLyrics) {
        this.id = id;
        this.trackName = StringUtils.defaultString(trackName);
        this.artistName = StringUtils.defaultString(artistName);
        this.albumName = StringUtils.defaultString(albumName);
        this.durationSeconds = Math.max(0, durationSeconds);
        this.instrumental = instrumental;
        this.plainLyrics = StringUtils.defaultString(plainLyrics);
        this.syncedLyrics = StringUtils.defaultString(syncedLyrics);
    }

    public long getId() {
        return id;
    }

    public String getTrackName() {
        return trackName;
    }

    public String getArtistName() {
        return artistName;
    }

    public String getAlbumName() {
        return albumName;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public boolean isInstrumental() {
        return instrumental;
    }

    public String getPlainLyrics() {
        return plainLyrics;
    }

    public String getSyncedLyrics() {
        return syncedLyrics;
    }

    public boolean hasSyncedLyrics() {
        return StringUtils.isNotBlank(syncedLyrics);
    }

    public boolean hasPlainLyrics() {
        return StringUtils.isNotBlank(plainLyrics);
    }
}
