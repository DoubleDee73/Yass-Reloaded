package yass.integration.separation.mvsep;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import yass.YassProperties;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

public class MvsepAlgorithmCacheService {

    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final String API_URL = "https://mvsep.com/api/app/algorithms";
    private static final String CACHE_FILE_NAME = "mvsep_algorithms.json";
    private static final long CACHE_TTL_DAYS = 7;
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    private final YassProperties properties;

    public MvsepAlgorithmCacheService(YassProperties properties) {
        this.properties = properties;
    }

    public Map<Integer, MvsepAlgorithmInfo> load() {
        Path cacheFile = getCacheFilePath();
        if (isCacheFresh(cacheFile)) {
            try {
                Map<Integer, MvsepAlgorithmInfo> cached = parseFile(cacheFile);
                if (!cached.isEmpty()) {
                    LOGGER.info("MVSEP algorithms loaded from cache: " + cached.size() + " entries.");
                    return cached;
                }
            } catch (Exception ex) {
                LOGGER.warning("MVSEP algorithm cache could not be read, will re-fetch: " + ex.getMessage());
            }
        }

        try {
            String json = fetchJson();
            saveToFile(cacheFile, json);
            Map<Integer, MvsepAlgorithmInfo> result = parseJson(json);
            LOGGER.info("MVSEP algorithms fetched and cached: " + result.size() + " entries.");
            return result;
        } catch (Exception ex) {
            LOGGER.warning("MVSEP algorithm fetch failed: " + ex.getMessage());
            if (Files.exists(cacheFile)) {
                try {
                    Map<Integer, MvsepAlgorithmInfo> stale = parseFile(cacheFile);
                    LOGGER.info("MVSEP algorithms loaded from stale cache: " + stale.size() + " entries.");
                    return stale;
                } catch (Exception ignored) {
                }
            }
            return Collections.emptyMap();
        }
    }

    private Path getCacheFilePath() {
        return Path.of(properties.getUserDir(), CACHE_FILE_NAME);
    }

    private boolean isCacheFresh(Path file) {
        if (!Files.exists(file)) {
            return false;
        }
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            Instant lastModified = attrs.lastModifiedTime().toInstant();
            return Duration.between(lastModified, Instant.now()).toDays() < CACHE_TTL_DAYS;
        } catch (IOException ex) {
            return false;
        }
    }

    private String fetchJson() throws IOException {
        URL url = URI.create(API_URL).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Yass Reloaded MVSEP Integration");
        int code = connection.getResponseCode();
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        return readFully(stream);
    }

    private void saveToFile(Path file, String json) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOGGER.warning("MVSEP algorithm cache could not be written: " + ex.getMessage());
        }
    }

    private Map<Integer, MvsepAlgorithmInfo> parseFile(Path file) throws IOException {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        return parseJson(json);
    }

    public Map<Integer, MvsepAlgorithmInfo> parseJson(String json) {
        LinkedHashMap<Integer, MvsepAlgorithmInfo> result = new LinkedHashMap<>();
        if (StringUtils.isBlank(json)) {
            return result;
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            JsonArray array = resolveArray(root);
            if (array == null) {
                return result;
            }
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject obj = element.getAsJsonObject();
                Integer renderId = getInteger(obj.get("render_id"));
                String name = getString(obj.get("name"));
                Integer orientation = getInteger(obj.get("orientation"));
                if (renderId == null || orientation == null || StringUtils.isBlank(name)) {
                    continue;
                }
                List<MvsepAlgorithmField> fields = parseAlgorithmFields(obj.get("algorithm_fields"));
                String description = parseDescription(obj.get("algorithm_descriptions"));
                result.put(renderId, new MvsepAlgorithmInfo(renderId, name, orientation, fields, description));
            }
        } catch (Exception ex) {
            LOGGER.warning("MVSEP algorithm JSON could not be parsed: " + ex.getMessage());
        }
        return result;
    }

    private JsonArray resolveArray(JsonElement root) {
        if (root.isJsonArray()) {
            return root.getAsJsonArray();
        }
        if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            JsonElement data = obj.get("data");
            if (data != null && data.isJsonArray()) {
                return data.getAsJsonArray();
            }
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                if (entry.getValue().isJsonArray()) {
                    return entry.getValue().getAsJsonArray();
                }
            }
        }
        return null;
    }

    private List<MvsepAlgorithmField> parseAlgorithmFields(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return Collections.emptyList();
        }
        List<MvsepAlgorithmField> fields = new ArrayList<>();
        for (JsonElement item : element.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject obj = item.getAsJsonObject();
            String serverKey = getString(obj.get("server_key"));
            String inputType = getString(obj.get("input_type"));
            if (!"select".equalsIgnoreCase(inputType)) {
                continue;
            }
            String text = getString(obj.get("text"));
            String defaultKey = getString(obj.get("default_key"));
            List<MvsepFieldOption> options = parseFieldOptions(getString(obj.get("options")));
            if (StringUtils.isNotBlank(serverKey) && !options.isEmpty()) {
                fields.add(new MvsepAlgorithmField(serverKey, text, options, defaultKey));
            }
        }
        return fields;
    }

    private List<MvsepFieldOption> parseFieldOptions(String optionsJson) {
        if (StringUtils.isBlank(optionsJson)) {
            return Collections.emptyList();
        }
        try {
            JsonElement parsed = JsonParser.parseString(optionsJson);
            List<MvsepFieldOption> options = new ArrayList<>();
            if (parsed.isJsonArray()) {
                // Format: [{"key": "...", "value": "..."}, ...]
                for (JsonElement item : parsed.getAsJsonArray()) {
                    if (!item.isJsonObject()) {
                        continue;
                    }
                    JsonObject obj = item.getAsJsonObject();
                    String key = getString(obj.get("key"));
                    String value = getString(obj.get("value"));
                    if (StringUtils.isNotBlank(key) && StringUtils.isNotBlank(value)) {
                        options.add(new MvsepFieldOption(key, value));
                    }
                }
            } else if (parsed.isJsonObject()) {
                // Format: {"1": "Label one", "2": "Label two", ...}
                for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
                    String key = entry.getKey();
                    String value = getString(entry.getValue());
                    if (StringUtils.isNotBlank(key) && StringUtils.isNotBlank(value)) {
                        options.add(new MvsepFieldOption(key, value));
                    }
                }
            }
            return options;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private String parseDescription(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return null;
        }
        String fallback = null;
        for (JsonElement item : element.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject obj = item.getAsJsonObject();
            String lang = getString(obj.get("lang"));
            String desc = getString(obj.get("long_description"));
            if (StringUtils.isBlank(desc)) {
                continue;
            }
            if ("en".equalsIgnoreCase(lang)) {
                return desc;
            }
            if (!"ru".equalsIgnoreCase(lang) && fallback == null) {
                fallback = desc;
            }
        }
        return fallback;
    }

    private Integer getInteger(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        try {
            return element.getAsInt();
        } catch (Exception ex) {
            return null;
        }
    }

    private String getString(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    private String readFully(InputStream input) throws IOException {
        if (input == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
    }
}
