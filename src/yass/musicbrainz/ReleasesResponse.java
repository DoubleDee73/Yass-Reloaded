package yass.musicbrainz;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ReleasesResponse implements MusicBrainzEntity {
    @com.google.gson.annotations.SerializedName("release-count")
    private int releaseCount;
    @com.google.gson.annotations.SerializedName("release-offset")
    private int releaseOffset;
    private List<Release> releases;
}
