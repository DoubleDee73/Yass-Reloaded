package yass.usdb;

import org.apache.commons.lang3.StringUtils;
import yass.YassSearchNormalizer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;
import java.util.logging.Level;

public class UsdbClient {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final Pattern SONG_ROW_PATTERN = Pattern.compile(
            "<tr[^>]*>(.*?)</tr>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SONG_CELL_PATTERN = Pattern.compile(
            "<td[^>]*onclick\\s*=\\s*[\"']show_detail\\((\\d+)\\)[\"'][^>]*>(.*?)</td>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern INPUT_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern TEXTAREA_PATTERN = Pattern.compile("<textarea[^>]*>(.*?)</textarea>",
                                                                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern BPM_PATTERN = Pattern.compile(">BPM<.*?<td[^>]*>(.*?)</td>",
                                                               Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern GAP_PATTERN = Pattern.compile(">GAP<.*?<td[^>]*>(.*?)</td>",
                                                               Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern COVER_PATTERN = Pattern.compile("<img[^>]+src=[\"']([^\"']*cover/[^\"']+)[\"']",
                                                                 Pattern.CASE_INSENSITIVE);

    private final UsdbSessionService sessionService;

    public UsdbClient(UsdbSessionService sessionService) {
        this.sessionService = sessionService;
    }

    public List<UsdbSongSummary> searchSongs(String artist, String title) throws IOException, InterruptedException {
        sessionService.resetSessionInfoRetryLimit();
        LOGGER.info(() -> "USDB web search start artist='" + StringUtils.defaultString(artist)
                + "' title='" + StringUtils.defaultString(title) + "'");
        LinkedHashMap<Integer, UsdbSongSummary> merged = new LinkedHashMap<>();
        for (UsdbSongSummary song : executeSearch(artist, title)) {
            merged.put(song.songId(), song);
        }
        String normalizedArtist = YassSearchNormalizer.normalizeForSearch(artist);
        String normalizedTitle = YassSearchNormalizer.normalizeForSearch(title);
        boolean tryNormalizedFallback = (!StringUtils.isBlank(normalizedArtist) || !StringUtils.isBlank(normalizedTitle))
                && (!StringUtils.equals(normalizedArtist, StringUtils.trimToEmpty(artist).toLowerCase())
                || !StringUtils.equals(normalizedTitle, StringUtils.trimToEmpty(title).toLowerCase()));
        if (merged.isEmpty() && tryNormalizedFallback) {
            LOGGER.info(() -> "USDB web search retry with normalized artist='" + normalizedArtist
                    + "' title='" + normalizedTitle + "'");
            for (UsdbSongSummary song : executeSearch(normalizedArtist, normalizedTitle)) {
                merged.put(song.songId(), song);
            }
        }
        LOGGER.info(() -> "USDB web search done artist='" + StringUtils.defaultString(artist)
                + "' title='" + StringUtils.defaultString(title)
                + "' results=" + merged.size());
        return new ArrayList<>(merged.values());
    }

    private List<UsdbSongSummary> executeSearch(String artist, String title) throws IOException, InterruptedException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("order", "id");
        form.put("ud", "desc");
        form.put("limit", String.valueOf(UsdbConstants.DEFAULT_LIMIT));
        if (StringUtils.isNotBlank(artist)) {
            form.put("interpret", artist.trim());
            form.put("artist", artist.trim());
        }
        if (StringUtils.isNotBlank(title)) {
            form.put("title", title.trim());
        }

        LOGGER.info(() -> "USDB POST index.php?link=list form=" + form);
        String html = sessionService.postForm("index.php", Map.of("link", "list"), form);
        List<UsdbSongSummary> results = new ArrayList<>();
        Matcher rowMatcher = SONG_ROW_PATTERN.matcher(html);
        while (rowMatcher.find()) {
            String rowHtml = rowMatcher.group(1);
            if (!StringUtils.containsIgnoreCase(rowHtml, "show_detail(")) {
                continue;
            }
            List<String> cells = new ArrayList<>();
            Integer songId = null;
            Matcher cellMatcher = SONG_CELL_PATTERN.matcher(rowHtml);
            while (cellMatcher.find()) {
                if (songId == null) {
                    songId = Integer.parseInt(cellMatcher.group(1));
                }
                cells.add(cellMatcher.group(2));
            }
            if (songId == null || cells.size() < 8) {
                continue;
            }
            results.add(new UsdbSongSummary(
                    songId,
                    cleanCell(cells.get(0)),
                    cleanCell(cells.get(1)),
                    cleanCell(cells.get(2)),
                    cleanCell(cells.get(6)),
                    cleanCell(cells.get(3)),
                    cleanCell(cells.get(7)),
                    parseBooleanCell(cells.get(5))));
        }
        LOGGER.info(() -> "USDB parsed search results count=" + results.size()
                + " for artist='" + StringUtils.defaultString(artist)
                + "' title='" + StringUtils.defaultString(title) + "'");
        if (results.isEmpty()) {
            LOGGER.info(() -> "USDB search html snippet: "
                    + StringUtils.abbreviate(StringUtils.normalizeSpace(html), 400));
        } else {
            UsdbSongSummary first = results.get(0);
            LOGGER.info(() -> "USDB first search result id=" + first.songId()
                    + " artist='" + first.artist() + "' title='" + first.title() + "'");
        }
        return results;
    }

    public UsdbSongDetails getSongDetails(int songId) throws IOException, InterruptedException {
        sessionService.resetSessionInfoRetryLimit();
        String html = sessionService.get("index.php", Map.of("link", "detail", "id", String.valueOf(songId)));
        String bpm = firstMatch(BPM_PATTERN, html);
        String gap = firstMatch(GAP_PATTERN, html);
        String cover = firstMatch(COVER_PATTERN, html);
        if (StringUtils.isNotBlank(cover) && !cover.startsWith("http")) {
            cover = UsdbConstants.BASE_URL + cover.replaceFirst("^/+", "");
        }
        return new UsdbSongDetails(songId,
                                   "",
                                   "",
                                   cleanCell(bpm),
                                   cleanCell(gap),
                                   cover,
                                   UsdbConstants.BASE_URL + "index.php?link=detail&id=" + songId);
    }

    public String downloadSongText(int songId) throws IOException, InterruptedException {
        sessionService.resetSessionInfoRetryLimit();
        String html = sessionService.postForm("index.php",
                                              Map.of("link", "gettxt", "id", String.valueOf(songId)),
                                              Map.of("wd", "1"));
        Matcher matcher = TEXTAREA_PATTERN.matcher(html);
        if (!matcher.find()) {
            throw new IOException("USDB song text could not be parsed.");
        }
        return UsdbSessionService.decodeHtml(matcher.group(1)).replace("\r\n", "\n");
    }

    private String firstMatch(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String cleanCell(String html) {
        if (html == null) {
            return "";
        }
        String noTags = INPUT_TAG_PATTERN.matcher(html).replaceAll("");
        return UsdbSessionService.decodeHtml(noTags).replace('\u00A0', ' ').trim();
    }

    private boolean parseBooleanCell(String html) {
        String normalized = StringUtils.lowerCase(cleanCell(html));
        return normalized.contains("yes")
                || normalized.contains("ja")
                || normalized.contains("true")
                || normalized.contains("*");
    }
}
