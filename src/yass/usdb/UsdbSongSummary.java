package yass.usdb;

public record UsdbSongSummary(int songId,
                              String artist,
                              String title,
                              String edition,
                              String language,
                              String rating,
                              String views,
                              boolean goldenNotes) {
    @Override
    public String toString() {
        return artist + " - " + title;
    }
}
