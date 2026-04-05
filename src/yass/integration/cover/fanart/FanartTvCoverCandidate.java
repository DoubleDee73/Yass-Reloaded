package yass.integration.cover.fanart;

public class FanartTvCoverCandidate {
    private final String imageUrl;
    private final String albumName;
    private final int likes;

    public FanartTvCoverCandidate(String imageUrl, String albumName, int likes) {
        this.imageUrl = imageUrl;
        this.albumName = albumName;
        this.likes = likes;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getAlbumName() {
        return albumName;
    }

    public int getLikes() {
        return likes;
    }
}
