package yass.usdb;

import org.apache.commons.lang3.StringUtils;
import yass.YassSearchNormalizer;
import yass.YassProperties;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class UsdbSyncerBridge {
    public static final String PROPERTY_SYNCER_PATH = "usdb-syncer-path";
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    private final YassProperties properties;

    public UsdbSyncerBridge(YassProperties properties) {
        this.properties = properties;
    }

    public boolean isConfigured() {
        return getSyncerRootDirectory().isPresent();
    }

    public String getConfiguredPath() {
        return StringUtils.trimToEmpty(properties.getProperty(PROPERTY_SYNCER_PATH));
    }

    public List<UsdbSongSummary> searchSongs(String artist, String title, int limit) throws IOException {
        Path databasePath = resolveDatabasePath()
                .orElseThrow(() -> new IOException("USDB Syncer database is not available."));
        List<UsdbSongSummary> results = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT song_id, artist, title, edition, language, rating, views, golden_notes FROM usdb_song");
        List<String> params = new ArrayList<>();
        boolean hasArtist = StringUtils.isNotBlank(artist);
        boolean hasTitle = StringUtils.isNotBlank(title);
        if (hasArtist || hasTitle) {
            sql.append(" WHERE ");
            boolean sameQuery = hasArtist && hasTitle
                    && artist.trim().equalsIgnoreCase(title.trim());
            if (hasArtist) {
                sql.append("song_id IN (SELECT rowid FROM fts_usdb_song WHERE artist MATCH ?)");
                params.add(toFts5Phrases(artist));
            }
            if (hasArtist && hasTitle) {
                sql.append(sameQuery ? " OR " : " AND ");
            }
            if (hasTitle) {
                sql.append("song_id IN (SELECT rowid FROM fts_usdb_song WHERE title MATCH ?)");
                params.add(toFts5Phrases(title));
            }
        }
        sql.append(" ORDER BY views DESC, rating DESC, song_id DESC LIMIT ?");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            for (String param : params) {
                statement.setString(index++, param);
            }
            statement.setInt(index, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(new UsdbSongSummary(
                            resultSet.getInt("song_id"),
                            StringUtils.defaultString(resultSet.getString("artist")),
                            StringUtils.defaultString(resultSet.getString("title")),
                            StringUtils.defaultString(resultSet.getString("edition")),
                            StringUtils.defaultString(resultSet.getString("language")),
                            String.valueOf(resultSet.getInt("rating")),
                            String.valueOf(resultSet.getInt("views")),
                            resultSet.getBoolean("golden_notes")));
                }
            }
        } catch (Exception ex) {
            throw new IOException("USDB Syncer database query failed: " + ex.getMessage(), ex);
        }
        if (!results.isEmpty() || !shouldUseNormalizedFallback(artist, title)) {
            return results;
        }
        return searchSongsByNormalizedScan(databasePath, artist, title, limit);
    }

    private boolean shouldUseNormalizedFallback(String artist, String title) {
        return !StringUtils.equals(YassSearchNormalizer.normalizeForSearch(artist), StringUtils.trimToEmpty(artist).toLowerCase(Locale.ROOT))
                || !StringUtils.equals(YassSearchNormalizer.normalizeForSearch(title), StringUtils.trimToEmpty(title).toLowerCase(Locale.ROOT));
    }

    private String toFts5Phrases(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        String sanitized = text.replace("\"", "");
        String[] parts = sanitized.trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append('"').append(part).append("\"*");
        }
        return builder.toString();
    }

    private List<UsdbSongSummary> searchSongsByNormalizedScan(Path databasePath,
                                                              String artist,
                                                              String title,
                                                              int limit) throws IOException {
        List<UsdbSongSummary> matches = new ArrayList<>();
        String normalizedArtist = YassSearchNormalizer.normalizeForSearch(artist);
        String normalizedTitle = YassSearchNormalizer.normalizeForSearch(title);
        String sql = "SELECT song_id, artist, title, edition, language, rating, views, golden_notes FROM usdb_song";
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                UsdbSongSummary song = new UsdbSongSummary(
                        resultSet.getInt("song_id"),
                        StringUtils.defaultString(resultSet.getString("artist")),
                        StringUtils.defaultString(resultSet.getString("title")),
                        StringUtils.defaultString(resultSet.getString("edition")),
                        StringUtils.defaultString(resultSet.getString("language")),
                        String.valueOf(resultSet.getInt("rating")),
                        String.valueOf(resultSet.getInt("views")),
                        resultSet.getBoolean("golden_notes"));
                if (matchesNormalized(song, normalizedArtist, normalizedTitle)) {
                    matches.add(song);
                }
            }
        } catch (Exception ex) {
            throw new IOException("USDB Syncer normalized search failed: " + ex.getMessage(), ex);
        }
        matches.sort((left, right) -> {
            int views = Integer.compare(parseInt(right.views()), parseInt(left.views()));
            if (views != 0) {
                return views;
            }
            int rating = Integer.compare(parseInt(right.rating()), parseInt(left.rating()));
            if (rating != 0) {
                return rating;
            }
            return Integer.compare(right.songId(), left.songId());
        });
        return matches.size() > Math.max(1, limit) ? new ArrayList<>(matches.subList(0, Math.max(1, limit))) : matches;
    }

    private boolean matchesNormalized(UsdbSongSummary song, String normalizedArtist, String normalizedTitle) {
        String songArtist = YassSearchNormalizer.normalizeForSearch(song.artist());
        String songTitle = YassSearchNormalizer.normalizeForSearch(song.title());
        boolean artistMatches = StringUtils.isBlank(normalizedArtist) || songArtist.contains(normalizedArtist);
        boolean titleMatches = StringUtils.isBlank(normalizedTitle) || songTitle.contains(normalizedTitle);
        return artistMatches && titleMatches;
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(StringUtils.trimToEmpty(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public int refreshSongList() throws IOException, InterruptedException {
        Path root = getSyncerRootDirectory()
                .orElseThrow(() -> new IOException("USDB Syncer path is not configured."));
        Path python = resolvePython(root)
                .orElseThrow(() -> new IOException("No Python executable found for USDB Syncer."));

        String script = String.join("\n",
                                    "import os, sys",
                                    "from pathlib import Path",
                                    "root = Path(os.environ['YASS_SYNCER_ROOT'])",
                                    "sys.path.insert(0, str(root / 'src'))",
                                    "from usdb_syncer import db, song_routines, utils",
                                    "utils.AppPaths.make_dirs()",
                                    "db.connect(utils.AppPaths.db)",
                                    "with db.transaction():",
                                    "    song_routines.load_available_songs(force_reload=True)",
                                    "print(db.usdb_song_count())",
                                    "db.close()");

        ProcessBuilder processBuilder = new ProcessBuilder(python.toString(), "-c", script);
        processBuilder.environment().put("YASS_SYNCER_ROOT", root.toAbsolutePath().toString());
        processBuilder.directory(root.toFile());
        Process process = processBuilder.start();
        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
             BufferedReader errorReader = new BufferedReader(
                     new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String stdout = reader.lines().reduce("", (a, b) -> a + b + System.lineSeparator()).trim();
            String stderr = errorReader.lines().reduce("", (a, b) -> a + b + System.lineSeparator()).trim();
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("USDB Syncer refresh timed out.");
            }
            if (process.exitValue() != 0) {
                String message = StringUtils.defaultIfBlank(stderr, stdout);
                throw new IOException("USDB Syncer refresh failed: " + message);
            }
            output = stdout;
        }
        String[] lines = output.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = StringUtils.trimToEmpty(lines[i]);
            if (line.matches("\\d+")) {
                return Integer.parseInt(line);
            }
        }
        LOGGER.info("USDB Syncer refresh output without song count: " + output);
        return -1;
    }

    private Optional<Path> resolveDatabasePath() {
        if (!isConfigured()) {
            return Optional.empty();
        }
        List<Path> candidates = new ArrayList<>();
        String localAppData = System.getenv("LOCALAPPDATA");
        if (StringUtils.isNotBlank(localAppData)) {
            candidates.add(Path.of(localAppData, "bohning", "usdb_syncer", "usdb_syncer.db"));
        }
        String appData = System.getenv("APPDATA");
        if (StringUtils.isNotBlank(appData)) {
            candidates.add(Path.of(appData, "bohning", "usdb_syncer", "usdb_syncer.db"));
        }
        getSyncerRootDirectory().ifPresent(root -> {
            candidates.add(root.resolve("usdb_syncer.db"));
            candidates.add(root.resolve("data").resolve("usdb_syncer.db"));
        });
        return candidates.stream().filter(Files::isRegularFile).findFirst();
    }

    private Optional<Path> getSyncerRootDirectory() {
        String configured = StringUtils.trimToEmpty(properties.getProperty(PROPERTY_SYNCER_PATH));
        if (StringUtils.isBlank(configured)) {
            return Optional.empty();
        }
        Path path = Path.of(configured);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        if (Files.isDirectory(path)) {
            return Optional.of(path);
        }
        Path parent = path.getParent();
        return parent != null && Files.isDirectory(parent) ? Optional.of(parent) : Optional.empty();
    }

    private Optional<Path> resolvePython(Path root) {
        List<Path> candidates = List.of(
                root.resolve(".venv").resolve("Scripts").resolve("python.exe"),
                root.resolve("venv").resolve("Scripts").resolve("python.exe"));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        String[] executableNames = {"python.exe", "python", "py.exe", "py"};
        String pathEnv = System.getenv("PATH");
        if (StringUtils.isBlank(pathEnv)) {
            return Optional.empty();
        }
        for (String pathEntry : pathEnv.split(java.io.File.pathSeparator)) {
            for (String executableName : executableNames) {
                Path candidate = Path.of(pathEntry).resolve(executableName);
                if (Files.isRegularFile(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }
}
