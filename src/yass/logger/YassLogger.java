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

package yass.logger;

import yass.YassMain;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.*;

public class YassLogger {
    public static void init(String logfile) {
        try {
            InputStream loggerProps = YassMain.class.getResourceAsStream("/yass/resources/logger.properties");
            LogManager.getLogManager().readConfiguration(loggerProps);
            Logger staticLogger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
            FileHandler fileHandler = new FileHandler(logfile);
            fileHandler.setFormatter(new SimpleFormatter());
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new SimpleFormatter());
            staticLogger.addHandler(fileHandler);
            staticLogger.addHandler(consoleHandler);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
