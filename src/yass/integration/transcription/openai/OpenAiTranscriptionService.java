package yass.integration.transcription.openai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import yass.*;
import yass.alignment.LyricToken;
import yass.alignment.LyricsAlignmentTokenizer;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OpenAiTranscriptionService {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final String API_URL = "https://api.openai.com/v1/audio/transcriptions";
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 300_000;

    private final YassProperties properties;

    public OpenAiTranscriptionService(YassProperties properties) {
        this.properties = properties;
    }

    public boolean isConfigured() {
        return StringUtils.isNotBlank(properties.getProperty("openai-api-key"));
    }

    public OpenAiTranscriptionRequest createRequest(YassTable table) {
        return createRequest(table, UltrastarHeaderTag.AUDIO.toString());
    }

    public OpenAiTranscriptionRequest createRequest(YassTable table, String selectedAudioTag) {
        if (table == null) {
            throw new IllegalArgumentException("No song is currently open.");
        }
        if (!isConfigured()) {
            throw new IllegalStateException("The OpenAI API key is missing.");
        }
        if (isDuet(table)) {
            throw new IllegalStateException("Transcription alignment is currently only available for non-duet songs.");
        }

        String requestedTag = StringUtils.defaultIfBlank(selectedAudioTag, UltrastarHeaderTag.AUDIO.toString());
        if (UltrastarHeaderTag.INSTRUMENTAL.toString().equals(requestedTag)) {
            throw new IllegalStateException(I18.get("edit_align_transcription_switch_from_instrumental"));
        }

        File sourceAudioFile = null;
        String sourceTag = null;

        if (UltrastarHeaderTag.VOCALS.toString().equals(requestedTag)) {
            if (StringUtils.isNotBlank(table.getVocals())) {
                sourceAudioFile = new File(table.getDir(), table.getVocals());
                sourceTag = "#VOCALS";
                if (!sourceAudioFile.isFile()) {
                    sourceAudioFile = null;
                }
            }
            if (sourceAudioFile == null) {
                requestedTag = UltrastarHeaderTag.AUDIO.toString();
            }
        }

        if (sourceAudioFile == null && UltrastarHeaderTag.AUDIO.toString().equals(requestedTag)) {
            String audio = table.getAudio();
            if (StringUtils.isBlank(audio)) {
                throw new IllegalStateException("The current song has no usable #VOCALS or #AUDIO file configured.");
            }
            sourceAudioFile = new File(table.getDir(), audio);
            sourceTag = "#AUDIO";
        }

        if (sourceAudioFile == null || !sourceAudioFile.isFile()) {
            throw new IllegalStateException("The configured transcription source file could not be found.");
        }

        File uploadAudioFile = resolveUploadAudioFile(sourceAudioFile);
        String fileName = sourceAudioFile.getName();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
        LOGGER.info("OpenAI transcription request prepared for " + baseName + " using " + sourceTag
                    + " (upload: " + uploadAudioFile.getName() + ")");
        return new OpenAiTranscriptionRequest(sourceAudioFile,
                                              uploadAudioFile,
                                              sourceTag,
                                              properties.getProperty("openai-model"),
                                              resolveIsoLanguage(table.getLanguage()),
                                              "word",
                                              baseName);
    }

    public boolean hasCachedTranscription(OpenAiTranscriptionRequest request) {
        return getCacheFile(request).isFile();
    }

    public OpenAiTranscriptionResult loadCachedTranscription(OpenAiTranscriptionRequest request, YassTable table) throws IOException {
        File cacheFile = getCacheFile(request);
        if (!cacheFile.isFile()) {
            throw new IOException("No cached transcription file was found.");
        }
        String rawJson = Files.readString(cacheFile.toPath(), StandardCharsets.UTF_8);
        JsonObject json = JsonParser.parseString(rawJson).getAsJsonObject();
        LOGGER.info("Loaded cached OpenAI transcription from " + cacheFile.getName());
        return toResult(request, table, json, true, cacheFile);
    }

    public OpenAiTranscriptionResult transcribe(OpenAiTranscriptionRequest request, YassTable table) throws IOException {
        HttpURLConnection connection = openConnection();
        String boundary = "----YassOpenAiBoundary" + System.currentTimeMillis();
        connection.setRequestProperty("Authorization", "Bearer " + properties.getProperty("openai-api-key"));
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setDoOutput(true);

        try (DataOutputStream output = new DataOutputStream(connection.getOutputStream())) {
            writeFormField(output, boundary, "model", request.getModel());
            writeFormField(output, boundary, "response_format", "verbose_json");
            writeFormField(output, boundary, "timestamp_granularities[]", request.getTimestampGranularity());
            if (StringUtils.isNotBlank(request.getLanguage())) {
                writeFormField(output, boundary, "language", request.getLanguage());
            }
            writeFileField(output, boundary, "file", request.getUploadAudioFile());
            output.writeBytes("--" + boundary + "--\r\n");
            output.flush();
        }

        JsonObject json = readJsonResponse(connection);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("OpenAI transcription response: " + json);
        }
        File cacheFile = persistRawResponse(request, json);
        return toResult(request, table, json, false, cacheFile);
    }

    public String buildSummary(OpenAiTranscriptionResult result) {
        StringBuilder summary = new StringBuilder("<html>");
        summary.append("Transcription source: ")
               .append(result.isFromCache() ? "cache" : "OpenAI API")
               .append("<br>");
        summary.append("Source: ").append(result.getSourceTag()).append(" -> ")
               .append(result.getSourceAudioFile().getName()).append("<br>");
        if (result.getUploadAudioFile() != null && !result.getSourceAudioFile().equals(result.getUploadAudioFile())) {
            summary.append("Upload file: ").append(escapeHtml(result.getUploadAudioFile().getName())).append("<br>");
        }
        if (result.getCacheFile() != null) {
            summary.append("Cache file: ").append(escapeHtml(result.getCacheFile().getName())).append("<br>");
        }
        summary.append("Timestamped words: ").append(result.getWords().size()).append("<br>");
        summary.append("Lyric tokens: ").append(result.getLyricTokens().size()).append("<br>");
        summary.append("Sample lyric tokens: ").append(escapeHtml(joinLyricSamples(result.getLyricTokens()))).append("<br>");
        summary.append("Transcript preview: ").append(escapeHtml(trimPreview(result.getTranscriptText())));
        summary.append("</html>");
        return summary.toString();
    }

    private OpenAiTranscriptionResult toResult(OpenAiTranscriptionRequest request,
                                               YassTable table,
                                               JsonObject json,
                                               boolean fromCache,
                                               File cacheFile) {
        String transcriptText = getString(json.get("text"));
        List<OpenAiTranscriptSegment> segments = extractSegments(json);
        if (StringUtils.isBlank(transcriptText) && !segments.isEmpty()) {
            transcriptText = joinSegmentTexts(segments);
        }
        List<OpenAiTranscriptWord> words = extractWords(json);
        List<LyricToken> lyricTokens = LyricsAlignmentTokenizer.buildTokens(table);
        LOGGER.info("OpenAI transcription ready for " + request.getSongBaseName() + ": "
                    + words.size() + " timestamped words, " + lyricTokens.size() + " lyric tokens.");
        return new OpenAiTranscriptionResult(request.getSourceAudioFile(),
                                             request.getUploadAudioFile(),
                                             request.getSourceTag(),
                                             transcriptText,
                                             words,
                                             segments,
                                             lyricTokens,
                                             fromCache,
                                             cacheFile);
    }

    private File persistRawResponse(OpenAiTranscriptionRequest request, JsonObject json) throws IOException {
        File cacheFile = getCacheFile(request);
        Files.writeString(cacheFile.toPath(), json.toString(), StandardCharsets.UTF_8);
        LOGGER.info("Saved OpenAI transcription cache to " + cacheFile.getAbsolutePath());
        return cacheFile;
    }

    private File getCacheFile(OpenAiTranscriptionRequest request) {
        String configured = StringUtils.defaultIfBlank(properties.getProperty("whisperx-cache-folder"), ".yass-cache");
        File cacheDir = new File(configured).isAbsolute()
                ? new File(configured)
                : new File(request.getSourceAudioFile().getParentFile(), configured);
        String cacheName = ("#VOCALS".equals(request.getSourceTag()) ? "vocals" : "audio") + "-transcript.openai.json";
        try { java.nio.file.Files.createDirectories(cacheDir.toPath()); } catch (Exception ignored) {}
        return new File(cacheDir, cacheName);
    }

    private String resolveIsoLanguage(String displayLanguage) {
        if (StringUtils.isBlank(displayLanguage)) return null;
        Locale locale = YassUtils.determineLocale(displayLanguage);
        return locale != null ? locale.getLanguage() : null;
    }

    private File resolveUploadAudioFile(File sourceAudioFile) {
        String lower = sourceAudioFile.getName().toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".opus")) {
            return sourceAudioFile;
        }

        try {
            YassPlayer player = new YassPlayer(null, properties);
            File converted = player.generateTemp(sourceAudioFile.getAbsolutePath());
            if (converted != null && converted.isFile()) {
                LOGGER.info("Converted Opus source to temporary WAV for OpenAI upload: " + converted.getAbsolutePath());
                return converted;
            }
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to convert Opus source to temporary WAV for OpenAI upload.", ex);
        }
        throw new IllegalStateException("The selected .opus file could not be converted to a temporary WAV. Check the FFmpeg configuration under External Tools > Locations.");
    }

    private boolean isDuet(YassTable table) {
        return table.getDuetTrackCount() > 1;
    }

    private HttpURLConnection openConnection() throws IOException {
        URL url = new URL(API_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod("POST");
        connection.setDoInput(true);
        return connection;
    }

    private JsonObject readJsonResponse(HttpURLConnection connection) throws IOException {
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        String responseBody = readStream(stream);
        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
        if (status < 200 || status >= 300) {
            throw new IOException(extractErrorMessage(json, status));
        }
        return json;
    }

    private List<OpenAiTranscriptSegment> extractSegments(JsonObject json) {
        List<OpenAiTranscriptSegment> segments = new ArrayList<>();
        JsonArray segmentArray = json.getAsJsonArray("segments");
        if (segmentArray == null) {
            return segments;
        }
        for (JsonElement segmentElement : segmentArray) {
            if (!segmentElement.isJsonObject()) {
                continue;
            }
            JsonObject segment = segmentElement.getAsJsonObject();
            JsonArray nestedWords = segment.getAsJsonArray("words");
            List<OpenAiTranscriptWord> segmentWords = new ArrayList<>();
            if (nestedWords != null) {
                for (JsonElement wordElement : nestedWords) {
                    if (!wordElement.isJsonObject()) {
                        continue;
                    }
                    JsonObject word = wordElement.getAsJsonObject();
                    String text = getString(word.get("word"));
                    if (StringUtils.isBlank(text)) {
                        text = getString(word.get("text"));
                    }
                    if (StringUtils.isBlank(text)) {
                        continue;
                    }
                    segmentWords.add(new OpenAiTranscriptWord(text,
                            LyricsAlignmentTokenizer.normalizeText(text),
                            secondsToMillis(word.get("start")),
                            secondsToMillis(word.get("end"))));
                }
            }
            segments.add(new OpenAiTranscriptSegment(
                    secondsToMillis(segment.get("start")),
                    secondsToMillis(segment.get("end")),
                    getString(segment.get("text")).trim(),
                    segmentWords));
        }
        return segments;
    }

    private String joinSegmentTexts(List<OpenAiTranscriptSegment> segments) {
        StringBuilder sb = new StringBuilder();
        for (OpenAiTranscriptSegment segment : segments) {
            if (segment == null || StringUtils.isBlank(segment.getText())) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(segment.getText().trim());
        }
        return sb.toString();
    }

    private List<OpenAiTranscriptWord> extractWords(JsonObject json) {
        List<OpenAiTranscriptWord> words = new ArrayList<>();
        JsonArray wordArray = json.getAsJsonArray("words");
        if (wordArray == null) {
            JsonArray segments = json.getAsJsonArray("segments");
            if (segments != null) {
                for (JsonElement segmentElement : segments) {
                    if (!segmentElement.isJsonObject()) {
                        continue;
                    }
                    JsonObject segment = segmentElement.getAsJsonObject();
                    JsonArray nestedWords = segment.getAsJsonArray("words");
                    if (nestedWords != null) {
                        wordArray = nestedWords;
                        break;
                    }
                }
            }
        }
        if (wordArray == null) {
            return words;
        }
        for (JsonElement element : wordArray) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject word = element.getAsJsonObject();
            String text = getString(word.get("word"));
            if (StringUtils.isBlank(text)) {
                text = getString(word.get("text"));
            }
            if (StringUtils.isBlank(text)) {
                continue;
            }
            int startMs = secondsToMillis(word.get("start"));
            int endMs = secondsToMillis(word.get("end"));
            words.add(new OpenAiTranscriptWord(text,
                                               LyricsAlignmentTokenizer.normalizeText(text),
                                               startMs,
                                               endMs));
        }
        return words;
    }

    private int secondsToMillis(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return -1;
        }
        try {
            double seconds = element.getAsDouble();
            return (int) Math.round(seconds * 1000.0);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private String extractErrorMessage(JsonObject json, int status) {
        JsonObject error = json.getAsJsonObject("error");
        if (error != null && error.has("message")) {
            return error.get("message").getAsString();
        }
        return "OpenAI transcription failed with HTTP " + status + ".";
    }

    private String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "{}";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    private void writeFormField(DataOutputStream output, String boundary, String name, String value) throws IOException {
        output.writeBytes("--" + boundary + "\r\n");
        output.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.writeBytes("\r\n");
    }

    private void writeFileField(DataOutputStream output, String boundary, String fieldName, File file) throws IOException {
        output.writeBytes("--" + boundary + "\r\n");
        output.writeBytes("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\""
                          + file.getName() + "\"\r\n");
        output.writeBytes("Content-Type: " + guessContentType(file) + "\r\n\r\n");
        try (InputStream input = Files.newInputStream(file.toPath())) {
            input.transferTo(output);
        }
        output.writeBytes("\r\n");
    }

    private String guessContentType(File file) {
        String lower = file.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".opus")) return "audio/ogg";
        if (lower.endsWith(".webm")) return "audio/webm";
        if (lower.endsWith(".flac")) return "audio/flac";
        return "application/octet-stream";
    }

    private String trimPreview(String text) {
        if (StringUtils.isBlank(text)) {
            return "No transcript text returned.";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 157) + "...";
    }

    private String joinLyricSamples(List<LyricToken> lyricTokens) {
        if (lyricTokens.isEmpty()) {
            return "none";
        }
        List<String> samples = new ArrayList<>();
        int limit = Math.min(6, lyricTokens.size());
        for (int i = 0; i < limit; i++) {
            samples.add(lyricTokens.get(i).getRawText());
        }
        return String.join(", ", samples);
    }

    private String escapeHtml(String value) {
        return StringUtils.defaultString(value)
                          .replace("&", "&amp;")
                          .replace("<", "&lt;")
                          .replace(">", "&gt;")
                          .replace("\r\n", "<br>")
                          .replace("\n", "<br>")
                          .replace("\r", "<br>");
    }

    private String getString(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        try {
            return element.getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }
}
