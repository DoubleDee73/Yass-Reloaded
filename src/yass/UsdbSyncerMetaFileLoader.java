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

package yass;

import com.google.gson.JsonSyntaxException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.stream.JsonReader;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UsdbSyncerMetaFileLoader {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final Set<String> REPORTED_INVALID_META_FILES = ConcurrentHashMap.newKeySet();
    private UsdbSyncerMetaFile metaFile;
    private String metaFilePath;

    public UsdbSyncerMetaFileLoader(String filename) {
        if (!loadUsdbSyncerMetaFile(filename)) {
            metaFile = null;
            metaFilePath = null;
        }
    }

    private boolean loadUsdbSyncerMetaFile(String filename) {
        File dir = new File(filename);
        Gson json = new GsonBuilder()
                .setObjectToNumberStrategy(ToNumberPolicy.BIG_DECIMAL)
                .disableHtmlEscaping()
                .create();

        if (dir.listFiles() != null) {
            for (File usdb : Objects.requireNonNull(dir.listFiles())) {
                if (!usdb.getName().endsWith(".usdb")) {
                    continue;
                }
                UsdbSyncerMetaFile metaFile;
                try (JsonReader jsonReader = new JsonReader(new FileReader(usdb.getAbsolutePath()))) {
                    metaFile = json.fromJson(jsonReader, UsdbSyncerMetaFile.class);
                } catch (IOException | JsonSyntaxException | IllegalStateException e) {
                    logInvalidMetaFile(usdb, e);
                    continue;
                }
                if (metaFile == null) {
                    logInvalidMetaFile(usdb, null);
                    continue;
                }
                this.metaFile = metaFile;
                this.metaFilePath = usdb.getAbsolutePath();
                return true;
            }
        }
        return false;
    }

    public UsdbSyncerMetaFile getMetaFile() {
        return metaFile;
    }

    public String getMetaFilePath() {
        return metaFilePath;
    }

    private void logInvalidMetaFile(File usdb, Exception exception) {
        String path = usdb.getAbsolutePath();
        if (!REPORTED_INVALID_META_FILES.add(path)) {
            return;
        }
        String reason = exception == null
                ? "empty or null JSON"
                : ((exception.getMessage() == null || exception.getMessage().isBlank())
                ? exception.getClass().getSimpleName()
                : exception.getMessage());
        LOGGER.log(Level.WARNING, "Skipping invalid USDB meta file: " + path + " (" + reason + ")");
    }
}
