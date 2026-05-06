package yass.musicbrainz;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ReleaseGroupsResponse implements MusicBrainzEntity {
    @SerializedName("release-group-count")
    private int releaseGroupCount;
    @SerializedName("release-group-offset")
    private int releaseGroupOffset;
    @SerializedName("release-groups")
    private List<ReleaseGroup> releaseGroups;
}
