package yass.usdb;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UsdbPendingSongService {
    private static final Pattern H1_PATTERN = Pattern.compile("<h1>(.*?)</h1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern INPUT_PATTERN = Pattern.compile("<input\\b([^>]+)>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TEXTAREA_PATTERN = Pattern.compile(
            "<textarea[^>]*name=[\"'](txt|txt2)[\"'][^>]*>(.*?)</textarea>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ATTR_PATTERN = Pattern.compile("(\\w+)\\s*=\\s*['\"]([^'\"]*)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUBMIT_PATTERN = Pattern.compile("window\\.location\\.href='\\?link=submit&id=(\\d+)'",
            Pattern.CASE_INSENSITIVE);

    private final UsdbSessionService sessionService;

    public UsdbPendingSongService(UsdbSessionService sessionService) {
        this.sessionService = sessionService;
    }

    public PendingSong loadPendingSong(int songId) throws IOException, InterruptedException {
        sessionService.resetSessionInfoRetryLimit();
        String html = sessionService.get("index.php", Map.of("link", "detailz", "id", String.valueOf(songId)));
        Map<String, String> formValues = extractRelevantInputs(html);
        PendingSong pendingSong = new PendingSong(
                songId,
                extractOptional(H1_PATTERN, html),
                extractTextarea(html, "txt"),
                extractTextarea(html, "txt2"),
                formValues,
                extractSubmitSongId(html));
        formValues.put("txt", pendingSong.remoteTxt());
        formValues.putIfAbsent("txt2", pendingSong.previousTxt());
        return pendingSong;
    }

    public void submitPendingSongUpdate(PendingSong pendingSong, String updatedTxt) throws IOException, InterruptedException {
        sessionService.resetSessionInfoRetryLimit();
        Map<String, String> form = new LinkedHashMap<>(pendingSong.formValues());
        String logicalUpdatedTxt = StringUtils.defaultString(updatedTxt).replace("\r\n", "\n").replace('\r', '\n');
        form.put("txt", logicalUpdatedTxt.replace("\n", "\r\n"));
        sessionService.postForm("index.php",
                Map.of("link", "update_edit", "id", String.valueOf(pendingSong.songId())),
                form);
    }

    public void completeVerification(PendingSong pendingSong) throws IOException, InterruptedException {
        sessionService.resetSessionInfoRetryLimit();
        if (pendingSong == null || pendingSong.submitSongId() <= 0) {
            throw new IOException("USDB verification submit id is missing.");
        }
        sessionService.get("index.php", Map.of("link", "submit", "id", String.valueOf(pendingSong.submitSongId())));
    }

    private String extractTextarea(String html, String name) {
        Matcher matcher = TEXTAREA_PATTERN.matcher(StringUtils.defaultString(html));
        while (matcher.find()) {
            if (StringUtils.equalsIgnoreCase(name, matcher.group(1))) {
                return UsdbSessionService.decodeHtml(matcher.group(2)).replace("\r\n", "\n");
            }
        }
        return "";
    }

    private Map<String, String> extractRelevantInputs(String html) {
        Map<String, String> values = new LinkedHashMap<>();
        Matcher matcher = INPUT_PATTERN.matcher(StringUtils.defaultString(html));
        while (matcher.find()) {
            Map<String, String> attrs = parseAttributes(matcher.group(1));
            String name = attrs.get("name");
            if (StringUtils.isBlank(name)) {
                continue;
            }
            String type = StringUtils.lowerCase(attrs.get("type"));
            if ("submit".equals(type) || "button".equals(type) || "reset".equals(type) || "file".equals(type)) {
                continue;
            }
            values.put(name, UsdbSessionService.decodeHtml(StringUtils.defaultString(attrs.get("value"))));
        }
        values.putIfAbsent("filename", "");
        values.putIfAbsent("txt2", "");
        return values;
    }

    private Map<String, String> parseAttributes(String inputTagContent) {
        Map<String, String> attributes = new LinkedHashMap<>();
        Matcher matcher = ATTR_PATTERN.matcher(StringUtils.defaultString(inputTagContent));
        while (matcher.find()) {
            attributes.put(StringUtils.lowerCase(matcher.group(1)), matcher.group(2));
        }
        return attributes;
    }

    private int extractSubmitSongId(String html) {
        Matcher matcher = SUBMIT_PATTERN.matcher(StringUtils.defaultString(html));
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private String extractOptional(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(StringUtils.defaultString(html));
        return matcher.find() ? UsdbSessionService.decodeHtml(matcher.group(1)).trim() : "";
    }

    public record PendingSong(int songId,
                              String title,
                              String remoteTxt,
                              String previousTxt,
                              Map<String, String> formValues,
                              int submitSongId) {
    }
}
