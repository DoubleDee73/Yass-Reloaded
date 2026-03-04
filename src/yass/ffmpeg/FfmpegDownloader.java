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

package yass.ffmpeg;

import yass.I18;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

public class FfmpegDownloader {

    private static final String FFMPEG_DOWNLOAD_URL = "https://ffmpeg.org/download.html";

    /**
     * Checks if an internet connection is available by trying to connect to a reliable host.
     */
    public static boolean isInternetAvailable() {
        try {
            final URL url = new URL("https://www.google.com");
            final URLConnection conn = url.openConnection();
            conn.connect();
            conn.getInputStream().close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Shows a dialog to the user to either download FFmpeg or select an existing installation.
     * @param parent The parent component for the dialog.
     * @return The path to the selected FFmpeg directory, or null if the user cancels.
     */
    public static String promptForFfmpegInstallation(Component parent) {
        if (!isInternetAvailable()) {
            JOptionPane.showMessageDialog(parent,
                    "<html>" + I18.get("ffmpeg_not_found_offline") + "</html>",
                    I18.get("tool_prefs_ffmpeg_title"),
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }

        Object[] options = {
                I18.get("ffmpeg_button_download"),
                I18.get("ffmpeg_button_select_folder"),
                I18.get("button_cancel")
        };

        int choice = JOptionPane.showOptionDialog(parent,
                "<html>" + I18.get("ffmpeg_not_found_online") + "</html>",
                I18.get("tool_prefs_ffmpeg_title"),
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                options,
                options[0]);

        switch (choice) {
            case 0: // Open Download Page
                openDownloadPage();
                return null; // User needs to download and then select manually
            case 1: // Select Folder
                return selectFfmpegFolder(parent);
            default: // Cancel or closed dialog
                return null;
        }
    }

    private static void openDownloadPage() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(FFMPEG_DOWNLOAD_URL));
            } else {
                // Fallback for systems where Desktop API is not supported
                JOptionPane.showMessageDialog(null, I18.get("ffmpeg_open_url_manually") + "\n" + FFMPEG_DOWNLOAD_URL);
            }
        } catch (IOException | URISyntaxException e) {
            JOptionPane.showMessageDialog(null, I18.get("ffmpeg_open_url_manually") + "\n" + FFMPEG_DOWNLOAD_URL);
        }
    }

    private static String selectFfmpegFolder(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(I18.get("ffmpeg_select_folder_title"));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = chooser.getSelectedFile();
            String path = selectedFolder.getAbsolutePath();

            // Use FFMPEGLocator to validate the selected folder
            // This ensures consistency with how Yass validates the path elsewhere
            FFMPEGLocator locator = FFMPEGLocator.initFfmpeg(path);

            if (locator.getFfmpeg() != null && locator.getFfprobe() != null) {
                return path;
            } else {
                JOptionPane.showMessageDialog(parent,
                        "<html>" + I18.get("ffmpeg_folder_invalid") + "</html>",
                        I18.get("ffmpeg_folder_invalid_title"),
                        JOptionPane.ERROR_MESSAGE);
                return null;
            }
        }
        return null;
    }
}
