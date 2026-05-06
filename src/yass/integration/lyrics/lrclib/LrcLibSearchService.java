package yass.integration.lyrics.lrclib;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import yass.VersionUtils;
import yass.alignment.LyricsAlignmentTokenizer;
import yass.integration.transcription.openai.OpenAiTranscriptSegment;
import yass.integration.transcription.openai.OpenAiTranscriptWord;
import yass.integration.transcription.openai.OpenAiTranscriptionResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLHandshakeException;

public class LrcLibSearchService {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final String API_BASE_URL = "https://lrclib.net/api";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]");
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofMillis(CONNECT_TIMEOUT_MS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    public LrcLibSearchResponse search(String artist, String title) throws IOException {
        return search(artist, title, false);
    }

    public LrcLibSearchResponse search(String artist, String title, boolean allowInsecureTlsFallback) throws IOException {
        String normalizedArtist = StringUtils.trimToEmpty(artist);
        String normalizedTitle = StringUtils.trimToEmpty(title);
        if (StringUtils.isBlank(normalizedArtist) || StringUtils.isBlank(normalizedTitle)) {
            return new LrcLibSearchResponse(List.of(), null);
        }

        LrcLibCandidate preferredCandidate = fetchExactMatch(normalizedArtist, normalizedTitle, allowInsecureTlsFallback);
        List<LrcLibCandidate> searchCandidates = fetchSearchResults(normalizedArtist, normalizedTitle, allowInsecureTlsFallback);
        Map<Long, LrcLibCandidate> deduplicated = new LinkedHashMap<>();
        if (preferredCandidate != null) {
            deduplicated.put(preferredCandidate.getId(), preferredCandidate);
        }
        for (LrcLibCandidate candidate : searchCandidates) {
            deduplicated.putIfAbsent(candidate.getId(), candidate);
        }

        List<LrcLibCandidate> candidates = new ArrayList<>(deduplicated.values());
        candidates.sort(Comparator
                .comparing((LrcLibCandidate candidate) -> preferredCandidate == null || candidate.getId() != preferredCandidate.getId())
                .thenComparing((LrcLibCandidate candidate) -> !candidate.hasSyncedLyrics())
                .thenComparing(LrcLibCandidate::getArtistName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(LrcLibCandidate::getTrackName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(LrcLibCandidate::getAlbumName, String.CASE_INSENSITIVE_ORDER));
        return new LrcLibSearchResponse(candidates, preferredCandidate != null ? preferredCandidate.getId() : null);
    }

    public OpenAiTranscriptionResult toTranscriptionResult(LrcLibCandidate candidate) {
        if (candidate == null) {
            return null;
        }

        List<OpenAiTranscriptSegment> segments = parseSyncedLyrics(candidate.getSyncedLyrics(), candidate.getDurationSeconds());
        List<OpenAiTranscriptWord> words = new ArrayList<>();
        for (OpenAiTranscriptSegment segment : segments) {
            words.addAll(segment.getWords());
        }

        String transcriptText = StringUtils.defaultIfBlank(normalizeLyrics(candidate.getPlainLyrics()),
                segments.stream().map(OpenAiTranscriptSegment::getText).filter(StringUtils::isNotBlank).reduce((a, b) -> a + "\n" + b).orElse(""));

        return new OpenAiTranscriptionResult(
                null,
                null,
                "#LRCLIB",
                transcriptText,
                words,
                segments,
                List.of(),
                false,
                null
        );
    }

    private LrcLibCandidate fetchExactMatch(String artist, String title, boolean allowInsecureTlsFallback) throws IOException {
        String path = "/get?artist_name=" + encode(artist) + "&track_name=" + encode(title);
        JsonElement response = fetchJson(path, true, allowInsecureTlsFallback);
        if (response == null || !response.isJsonObject()) {
            return null;
        }
        return parseCandidate(response.getAsJsonObject());
    }

    private List<LrcLibCandidate> fetchSearchResults(String artist, String title, boolean allowInsecureTlsFallback) throws IOException {
        String path = "/search?artist_name=" + encode(artist) + "&track_name=" + encode(title);
        JsonElement response = fetchJson(path, false, allowInsecureTlsFallback);
        if (response == null || !response.isJsonArray()) {
            return List.of();
        }

        List<LrcLibCandidate> candidates = new ArrayList<>();
        JsonArray array = response.getAsJsonArray();
        for (JsonElement element : array) {
            if (element != null && element.isJsonObject()) {
                LrcLibCandidate candidate = parseCandidate(element.getAsJsonObject());
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
        }
        return candidates;
    }

    private JsonElement fetchJson(String pathAndQuery, boolean accept404, boolean allowInsecureTlsFallback) throws IOException {
        try {
            URI uri = URI.create(API_BASE_URL + pathAndQuery);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .timeout(java.time.Duration.ofMillis(READ_TIMEOUT_MS))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Yass Reloaded/" + VersionUtils.getVersion()
                            + " ( https://github.com/DoubleDee73/Yass-Reloaded )")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int responseCode = response.statusCode();
            if (accept404 && responseCode == 404) {
                return null;
            }
            String body = response.body();
            if (responseCode >= 400) {
                throw new IOException("LRCLib request failed with HTTP " + responseCode + ": " + body);
            }
            return JsonParser.parseString(body);
        } catch (IOException ex) {
            if (isSslHandshakeFailure(ex)) {
                LOGGER.info("LRCLib HTTPS handshake failed in JVM, retrying via curl: " + ex.getMessage());
                return fetchJsonViaCurl(pathAndQuery, accept404, allowInsecureTlsFallback);
            }
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("LRCLib request interrupted.", ex);
        }
    }

    private JsonElement fetchJsonViaCurl(String pathAndQuery, boolean accept404, boolean allowInsecureTlsFallback) throws IOException {
        URI uri = URI.create(API_BASE_URL + pathAndQuery);
        List<String> command = new ArrayList<>();
        command.add("curl");
        command.add("-L");
        command.add("-sS");
        if (allowInsecureTlsFallback) {
            command.add("-k");
        }
        command.add("-A");
        command.add("Yass Reloaded/" + VersionUtils.getVersion() + " ( https://github.com/DoubleDee73/Yass-Reloaded )");
        command.add("-H");
        command.add("Accept: application/json");
        command.add("-w");
        command.add("\n%{http_code}");
        command.add(uri.toString());
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String output;
        try (InputStream stream = process.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder body = new StringBuilder();
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (!first) {
                    body.append('\n');
                }
                body.append(line);
                first = false;
            }
            output = body.toString();
        }
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                if (!allowInsecureTlsFallback && exitCode == 60 && output.contains("SEC_E_UNTRUSTED_ROOT")) {
                    throw new LrcLibCertificateException("LRCLib's TLS certificate is not trusted on this system.");
                }
                throw new IOException("LRCLib curl fallback failed with exit code " + exitCode + ": " + output);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("LRCLib curl fallback interrupted.", ex);
        }

        int split = output.lastIndexOf('\n');
        if (split < 0) {
            throw new IOException("LRCLib curl fallback returned no HTTP status.");
        }
        String body = output.substring(0, split);
        String statusText = output.substring(split + 1).trim();
        int statusCode;
        try {
            statusCode = Integer.parseInt(statusText);
        } catch (NumberFormatException ex) {
            throw new IOException("LRCLib curl fallback returned an invalid HTTP status: " + statusText, ex);
        }
        if (accept404 && statusCode == 404) {
            return null;
        }
        if (statusCode >= 400) {
            if (statusCode == 403 && (body.contains("block.opendns.com") || body.contains("ablock"))) {
                throw new IOException("LRCLib is blocked by a DNS or web content filter on this network.");
            }
            throw new IOException("LRCLib request failed with HTTP " + statusCode + ": " + body);
        }
        return JsonParser.parseString(body);
    }

    private boolean isSslHandshakeFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SSLHandshakeException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private LrcLibCandidate parseCandidate(JsonObject json) {
        if (json == null) {
            return null;
        }
        long id = getLong(json, "id");
        return new LrcLibCandidate(
                id,
                getString(json, "trackName"),
                getString(json, "artistName"),
                getString(json, "albumName"),
                getRoundedInt(json, "duration"),
                getBoolean(json, "instrumental"),
                getString(json, "plainLyrics"),
                getString(json, "syncedLyrics"));
    }

    private List<OpenAiTranscriptSegment> parseSyncedLyrics(String syncedLyrics, int durationSeconds) {
        if (StringUtils.isBlank(syncedLyrics)) {
            return List.of();
        }

        List<TimedLine> timedLines = new ArrayList<>();
        for (String rawLine : syncedLyrics.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            Matcher matcher = TIMESTAMP_PATTERN.matcher(rawLine);
            List<Integer> timestamps = new ArrayList<>();
            int endOfLastTimestamp = 0;
            while (matcher.find()) {
                timestamps.add(toMilliseconds(matcher.group(1), matcher.group(2), matcher.group(3)));
                endOfLastTimestamp = matcher.end();
            }
            if (timestamps.isEmpty()) {
                continue;
            }
            String text = StringUtils.trimToEmpty(rawLine.substring(endOfLastTimestamp));
            if (StringUtils.isBlank(text)) {
                continue;
            }
            for (Integer timestamp : timestamps) {
                timedLines.add(new TimedLine(timestamp, text));
            }
        }
        timedLines.sort(Comparator.comparingInt(TimedLine::startMs));
        if (timedLines.isEmpty()) {
            return List.of();
        }

        List<OpenAiTranscriptSegment> segments = new ArrayList<>();
        int fallbackEndMs = durationSeconds > 0 ? durationSeconds * 1000 : timedLines.getLast().startMs() + 2000;
        for (int i = 0; i < timedLines.size(); i++) {
            TimedLine current = timedLines.get(i);
            int nextStartMs = i + 1 < timedLines.size() ? timedLines.get(i + 1).startMs() : fallbackEndMs;
            int endMs = Math.max(current.startMs() + 1, nextStartMs);
            List<OpenAiTranscriptWord> words = buildWords(current.text(), current.startMs(), endMs);
            segments.add(new OpenAiTranscriptSegment(current.startMs(), endMs, current.text(), words));
        }
        return segments;
    }

    private List<OpenAiTranscriptWord> buildWords(String line, int startMs, int endMs) {
        List<String> tokens = new ArrayList<>();
        for (String token : StringUtils.defaultString(line).trim().split("\\s+")) {
            String trimmed = StringUtils.trimToEmpty(token);
            if (StringUtils.isNotBlank(trimmed)) {
                tokens.add(trimmed);
            }
        }
        if (tokens.isEmpty()) {
            return List.of();
        }

        List<OpenAiTranscriptWord> words = new ArrayList<>(tokens.size());
        int span = Math.max(1, endMs - startMs);
        for (int i = 0; i < tokens.size(); i++) {
            int wordStart = startMs + (int) Math.round((double) span * i / tokens.size());
            int wordEnd = startMs + (int) Math.round((double) span * (i + 1) / tokens.size());
            if (wordEnd <= wordStart) {
                wordEnd = wordStart + 1;
            }
            String token = tokens.get(i);
            words.add(new OpenAiTranscriptWord(token, LyricsAlignmentTokenizer.normalizeText(token), wordStart, wordEnd));
        }
        return words;
    }

    private int toMilliseconds(String minutePart, String secondPart, String fractionPart) {
        int minutes = Integer.parseInt(minutePart);
        int seconds = Integer.parseInt(secondPart);
        int millis = 0;
        if (StringUtils.isNotBlank(fractionPart)) {
            String padded = StringUtils.rightPad(fractionPart, 3, '0');
            millis = Integer.parseInt(padded.substring(0, 3));
        }
        return minutes * 60_000 + seconds * 1_000 + millis;
    }

    private String normalizeLyrics(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        normalized = normalized.replaceAll("(?m)[ \t]+$", "");
        normalized = normalized.replaceAll("\n{3,}", "\n\n");
        return StringUtils.strip(normalized, "\n");
    }

    private String encode(String value) {
        return URLEncoder.encode(StringUtils.defaultString(value), StandardCharsets.UTF_8);
    }

    private String getString(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && !element.isJsonNull() ? element.getAsString() : "";
    }

    private long getLong(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && !element.isJsonNull() ? element.getAsLong() : 0L;
    }

    private int getRoundedInt(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return 0;
        }
        return (int) Math.round(element.getAsDouble());
    }

    private boolean getBoolean(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }

    private record TimedLine(int startMs, String text) {
    }
}
