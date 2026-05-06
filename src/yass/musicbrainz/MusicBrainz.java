/*
 * Yass Reloaded - Karaoke Editor
 * Copyright (C) 2009-2023 Saruta
 * Copyright (C) 2023 DoubleDee
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package yass.musicbrainz;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.StringUtils;
import yass.VersionUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public class MusicBrainz {
    private static final int BROWSE_LIMIT = 100;
    private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    public MusicBrainzInfo queryMusicBrainz(String artist, String title) throws Exception {
        String query = "recording:\"" + title + "\" AND artist:\"" + artist + "\"";
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

        String urlStr = "https://musicbrainz.org/ws/2/recording/?query=" + encodedQuery + "&fmt=json";
        LOGGER.info(urlStr);
        RecordingsResponse response = fetchFromMusicBrainz(urlStr, RecordingsResponse.class);
        Recording firstRecording = findEarliestRecording(response.getRecordings());
        MusicBrainzInfo musicBrainzInfo = new MusicBrainzInfo();
        if (firstRecording != null) {
            musicBrainzInfo.setRecording(firstRecording);
            musicBrainzInfo.setYear(parseYear(firstRecording.getFirstReleaseDate()));
            Artist recordingArtist = determineArtist(firstRecording);
            musicBrainzInfo.setGenres(getGenresForRecording(recordingArtist));
        }
        return musicBrainzInfo;
    }

    private <T extends MusicBrainzEntity> T fetchFromMusicBrainz(String urlStr,
                                                                 Class<? extends MusicBrainzEntity> responseClass) throws URISyntaxException, IOException {
        URI uri = new URI(urlStr);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Yass Reloaded/" + VersionUtils.getVersion() +
                " ( https://github.com/DoubleDee73/Yass-Reloaded )");

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        T response = (T) gson.fromJson(in, responseClass);
        in.close();
        conn.disconnect();
        return response;
    }

    public static Recording findEarliestRecording(List<Recording> recordings) {
        return recordings.stream()
                         .filter(r -> StringUtils.isNotEmpty(r.firstReleaseDate))
                         .min(Comparator.comparing(r -> parsePartialDate(r.firstReleaseDate)))
                         .orElse(null);
    }

    private static LocalDate parsePartialDate(String dateStr) {
        try {
            if (dateStr.length() == 4) {
                return LocalDate.parse(dateStr + "-01-01", DateTimeFormatter.ISO_LOCAL_DATE);
            } else if (dateStr.length() == 7) {
                return LocalDate.parse(dateStr + "-01", DateTimeFormatter.ISO_LOCAL_DATE);
            } else {
                return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            }
        } catch (Exception e) {
            return LocalDate.MAX;
        }
    }

    private static String parseYear(String dateStr) {
        if (StringUtils.isNotEmpty(dateStr) && dateStr.length() >= 4) {
            return dateStr.substring(0, 4);
        } else {
            return StringUtils.EMPTY;
        }
    }

    public static Artist determineArtist(Recording recording) {
        if (recording == null) {
            return null;
        }
        return recording.getArtistCredit()
                        .stream()
                        .map(ArtistCredit::getArtist)
                        .filter(Objects::nonNull)
                        .filter(it -> StringUtils.isNotEmpty(it.getName()))
                        .filter(it -> !it.getName().equalsIgnoreCase("various artists"))
                        .findFirst()
                        .orElse(null);
    }

    public List<String> getGenresForRecording(Artist artist) throws Exception {
        if (artist == null) {
            return List.of();
        }
        String urlStr = "https://musicbrainz.org/ws/2/artist/" + artist.getId() + "?inc=genres&fmt=json";
        Artist response = fetchFromMusicBrainz(urlStr, Artist.class);
        if (response == null || response.getGenres() == null) {
            return List.of();
        }
        return response.getGenres()
                       .stream()
                       .map(Genre::getName)
                       .filter(StringUtils::isNotEmpty)
                       .map(StringUtils::capitalize)
                       .toList();
    }

    public String getReleaseGroupTitle(String releaseGroupId) {
        if (StringUtils.isBlank(releaseGroupId)) {
            return null;
        }
        try {
            String urlStr = "https://musicbrainz.org/ws/2/release-group/" + releaseGroupId + "?fmt=json";
            ReleaseGroup response = fetchFromMusicBrainz(urlStr, ReleaseGroup.class);
            return response == null ? null : StringUtils.trimToNull(response.getTitle());
        } catch (Exception e) {
            LOGGER.fine("MusicBrainz release-group lookup failed for id=" + releaseGroupId + ": " + e.getMessage());
            return null;
        }
    }

    public String getReleaseTitle(String releaseId) {
        if (StringUtils.isBlank(releaseId)) {
            return null;
        }
        try {
            String urlStr = "https://musicbrainz.org/ws/2/release/" + releaseId + "?fmt=json";
            Release response = fetchFromMusicBrainz(urlStr, Release.class);
            return response == null ? null : StringUtils.trimToNull(response.getTitle());
        } catch (Exception e) {
            LOGGER.fine("MusicBrainz release lookup failed for id=" + releaseId + ": " + e.getMessage());
            return null;
        }
    }

    public List<ReleaseGroup> getArtistReleaseGroups(String artistId) {
        if (StringUtils.isBlank(artistId)) {
            return List.of();
        }
        try {
            return browseArtistReleaseGroups(artistId);
        } catch (Exception e) {
            LOGGER.fine("MusicBrainz artist release-group browse failed for artistId=" + artistId + ": " + e.getMessage());
            return List.of();
        }
    }

    public List<Release> getArtistReleases(String artistId) {
        if (StringUtils.isBlank(artistId)) {
            return List.of();
        }
        try {
            return browseArtistReleases(artistId);
        } catch (Exception e) {
            LOGGER.fine("MusicBrainz artist release browse failed for artistId=" + artistId + ": " + e.getMessage());
            return List.of();
        }
    }

    private List<ReleaseGroup> browseArtistReleaseGroups(String artistId) throws Exception {
        List<ReleaseGroup> releaseGroups = new java.util.ArrayList<>();
        int offset = 0;
        while (true) {
            String urlStr = "https://musicbrainz.org/ws/2/release-group?artist=" + artistId
                    + "&release-group-status=all&limit=" + BROWSE_LIMIT + "&offset=" + offset + "&fmt=json";
            ReleaseGroupsResponse response = fetchFromMusicBrainz(urlStr, ReleaseGroupsResponse.class);
            List<ReleaseGroup> page = response == null || response.getReleaseGroups() == null
                    ? List.of()
                    : response.getReleaseGroups();
            releaseGroups.addAll(page);
            int total = response == null ? 0 : response.getReleaseGroupCount();
            if (response == null || page.size() < BROWSE_LIMIT || (total > 0 && releaseGroups.size() >= total)) {
                break;
            }
            offset += page.size();
        }
        return releaseGroups;
    }

    private List<Release> browseArtistReleases(String artistId) throws Exception {
        List<Release> releases = new java.util.ArrayList<>();
        int offset = 0;
        while (true) {
            String urlStr = "https://musicbrainz.org/ws/2/release?artist=" + artistId
                    + "&limit=" + BROWSE_LIMIT + "&offset=" + offset + "&fmt=json";
            ReleasesResponse response = fetchFromMusicBrainz(urlStr, ReleasesResponse.class);
            List<Release> page = response == null || response.getReleases() == null
                    ? List.of()
                    : response.getReleases();
            releases.addAll(page);
            int total = response == null ? 0 : response.getReleaseCount();
            if (response == null || page.size() < BROWSE_LIMIT || (total > 0 && releases.size() >= total)) {
                break;
            }
            offset += page.size();
        }
        return releases;
    }
}
