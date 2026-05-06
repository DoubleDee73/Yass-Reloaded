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
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FanartTvCoverSearchService {
    private static final int TIMEOUT_MS = 8000;
    private static final int RESPONSE_LOG_LIMIT = 4000;
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    private final String apiKey;
    private final MusicBrainz musicBrainz;

    public FanartTvCoverSearchService(String apiKey) {
        this(apiKey, new MusicBrainz());
    }

    FanartTvCoverSearchService(String apiKey, MusicBrainz musicBrainz) {
        this.apiKey = StringUtils.trimToEmpty(apiKey);
        this.musicBrainz = musicBrainz == null ? new MusicBrainz() : musicBrainz;
    }

    public List<FanartTvCoverCandidate> search(YassSong song) throws Exception {
        LOGGER.info("fanart.tv search start: artist=\"" + (song == null ? "" : StringUtils.defaultString(song.getArtist()))
                + "\", title=\"" + (song == null ? "" : StringUtils.defaultString(song.getTitle()))
                + "\", album=\"" + (song == null ? "" : StringUtils.defaultString(song.getAlbum()))
                + "\", apiKeyPresent=" + StringUtils.isNotBlank(apiKey));
        if (song == null || StringUtils.isBlank(song.getArtist()) || StringUtils.isBlank(song.getTitle())
                || StringUtils.isBlank(apiKey)) {
            LOGGER.warning("fanart.tv search aborted before MusicBrainz lookup: "
                    + "songPresent=" + (song != null)
                    + ", artistPresent=" + (song != null && StringUtils.isNotBlank(song.getArtist()))
                    + ", titlePresent=" + (song != null && StringUtils.isNotBlank(song.getTitle()))
                    + ", apiKeyPresent=" + StringUtils.isNotBlank(apiKey));
            return List.of();
        }

        MusicBrainzInfo musicBrainzInfo = musicBrainz.queryMusicBrainz(song.getArtist(), song.getTitle());
        Recording recording = musicBrainzInfo == null ? null : musicBrainzInfo.getRecording();
        if (recording == null) {
            LOGGER.warning("fanart.tv search aborted after MusicBrainz lookup: no recording found");
            return List.of();
        }

        Artist artist = MusicBrainz.determineArtist(recording);
        List<AlbumCandidate> albumCandidates = buildAlbumCandidates(song, recording);
        LOGGER.info("fanart.tv MusicBrainz result: artistId=\"" + (artist == null ? "" : StringUtils.defaultString(artist.getId()))
                + "\", releases=" + safeSize(recording.getReleases())
                + ", albumCandidates=" + albumCandidates.size()
                + ", albumCandidateIds=" + summarizeCollectionForLog(albumCandidates.stream().map(candidate -> candidate.id).toList())
                + ", albumCandidateTitles=" + summarizeCollectionForLog(albumCandidates.stream().map(candidate -> candidate.albumName).toList()));
        Map<String, String> recordingAlbumNamesById = buildAlbumNamesById(albumCandidates);
        Map<String, String> artistDiscographyAlbumNamesById = buildArtistDiscographyAlbumNamesById(artist);
        List<String> preferredAlbumIds = albumCandidates.stream().map(candidate -> candidate.id).filter(StringUtils::isNotBlank).toList();
        String wantedAlbum = normalize(song.getAlbum());
        String songAlbumFallback = StringUtils.trimToEmpty(song.getAlbum());
        LinkedHashMap<String, FanartTvCoverCandidate> deduped = new LinkedHashMap<>();
        LOGGER.info("fanart.tv artist discography result: releaseGroups=" + artistDiscographyAlbumNamesById.size()
                + ", discographyAlbumIds=" + summarizeCollectionForLog(artistDiscographyAlbumNamesById.keySet())
                + ", discographyAlbumTitles=" + summarizeCollectionForLog(artistDiscographyAlbumNamesById.values()));

        if (albumCandidates.isEmpty()) {
            LOGGER.warning("fanart.tv search has no album candidates from MusicBrainz releases");
        }
        if (artist != null && StringUtils.isNotBlank(artist.getId())) {
            LOGGER.info("fanart.tv artist fetch candidate: artistId=" + artist.getId());
            JsonObject response = fetchJson("https://webservice.fanart.tv/v3/music/" + encode(artist.getId()));
            collectArtistAlbumCovers(response, deduped, wantedAlbum, recordingAlbumNamesById,
                    artistDiscographyAlbumNamesById, preferredAlbumIds, songAlbumFallback);
        } else {
            LOGGER.warning("fanart.tv artist fetch skipped: no artist id from MusicBrainz");
        }

        List<FanartTvCoverCandidate> result = new ArrayList<>(deduped.values());
        result.sort(Comparator.comparing(FanartTvCoverCandidate::isPreferred)
                              .reversed()
                              .thenComparing(FanartTvCoverCandidate::getLikes, Comparator.reverseOrder())
                              .thenComparing(candidate -> StringUtils.defaultString(candidate.getAlbumName())));
        LOGGER.info("fanart.tv search finished: coversFound=" + result.size()
                + ", coverAlbums=" + summarizeCollectionForLog(result.stream().map(FanartTvCoverCandidate::getAlbumName).toList()));
        return result;
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

    private Map<String, String> buildAlbumNamesById(List<AlbumCandidate> albumCandidates) {
        LinkedHashMap<String, String> namesById = new LinkedHashMap<>();
        if (albumCandidates == null) {
            return namesById;
        }
        for (AlbumCandidate candidate : albumCandidates) {
            if (candidate == null || StringUtils.isBlank(candidate.id) || StringUtils.isBlank(candidate.albumName)) {
                continue;
            }
            namesById.putIfAbsent(candidate.id, candidate.albumName);
        }
        return namesById;
    }

    private Map<String, String> buildArtistDiscographyAlbumNamesById(Artist artist) {
        LinkedHashMap<String, String> namesById = new LinkedHashMap<>();
        if (artist == null || StringUtils.isBlank(artist.getId())) {
            LOGGER.warning("fanart.tv artist discography skipped: no artist id");
            return namesById;
        }

        List<ReleaseGroup> releaseGroups = musicBrainz.getArtistReleaseGroups(artist.getId());
        for (ReleaseGroup releaseGroup : releaseGroups) {
            if (releaseGroup == null || StringUtils.isBlank(releaseGroup.getId()) || StringUtils.isBlank(releaseGroup.getTitle())) {
                continue;
            }
            namesById.putIfAbsent(releaseGroup.getId(), StringUtils.trimToNull(releaseGroup.getTitle()));
        }

        List<Release> releases = musicBrainz.getArtistReleases(artist.getId());
        for (Release release : releases) {
            if (release == null || StringUtils.isBlank(release.getId()) || StringUtils.isBlank(release.getTitle())) {
                continue;
            }
            namesById.putIfAbsent(release.getId(), StringUtils.trimToNull(release.getTitle()));
        }

        return namesById;
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

    protected JsonObject fetchJson(String baseUrl) throws IOException {
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
            LOGGER.info("fanart.tv request: GET " + urlString
                    + ", Accept=application/json, User-Agent=" + connection.getRequestProperty("User-Agent"));
            int status = connection.getResponseCode();
            String responseHeaders = formatHeadersForLog(connection.getHeaderFields());
            LOGGER.info("fanart.tv response meta for " + baseUrl + ": status=" + status + ", headers=" + responseHeaders);
            if (status < 200 || status >= 300) {
                LOGGER.warning("fanart.tv non-success response for " + baseUrl + ": "
                        + abbreviateForLog(readResponseBody(connection.getErrorStream())));
                return null;
            }
            try (InputStream inputStream = connection.getInputStream()) {
                String responseBody = readResponseBody(inputStream);
                LOGGER.info("fanart.tv raw response for " + baseUrl + ": " + abbreviateForLog(responseBody));
                return GSON.fromJson(responseBody, JsonObject.class);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "fanart.tv request failed for " + baseUrl + " (" + urlString + ")", e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void collectArtistAlbumCovers(JsonObject response,
                                          Map<String, FanartTvCoverCandidate> deduped,
                                          String wantedAlbum,
                                          Map<String, String> recordingAlbumNamesById,
                                          Map<String, String> artistDiscographyAlbumNamesById,
                                          Collection<String> preferredAlbumIds,
                                          String songAlbumFallback) {
        if (response == null) {
            return;
        }
        collectNestedAlbumCovers(response.get("albums"), deduped, wantedAlbum, recordingAlbumNamesById,
                artistDiscographyAlbumNamesById, preferredAlbumIds, songAlbumFallback);
    }

    private void collectNestedAlbumCovers(JsonElement albumsElement,
                                          Map<String, FanartTvCoverCandidate> deduped,
                                          String wantedAlbum,
                                          Map<String, String> recordingAlbumNamesById,
                                          Map<String, String> artistDiscographyAlbumNamesById,
                                          Collection<String> preferredAlbumIds,
                                          String songAlbumFallback) {
        if (albumsElement == null || !albumsElement.isJsonObject()) {
            return;
        }
        JsonObject albums = albumsElement.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : albums.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject albumObject = entry.getValue().getAsJsonObject();
            String albumName = getString(albumObject, "name");
            if (StringUtils.isBlank(albumName)) {
                albumName = getString(albumObject, "title");
            }
            AlbumNameResolution albumNameResolution = resolveAlbumName(entry.getKey(), albumName,
                    recordingAlbumNamesById, artistDiscographyAlbumNamesById, songAlbumFallback);
            collectAlbumCovers(albumObject, albumNameResolution.albumName, deduped, wantedAlbum,
                    (preferredAlbumIds != null && preferredAlbumIds.contains(entry.getKey())) || albumNameResolution.preferredById);
        }
    }

    private void collectAlbumCovers(JsonObject response,
                                    String fallbackAlbumName,
                                    Map<String, FanartTvCoverCandidate> deduped,
                                    String wantedAlbum,
                                    boolean preferredMatchById) {
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
            String previewImageUrl = toPreviewImageUrl(url);
            LOGGER.info("fanart.tv cover candidate album=\"" + StringUtils.defaultString(albumName)
                    + "\", likes=" + likes + ", url=" + url);
            deduped.put(url, new FanartTvCoverCandidate(
                    url,
                    previewImageUrl,
                    albumName,
                    likes,
                    preferredMatchById || isPreferredAlbumMatch(albumName, wantedAlbum)
            ));
        }
    }

    private boolean isPreferredAlbumMatch(String albumName, String wantedAlbum) {
        if (StringUtils.isBlank(wantedAlbum)) {
            return false;
        }
        String normalizedAlbumName = normalize(albumName);
        return StringUtils.equals(normalizedAlbumName, wantedAlbum);
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

    static String toPreviewImageUrl(String imageUrl) {
        if (StringUtils.isBlank(imageUrl)) {
            return null;
        }
        return imageUrl.replace("images.fanart.tv", "assets.fanart.tv");
    }

    static String abbreviateForLog(String value) {
        if (value == null || value.length() <= RESPONSE_LOG_LIMIT) {
            return value;
        }
        return value.substring(0, RESPONSE_LOG_LIMIT)
                + "...(truncated " + (value.length() - RESPONSE_LOG_LIMIT) + " chars)";
    }

    static String formatHeadersForLog(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        return headers.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .map(entry -> {
                    String key = entry.getKey() == null || "null".equals(entry.getKey()) ? "Status" : entry.getKey();
                    return key + "=" + String.join("|", entry.getValue());
                })
                .collect(Collectors.joining(", "));
    }

    static String summarizeCollectionForLog(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        List<String> sanitized = values.stream()
                .map(value -> StringUtils.abbreviate(StringUtils.defaultString(value), 60))
                .toList();
        if (sanitized.size() <= 4) {
            return sanitized.toString();
        }
        return sanitized.subList(0, 4).toString() + " ...(" + (sanitized.size() - 4) + " more)";
    }

    static String readResponseBody(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "<no response body>";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }

    private String normalize(String value) {
        return StringUtils.trimToEmpty(value).toLowerCase(Locale.ROOT);
    }

    AlbumNameResolution resolveAlbumName(String albumId,
                                         String fanartAlbumName,
                                         Map<String, String> recordingAlbumNamesById,
                                         Map<String, String> artistDiscographyAlbumNamesById,
                                         String songAlbumFallback) {
        if (StringUtils.isNotBlank(fanartAlbumName)) {
            LOGGER.info("fanart.tv album match: id=" + albumId + ", reason=fanart-title, title=\"" + fanartAlbumName + "\"");
            return new AlbumNameResolution(fanartAlbumName, false);
        }

        if (StringUtils.isBlank(albumId)) {
            String fallback = StringUtils.trimToNull(songAlbumFallback);
            LOGGER.info("fanart.tv album match: id=<blank>, reason="
                    + (fallback == null ? "no-album-id-no-fallback" : "song-album-fallback")
                    + ", title=\"" + StringUtils.defaultString(fallback) + "\"");
            return new AlbumNameResolution(fallback, false);
        }

        String recordingAlbumName = recordingAlbumNamesById == null ? null : StringUtils.trimToNull(recordingAlbumNamesById.get(albumId));
        if (recordingAlbumName != null) {
            LOGGER.info("fanart.tv album match: id=" + albumId + ", reason=recording-exact-id-match, title=\"" + recordingAlbumName + "\"");
            return new AlbumNameResolution(recordingAlbumName, true);
        }

        String artistDiscographyAlbumName = artistDiscographyAlbumNamesById == null ? null : StringUtils.trimToNull(artistDiscographyAlbumNamesById.get(albumId));
        if (artistDiscographyAlbumName != null) {
            LOGGER.info("fanart.tv album match: id=" + albumId + ", reason=artist-discography-exact-id-match, title=\"" + artistDiscographyAlbumName + "\"");
            return new AlbumNameResolution(artistDiscographyAlbumName, false);
        }

        String resolvedAlbumName = resolveAlbumTitleFromMusicBrainz(albumId);
        if (resolvedAlbumName != null) {
            if (recordingAlbumNamesById != null) {
                recordingAlbumNamesById.put(albumId, resolvedAlbumName);
            }
            LOGGER.info("fanart.tv album match: id=" + albumId + ", reason=direct-musicbrainz-lookup, title=\"" + resolvedAlbumName + "\"");
            return new AlbumNameResolution(resolvedAlbumName, false);
        }

        String fallbackAlbumName = StringUtils.trimToNull(songAlbumFallback);
        LOGGER.warning("fanart.tv album match failed: id=" + albumId
                + ", reason=no-recording-match-no-discography-match-no-lookup-match"
                + ", fallbackTitle=\"" + StringUtils.defaultString(fallbackAlbumName) + "\"");
        return new AlbumNameResolution(fallbackAlbumName, false);
    }

    protected String resolveAlbumTitleFromMusicBrainz(String albumId) {
        String releaseGroupTitle = musicBrainz.getReleaseGroupTitle(albumId);
        if (StringUtils.isNotBlank(releaseGroupTitle)) {
            LOGGER.info("fanart.tv resolved album title via MusicBrainz release-group: id=" + albumId
                    + ", title=\"" + releaseGroupTitle + "\"");
            return releaseGroupTitle;
        }
        String releaseTitle = musicBrainz.getReleaseTitle(albumId);
        if (StringUtils.isNotBlank(releaseTitle)) {
            LOGGER.info("fanart.tv resolved album title via MusicBrainz release: id=" + albumId
                    + ", title=\"" + releaseTitle + "\"");
            return releaseTitle;
        }
        LOGGER.info("fanart.tv could not resolve album title via MusicBrainz: id=" + albumId);
        return null;
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

    private int safeSize(Collection<?> values) {
        return values == null ? 0 : values.size();
    }

    private static final class AlbumCandidate {
        private final String id;
        private final String albumName;

        private AlbumCandidate(String id, String albumName) {
            this.id = id;
            this.albumName = albumName;
        }
    }

    static final class AlbumNameResolution {
        private final String albumName;
        private final boolean preferredById;

        private AlbumNameResolution(String albumName, boolean preferredById) {
            this.albumName = albumName;
            this.preferredById = preferredById;
        }
    }
}
