package yass.usdb;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UsdbSongCommentService {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final Pattern COMMENT_FORM_PATTERN = Pattern.compile(
            "<form\\b([^>]*)>(.*?)<textarea[^>]*name=[\"']text[\"'][^>]*>.*?</textarea>.*?<select[^>]*name=[\"']stars[\"'][^>]*>.*?</select>.*?</form>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ATTR_PATTERN = Pattern.compile("(\\w+)\\s*=\\s*['\"]([^'\"]*)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern INPUT_PATTERN = Pattern.compile("<input\\b([^>]+)>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final UsdbSessionService sessionService;

    public UsdbSongCommentService(UsdbSessionService sessionService) {
        this.sessionService = sessionService;
    }

    public boolean submitYoutubeCommentIfMissing(int songId, String youtubeId) throws IOException, InterruptedException {
        sessionService.resetSessionInfoRetryLimit();
        if (songId <= 0 || StringUtils.isBlank(youtubeId)) {
            LOGGER.info("USDB youtube comment skipped for songId=" + songId + " because youtubeId is blank.");
            return false;
        }
        String trimmedYoutubeId = StringUtils.trimToEmpty(youtubeId);
        String youtubeUrl = "https://www.youtube.com/watch?v=" + trimmedYoutubeId;
        String commentText = "[youtube]" + youtubeUrl + "[/youtube]";

        String detailHtml = sessionService.get("index.php", Map.of("link", "detail", "id", String.valueOf(songId)));
        if (StringUtils.containsIgnoreCase(detailHtml, commentText)) {
            LOGGER.info("USDB youtube comment skipped for songId=" + songId + " because comment already exists.");
            return false;
        }

        CommentForm commentForm = extractCommentForm(detailHtml, songId);
        Map<String, String> form = new LinkedHashMap<>(commentForm.formValues());
        form.put("text", commentText);
        form.put("stars", "neutral");
        LOGGER.info("Submitting USDB youtube comment for songId=" + songId
                + " path=" + commentForm.path() + " params=" + commentForm.params());
        sessionService.postForm(commentForm.path(), commentForm.params(), form);
        return true;
    }

    private CommentForm extractCommentForm(String html, int songId) throws IOException {
        Matcher matcher = COMMENT_FORM_PATTERN.matcher(StringUtils.defaultString(html));
        if (!matcher.find()) {
            throw new IOException("USDB comment form could not be parsed.");
        }

        Map<String, String> formTagAttributes = parseAttributes(matcher.group(1));
        String action = StringUtils.trimToEmpty(formTagAttributes.get("action"));
        String formHtml = matcher.group(2);
        Map<String, String> formValues = extractHiddenInputs(formHtml);
        formValues.putIfAbsent("text", "");
        formValues.putIfAbsent("stars", "neutral");
        return parseAction(action, songId, formValues);
    }

    private CommentForm parseAction(String action, int songId, Map<String, String> formValues) {
        String normalizedAction = StringUtils.trimToEmpty(action);
        if (StringUtils.isBlank(normalizedAction)) {
            return new CommentForm("index.php", Map.of("link", "detail", "id", String.valueOf(songId)), formValues);
        }

        String path = normalizedAction;
        String query = "";
        int queryIndex = normalizedAction.indexOf('?');
        if (queryIndex >= 0) {
            path = normalizedAction.substring(0, queryIndex);
            query = normalizedAction.substring(queryIndex + 1);
        } else if (normalizedAction.startsWith("?")) {
            path = "index.php";
            query = normalizedAction.substring(1);
        }
        if (StringUtils.isBlank(path)) {
            path = "index.php";
        }

        Map<String, String> params = new LinkedHashMap<>();
        for (String part : query.split("&")) {
            String token = StringUtils.trimToEmpty(part);
            if (StringUtils.isBlank(token)) {
                continue;
            }
            String[] keyValue = token.split("=", 2);
            params.put(keyValue[0], keyValue.length > 1 ? keyValue[1] : "");
        }
        return new CommentForm(path, params, formValues);
    }

    private Map<String, String> extractHiddenInputs(String html) {
        Map<String, String> values = new LinkedHashMap<>();
        Matcher matcher = INPUT_PATTERN.matcher(StringUtils.defaultString(html));
        while (matcher.find()) {
            Map<String, String> attributes = parseAttributes(matcher.group(1));
            String name = attributes.get("name");
            if (StringUtils.isBlank(name)) {
                continue;
            }
            String type = StringUtils.lowerCase(attributes.get("type"));
            if (!StringUtils.equals(type, "hidden")) {
                continue;
            }
            values.put(name, UsdbSessionService.decodeHtml(StringUtils.defaultString(attributes.get("value"))));
        }
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

    private record CommentForm(String path, Map<String, String> params, Map<String, String> formValues) {
    }
}
