package yass.usdb;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteOpenMode;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

final class UsdbChromiumCookieImporter {
    private static final URI USDB_URI = URI.create(UsdbConstants.BASE_URL);
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private final UsdbCookieBrowser browser;
    private final UsdbWindowsDpapi dpapi = new UsdbWindowsDpapi();

    UsdbChromiumCookieImporter(UsdbCookieBrowser browser) {
        this.browser = browser;
    }

    void importInto(CookieManager cookieManager) throws IOException {
        Path baseDir = resolveBaseDir();
        if (baseDir == null || !Files.isDirectory(baseDir)) {
            LOGGER.warning(browser + " user data directory was not found.");
            throw new IOException(browser + " user data directory was not found.");
        }
        LOGGER.info("Using " + browser + " user data directory: " + baseDir);
        Path localState = baseDir.resolve("Local State");
        if (!Files.isRegularFile(localState)) {
            LOGGER.warning(browser + " Local State was not found at " + localState);
            throw new IOException(browser + " Local State was not found.");
        }
        LOGGER.info("Using " + browser + " Local State: " + localState);
        Path cookieDb = resolveCookieDb(baseDir, localState);
        if (cookieDb == null || !Files.isRegularFile(cookieDb)) {
            LOGGER.warning(browser + " cookie database was not found.");
            throw new IOException(browser + " cookie database was not found.");
        }
        LOGGER.info("Using " + browser + " cookie database: " + cookieDb);

        byte[] masterKey = loadMasterKey(localState);
        Path tempCopy = Files.createTempFile("yass-usdb-" + browser.name().toLowerCase() + "-cookies-", ".sqlite");
        try {
            boolean copied = copyCookieDatabase(cookieDb, tempCopy);
            if (copied) {
                try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + tempCopy.toAbsolutePath())) {
                    importCookies(connection, cookieManager, masterKey);
                    return;
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, browser + " cookies could not be read from copied DB " + tempCopy, ex);
                }
            }
            importCookiesDirectly(cookieDb, cookieManager, masterKey);
        } finally {
            Files.deleteIfExists(tempCopy);
        }
    }

    private void importCookies(Connection connection, CookieManager cookieManager, byte[] masterKey) throws Exception {
        cookieManager.getCookieStore().removeAll();
        int imported = 0;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT name, value, encrypted_value, host_key, path, expires_utc, is_secure
                FROM cookies
                WHERE host_key = ? OR host_key = ?
                """)) {
            statement.setString(1, "usdb.animux.de");
            statement.setString(2, ".usdb.animux.de");
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String value = StringUtils.defaultString(resultSet.getString("value"));
                    if (StringUtils.isBlank(value)) {
                        value = decryptCookieValue(resultSet.getBytes("encrypted_value"), masterKey);
                    }
                    if (StringUtils.isBlank(value)) {
                        continue;
                    }
                    HttpCookie cookie = new HttpCookie(resultSet.getString("name"), value);
                    cookie.setDomain(StringUtils.removeStart(resultSet.getString("host_key"), "."));
                    cookie.setPath(StringUtils.defaultIfBlank(resultSet.getString("path"), "/"));
                    cookie.setSecure(resultSet.getBoolean("is_secure"));
                    long remainingSeconds = chromiumExpiryToRemainingSeconds(resultSet.getLong("expires_utc"));
                    if (remainingSeconds > 0) {
                        cookie.setMaxAge(remainingSeconds);
                    }
                    cookie.setVersion(0);
                    cookieManager.getCookieStore().add(USDB_URI, cookie);
                    imported++;
                }
            }
        }
        LOGGER.info("Imported " + imported + " " + browser + " cookie(s) for usdb.animux.de");
    }

    private byte[] loadMasterKey(Path localState) throws IOException {
        try {
            JsonObject json = JsonParser.parseString(Files.readString(localState)).getAsJsonObject();
            String encryptedKey = json.getAsJsonObject("os_crypt").get("encrypted_key").getAsString();
            byte[] decoded = Base64.getDecoder().decode(encryptedKey);
            byte[] dpapiProtected = new byte[decoded.length - 5];
            System.arraycopy(decoded, 5, dpapiProtected, 0, dpapiProtected.length);
            LOGGER.info("Decrypting " + browser + " master cookie key from Local State");
            return dpapi.decrypt(dpapiProtected);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, browser + " master key could not be loaded from " + localState, ex);
            throw new IOException(browser + " master key could not be loaded.", ex);
        }
    }

    private String decryptCookieValue(byte[] encryptedValue, byte[] masterKey) throws Exception {
        if (encryptedValue == null || encryptedValue.length == 0) {
            return "";
        }
        if (startsWith(encryptedValue, "v10") || startsWith(encryptedValue, "v11")) {
            byte[] nonce = new byte[12];
            System.arraycopy(encryptedValue, 3, nonce, 0, nonce.length);
            byte[] cipherBytes = new byte[encryptedValue.length - 15];
            System.arraycopy(encryptedValue, 15, cipherBytes, 0, cipherBytes.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKey, "AES"), new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        }
        return new String(dpapi.decrypt(encryptedValue), StandardCharsets.UTF_8);
    }

    private boolean startsWith(byte[] value, String prefix) {
        byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        if (value.length < prefixBytes.length) {
            return false;
        }
        for (int i = 0; i < prefixBytes.length; i++) {
            if (value[i] != prefixBytes[i]) {
                return false;
            }
        }
        return true;
    }

    private long chromiumExpiryToRemainingSeconds(long expiresUtc) {
        if (expiresUtc <= 0) {
            return -1;
        }
        long epochSeconds = (expiresUtc / 1_000_000L) - 11_644_473_600L;
        return epochSeconds - Instant.now().getEpochSecond();
    }

    private Path resolveCookieDb(Path baseDir, Path localState) throws IOException {
        String lastUsed = "Default";
        try {
            JsonObject json = JsonParser.parseString(Files.readString(localState)).getAsJsonObject();
            JsonObject profile = json.getAsJsonObject("profile");
            if (profile != null && profile.has("last_used")) {
                lastUsed = profile.get("last_used").getAsString();
            }
        } catch (Exception ignored) {
        }
        LOGGER.info(browser + " last_used profile from Local State: " + lastUsed);
        Path preferred = resolveCookiePath(baseDir, lastUsed);
        if (Files.isRegularFile(preferred)) {
            LOGGER.info("Selected " + browser + " preferred cookie DB: " + preferred);
            return preferred;
        }
        Path fallback = resolveCookiePath(baseDir, "Default");
        if (Files.isRegularFile(fallback)) {
            LOGGER.info("Selected " + browser + " fallback cookie DB: " + fallback);
            return fallback;
        }
        LOGGER.warning(browser + " cookie DB was not found in preferred or Default profile.");
        return null;
    }

    private Path resolveCookiePath(Path baseDir, String profileName) {
        Path profileDir = baseDir.resolve(profileName);
        Path networkCookies = profileDir.resolve("Network").resolve("Cookies");
        if (Files.isRegularFile(networkCookies)) {
            return networkCookies;
        }
        return profileDir.resolve("Cookies");
    }

    private Path resolveBaseDir() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (StringUtils.isBlank(localAppData)) {
            return null;
        }
        return switch (browser) {
            case CHROME -> Path.of(localAppData, "Google", "Chrome", "User Data");
            case EDGE -> Path.of(localAppData, "Microsoft", "Edge", "User Data");
            default -> null;
        };
    }

    private boolean copyCookieDatabase(Path source, Path target) {
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info(browser + " cookie database copied to temp file: " + target);
            return true;
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, browser + " cookie database copy failed: " + source + " -> " + target, ex);
            return false;
        }
    }

    private void importCookiesDirectly(Path cookieDb, CookieManager cookieManager, byte[] masterKey) throws IOException {
        SQLiteConfig config = new SQLiteConfig();
        config.setOpenMode(SQLiteOpenMode.READONLY);
        config.setOpenMode(SQLiteOpenMode.OPEN_URI);
        config.setReadOnly(true);
        String normalizedPath = cookieDb.toAbsolutePath().toString().replace("\\", "/");
        List<String> jdbcUrls = List.of(
                "jdbc:sqlite:file:" + normalizedPath + "?mode=ro&immutable=1&nolock=1",
                "jdbc:sqlite:file:/" + normalizedPath + "?mode=ro&immutable=1&nolock=1",
                "jdbc:sqlite:" + cookieDb.toAbsolutePath()
        );
        Exception lastException = null;
        for (String jdbcUrl : jdbcUrls) {
            LOGGER.info("Trying direct read-only access to " + browser + " cookie DB: " + jdbcUrl);
            try (Connection connection = DriverManager.getConnection(jdbcUrl, config.toProperties())) {
                importCookies(connection, cookieManager, masterKey);
                LOGGER.info(browser + " direct cookie DB access succeeded via " + jdbcUrl);
                return;
            } catch (Exception ex) {
                lastException = ex;
                LOGGER.log(Level.WARNING, browser + " direct read-only cookie access failed via " + jdbcUrl, ex);
            }
        }
        throw new IOException(browser + " cookie database is locked or inaccessible: " + cookieDb, lastException);
    }
}
