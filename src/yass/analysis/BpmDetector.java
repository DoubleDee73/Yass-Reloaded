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
public class BpmDetector {

    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    public static void detectBpm(String audioFilePath, Consumer<Float> onBpmDetected, YassProperties properties) {
        if (audioFilePath == null || audioFilePath.isEmpty()) {
            onBpmDetected.accept(0.0f);
            return;
        }

        File audioFile = new File(audioFilePath);
        if (!audioFile.exists()) {
            LOGGER.warning("BPM detection failed: Audio file not found at " + audioFilePath);
            onBpmDetected.accept(0.0f);
            return;
        }

        File tempWavFile;
        try {
            // Use a managed temporary file to avoid cluttering the user's directories
            tempWavFile = File.createTempFile("yass-bpm-", ".wav");
            tempWavFile.deleteOnExit();
            YassPlayer player = new YassPlayer(null, false, false);
            player.generateTemp(audioFilePath, Timebase.NORMAL, tempWavFile.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to create temporary WAV for BPM detection", e);
            onBpmDetected.accept(0.0f);
            return;
        }

        // Run Aubio in a new thread to avoid blocking the UI
        new Thread(() -> {
            try {
                String aubioCommand = "aubiotrack";
                String aubioPath = properties.getProperty("aubioPath");
                if (aubioPath != null && !aubioPath.isEmpty()) {
                    aubioCommand = new File(aubioPath, aubioCommand).getAbsolutePath();
                }
                String[] command = {aubioCommand, "-i", tempWavFile.getAbsolutePath()};
                ProcessBuilder processBuilder = new ProcessBuilder(command);
                Process process = processBuilder.start();

                StringBuilder output = new StringBuilder(); 
                List<Double> beatTimestamps = new ArrayList<>();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        try {
                            beatTimestamps.add(Double.parseDouble(line.trim()));
                        } catch (NumberFormatException ignored) {
                            // Ignore non-numeric lines
                        }
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    if (beatTimestamps.size() < 5) {
                        LOGGER.warning("Aubiotrack detected fewer than 5 beats; cannot calculate a reliable BPM.");
                        SwingUtilities.invokeLater(() -> onBpmDetected.accept(0.0f));
                        return;
                    }

                    // Calculate BPM from the collected timestamps using a histogram method.
                    final List<Double> iois = new ArrayList<>(); // Inter-Onset Intervals
                    for (int i = 0; i < beatTimestamps.size() - 1; i++) {
                        iois.add(beatTimestamps.get(i + 1) - beatTimestamps.get(i));
                    }

                    final int numberOfBins = 100;
                    final double minIoi = 0.2;  // 300 BPM
                    final double maxIoi = 2.0;   // 30 BPM
                    int[] bins = new int[numberOfBins];
                    for (double ioi : iois) {
                        if (ioi >= minIoi && ioi <= maxIoi) {
                            int binIndex = (int) Math.round(((ioi - minIoi) / (maxIoi - minIoi)) * (numberOfBins - 1));
                            bins[binIndex]++;
                        }
                    }
                    int bestBin = -1, maxCount = 0;
                    for (int i = 0; i < bins.length; i++) {
                        if (bins[i] > maxCount) {
                            maxCount = bins[i];
                            bestBin = i;
                        }
                    }

                    float bpm = 0.0f;
                    if (bestBin != -1 && maxCount > 1) {
                        double bestIoi = minIoi + bestBin * (maxIoi - minIoi) / (double) (numberOfBins - 1);
                        bpm = (float) (60.0 / bestIoi);
                    }

                    LOGGER.info("Aubio calculated BPM: " + bpm);
                    if (bpm > 0) {
                        while (bpm <= 200) {
                            bpm *= 2;
                        }
                    }
                    final float finalBpm = Math.round(bpm);
                    LOGGER.info("Final adjusted BPM: " + finalBpm);
                    SwingUtilities.invokeLater(() -> onBpmDetected.accept(finalBpm));
                } else {
                    // Read error stream for more details
                    StringBuilder errorOutput = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            errorOutput.append(line).append("\n");
                        }
                    }
                    LOGGER.severe("Aubio process failed with exit code " + exitCode + ". Error: " + errorOutput);
                    SwingUtilities.invokeLater(() -> onBpmDetected.accept(0.0f));
                }
            } catch (IOException | InterruptedException e) {
                // Check if the error is because 'aubio' is not found
                if (e instanceof IOException && e.getMessage().contains("Cannot run program \"aubiotrack\"")) {
                    LOGGER.log(Level.SEVERE, "Aubiotrack executable not found. Please ensure it is installed and in your system's PATH, or configure the path in the options.", e);
                    // Optionally, notify the user via the UI.
                } else {
                    LOGGER.log(Level.SEVERE, "Failed to run Aubio for BPM detection", e);
                }
                SwingUtilities.invokeLater(() -> onBpmDetected.accept(0.0f));
            }
        }, "Aubio BPM Thread").start();
    }
}