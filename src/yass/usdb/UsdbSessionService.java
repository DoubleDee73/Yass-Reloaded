package yass.usdb;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UsdbSessionService {
    private static final int MAX_CONSECUTIVE_SESSION_INFO_FAILURES = 3;
    private static final Pattern LOGGED_IN_PATTERN = Pattern.compile(
            "<span class=['\"]gen['\"]>\\s*([^<]+)\\s*<b>([^<]+)</b>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EDIT_SONGS_LINK_PATTERN = Pattern.compile(
            "href\\s*=\\s*['\"]?\\?link=editsongs(?:['\"&>\\s])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RANK_PATTERN = Pattern.compile(
            "images/rank_(\\d+)\\.gif",
            Pattern.CASE_INSENSITIVE);
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    public static final class UsdbSessionInfo {
        private final String loggedInUser;
        private final boolean directEditAllowed;
        private final Integer rank;

        private UsdbSessionInfo(String loggedInUser, boolean directEditAllowed, Integer rank) {
            this.loggedInUser = loggedInUser;
            this.directEditAllowed = directEditAllowed;
            this.rank = rank;
        }

        public static UsdbSessionInfo loggedOut() {
            return new UsdbSessionInfo(null, false, null);
        }

        public String loggedInUser() {
            return loggedInUser;
        }

        public boolean isLoggedIn() {
            return StringUtils.isNotBlank(loggedInUser);
        }

        public boolean directEditAllowed() {
            return directEditAllowed;
        }

        public Integer rank() {
            return rank;
        }
    }

    private final CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .cookieHandler(cookieManager)
            .connectTimeout(Duration.ofMillis(UsdbConstants.CONNECT_TIMEOUT_MS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private UsdbSessionInfo cachedSessionInfo = UsdbSessionInfo.loggedOut();
    private int consecutiveSessionInfoFailures = 0;
    private boolean sessionInfoRetryLimitLogged = false;

    public synchronized boolean login(String username, char[] password) throws IOException, InterruptedException {
        resetSessionInfoRetryLimit();
        Map<String, String> form = new LinkedHashMap<>();
        form.put("user", StringUtils.defaultString(username));
        form.put("pass", password == null ? "" : new String(password));
        form.put("login", "Login");
        postForm("",
                 null,
                 form);
        return StringUtils.isNotBlank(getLoggedInUser());
    }

    public synchronized String getLoggedInUser() throws IOException, InterruptedException {
        return getSessionInfo().loggedInUser();
    }

    public synchronized boolean isLoggedIn() throws IOException, InterruptedException {
        return StringUtils.isNotBlank(getLoggedInUser());
    }

    public synchronized boolean canDirectlyEditSongs() throws IOException, InterruptedException {
        return getSessionInfo().directEditAllowed();
    }

    public synchronized UsdbSessionInfo getSessionInfo() throws IOException, InterruptedException {
        if (consecutiveSessionInfoFailures >= MAX_CONSECUTIVE_SESSION_INFO_FAILURES) {
            logSessionInfoRetryLimitOnce();
            return UsdbSessionInfo.loggedOut();
        }
        try {
            String html = get("",
                              Map.of("link", "profil"));
            Matcher loggedInMatcher = LOGGED_IN_PATTERN.matcher(html);
            if (!loggedInMatcher.find()) {
                resetSessionInfoRetryLimit();
                cachedSessionInfo = UsdbSessionInfo.loggedOut();
                return UsdbSessionInfo.loggedOut();
            }
            String user = decodeHtml(loggedInMatcher.group(2));
            boolean directEditAllowed = EDIT_SONGS_LINK_PATTERN.matcher(html).find();
            Integer rank = null;
            Matcher rankMatcher = RANK_PATTERN.matcher(html);
            if (rankMatcher.find()) {
                rank = Integer.parseInt(rankMatcher.group(1));
            }
            cachedSessionInfo = new UsdbSessionInfo(user, directEditAllowed, rank);
            resetSessionInfoRetryLimit();
            return cachedSessionInfo;
        } catch (IOException ex) {
            consecutiveSessionInfoFailures++;
            throw ex;
        }
    }

    public synchronized void logout() {
        cookieManager.getCookieStore().removeAll();
        cachedSessionInfo = UsdbSessionInfo.loggedOut();
        resetSessionInfoRetryLimit();
    }

    public synchronized String useImportedCookies(List<HttpCookie> cookies) throws IOException, InterruptedException {
        resetSessionInfoRetryLimit();
        cookieManager.getCookieStore().removeAll();
        if (cookies == null || cookies.isEmpty()) {
            return null;
        }
        URI usdbUri = URI.create(UsdbConstants.BASE_URL);
        for (HttpCookie cookie : cookies) {
            cookieManager.getCookieStore().add(usdbUri, cookie);
        }
        String loggedInUser = getLoggedInUser();
        if (StringUtils.isNotBlank(loggedInUser)) {
            LOGGER.info("USDB imported-cookie login succeeded for user: " + loggedInUser);
        } else {
            LOGGER.info("USDB imported-cookie login did not yield an authenticated session.");
        }
        return loggedInUser;
    }

    public synchronized String useBrowserCookies(UsdbCookieBrowser browser) throws IOException, InterruptedException {
        resetSessionInfoRetryLimit();
        cookieManager.getCookieStore().removeAll();
        if (browser == null || browser == UsdbCookieBrowser.NONE) {
            return null;
        }
        LOGGER.info("Trying USDB login via browser cookies: " + browser);
        switch (browser) {
            case FIREFOX -> new UsdbFirefoxCookieImporter().importInto(cookieManager);
            case CHROME, EDGE -> new UsdbChromiumCookieImporter(browser).importInto(cookieManager);
            default -> {
                return null;
            }
        }
        String loggedInUser = getLoggedInUser();
        if (StringUtils.isNotBlank(loggedInUser)) {
            LOGGER.info("USDB browser-cookie login succeeded for user: " + loggedInUser);
        } else {
            LOGGER.info("USDB browser-cookie login did not yield an authenticated session.");
        }
        return loggedInUser;
    }

    public synchronized void resetSessionInfoRetryLimit() {
        consecutiveSessionInfoFailures = 0;
        sessionInfoRetryLimitLogged = false;
    }

    public synchronized UsdbSessionInfo getCachedSessionInfo() {
        return cachedSessionInfo;
    }

    private void logSessionInfoRetryLimitOnce() {
        if (sessionInfoRetryLimitLogged) {
            return;
        }
        sessionInfoRetryLimitLogged = true;
        LOGGER.info("USDB session info lookup suspended after 3 consecutive failures until an explicit USDB action retries.");
    }

    String get(String path, Map<String, String> params) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(buildUri(path, params))
                .GET()
                .timeout(Duration.ofMillis(UsdbConstants.READ_TIMEOUT_MS))
                .header("Accept", "text/html,application/xhtml+xml")
                .header("User-Agent", UsdbConstants.USER_AGENT)
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response);
        return response.body();
    }

    String postForm(String path, Map<String, String> params, Map<String, String> form)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(buildUri(path, params))
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(form), StandardCharsets.UTF_8))
                .timeout(Duration.ofMillis(UsdbConstants.READ_TIMEOUT_MS))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Accept-Charset", "UTF-8")
                .header("User-Agent", UsdbConstants.USER_AGENT)
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response);
        return response.body();
    }

    String postMultipartFile(String path,
                             Map<String, String> params,
                             String fieldName,
                             String fileName,
                             String contentType,
                             byte[] fileContent) throws IOException, InterruptedException {
        String boundary = "----YassUsdbBoundary" + System.currentTimeMillis();
        byte[] body = buildMultipartBody(boundary,
                StringUtils.defaultIfBlank(fieldName, "file"),
                StringUtils.defaultIfBlank(fileName, "song.txt"),
                StringUtils.defaultIfBlank(contentType, "application/octet-stream"),
                fileContent == null ? new byte[0] : fileContent);
        HttpRequest request = HttpRequest.newBuilder(buildUri(path, params))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .timeout(Duration.ofMillis(UsdbConstants.READ_TIMEOUT_MS))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("User-Agent", UsdbConstants.USER_AGENT)
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response);
        return response.body();
    }

    private URI buildUri(String path, Map<String, String> params) {
        StringBuilder url = new StringBuilder(UsdbConstants.BASE_URL);
        if (StringUtils.isNotBlank(path)) {
            url.append(path);
        }
        if (params != null && !params.isEmpty()) {
            url.append(url.indexOf("?") >= 0 ? "&" : "?");
            url.append(formEncode(params));
        }
        return URI.create(url.toString());
    }

    private String formEncode(Map<String, String> values) {
        StringBuilder encoded = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (encoded.length() > 0) {
                encoded.append('&');
            }
            encoded.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            encoded.append('=');
            encoded.append(URLEncoder.encode(StringUtils.defaultString(entry.getValue()), StandardCharsets.UTF_8));
        }
        return encoded.toString();
    }

    private byte[] buildMultipartBody(String boundary,
                                      String fieldName,
                                      String fileName,
                                      String contentType,
                                      byte[] fileContent) {
        Charset utf8 = StandardCharsets.UTF_8;
        StringBuilder header = new StringBuilder();
        header.append("--").append(boundary).append("\r\n");
        header.append("Content-Disposition: form-data; name=\"")
                .append(fieldName)
                .append("\"; filename=\"")
                .append(fileName.replace("\"", ""))
                .append("\"\r\n");
        header.append("Content-Type: ").append(contentType).append("\r\n\r\n");
        byte[] headerBytes = header.toString().getBytes(utf8);
        byte[] footerBytes = ("\r\n--" + boundary + "--\r\n").getBytes(utf8);
        byte[] body = new byte[headerBytes.length + fileContent.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
        System.arraycopy(fileContent, 0, body, headerBytes.length, fileContent.length);
        System.arraycopy(footerBytes, 0, body, headerBytes.length + fileContent.length, footerBytes.length);
        return body;
    }

    private void ensureSuccess(HttpResponse<String> response) throws IOException {
        if (response.statusCode() >= 400) {
            throw new IOException("USDB request failed with HTTP " + response.statusCode());
        }
    }

    static String decodeHtml(String value) {
        if (value == null) {
            return "";
        }
        String decoded = value
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", "\u00A0")
                .replace("&uuml;", "ü")
                .replace("&ouml;", "ö")
                .replace("&auml;", "ä")
                .replace("&Uuml;", "Ü")
                .replace("&Ouml;", "Ö")
                .replace("&Auml;", "Ä")
                .replace("&szlig;", "ß");
        Matcher matcher = Pattern.compile("&#(\\d+);").matcher(decoded);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            int codePoint = Integer.parseInt(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
        }
        matcher.appendTail(result);
        String decimalDecoded = result.toString();
        Matcher hexMatcher = Pattern.compile("&#x([0-9A-Fa-f]+);").matcher(decimalDecoded);
        StringBuilder hexResult = new StringBuilder();
        while (hexMatcher.find()) {
            int codePoint = Integer.parseInt(hexMatcher.group(1), 16);
            hexMatcher.appendReplacement(hexResult, Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
        }
        hexMatcher.appendTail(hexResult);
        return hexResult.toString();
    }
}
