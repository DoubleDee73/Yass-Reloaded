package yass.integration.cover.fanart;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import yass.VersionUtils;
import yass.YassSong;
import yass.musicbrainz.Artist;
import yass.musicbrainz.MusicBrainz;
import yass.musicbrainz.MusicBrainzInfo;
import yass.musicbrainz.Recording;
import yass.musicbrainz.Release;
import yass.musicbrainz.ReleaseGroup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class FanartTvCoverSearchService {
    private static final int TIMEOUT_MS = 8000;
    private static final Gson GSON = new Gson();

    private final String apiKey;

    public FanartTvCoverSearchService(String apiKey) {
        this.apiKey = StringUtils.trimToEmpty(apiKey);
    }

    public List<FanartTvCoverCandidate> search(YassSong song) throws Exception {
        if (song == null || StringUtils.isBlank(song.getArtist()) || StringUtils.isBlank(song.getTitle())
                || StringUtils.isBlank(apiKey)) {
            return List.of();
        }

        MusicBrainzInfo musicBrainzInfo = new MusicBrainz().queryMusicBrainz(song.getArtist(), song.getTitle());
        Recording recording = musicBrainzInfo == null ? null : musicBrainzInfo.getRecording();
        if (recording == null) {
            return List.of();
        }

        Artist artist = MusicBrainz.determineArtist(recording);
        List<AlbumCandidate> albumCandidates = buildAlbumCandidates(song, recording);
        LinkedHashMap<String, FanartTvCoverCandidate> deduped = new LinkedHashMap<>();

        for (AlbumCandidate candidate : albumCandidates) {
            JsonObject response = fetchJson("https://webservice.fanart.tv/v3/music/albums/" + encode(candidate.id));
            collectAlbumCovers(response, candidate.albumName, deduped);
        }

        if (deduped.isEmpty() && artist != null && StringUtils.isNotBlank(artist.getId())) {
            JsonObject response = fetchJson("https://webservice.fanart.tv/v3/music/" + encode(artist.getId()));
            collectArtistAlbumCovers(response, albumCandidates, deduped);
        }

        return new ArrayList<>(deduped.values());
    }

    private List<AlbumCandidate> buildAlbumCandidates(YassSong song, Recording recording) {
        List<Release> releases = recording.getReleases();
        if (releases == null || releases.isEmpty()) {
            return List.of();
        }

        String wantedAlbum = normalize(song.getAlbum());
        List<Release> sorted = new ArrayList<>(releases);
        sorted.sort(Comparator
                .comparingInt((Release release) -> scoreRelease(release, wantedAlbum))
                .reversed()
                .thenComparing(release -> StringUtils.defaultString(release.getDate())));

        LinkedHashMap<String, AlbumCandidate> unique = new LinkedHashMap<>();
        for (Release release : sorted) {
            ReleaseGroup releaseGroup = release.getReleaseGroup();
            String releaseTitle = StringUtils.firstNonBlank(release.getTitle(),
                    releaseGroup == null ? null : releaseGroup.getTitle());
            if (releaseGroup != null && StringUtils.isNotBlank(releaseGroup.getId())) {
                unique.putIfAbsent(releaseGroup.getId(), new AlbumCandidate(releaseGroup.getId(), releaseTitle));
            }
            if (StringUtils.isNotBlank(release.getId())) {
                unique.putIfAbsent(release.getId(), new AlbumCandidate(release.getId(), releaseTitle));
            }
        }
        return new ArrayList<>(unique.values());
    }

    private int scoreRelease(Release release, String wantedAlbum) {
        int score = release.isNotCompilation() ? 10 : 0;
        String releaseTitle = normalize(release.getTitle());
        String groupTitle = normalize(release.getReleaseGroup() == null ? null : release.getReleaseGroup().getTitle());
        if (StringUtils.isNotBlank(wantedAlbum)) {
            if (StringUtils.equals(wantedAlbum, releaseTitle) || StringUtils.equals(wantedAlbum, groupTitle)) {
                score += 100;
            } else if (StringUtils.contains(releaseTitle, wantedAlbum) || StringUtils.contains(groupTitle, wantedAlbum)
                    || StringUtils.contains(wantedAlbum, releaseTitle) || StringUtils.contains(wantedAlbum, groupTitle)) {
                score += 50;
            }
        }
        return score;
    }

    private JsonObject fetchJson(String baseUrl) throws IOException {
        String urlString = baseUrl + "?api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URI(urlString).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "Yass Reloaded/" + VersionUtils.getVersion() +
                    " ( https://github.com/DoubleDee73/Yass-Reloaded )");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                return GSON.fromJson(reader, JsonObject.class);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void collectArtistAlbumCovers(JsonObject response,
                                          List<AlbumCandidate> albumCandidates,
                                          Map<String, FanartTvCoverCandidate> deduped) {
        if (response == null) {
            return;
        }
        Set<String> candidateIds = albumCandidates.stream()
                                                  .map(candidate -> candidate.id)
                                                  .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        collectNestedAlbumCovers(response.get("albums"), candidateIds, deduped);
        if (deduped.isEmpty()) {
            collectNestedAlbumCovers(response, candidateIds, deduped);
        }
    }

    private void collectNestedAlbumCovers(JsonElement albumsElement,
                                          Set<String> candidateIds,
                                          Map<String, FanartTvCoverCandidate> deduped) {
        if (albumsElement == null || !albumsElement.isJsonObject()) {
            return;
        }
        JsonObject albums = albumsElement.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : albums.entrySet()) {
            if (!candidateIds.isEmpty() && !candidateIds.contains(entry.getKey())) {
                continue;
            }
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject albumObject = entry.getValue().getAsJsonObject();
            String albumName = getString(albumObject, "name");
            if (StringUtils.isBlank(albumName)) {
                albumName = getString(albumObject, "title");
            }
            collectAlbumCovers(albumObject, albumName, deduped);
        }
    }

    private void collectAlbumCovers(JsonObject response,
                                    String fallbackAlbumName,
                                    Map<String, FanartTvCoverCandidate> deduped) {
        if (response == null) {
            return;
        }
        JsonArray covers = getArray(response, "albumcover");
        if (covers == null) {
            return;
        }
        for (JsonElement element : covers) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String url = normalizeFanartUrl(getString(object, "url"));
            if (StringUtils.isBlank(url) || deduped.containsKey(url)) {
                continue;
            }
            int likes = parseInt(getString(object, "likes"));
            String albumName = StringUtils.firstNonBlank(getString(object, "album"), fallbackAlbumName);
            deduped.put(url, new FanartTvCoverCandidate(url, albumName, likes));
        }
    }

    private JsonArray getArray(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) {
            return null;
        }
        return object.getAsJsonArray(key);
    }

    private String getString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        return object.get(key).getAsString();
    }

    private String normalizeFanartUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return null;
        }
        return url.replace("images.fanart.tv", "assets.fanart.tv");
    }

    private String normalize(String value) {
        return StringUtils.trimToEmpty(value).toLowerCase(Locale.ROOT);
    }

    private String encode(String value) {
        return StringUtils.trimToEmpty(value);
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(StringUtils.trimToEmpty(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static final class AlbumCandidate {
        private final String id;
        private final String albumName;

        private AlbumCandidate(String id, String albumName) {
            this.id = id;
            this.albumName = albumName;
        }
    }
}
