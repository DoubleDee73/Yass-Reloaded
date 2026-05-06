package yass.usdb;

public record UsdbSongDetails(int songId,
                              String artist,
                              String title,
                              String bpm,
                              String gap,
                              String coverUrl,
                              String pageUrl) {
}
