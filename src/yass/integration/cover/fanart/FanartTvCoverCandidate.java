package yass.integration.cover.fanart;

import java.awt.image.BufferedImage;

public class FanartTvCoverCandidate {
    private final String imageUrl;
    private final String previewImageUrl;
    private final String albumName;
    private final int likes;
    private final boolean preferred;
    private BufferedImage previewImage;

    public FanartTvCoverCandidate(String imageUrl, String previewImageUrl, String albumName, int likes, boolean preferred) {
        this.imageUrl = imageUrl;
        this.previewImageUrl = previewImageUrl;
        this.albumName = albumName;
        this.likes = likes;
        this.preferred = preferred;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getPreviewImageUrl() {
        return previewImageUrl;
    }

    public String getAlbumName() {
        return albumName;
    }

    public int getLikes() {
        return likes;
    }

    public boolean isPreferred() {
        return preferred;
    }

    public BufferedImage getPreviewImage() {
        return previewImage;
    }

    public void setPreviewImage(BufferedImage previewImage) {
        this.previewImage = previewImage;
    }
}
