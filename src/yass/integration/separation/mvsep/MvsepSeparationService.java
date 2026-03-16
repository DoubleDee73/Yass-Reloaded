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

package yass.integration.separation.mvsep;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import yass.YassProperties;
import yass.YassTable;
import yass.integration.separation.SeparationProgressListener;
import yass.integration.separation.SeparationRequest;
import yass.integration.separation.SeparationResult;
import yass.integration.separation.SeparationService;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MvsepSeparationService implements SeparationService {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final String API_BASE = "https://mvsep.com/api";
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 300_000;
    private static final int MAX_POLLS = 1_440;

    private final YassProperties properties;

    public MvsepSeparationService(YassProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.isNotBlank(properties.getProperty("mvsep-api-token"));
    }

    @Override
    public SeparationRequest createRequest(YassTable table) {
        return createRequest(table, properties.getProperty("mvsep-model"), properties.getProperty("mvsep-output-format"));
    }

    public SeparationRequest createRequest(File outputDirectory, File audioFile, String songBaseName) {
        if (outputDirectory == null) {
            throw new IllegalArgumentException("No song directory was provided for the separation output.");
        }
        if (audioFile == null || !audioFile.isFile()) {
            throw new IllegalArgumentException("The source audio file for MVSEP could not be found.");
        }
        if (!isConfigured()) {
            throw new IllegalStateException("The MVSEP API token is missing.");
        }
        String baseName = StringUtils.defaultIfBlank(songBaseName, stripExtension(audioFile.getName()));
        MvsepModel model = MvsepModel.fromValue(properties.getProperty("mvsep-model"));
        MvsepOutputFormat outputFormat = MvsepOutputFormat.fromValue(properties.getProperty("mvsep-output-format"));
        LOGGER.info("MVSEP wizard request prepared for " + baseName + " using model " + model.getValue() + " and format " + outputFormat.getValue());
        return new SeparationRequest(outputDirectory.getAbsolutePath(), audioFile, model.getValue(), outputFormat.getValue(), baseName);
    }

    public SeparationRequest createRequest(YassTable table, String modelValue, String outputFormatValue) {
        if (table == null) {
            throw new IllegalArgumentException("No song is currently open.");
        }
        if (!isConfigured()) {
            throw new IllegalStateException("The MVSEP API token is missing.");
        }
        String audio = table.getAudio();
        if (StringUtils.isBlank(audio)) {
            throw new IllegalStateException("The current song has no #AUDIO file configured.");
        }

        File audioFile = new File(table.getDir(), audio);
        if (!audioFile.isFile()) {
            throw new IllegalStateException("The configured #AUDIO file could not be found.");
        }

        MvsepModel model = MvsepModel.fromValue(modelValue);
        MvsepOutputFormat outputFormat = MvsepOutputFormat.fromValue(outputFormatValue);
        String songBaseName = buildSongBaseName(table, audioFile);
        LOGGER.info("MVSEP request prepared for " + songBaseName + " using model " + model.getValue() + " and format " + outputFormat.getValue());
        return new SeparationRequest(table.getDir(), audioFile, model.getValue(), outputFormat.getValue(),
                                     songBaseName);
    }

    public MvsepAccountInfo fetchAccountInfo() throws IOException {
        JsonObject response = getJson("/app/user?api_token=" + encode(properties.getProperty("mvsep-api-token")), true);
        JsonObject data = getNestedObject(response, "data");
        if (data == null) {
            return null;
        }
        return new MvsepAccountInfo(getString(data.get("email")),
                                    getBoolean(data.get("premium_enabled")),
                                    getIntegerValue(data.get("premium_minutes"), 0),
                                    getIntegerValue(data.get("current_queue"), 0));
    }

    public Map<Integer, MvsepAlgorithmInfo> fetchAlgorithms() throws IOException {
        JsonObject response = getJson("/app/algorithms", false);
        LinkedHashMap<Integer, MvsepAlgorithmInfo> algorithms = new LinkedHashMap<>();
        collectAlgorithms(response, algorithms);
        return algorithms;
    }

    public Integer fetchPlanQueue() throws IOException {
        JsonObject response = getJson("/app/queue?api_token=" + encode(properties.getProperty("mvsep-api-token")), true);
        JsonObject data = getNestedObject(response, "data");
        if (data == null) {
            return null;
        }
        Integer queueCount = getInteger(data.get("queue_count"));
        if (queueCount != null) {
            return queueCount;
        }
        JsonArray queue = data.getAsJsonArray("queue");
        return queue == null ? null : queue.size();
    }

    @Override
    public SeparationResult startSeparation(SeparationRequest request, SeparationProgressListener progressListener) throws Exception {
        return startSeparation(request, progressListener, null);
    }

    public SeparationResult startSeparation(SeparationRequest request,
                                            SeparationProgressListener progressListener,
                                            Consumer<String> jobCreatedListener) throws Exception {
        Objects.requireNonNull(request, "request");
        SeparationProgressListener listener = progressListener == null ? status -> { } : progressListener;
        MvsepModel model = MvsepModel.fromValue(request.getModel());
        MvsepOutputFormat outputFormat = MvsepOutputFormat.fromValue(request.getOutputFormat());

        LOGGER.info("Starting MVSEP separation for " + request.getSongBaseName() + " with model " + model.getValue() + " and output format " + outputFormat.getValue());
        listener.onStatusChanged("Uploading audio to MVSEP...");
        String hash = createRemoteJob(request, model, outputFormat);
        LOGGER.info("MVSEP job created with hash " + hash);
        if (jobCreatedListener != null) {
            jobCreatedListener.accept(hash);
        }
        return awaitSeparationResult(request, model, outputFormat, hash, listener);
    }

    public SeparationResult resumeSeparation(SeparationRequest request,
                                             String hash,
                                             SeparationProgressListener progressListener) throws Exception {
        Objects.requireNonNull(request, "request");
        if (StringUtils.isBlank(hash)) {
            throw new IllegalArgumentException("The MVSEP job hash is missing.");
        }
        SeparationProgressListener listener = progressListener == null ? status -> { } : progressListener;
        MvsepModel model = MvsepModel.fromValue(request.getModel());
        MvsepOutputFormat outputFormat = MvsepOutputFormat.fromValue(request.getOutputFormat());
        LOGGER.info("Resuming MVSEP monitoring for " + request.getSongBaseName() + " with hash " + hash);
        listener.onStatusChanged("Resuming MVSEP monitoring...");
        return awaitSeparationResult(request, model, outputFormat, hash, listener);
    }

    public void cancelSeparation(String hash) throws IOException {
        if (StringUtils.isBlank(hash)) {
            throw new IllegalArgumentException("The MVSEP job hash is missing.");
        }
        LOGGER.info("Cancelling MVSEP separation " + hash);
        HttpURLConnection connection = openConnection("/separation/cancel");
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        String body = "api_token=" + encode(properties.getProperty("mvsep-api-token")) + "&hash=" + encode(hash);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body.getBytes(StandardCharsets.UTF_8));
        }
        JsonObject response = readJsonResponse(connection);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("MVSEP cancel response: " + response);
        }
        ensureSuccess(response, "MVSEP could not cancel the separation job.");
    }

    private SeparationResult awaitSeparationResult(SeparationRequest request,
                                                   MvsepModel model,
                                                   MvsepOutputFormat outputFormat,
                                                   String hash,
                                                   SeparationProgressListener listener) throws Exception {
        JsonObject result = null;
        int pollIntervalSeconds = Math.max(5, parsePollInterval(properties.getProperty("mvsep-poll-interval")));
        for (int poll = 0; poll < MAX_POLLS; poll++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("The separation job was interrupted.");
            }
            result = getJobResult(hash);
            String status = extractStatus(result);
            LOGGER.info("MVSEP job " + hash + " status: " + status);
            if ("done".equals(status)) {
                break;
            }
            if ("failed".equals(status) || "error".equals(status) || "canceled".equals(status)) {
                throw new IOException(extractFailureMessage(result, status));
            }

            listener.onStatusChanged(toStatusMessage(result, status));
            Thread.sleep(pollIntervalSeconds * 1000L);
        }

        if (result == null) {
            throw new IOException("MVSEP did not return a job result.");
        }
        if (!"done".equals(extractStatus(result))) {
            throw new IOException("MVSEP did not finish the separation job in time.");
        }

        LinkedHashMap<String, DownloadDescriptor> stems = extractStemDownloads(result, model);
        LOGGER.info("MVSEP stem mapping: " + stems);
        if (stems.isEmpty()) {
            throw new IOException("MVSEP finished, but no downloadable stems were found in the response.");
        }

        File songDirectory = new File(request.getSongDirectory());
        String baseName = request.getSongBaseName();
        File vocalsFile = null;
        File leadFile = null;
        File instrumentalFile = null;
        File instrumentalBackingFile = null;

        if (stems.containsKey("vocals")) {
            listener.onStatusChanged("Downloading vocals...");
            vocalsFile = downloadStem(stems.get("vocals").url(), songDirectory, baseName, "Vocals", outputFormat.getExtension());
        }

        if (stems.containsKey("lead")) {
            listener.onStatusChanged("Downloading lead vocals...");
            leadFile = downloadStem(stems.get("lead").url(), songDirectory, baseName, "Lead", outputFormat.getExtension());
        }

        if (stems.containsKey("instrumental")) {
            listener.onStatusChanged("Downloading instrumental...");
            instrumentalFile = downloadStem(stems.get("instrumental").url(), songDirectory, baseName, "Instrumental", outputFormat.getExtension());
        }

        if (stems.containsKey("instrumental-backing")) {
            listener.onStatusChanged("Downloading instrumental + backing...");
            instrumentalBackingFile = downloadStem(stems.get("instrumental-backing").url(), songDirectory, baseName, "Instrumental + Backing", outputFormat.getExtension());
        }

        if (vocalsFile == null && leadFile == null && instrumentalFile == null && instrumentalBackingFile == null) {
            throw new IOException("MVSEP finished, but no expected stems could be downloaded.");
        }

        return new SeparationResult(vocalsFile, leadFile, instrumentalFile, instrumentalBackingFile);
    }

    private int parsePollInterval(String value) {
        try {
            return Integer.parseInt(StringUtils.defaultIfBlank(value, "15"));
        } catch (NumberFormatException ex) {
            return 15;
        }
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String createRemoteJob(SeparationRequest request, MvsepModel model, MvsepOutputFormat outputFormat) throws IOException {
        String boundary = "----YassMvsepBoundary" + System.currentTimeMillis();
        HttpURLConnection connection = openConnection("/separation/create");
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream output = connection.getOutputStream();
             DataOutputStream data = new DataOutputStream(output)) {
            writeFormField(data, boundary, "api_token", properties.getProperty("mvsep-api-token"));
            writeFormField(data, boundary, "sep_type", Integer.toString(model.getSepType()));
            if (StringUtils.isNotBlank(model.getAddOpt1())) {
                writeFormField(data, boundary, "add_opt1", model.getAddOpt1());
            }
            if (StringUtils.isNotBlank(model.getAddOpt2())) {
                writeFormField(data, boundary, "add_opt2", model.getAddOpt2());
            }
            writeFormField(data, boundary, "output_format", outputFormat.getApiValue());
            writeFileField(data, boundary, "audiofile", request.getAudioFile());
            data.writeBytes("--" + boundary + "--\r\n");
            data.flush();
        }

        JsonObject response = readJsonResponse(connection);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("MVSEP create response: " + response);
        }
        ensureSuccess(response, "MVSEP rejected the separation request.");

        String hash = getString(getNested(response, "data", "hash"));
        if (StringUtils.isBlank(hash)) {
            hash = getString(response.get("hash"));
        }
        if (StringUtils.isBlank(hash)) {
            throw new IOException("MVSEP did not return a job hash.");
        }
        return hash;
    }

    private JsonObject getJobResult(String hash) throws IOException {
        return getJson("/separation/get?hash=" + encode(hash), false);
    }

    private JsonObject getJson(String pathAndQuery, boolean authorized) throws IOException {
        HttpURLConnection connection = openConnection(pathAndQuery);
        connection.setRequestMethod("GET");
        JsonObject response = readJsonResponse(connection);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("MVSEP GET " + pathAndQuery + " response: " + response);
        }
        ensureSuccess(response, "MVSEP returned an invalid response.");
        return response;
    }

    private HttpURLConnection openConnection(String pathAndQuery) throws IOException {
        URL url = URI.create(API_BASE + pathAndQuery).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Yass Reloaded MVSEP Integration");
        return connection;
    }

    private JsonObject readJsonResponse(HttpURLConnection connection) throws IOException {
        int code = connection.getResponseCode();
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String body = readFully(stream);
        if (StringUtils.isBlank(body)) {
            throw new IOException("Empty response from MVSEP (HTTP " + code + ").");
        }

        JsonElement parsed = JsonParser.parseString(body);
        if (!parsed.isJsonObject()) {
            throw new IOException("Unexpected response from MVSEP: " + body);
        }
        JsonObject json = parsed.getAsJsonObject();
        if (code >= 400) {
            throw new IOException(extractFailureMessage(json, "HTTP " + code));
        }
        return json;
    }

    private void ensureSuccess(JsonObject response, String defaultMessage) throws IOException {
        JsonElement success = response.get("success");
        if (success != null && success.isJsonPrimitive() && !success.getAsBoolean()) {
            throw new IOException(extractFailureMessage(response, defaultMessage));
        }
    }

    private String extractFailureMessage(JsonObject response, String defaultMessage) {
        for (String key : List.of("message", "error", "msg", "detail")) {
            String value = getString(response.get(key));
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
            String nested = getString(getNested(response, "data", key));
            if (StringUtils.isNotBlank(nested)) {
                return nested;
            }
        }
        return defaultMessage;
    }

    private String extractStatus(JsonObject response) {
        String status = getString(getNested(response, "data", "status"));
        if (StringUtils.isBlank(status)) {
            status = getString(response.get("status"));
        }
        return StringUtils.defaultIfBlank(status, "processing").trim().toLowerCase(Locale.ROOT);
    }

    private String toStatusMessage(JsonObject response, String status) {
        String normalizedStatus = StringUtils.defaultIfBlank(status, "processing").toLowerCase(Locale.ROOT);
        Integer queueCount = getInteger(getNested(response, "data", "queue_count"));
        Integer currentOrder = getInteger(getNested(response, "data", "current_order"));
        Integer finishedChunks = getInteger(getNested(response, "data", "finished_chunks"));
        Integer allChunks = getInteger(getNested(response, "data", "all_chunks"));
        String message = getString(getNested(response, "data", "message"));

        return switch (normalizedStatus) {
            case "waiting", "queued" -> currentOrder != null || queueCount != null
                    ? "Waiting in MVSEP queue: position " + StringUtils.defaultIfBlank(currentOrder == null ? null : currentOrder.toString(), "?")
                      + ", queued jobs " + StringUtils.defaultIfBlank(queueCount == null ? null : queueCount.toString(), "?") + "."
                    : "Waiting in MVSEP queue...";
            case "processing", "running" -> StringUtils.isNotBlank(message)
                    ? message
                    : "MVSEP is processing the stems...";
            case "distributing" -> finishedChunks != null && allChunks != null
                    ? "MVSEP is distributing chunks: " + finishedChunks + "/" + allChunks + "."
                    : "MVSEP is distributing chunks...";
            case "merging" -> "MVSEP is merging processed chunks...";
            default -> StringUtils.isNotBlank(message) ? message : "MVSEP status: " + status;
        };
    }

    private LinkedHashMap<String, DownloadDescriptor> extractStemDownloads(JsonObject response, MvsepModel model) {
        LinkedHashMap<String, DownloadDescriptor> allDownloads = new LinkedHashMap<>();
        collectDownloads(response, "$", allDownloads);
        List<DownloadDescriptor> candidates = new ArrayList<>(new LinkedHashSet<>(allDownloads.values()));
        candidates.sort(Comparator.comparing(DownloadDescriptor::name, String.CASE_INSENSITIVE_ORDER));
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("MVSEP download candidates: " + candidates);
        }
        return model.isKaraokeThreeStem() ? extractKaraokeStems(candidates) : extractTwoStemResult(candidates);
    }

    private LinkedHashMap<String, DownloadDescriptor> extractTwoStemResult(List<DownloadDescriptor> candidates) {
        LinkedHashMap<String, DownloadDescriptor> stems = new LinkedHashMap<>();
        DownloadDescriptor vocals = findBestCandidate(candidates, CandidateKind.VOCALS, Set.of());
        Set<DownloadDescriptor> used = vocals == null ? Set.of() : Set.of(vocals);
        DownloadDescriptor instrumental = findBestCandidate(candidates, CandidateKind.INSTRUMENTAL, used);
        if (vocals != null) {
            stems.put("vocals", vocals);
        }
        if (instrumental != null) {
            stems.put("instrumental", instrumental);
        }
        return stems;
    }

    private LinkedHashMap<String, DownloadDescriptor> extractKaraokeStems(List<DownloadDescriptor> candidates) {
        LinkedHashMap<String, DownloadDescriptor> stems = new LinkedHashMap<>();
        Set<DownloadDescriptor> used = new HashSet<>();

        DownloadDescriptor vocals = findBestCandidate(candidates, CandidateKind.VOCALS, used);
        if (vocals != null) {
            stems.put("vocals", vocals);
            used.add(vocals);
        }
        DownloadDescriptor lead = findBestCandidate(candidates, CandidateKind.LEAD, used);
        if (lead != null) {
            stems.put("lead", lead);
            used.add(lead);
        }
        DownloadDescriptor backing = findBestCandidate(candidates, CandidateKind.INSTRUMENTAL_BACKING, used);
        if (backing != null) {
            stems.put("instrumental-backing", backing);
            used.add(backing);
        }
        DownloadDescriptor instrumental = findBestCandidate(candidates, CandidateKind.INSTRUMENTAL, used);
        if (instrumental != null) {
            stems.put("instrumental", instrumental);
            used.add(instrumental);
        }

        if (stems.size() < 4 && candidates.size() >= 3) {
            for (DownloadDescriptor candidate : candidates) {
                if (used.contains(candidate)) {
                    continue;
                }
                if (!stems.containsKey("vocals")) {
                    stems.put("vocals", candidate);
                } else if (!stems.containsKey("lead")) {
                    stems.put("lead", candidate);
                } else if (!stems.containsKey("instrumental")) {
                    stems.put("instrumental", candidate);
                } else if (!stems.containsKey("instrumental-backing")) {
                    stems.put("instrumental-backing", candidate);
                }
                used.add(candidate);
            }
        }
        return stems;
    }

    private DownloadDescriptor findBestCandidate(List<DownloadDescriptor> candidates,
                                                 CandidateKind kind,
                                                 Set<DownloadDescriptor> excluded) {
        DownloadDescriptor best = null;
        int bestScore = Integer.MIN_VALUE;
        for (DownloadDescriptor candidate : candidates) {
            if (excluded.contains(candidate)) {
                continue;
            }
            int score = scoreCandidate(candidate, kind);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return bestScore > 0 ? best : null;
    }

    private int scoreCandidate(DownloadDescriptor candidate, CandidateKind kind) {
        String haystack = (candidate.name() + " " + candidate.url()).toLowerCase(Locale.ROOT);
        return switch (kind) {
            case VOCALS -> scoreVocals(haystack);
            case LEAD -> scoreLead(haystack);
            case INSTRUMENTAL -> scoreInstrumental(haystack);
            case INSTRUMENTAL_BACKING -> scoreInstrumentalBacking(haystack);
        };
    }

    private int scoreVocals(String haystack) {
        int score = 0;
        if (haystack.contains("vocals")) score += 6;
        if (haystack.contains("vocal")) score += 4;
        if (haystack.contains("acapella")) score += 4;
        if (haystack.contains("lead")) score += 2;
        if (haystack.contains("instrumental")) score -= 6;
        if (haystack.contains("karaoke")) score -= 4;
        if (haystack.contains("backing")) score -= 3;
        return score;
    }

    private int scoreLead(String haystack) {
        int score = scoreVocals(haystack);
        if (haystack.contains("lead")) score += 6;
        if (haystack.contains("vocals-lead")) score += 12;
        if (haystack.contains("back")) score -= 4;
        if (haystack.contains("backing")) score -= 5;
        return score;
    }

    private int scoreInstrumental(String haystack) {
        int score = 0;
        if (haystack.contains("instrumental")) score += 6;
        if (haystack.contains("instrum-only")) score += 12;
        if (haystack.contains("karaoke")) score += 5;
        if (haystack.contains("music")) score += 2;
        if (containsBackingKeywords(haystack)) score -= 8;
        if (haystack.contains("lead")) score -= 4;
        if (haystack.contains("vocals")) score -= 6;
        if (haystack.contains("vocal")) score -= 4;
        return score;
    }

    private int scoreInstrumentalBacking(String haystack) {
        int score = 0;
        if (containsBackingKeywords(haystack)) score += 10;
        if (haystack.contains("back-instrum")) score += 14;
        if (haystack.contains("instrumental")) score += 3;
        if (haystack.contains("karaoke")) score += 2;
        if (haystack.contains("lead")) score -= 3;
        return score;
    }

    private boolean containsBackingKeywords(String haystack) {
        return haystack.contains("backing")
                || haystack.contains("back vocals")
                || haystack.contains("back-vocals")
                || haystack.contains("backing vocals")
                || haystack.contains("back vocal")
                || haystack.contains("vocals-back")
                || haystack.contains("back-instrum")
                || haystack.contains(" bv ")
                || haystack.endsWith(" bv")
                || haystack.startsWith("bv ");
    }

    private void collectDownloads(JsonElement element, String path, Map<String, DownloadDescriptor> downloads) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            String url = null;
            String name = null;
            for (String key : List.of("url", "link", "download", "href", "file", "filename", "name", "title")) {
                JsonElement child = object.get(key);
                String value = getString(child);
                if (StringUtils.isBlank(value)) {
                    continue;
                }
                if (looksLikeUrl(value)) {
                    if (url == null) {
                        url = value;
                    }
                } else if (name == null) {
                    name = value;
                }
            }
            if (url != null) {
                String descriptorName = StringUtils.defaultIfBlank(name, path);
                downloads.putIfAbsent(url, new DownloadDescriptor(url, descriptorName));
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                collectDownloads(entry.getValue(), path + "." + entry.getKey(), downloads);
            }
            return;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                collectDownloads(array.get(i), path + "[" + i + "]", downloads);
            }
            return;
        }
        String value = getString(element);
        if (looksLikeUrl(value)) {
            downloads.putIfAbsent(value, new DownloadDescriptor(value, path));
        }
    }

    private void collectAlgorithms(JsonElement element, Map<Integer, MvsepAlgorithmInfo> algorithms) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement entry : element.getAsJsonArray()) {
                collectAlgorithms(entry, algorithms);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        Integer renderId = getInteger(object.get("render_id"));
        Integer orientation = getInteger(object.get("orientation"));
        String name = getString(object.get("name"));
        if (renderId != null && orientation != null && StringUtils.isNotBlank(name)) {
            algorithms.put(renderId, new MvsepAlgorithmInfo(renderId, name, orientation));
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            collectAlgorithms(entry.getValue(), algorithms);
        }
    }

    private File downloadStem(String url,
                              File songDirectory,
                              String baseName,
                              String stemType,
                              String preferredExtension) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "Yass Reloaded MVSEP Integration");
        connection.setInstanceFollowRedirects(true);

        int code = connection.getResponseCode();
        if (code >= 400) {
            throw new IOException("MVSEP download failed with HTTP " + code + ".");
        }
        Files.createDirectories(songDirectory.toPath());
        try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream())) {
            input.mark(64);
            byte[] header = input.readNBytes(32);
            input.reset();
            String extension = detectAudioExtension(connection, url, header, preferredExtension);
            File targetFile = buildUniqueTarget(songDirectory, baseName, stemType, extension);
            try (OutputStream output = new BufferedOutputStream(new FileOutputStream(targetFile))) {
                input.transferTo(output);
            }
            LOGGER.info("Downloaded MVSEP stem " + stemType + " to " + targetFile.getAbsolutePath());
            return targetFile;
        }
    }

    private String detectAudioExtension(HttpURLConnection connection,
                                        String url,
                                        byte[] header,
                                        String preferredExtension) {
        String byMagic = detectAudioExtensionFromMagic(header);
        if (StringUtils.isNotBlank(byMagic)) {
            return byMagic;
        }
        String byDisposition = extensionFromValue(connection.getHeaderField("Content-Disposition"));
        if (StringUtils.isNotBlank(byDisposition)) {
            return byDisposition;
        }
        String byUrl = extensionFromValue(url);
        if (StringUtils.isNotBlank(byUrl)) {
            return byUrl;
        }
        return preferredExtension;
    }

    private String detectAudioExtensionFromMagic(byte[] header) {
        if (header == null || header.length < 4) {
            return null;
        }
        if (header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
            return "mp3";
        }
        if (header[0] == 'f' && header[1] == 'L' && header[2] == 'a' && header[3] == 'C') {
            return "flac";
        }
        if (header.length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'A' && header[10] == 'V' && header[11] == 'E') {
            return "wav";
        }
        if (header.length >= 12 && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p') {
            return "m4a";
        }
        return null;
    }

    private String extensionFromValue(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String ext : List.of("mp3", "flac", "wav", "m4a")) {
            if (normalized.contains("." + ext)) {
                return ext;
            }
        }
        return null;
    }

    private File buildUniqueTarget(File directory, String baseName, String stemType, String extension) {
        File candidate = new File(directory, baseName + " (" + stemType + ")." + extension);
        int index = 2;
        while (candidate.exists()) {
            candidate = new File(directory, baseName + " (" + stemType + ") " + index + "." + extension);
            index++;
        }
        return candidate;
    }

    private String buildSongBaseName(YassTable table, File audioFile) {
        String artist = sanitizeFilenamePart(table.getArtist());
        String title = sanitizeFilenamePart(table.getTitle());
        if (StringUtils.isNotBlank(artist) && StringUtils.isNotBlank(title)) {
            return artist + " - " + title;
        }
        String fallback = sanitizeFilenamePart(getBaseName(audioFile.getName()));
        return StringUtils.defaultIfBlank(fallback, "MVSEP Separation");
    }

    private String sanitizeFilenamePart(String value) {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        String sanitized = value.replaceAll("[\\\\/:*?\"<>|]", "_")
                                .replaceAll("\\s+", " ")
                                .trim();
        while (sanitized.endsWith(".")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1).trim();
        }
        return sanitized;
    }

    private String getBaseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private JsonElement getNested(JsonObject object, String... path) {
        JsonElement current = object;
        for (String key : path) {
            if (current == null || !current.isJsonObject()) {
                return null;
            }
            current = current.getAsJsonObject().get(key);
        }
        return current;
    }

    private JsonObject getNestedObject(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
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

    private int getIntegerValue(JsonElement element, int fallback) {
        Integer value = getInteger(element);
        return value == null ? fallback : value;
    }

    private boolean getBoolean(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return false;
        }
        try {
            return element.getAsBoolean();
        } catch (Exception ex) {
            Integer intValue = getInteger(element);
            return intValue != null && intValue != 0;
        }
    }

    private String getString(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    private boolean looksLikeUrl(String value) {
        return StringUtils.startsWithIgnoreCase(value, "http://") || StringUtils.startsWithIgnoreCase(value, "https://");
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

    private void writeFormField(DataOutputStream data, String boundary, String name, String value) throws IOException {
        data.writeBytes("--" + boundary + "\r\n");
        data.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        data.write(value.getBytes(StandardCharsets.UTF_8));
        data.writeBytes("\r\n");
    }

    private void writeFileField(DataOutputStream data, String boundary, String fieldName, File file) throws IOException {
        data.writeBytes("--" + boundary + "\r\n");
        data.writeBytes("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\""
                        + file.getName() + "\"\r\n");
        String contentType = StringUtils.defaultIfBlank(Files.probeContentType(file.toPath()), "application/octet-stream");
        data.writeBytes("Content-Type: " + contentType + "\r\n\r\n");
        Files.copy(file.toPath(), data);
        data.writeBytes("\r\n");
    }

    private String encode(String value) {
        return URLEncoder.encode(StringUtils.defaultString(value), StandardCharsets.UTF_8);
    }

    private enum CandidateKind {
        VOCALS,
        LEAD,
        INSTRUMENTAL,
        INSTRUMENTAL_BACKING
    }

    private record DownloadDescriptor(String url, String name) {
    }
}











