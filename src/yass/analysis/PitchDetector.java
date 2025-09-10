/*
 * Yass Reloaded - Karaoke Editor
 * Copyright (C) 2009-2023 Saruta
 * Copyright (C) 2024-2025 DoubleDee
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

package yass.analysis;

import yass.Timebase;
import yass.YassPlayer;
import yass.YassProperties;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PitchDetector {

    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    public static void detectPitch(String audioFilePath, Consumer<List<PitchData>> onPitchDetected, YassProperties properties) {
        if (audioFilePath == null || audioFilePath.isEmpty()) {
            onPitchDetected.accept(new ArrayList<>());
            return;
        }

        File tempWavFile;
        try {
            tempWavFile = File.createTempFile("yass-pitch-", ".wav");
            tempWavFile.deleteOnExit();
            YassPlayer player = new YassPlayer(null, false, false);
            player.generateTemp(audioFilePath, Timebase.NORMAL, tempWavFile.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to create temporary WAV for pitch detection", e);
            onPitchDetected.accept(new ArrayList<>());
            return;
        }

        new Thread(() -> {
            List<PitchData> pitchDataList = new ArrayList<>();
            try {
                String aubioCommand = "aubiopitch";
                String aubioPath = properties.getProperty("aubioPath");
                if (aubioPath != null && !aubioPath.isEmpty()) {
                    aubioCommand = new File(aubioPath, aubioCommand).getAbsolutePath();
                }
                String[] command = {aubioCommand, "-i", tempWavFile.getAbsolutePath()};
                ProcessBuilder processBuilder = new ProcessBuilder(command);
                Process process = processBuilder.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // Output is two columns: timestamp pitch
                        String[] parts = line.split("\\s+");
                        if (parts.length == 2) {
                            try {
                                float time = Float.parseFloat(parts[0]);
                                float pitch = Float.parseFloat(parts[1]);
                                // aubio pitch outputs 0 for unpitched frames, so we filter them.
                                if (pitch > 0) {
                                    // The default CLI output doesn't include confidence, so we use 1.0 for detected pitches.
                                    pitchDataList.add(new PitchData(time, pitch, 1.0f));
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    LOGGER.info("Aubio pitch detection found " + pitchDataList.size() + " pitched frames.");
                    SwingUtilities.invokeLater(() -> onPitchDetected.accept(pitchDataList));
                } else {
                    // Handle errors by logging them
                    LOGGER.severe("aubio pitch process failed with exit code " + exitCode);
                    SwingUtilities.invokeLater(() -> onPitchDetected.accept(new ArrayList<>()));
                }
            } catch (IOException | InterruptedException e) {
                LOGGER.log(Level.SEVERE, "Failed to run Aubio for pitch detection. Is 'aubiopitch' in the system PATH, or configured in options?", e);
                SwingUtilities.invokeLater(() -> onPitchDetected.accept(new ArrayList<>()));
            }
        }, "Aubio Pitch Thread").start();
    }

    /**
     * A simple record to hold the results of pitch detection at a specific time.
     * @param time The timestamp in seconds.
     * @param pitch The detected pitch in Hz.
     * @param probability The confidence of the pitch detection (0-1).
     */
    public record PitchData(float time, float pitch, float probability) {}
}