package yass.integration.transcription.whisperx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import yass.*;
import yass.alignment.LyricToken;
import yass.alignment.LyricsAlignmentTokenizer;
import yass.integration.transcription.openai.OpenAiTranscriptSegment;
import yass.integration.transcription.openai.OpenAiTranscriptWord;
import yass.integration.transcription.openai.OpenAiTranscriptionResult;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WhisperXTranscriptionService {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(30);

    private final YassProperties properties;

    public WhisperXTranscriptionService(YassProperties properties) {
        this.properties = properties;
    }

    public boolean isConfigured() {
        if (Boolean.parseBoolean(properties.getProperty("whisperx-use-module"))) {
            return StringUtils.isNotBlank(properties.getProperty("whisperx-python"));
        }
        return StringUtils.isNotBlank(properties.getProperty("whisperx-command"));
    }

    public WhisperXTranscriptionRequest createRequest(YassTable table, String selectedAudioTag) {
        if (table == null) {
            throw new IllegalArgumentException("No song is currently open.");
        }
        if (!isConfigured()) {
            throw new IllegalStateException(I18.get("edit_align_transcription_missing_whisperx"));
        }
        if (table.getDuetTrackCount() > 1) {
            throw new IllegalStateException(I18.get("edit_align_transcription_duet_blocked"));
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
        File cacheDir = resolveCacheDir(sourceAudioFile.getParentFile());
        String cacheName = ("#VOCALS".equals(sourceTag) ? "vocals" : "audio") + "-transcript.json";
        File cacheFile = new File(cacheDir, cacheName);
        LOGGER.info("WhisperX transcription request prepared for " + baseName + " using " + sourceTag
                + " (upload: " + uploadAudioFile.getName() + ")");
        String language = resolveIsoLanguage(table.getLanguage());
        return new WhisperXTranscriptionRequest(sourceAudioFile, uploadAudioFile, sourceTag, baseName, cacheDir, cacheFile, language);
    }

    public WhisperXTranscriptionRequest createRequest(File sourceAudioFile, String sourceTag, File cacheDir, String songBaseName) {
        if (sourceAudioFile == null || !sourceAudioFile.isFile()) {
            throw new IllegalArgumentException("The configured transcription source file could not be found.");
        }
        if (!isConfigured()) {
            throw new IllegalStateException(I18.get("edit_align_transcription_missing_whisperx"));
        }
        File uploadAudioFile = resolveUploadAudioFile(sourceAudioFile);
        String baseName = StringUtils.defaultIfBlank(songBaseName, stripExtension(sourceAudioFile.getName()));
        File effectiveCacheDir = cacheDir != null ? cacheDir : resolveCacheDir(sourceAudioFile.getParentFile());
        String cacheName = ("#VOCALS".equals(sourceTag) ? "vocals" : "audio") + "-transcript.json";
        File cacheFile = new File(effectiveCacheDir, cacheName);
        LOGGER.info("WhisperX wizard request prepared for " + baseName + " using " + sourceTag
                + " (upload: " + uploadAudioFile.getName() + ")");
        return new WhisperXTranscriptionRequest(sourceAudioFile, uploadAudioFile, sourceTag, baseName, effectiveCacheDir, cacheFile, null);
    }

    private String stripExtension(String value) {
        int dot = value.lastIndexOf('.');
        return dot > 0 ? value.substring(0, dot) : value;
    }

    private String resolveIsoLanguage(String displayLanguage) {
        if (StringUtils.isBlank(displayLanguage)) return null;
        Locale locale = YassUtils.determineLocale(displayLanguage);
        return locale != null ? locale.getLanguage() : null;
    }

    public boolean hasCachedTranscription(WhisperXTranscriptionRequest request) {
        return request.getCacheFile().isFile();
    }

    public OpenAiTranscriptionResult loadCachedTranscription(WhisperXTranscriptionRequest request, YassTable table) throws IOException {
        String rawJson = Files.readString(request.getCacheFile().toPath(), StandardCharsets.UTF_8);
        JsonObject json = JsonParser.parseString(rawJson).getAsJsonObject();
        LOGGER.info("Loaded cached WhisperX transcription from " + request.getCacheFile().getName());
        return toResult(request, table, json, true);
    }

    public OpenAiTranscriptionResult transcribe(WhisperXTranscriptionRequest request, YassTable table) throws IOException {
        return transcribe(request, table, null);
    }

    public OpenAiTranscriptionResult transcribe(WhisperXTranscriptionRequest request, YassTable table, Consumer<String> progressListener) throws IOException {
        Files.createDirectories(request.getCacheDir().toPath());
        List<String> command = buildCommand(request, progressListener != null);
        ProcessOutput output = executeCommand(command, request.getCacheDir(), progressListener);
        if (output.exitCode != 0 && shouldRetryWithCpuFallback(output.stderr, output.stdout)) {
            List<String> fallbackCommand = buildCpuFallbackCommand(request, progressListener != null);
            LOGGER.warning("WhisperX failed with float16 on the current backend. Retrying with CPU/int8 fallback.");
            if (progressListener != null) {
                progressListener.accept("WhisperX fallback: retrying with CPU/int8.");
            }
            output = executeCommand(fallbackCommand, request.getCacheDir(), progressListener);
        }

        if (output.exitCode != 0) {
            throw new IOException("WhisperX failed.\n\n" + StringUtils.defaultIfBlank(output.stderr, output.stdout));
        }

        File jsonFile = findOutputJson(request);
        if (jsonFile == null || !jsonFile.isFile()) {
            throw new IOException("WhisperX finished but no transcript JSON was produced.");
        }

        String rawJson = Files.readString(jsonFile.toPath(), StandardCharsets.UTF_8);
        Files.writeString(request.getCacheFile().toPath(), rawJson, StandardCharsets.UTF_8);
        JsonObject json = JsonParser.parseString(rawJson).getAsJsonObject();
        LOGGER.info("Saved WhisperX transcription cache to " + request.getCacheFile().getAbsolutePath());
        if (StringUtils.isNotBlank(output.stderr)) {
            LOGGER.fine("WhisperX stderr: " + output.stderr);
        }
        if (StringUtils.isNotBlank(output.stdout)) {
            LOGGER.fine("WhisperX stdout: " + output.stdout);
        }
        return toResult(request, table, json, false);
    }

    private ProcessOutput executeCommand(List<String> command, File workingDirectory, Consumer<String> progressListener) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory);
        builder.redirectErrorStream(false);
        prependFfmpegToPath(builder);
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        try {
            if (progressListener != null) {
                progressListener.accept("WhisperX command: " + String.join(" ", command));
            }
            Process process = builder.start();
            Thread stdoutThread = pumpStream(process.getInputStream(), stdout, progressListener, null);
            Thread stderrThread = pumpStream(process.getErrorStream(), stderr, progressListener, "[stderr] ");
            boolean finished = process.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("WhisperX did not finish within the expected time.");
            }
            stdoutThread.join(TimeUnit.SECONDS.toMillis(2));
            stderrThread.join(TimeUnit.SECONDS.toMillis(2));
            return new ProcessOutput(stdout.toString(), stderr.toString(), process.exitValue());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("WhisperX execution was interrupted.", ex);
        }
    }

    private boolean shouldRetryWithCpuFallback(String stderr, String stdout) {
        String output = (StringUtils.defaultString(stderr) + "\n" + StringUtils.defaultString(stdout)).toLowerCase(Locale.ROOT);
        return output.contains("requested float16 compute type") || output.contains("do not support efficient float16 computation");
    }

    private List<String> buildCpuFallbackCommand(WhisperXTranscriptionRequest request, boolean printProgress) {
        List<String> command = buildCommand(request, printProgress);
        replaceOrAppendOption(command, "--device", "cpu");
        replaceOrAppendOption(command, "--compute_type", "int8");
        return command;
    }

    private void replaceOrAppendOption(List<String> command, String option, String value) {
        int index = command.indexOf(option);
        if (index >= 0) {
            if (index + 1 < command.size()) {
                command.set(index + 1, value);
            } else {
                command.add(value);
            }
            return;
        }
        command.add(option);
        command.add(value);
    }

    private void prependFfmpegToPath(ProcessBuilder pb) {
        String ffmpegPath = properties.getProperty("ffmpegPath");
        if (StringUtils.isBlank(ffmpegPath)) {
            return;
        }
        File ffmpegFile = new File(ffmpegPath);
        String ffmpegDir = ffmpegFile.isDirectory() ? ffmpegFile.getAbsolutePath() : ffmpegFile.getParent();
        if (StringUtils.isBlank(ffmpegDir)) {
            return;
        }
        pb.environment().merge("PATH", ffmpegDir, (existing, added) -> added + File.pathSeparator + existing);
    }

    private static class ProcessOutput {
        private final String stdout;
        private final String stderr;
        private final int exitCode;

        private ProcessOutput(String stdout, String stderr, int exitCode) {
            this.stdout = stdout;
            this.stderr = stderr;
            this.exitCode = exitCode;
        }
    }

    public String buildSummary(OpenAiTranscriptionResult result) {
        StringBuilder summary = new StringBuilder("<html>");
        summary.append("Transcription source: ")
                .append(result.isFromCache() ? "cache" : "WhisperX")
                .append("<br>");
        summary.append("Source: ").append(result.getSourceTag()).append(" -> ")
                .append(result.getSourceAudioFile().getName()).append("<br>");
        if (result.getUploadAudioFile() != null && !result.getSourceAudioFile().equals(result.getUploadAudioFile())) {
            summary.append("Upload file: ").append(escapeHtml(result.getUploadAudioFile().getName())).append("<br>");
        }
        if (result.getCacheFile() != null) {
            summary.append("Cache file: ").append(escapeHtml(result.getCacheFile().getPath())).append("<br>");
        }
        summary.append("Timestamped words: ").append(result.getWords().size()).append("<br>");
        summary.append("Lyric tokens: ").append(result.getLyricTokens().size()).append("<br>");
        summary.append("Sample lyric tokens: ").append(escapeHtml(joinLyricSamples(result.getLyricTokens()))).append("<br>");
        summary.append("Transcript preview: ").append(escapeHtml(trimPreview(result.getTranscriptText())));
        summary.append("</html>");
        return summary.toString();
    }

    private OpenAiTranscriptionResult toResult(WhisperXTranscriptionRequest request, YassTable table, JsonObject json, boolean fromCache) {
        List<OpenAiTranscriptSegment> segments = extractSegments(json);
        String transcriptText = extractTranscriptText(json, segments);
        List<OpenAiTranscriptWord> words = extractWords(json);
        List<LyricToken> lyricTokens = LyricsAlignmentTokenizer.buildTokens(table);
        LOGGER.info("WhisperX transcription ready for " + request.getSongBaseName() + ": "
                + words.size() + " timestamped words, " + lyricTokens.size() + " lyric tokens.");
        return new OpenAiTranscriptionResult(request.getSourceAudioFile(),
                request.getUploadAudioFile(),
                request.getSourceTag(),
                transcriptText,
                words,
                segments,
                lyricTokens,
                fromCache,
                request.getCacheFile());
    }

    private String extractTranscriptText(JsonObject json, List<OpenAiTranscriptSegment> segments) {
        if (segments != null && !segments.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (OpenAiTranscriptSegment segment : segments) {
                String value = segment.getText();
                if (StringUtils.isBlank(value)) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(value.trim());
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }

        JsonElement text = json.get("text");
        if (text != null && text.isJsonPrimitive()) {
            return text.getAsString();
        }
        return "";
    }

    private List<OpenAiTranscriptSegment> extractSegments(JsonObject json) {
        List<OpenAiTranscriptSegment> segments = new ArrayList<>();
        JsonArray segmentArray = json.getAsJsonArray("segments");
        if (segmentArray == null) {
            return segments;
        }
        for (JsonElement segmentElement : segmentArray) {
            JsonObject segment = segmentElement.getAsJsonObject();
            JsonArray segmentWords = segment.getAsJsonArray("words");
            List<OpenAiTranscriptWord> words = new ArrayList<>();
            if (segmentWords != null) {
                for (JsonElement wordElement : segmentWords) {
                    JsonObject wordObject = wordElement.getAsJsonObject();
                    String word = getString(wordObject.get("word"));
                    Integer startMs = getMilliseconds(wordObject.get("start"));
                    Integer endMs = getMilliseconds(wordObject.get("end"));
                    if (StringUtils.isBlank(word) || startMs == null || endMs == null) {
                        continue;
                    }
                    words.add(new OpenAiTranscriptWord(word,
                            LyricsAlignmentTokenizer.normalizeText(word),
                            startMs,
                            Math.max(startMs, endMs)));
                }
            }
            segments.add(new OpenAiTranscriptSegment(
                    getMilliseconds(segment.get("start")) != null ? getMilliseconds(segment.get("start")) : -1,
                    getMilliseconds(segment.get("end")) != null ? getMilliseconds(segment.get("end")) : -1,
                    getString(segment.get("text")).trim(),
                    words));
        }
        return segments;
    }

    private List<OpenAiTranscriptWord> extractWords(JsonObject json) {
        List<OpenAiTranscriptWord> words = new ArrayList<>();
        JsonArray segments = json.getAsJsonArray("segments");
        if (segments == null) {
            return words;
        }
        for (JsonElement segmentElement : segments) {
            JsonObject segment = segmentElement.getAsJsonObject();
            JsonArray segmentWords = segment.getAsJsonArray("words");
            if (segmentWords == null) {
                continue;
            }
            for (JsonElement wordElement : segmentWords) {
                JsonObject wordObject = wordElement.getAsJsonObject();
                String word = getString(wordObject.get("word"));
                Integer startMs = getMilliseconds(wordObject.get("start"));
                Integer endMs = getMilliseconds(wordObject.get("end"));
                if (StringUtils.isBlank(word) || startMs == null || endMs == null) {
                    continue;
                }
                words.add(new OpenAiTranscriptWord(word,
                        LyricsAlignmentTokenizer.normalizeText(word),
                        startMs,
                        Math.max(startMs, endMs)));
            }
        }
        return words;
    }

    private Integer getMilliseconds(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return (int) Math.round(element.getAsDouble() * 1000d);
        } catch (Exception ex) {
            return null;
        }
    }

    private String getString(JsonElement element) {
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private List<String> buildCommand(WhisperXTranscriptionRequest request, boolean printProgress) {
        List<String> command = new ArrayList<>();
        boolean useModule = Boolean.parseBoolean(properties.getProperty("whisperx-use-module"));
        if (useModule) {
            command.add(properties.getProperty("whisperx-python"));
            command.add("-m");
            command.add("whisperx");
        } else {
            command.add(StringUtils.defaultIfBlank(properties.getProperty("whisperx-command"), "whisperx"));
        }
        command.add(request.getUploadAudioFile().getAbsolutePath());
        command.add("--model");
        command.add(StringUtils.defaultIfBlank(properties.getProperty("whisperx-model"), "large-v2"));
        command.add("--output_dir");
        command.add(request.getCacheDir().getAbsolutePath());
        command.add("--output_format");
        command.add("json");

        String language = StringUtils.trimToEmpty(request.getLanguage());
        if (StringUtils.isNotBlank(language)) {
            command.add("--language");
            command.add(language);
        }
        String device = StringUtils.defaultIfBlank(properties.getProperty("whisperx-device"), "auto");
        if (!"auto".equalsIgnoreCase(device)) {
            command.add("--device");
            command.add(device);
        }
        String computeType = StringUtils.defaultIfBlank(properties.getProperty("whisperx-compute-type"), "auto");
        if (!"auto".equalsIgnoreCase(computeType)) {
            command.add("--compute_type");
            command.add(computeType);
        }
        command.add("--print_progress");
        command.add(printProgress ? "True" : "False");
        return command;
    }

    private File findOutputJson(WhisperXTranscriptionRequest request) {
        String uploadBaseName = request.getUploadAudioFile().getName();
        int dot = uploadBaseName.lastIndexOf('.');
        uploadBaseName = dot > 0 ? uploadBaseName.substring(0, dot) : uploadBaseName;
        File direct = new File(request.getCacheDir(), uploadBaseName + ".json");
        if (direct.isFile()) {
            return direct;
        }
        File[] candidates = request.getCacheDir().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (candidates == null || candidates.length == 0) {
            return null;
        }
        File newest = candidates[0];
        for (File candidate : candidates) {
            if (candidate.lastModified() > newest.lastModified()) {
                newest = candidate;
            }
        }
        return newest;
    }

    private File resolveCacheDir(File songDir) {
        String configured = StringUtils.defaultIfBlank(properties.getProperty("whisperx-cache-folder"), ".yass-cache");
        File configuredFile = new File(configured);
        if (configuredFile.isAbsolute()) {
            return configuredFile;
        }
        return new File(songDir, configured);
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
                LOGGER.info("Converted Opus source to temporary WAV for WhisperX input: " + converted.getAbsolutePath());
                return converted;
            }
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to convert Opus source to temporary WAV for WhisperX input.", ex);
        }
        throw new IllegalStateException("The selected .opus file could not be converted to a temporary WAV. Check the FFmpeg configuration under External Tools > Locations.");
    }

    private String trimPreview(String text) {
        if (text == null) {
            return "";
        }
        text = text.trim();
        return text.length() > 160 ? text.substring(0, 157) + "..." : text;
    }

    private String joinLyricSamples(List<LyricToken> lyricTokens) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(5, lyricTokens.size()); i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append(lyricTokens.get(i).getNormalizedText());
        }
        return sb.toString();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\r\n", "<br>")
                    .replace("\n", "<br>")
                    .replace("\r", "<br>");
    }

    private Thread pumpStream(InputStream inputStream, StringBuilder buffer, Consumer<String> progressListener, String prefix) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (buffer) {
                        if (buffer.length() > 0) {
                            buffer.append('\n');
                        }
                        buffer.append(line);
                    }
                    if (progressListener != null && StringUtils.isNotBlank(line)) {
                        progressListener.accept((prefix != null ? prefix : "") + line);
                    }
                }
            } catch (IOException ex) {
                LOGGER.log(Level.FINE, "Failed to read WhisperX process output.", ex);
            }
        }, "whisperx-stream");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
