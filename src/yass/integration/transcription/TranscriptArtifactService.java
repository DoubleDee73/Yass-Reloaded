package yass.integration.transcription;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import yass.alignment.LyricsAlignmentTokenizer;
import yass.integration.transcription.openai.OpenAiTranscriptSegment;
import yass.integration.transcription.openai.OpenAiTranscriptWord;
import yass.integration.transcription.openai.OpenAiTranscriptionResult;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class TranscriptArtifactService {
    public static final String FILE_NAME = "yass-transcript.json";
    private static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public File save(File songDirectory, OpenAiTranscriptionResult result) throws IOException {
        if (songDirectory == null || result == null) {
            return null;
        }
        Files.createDirectories(songDirectory.toPath());
        File target = new File(songDirectory, FILE_NAME);
        Files.writeString(target.toPath(), GSON.toJson(toJson(result)), StandardCharsets.UTF_8);
        return target;
    }

    public OpenAiTranscriptionResult load(File songDirectory) throws IOException {
        if (songDirectory == null) {
            return null;
        }
        return loadFile(new File(songDirectory, FILE_NAME));
    }

    public OpenAiTranscriptionResult loadFile(File transcriptFile) throws IOException {
        if (transcriptFile == null || !transcriptFile.isFile()) {
            return null;
        }
        JsonObject json = JsonParser.parseString(Files.readString(transcriptFile.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
        return fromJson(json);
    }

    public boolean exists(File songDirectory) {
        return songDirectory != null && new File(songDirectory, FILE_NAME).isFile();
    }

    private JsonObject toJson(OpenAiTranscriptionResult result) {
        JsonObject json = new JsonObject();
        json.addProperty("schemaVersion", SCHEMA_VERSION);
        json.addProperty("sourceTag", result.getSourceTag());
        json.addProperty("textSourceTag", result.getTextSourceTag());
        json.addProperty("timingSourceTag", result.getTimingSourceTag());
        json.addProperty("transcriptText", result.getTranscriptText());
        json.addProperty("fromCache", result.isFromCache());
        addFilePath(json, "sourceAudioFile", result.getSourceAudioFile());
        addFilePath(json, "uploadAudioFile", result.getUploadAudioFile());
        addFilePath(json, "cacheFile", result.getCacheFile());
        json.add("words", wordsToJson(result.getWords()));
        json.add("segments", segmentsToJson(result.getSegments()));
        return json;
    }

    private OpenAiTranscriptionResult fromJson(JsonObject json) {
        String sourceTag = getString(json, "sourceTag");
        String textSourceTag = StringUtils.defaultIfBlank(getString(json, "textSourceTag"), sourceTag);
        String timingSourceTag = StringUtils.defaultIfBlank(getString(json, "timingSourceTag"), sourceTag);
        List<OpenAiTranscriptWord> words = wordsFromJson(json.getAsJsonArray("words"));
        List<OpenAiTranscriptSegment> segments = segmentsFromJson(json.getAsJsonArray("segments"));
        return new OpenAiTranscriptionResult(
                toFile(getString(json, "sourceAudioFile")),
                toFile(getString(json, "uploadAudioFile")),
                sourceTag,
                getString(json, "transcriptText"),
                words,
                segments,
                List.of(),
                getBoolean(json, "fromCache"),
                toFile(getString(json, "cacheFile")),
                textSourceTag,
                timingSourceTag);
    }

    private JsonArray segmentsToJson(List<OpenAiTranscriptSegment> segments) {
        JsonArray array = new JsonArray();
        if (segments == null) {
            return array;
        }
        for (OpenAiTranscriptSegment segment : segments) {
            JsonObject json = new JsonObject();
            json.addProperty("startMs", segment.getStartMs());
            json.addProperty("endMs", segment.getEndMs());
            json.addProperty("text", segment.getText());
            json.add("words", wordsToJson(segment.getWords()));
            array.add(json);
        }
        return array;
    }

    private List<OpenAiTranscriptSegment> segmentsFromJson(JsonArray array) {
        List<OpenAiTranscriptSegment> segments = new ArrayList<>();
        if (array == null) {
            return segments;
        }
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject json = element.getAsJsonObject();
            segments.add(new OpenAiTranscriptSegment(getInt(json, "startMs"),
                    getInt(json, "endMs"),
                    getString(json, "text"),
                    wordsFromJson(json.getAsJsonArray("words"))));
        }
        return segments;
    }

    private JsonArray wordsToJson(List<OpenAiTranscriptWord> words) {
        JsonArray array = new JsonArray();
        if (words == null) {
            return array;
        }
        for (OpenAiTranscriptWord word : words) {
            JsonObject json = new JsonObject();
            json.addProperty("text", word.getText());
            json.addProperty("normalizedText", word.getNormalizedText());
            json.addProperty("startMs", word.getStartMs());
            json.addProperty("endMs", word.getEndMs());
            if (word.getScore() != null) {
                json.addProperty("score", word.getScore());
            }
            array.add(json);
        }
        return array;
    }

    private List<OpenAiTranscriptWord> wordsFromJson(JsonArray array) {
        List<OpenAiTranscriptWord> words = new ArrayList<>();
        if (array == null) {
            return words;
        }
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject json = element.getAsJsonObject();
            Double score = json.has("score") && !json.get("score").isJsonNull() ? json.get("score").getAsDouble() : null;
            String text = getString(json, "text");
            String normalizedText = getString(json, "normalizedText");
            if (StringUtils.isBlank(normalizedText) || normalizedText.length() < Math.min(2, text.length())) {
                normalizedText = LyricsAlignmentTokenizer.normalizeText(text);
            }
            words.add(new OpenAiTranscriptWord(text,
                    normalizedText,
                    getInt(json, "startMs"),
                    getInt(json, "endMs"),
                    score));
        }
        return words;
    }

    private void addFilePath(JsonObject json, String key, File file) {
        if (file != null) {
            json.addProperty(key, file.getAbsolutePath());
        }
    }

    private File toFile(String value) {
        return StringUtils.isBlank(value) ? null : new File(value);
    }

    private String getString(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && !element.isJsonNull() ? element.getAsString() : "";
    }

    private int getInt(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && !element.isJsonNull() ? element.getAsInt() : 0;
    }

    private boolean getBoolean(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }
}
