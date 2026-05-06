package yass.usdb;

import org.apache.commons.lang3.StringUtils;
import yass.extras.UsdbSyncerMetaTagCreator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UsdbSongEditService {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final DateTimeFormatter DEBUG_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Pattern ENTITY_CHANGED_PATTERN = Pattern.compile("entity\\s+changed!?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern H1_PATTERN = Pattern.compile("<h1>(.*?)</h1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TXT_PATTERN = Pattern.compile(
            "<textarea[^>]*name=[\"']txt[\"'][^>]*>(.*?)</textarea>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern INPUT_PATTERN = Pattern.compile("<input\\b([^>]+)>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ATTR_PATTERN = Pattern.compile("(\\w+)\\s*=\\s*['\"]([^'\"]*)['\"]", Pattern.CASE_INSENSITIVE);

    private final UsdbSessionService sessionService;

    public UsdbSongEditService(UsdbSessionService sessionService) {
        this.sessionService = sessionService;
    }

    public UsdbEditableSong loadEditableSong(int songId) throws IOException, InterruptedException {
        sessionService.resetSessionInfoRetryLimit();
        String html = sessionService.get("index.php", Map.of("link", "editsongs", "id", String.valueOf(songId)));
        String remoteTxt = extractRequiredTextarea(html);
        Map<String, String> formValues = extractRelevantInputs(html);
        formValues.put("txt", remoteTxt);
        String title = extractOptional(H1_PATTERN, html);
        return new UsdbEditableSong(songId, title, remoteTxt, formValues);
    }

    public void submitSongUpdate(UsdbEditableSong editableSong, String updatedTxt) throws IOException, InterruptedException {
        sessionService.resetSessionInfoRetryLimit();
        Map<String, String> form = new LinkedHashMap<>(editableSong.formValues());
        String logicalUpdatedTxt = StringUtils.defaultString(updatedTxt).replace("\r\n", "\n").replace('\r', '\n');
        String submittedTxt = logicalUpdatedTxt.replace("\n", "\r\n");
        form.put("txt", submittedTxt);

        LOGGER.info("Submitting USDB song update for id=" + editableSong.songId()
                + ", filename=" + StringUtils.defaultIfBlank(form.get("filename"), "<empty>")
                + ", coverinput=" + StringUtils.defaultIfBlank(form.get("coverinput"), "<empty>")
                + ", sampleinput=" + StringUtils.defaultIfBlank(form.get("sampleinput"), "<empty>")
                + ", txtLength=" + submittedTxt.length());

        String response = sessionService.postForm("index.php",
                Map.of("link", "editsongsupdate", "id", String.valueOf(editableSong.songId())),
                form);
        writeDebugResponse(editableSong, logicalUpdatedTxt, submittedTxt, response);
        String normalized = StringUtils.lowerCase(response);
        if (normalized.contains("nicht eingeloggt")
                || normalized.contains("please login")
                || normalized.contains("keine id")
                || normalized.contains("no id")) {
            throw new IOException("USDB edit submit was rejected.");
        }

        if (ENTITY_CHANGED_PATTERN.matcher(response).find()) {
            LOGGER.info("USDB edit submit acknowledged with 'Entity Changed!' for id=" + editableSong.songId());
            verifyUpdatedSongState(editableSong, logicalUpdatedTxt);
            return;
        }

        String returnedTxt = null;
        try {
            returnedTxt = extractRequiredTextarea(response).replace("\r\n", "\n").replace('\r', '\n');
        } catch (IOException ignored) {
            LOGGER.info("USDB edit submit response for id=" + editableSong.songId()
                    + " did not contain an editable TXT form. Response snippet: " + abbreviateForLog(response));
        }

        if (returnedTxt != null) {
            LOGGER.info("USDB edit submit response for id=" + editableSong.songId()
                    + " returned txtLength=" + returnedTxt.length()
                    + ", changed=" + !StringUtils.equals(returnedTxt, editableSong.remoteTxt())
                    + ", matchesSubmitted=" + StringUtils.equals(returnedTxt, logicalUpdatedTxt));
            if (StringUtils.equals(returnedTxt, editableSong.remoteTxt())) {
                throw new IOException("USDB edit submit did not change the remote TXT. Response snippet: "
                        + abbreviateForLog(response));
            }
            if (!StringUtils.equals(returnedTxt, logicalUpdatedTxt)) {
                LOGGER.info("USDB edit submit response differs from submitted TXT for id=" + editableSong.songId()
                        + ". Response snippet: " + abbreviateForLog(response));
            }
        } else {
            LOGGER.info("USDB edit submit response snippet for id=" + editableSong.songId() + ": "
                    + abbreviateForLog(response));
        }
    }

    public boolean submitSongMediaInputsIfMissing(UsdbEditableSong editableSong,
                                                  String coverUrl,
                                                  String sampleUrl) throws IOException, InterruptedException {
        sessionService.resetSessionInfoRetryLimit();
        if (editableSong == null) {
            return false;
        }
        Map<String, String> form = new LinkedHashMap<>(editableSong.formValues());
        boolean changed = false;
        String normalizedCoverUrl = normalizeCoverSubmitUrl(coverUrl);
        if (StringUtils.isBlank(form.get("coverinput")) && StringUtils.startsWithIgnoreCase(StringUtils.trimToEmpty(normalizedCoverUrl), "http")) {
            form.put("coverinput", StringUtils.trim(normalizedCoverUrl));
            changed = true;
        }
        if (StringUtils.isBlank(form.get("sampleinput")) && StringUtils.startsWithIgnoreCase(StringUtils.trimToEmpty(sampleUrl), "http")) {
            form.put("sampleinput", StringUtils.trim(sampleUrl));
            changed = true;
        }
        if (!changed) {
            return false;
        }

        String logicalTxt = StringUtils.defaultString(editableSong.remoteTxt()).replace("\r\n", "\n").replace('\r', '\n');
        form.put("txt", logicalTxt.replace("\n", "\r\n"));
        LOGGER.info("Submitting USDB media input update for id=" + editableSong.songId()
                + ", coverinput=" + StringUtils.defaultIfBlank(form.get("coverinput"), "<empty>")
                + ", sampleinput=" + StringUtils.defaultIfBlank(form.get("sampleinput"), "<empty>"));
        sessionService.postForm("index.php",
                Map.of("link", "editsongsupdate", "id", String.valueOf(editableSong.songId())),
                form);

        UsdbEditableSong refreshedSong = loadEditableSong(editableSong.songId());
        boolean coverSaved = StringUtils.equals(StringUtils.trimToEmpty(form.get("coverinput")),
                StringUtils.trimToEmpty(refreshedSong.formValues().get("coverinput")));
        boolean sampleSaved = StringUtils.equals(StringUtils.trimToEmpty(form.get("sampleinput")),
                StringUtils.trimToEmpty(refreshedSong.formValues().get("sampleinput")));
        return coverSaved || sampleSaved;
    }

    private String normalizeCoverSubmitUrl(String coverUrl) {
        String normalizedSource = StringUtils.trimToEmpty(UsdbSyncerMetaTagCreator.toSyncerImageLink(coverUrl));
        if (StringUtils.isBlank(normalizedSource)) {
            return null;
        }
        if (normalizedSource.startsWith("//")) {
            normalizedSource = "https:" + normalizedSource;
        } else if (!normalizedSource.contains("://")) {
            normalizedSource = "https://images.fanart.tv/fanart/" + normalizedSource;
        }
        return normalizedSource
                .replace("https://assets.fanart.tv/fanart/", "https://images.fanart.tv/fanart/")
                .replace("http://assets.fanart.tv/fanart/", "https://images.fanart.tv/fanart/")
                .replace("assets.fanart.tv", "images.fanart.tv");
    }

    private void verifyUpdatedSongState(UsdbEditableSong editableSong, String normalizedUpdatedTxt)
            throws IOException, InterruptedException {
        UsdbEditableSong refreshedSong = loadEditableSong(editableSong.songId());
        String refreshedTxt = StringUtils.defaultString(refreshedSong.remoteTxt()).replace("\r\n", "\n").replace('\r', '\n');
        LOGGER.info("USDB edit verification for id=" + editableSong.songId()
                + ": matchesSubmitted=" + StringUtils.equals(refreshedTxt, normalizedUpdatedTxt)
                + ", changedFromPrevious=" + !StringUtils.equals(refreshedTxt, editableSong.remoteTxt())
                + ", refreshedTxtLength=" + refreshedTxt.length());
        if (StringUtils.equals(refreshedTxt, editableSong.remoteTxt())) {
            throw new IOException("USDB acknowledged the edit, but reloading the song still returned the previous TXT.");
        }
    }

    private String extractRequiredTextarea(String html) throws IOException {
        Matcher matcher = TXT_PATTERN.matcher(html);
        if (!matcher.find()) {
            throw new IOException("USDB edit form could not be parsed.");
        }
        return UsdbSessionService.decodeHtml(matcher.group(1)).replace("\r\n", "\n");
    }

    private Map<String, String> extractRelevantInputs(String html) {
        Map<String, String> values = new LinkedHashMap<>();
        Matcher matcher = INPUT_PATTERN.matcher(html);
        while (matcher.find()) {
            Map<String, String> attrs = parseAttributes(matcher.group(1));
            String name = attrs.get("name");
            if (StringUtils.isBlank(name)) {
                continue;
            }
            String type = StringUtils.lowerCase(attrs.get("type"));
            if ("submit".equals(type) || "button".equals(type) || "reset".equals(type)) {
                continue;
            }
            values.put(name, UsdbSessionService.decodeHtml(StringUtils.defaultString(attrs.get("value"))));
        }
        values.putIfAbsent("coverinput", "");
        values.putIfAbsent("sampleinput", "");
        values.putIfAbsent("filename", "");
        return values;
    }

    private Map<String, String> parseAttributes(String inputTagContent) {
        Map<String, String> attributes = new LinkedHashMap<>();
        Matcher matcher = ATTR_PATTERN.matcher(inputTagContent);
        while (matcher.find()) {
            attributes.put(StringUtils.lowerCase(matcher.group(1)), matcher.group(2));
        }
        return attributes;
    }

    private String extractOptional(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? UsdbSessionService.decodeHtml(matcher.group(1)).trim() : "";
    }

    private String abbreviateForLog(String html) {
        String flattened = StringUtils.normalizeSpace(StringUtils.defaultString(html));
        return StringUtils.abbreviate(flattened, 400);
    }

    private void writeDebugResponse(UsdbEditableSong editableSong,
                                    String logicalUpdatedTxt,
                                    String submittedTxt,
                                    String response) {
        try {
            Path debugDir = Path.of(System.getProperty("user.home"), ".yass", "logs");
            Files.createDirectories(debugDir);
            String baseName = "usdb-edit-submit-" + editableSong.songId() + "-" + DEBUG_TIMESTAMP.format(LocalDateTime.now());
            Path htmlFile = debugDir.resolve(baseName + ".html");
            Path metaFile = debugDir.resolve(baseName + ".txt");
            Path submittedTxtFile = debugDir.resolve(baseName + "-submitted.txt");
            Files.writeString(htmlFile, StringUtils.defaultString(response), StandardCharsets.UTF_8);
            Files.writeString(submittedTxtFile, StringUtils.defaultString(submittedTxt), StandardCharsets.UTF_8);
            String metadata = "songId=" + editableSong.songId() + System.lineSeparator()
                    + "title=" + StringUtils.defaultString(editableSong.title()) + System.lineSeparator()
                    + "submittedLength=" + submittedTxt.length() + System.lineSeparator()
                    + "logicalSubmittedLength=" + logicalUpdatedTxt.length() + System.lineSeparator()
                    + "responseLength=" + StringUtils.length(response) + System.lineSeparator()
                    + "submittedTxtFile=" + submittedTxtFile + System.lineSeparator()
                    + "htmlFile=" + htmlFile + System.lineSeparator();
            Files.writeString(metaFile, metadata, StandardCharsets.UTF_8);
            LOGGER.info("USDB edit submit debug response written to " + htmlFile);
        } catch (Exception ex) {
            LOGGER.info("USDB edit submit debug response could not be written: " + ex.getMessage());
        }
    }

    public record UsdbEditableSong(int songId, String title, String remoteTxt, Map<String, String> formValues) {
    }
}
