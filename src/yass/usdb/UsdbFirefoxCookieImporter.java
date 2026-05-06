package yass.usdb;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

final class UsdbFirefoxCookieImporter {
    private static final URI USDB_URI = URI.create(UsdbConstants.BASE_URL);
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    void importInto(CookieManager cookieManager) throws IOException {
        Path cookieDb = resolveCookieDb();
        if (cookieDb == null || !Files.isRegularFile(cookieDb)) {
            LOGGER.warning("Firefox cookies.sqlite was not found.");
            throw new IOException("Firefox cookies.sqlite was not found.");
        }
        LOGGER.info("Using Firefox cookie database: " + cookieDb);

        Path tempCopy = Files.createTempFile("yass-usdb-firefox-cookies-", ".sqlite");
        try {
            copyCookieDatabase(cookieDb, tempCopy, "Firefox");
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + tempCopy.toAbsolutePath())) {
                importCookies(connection, cookieManager);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Firefox cookies could not be read from " + tempCopy, ex);
                throw new IOException("Firefox cookies could not be read.", ex);
            }
        } finally {
            Files.deleteIfExists(tempCopy);
        }
    }

    private void importCookies(Connection connection, CookieManager cookieManager) throws Exception {
        cookieManager.getCookieStore().removeAll();
        int imported = 0;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT name, value, host, path, expiry, isSecure
                FROM moz_cookies
                WHERE host = ? OR host = ?
                """)) {
            statement.setString(1, "usdb.animux.de");
            statement.setString(2, ".usdb.animux.de");
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    HttpCookie cookie = new HttpCookie(resultSet.getString("name"), resultSet.getString("value"));
                    cookie.setDomain(StringUtils.removeStart(resultSet.getString("host"), "."));
                    cookie.setPath(StringUtils.defaultIfBlank(resultSet.getString("path"), "/"));
                    cookie.setSecure(resultSet.getBoolean("isSecure"));
                    long expiry = resultSet.getLong("expiry");
                    long remainingSeconds = expiry - Instant.now().getEpochSecond();
                    if (remainingSeconds > 0) {
                        cookie.setMaxAge(remainingSeconds);
                    }
                    cookie.setVersion(0);
                    cookieManager.getCookieStore().add(USDB_URI, cookie);
                    imported++;
                }
            }
        }
        LOGGER.info("Imported " + imported + " Firefox cookie(s) for usdb.animux.de");
    }

    private Path resolveCookieDb() throws IOException {
        String appData = System.getenv("APPDATA");
        if (StringUtils.isBlank(appData)) {
            return null;
        }
        Path profilesIni = Path.of(appData, "Mozilla", "Firefox", "profiles.ini");
        if (!Files.isRegularFile(profilesIni)) {
            LOGGER.info("Firefox profiles.ini not found at " + profilesIni);
            return null;
        }
        LOGGER.info("Reading Firefox profiles.ini from " + profilesIni);
        List<String> lines = Files.readAllLines(profilesIni);
        String currentPath = null;
        boolean currentRelative = true;
        boolean currentDefault = false;
        boolean installSection = false;
        Path fallback = null;
        for (String rawLine : lines) {
            String line = StringUtils.trimToEmpty(rawLine);
            if (line.startsWith("[")) {
                Path candidate = toCookieDb(appData, currentPath, currentRelative);
                if (currentDefault && candidate != null && Files.isRegularFile(candidate)) {
                    LOGGER.info("Selected Firefox default profile cookie DB: " + candidate);
                    return candidate;
                }
                if (fallback == null && candidate != null && Files.isRegularFile(candidate)) {
                    fallback = candidate;
                }
                currentPath = null;
                currentRelative = true;
                currentDefault = false;
                installSection = line.startsWith("[Install");
            } else if (installSection && line.startsWith("Default=")) {
                Path installCandidate = toCookieDb(appData, line.substring("Default=".length()), true);
                if (installCandidate != null && Files.isRegularFile(installCandidate)) {
                    LOGGER.info("Selected Firefox install-default cookie DB: " + installCandidate);
                    return installCandidate;
                }
            } else if (line.startsWith("Path=")) {
                currentPath = line.substring("Path=".length());
            } else if (line.startsWith("IsRelative=")) {
                currentRelative = !"0".equals(line.substring("IsRelative=".length()));
            } else if (line.startsWith("Default=")) {
                currentDefault = "1".equals(line.substring("Default=".length()));
            }
        }
        Path candidate = toCookieDb(appData, currentPath, currentRelative);
        if (currentDefault && candidate != null && Files.isRegularFile(candidate)) {
            LOGGER.info("Selected trailing Firefox default profile cookie DB: " + candidate);
            return candidate;
        }
        if (fallback != null) {
            LOGGER.info("Selected Firefox fallback cookie DB: " + fallback);
            return fallback;
        }
        if (candidate != null) {
            LOGGER.info("Last Firefox candidate cookie DB was: " + candidate);
        }
        return candidate != null && Files.isRegularFile(candidate) ? candidate : null;
    }

    private void copyCookieDatabase(Path source, Path target, String browserName) throws IOException {
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, browserName + " cookie database copy failed: " + source + " -> " + target, ex);
            throw new IOException(browserName + " cookie database is locked or inaccessible: " + source, ex);
        }
    }

    private Path toCookieDb(String appData, String profilePath, boolean relative) {
        if (StringUtils.isBlank(profilePath)) {
            return null;
        }
        Path profile = relative
                ? Path.of(appData, "Mozilla", "Firefox", profilePath)
                : Path.of(profilePath);
        return profile.resolve("cookies.sqlite");
    }
}
