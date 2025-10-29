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

import yass.YassProperties;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PitchDetector {

    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final double A4_FREQ = 440.0;
    private static final double C4_FREQ = A4_FREQ * Math.pow(2.0, -9.0 / 12.0); // approx 261.626 Hz

    public static List<PitchData> detectPitch(File tempWavFile,
                                   YassProperties properties) {
        List<PitchData> pitchDataList = new ArrayList<>();
        try {
            String aubioCommand = "aubiopitch";
            String aubioPath = properties.getProperty("aubioPath");
            if (aubioPath != null && !aubioPath.isEmpty()) {
                aubioCommand = new File(aubioPath, aubioCommand).getAbsolutePath();
            } else {
                return Collections.emptyList();
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
                                int note = frequencyToNote(pitch);
                                // The default CLI output doesn't include confidence, so we use 1.0 for detected pitches.
                                pitchDataList.add(new PitchData(time, note, freqToNoteName(pitch)));
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                LOGGER.info("Aubio pitch detection found " + pitchDataList.size() + " pitched frames.");
                    return pitchDataList;
            } else {
                // Handle errors by logging them
                LOGGER.severe("aubio pitch process failed with exit code " + exitCode);
                return Collections.emptyList();
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE,
                       "Failed to run Aubio for pitch detection. Is 'aubiopitch' in the system PATH, or configured in options?",
                       e);
            return Collections.emptyList();
        }
        
    }

    /**
     * A simple record to hold the results of pitch detection at a specific time.
     *
     * @param time        The timestamp in seconds.
     * @param pitch       The detected pitch in Hz.
     */
    public record PitchData(float time, int pitch, String noteName) {
    }

    /**
     * Converts a frequency in Hertz (Hz) to a relative integer note value,
     * where C4 is 0.
     * <p>
     * The calculation is based on the equal temperament scale. Each integer
     * value represents a semitone (half step). For example:
     * <ul>
     *   <li>C4  -> 0</li>
     *   <li>C#4 -> 1</li>
     *   <li>D4  -> 2</li>
     *   <li>B3  -> -1</li>
     * </ul>
     *
     * @param frequency The frequency in Hz to convert. Must be a positive value.
     * @return The integer representing the note, relative to C4.
     */
    public static int frequencyToNote(double frequency) {
        if (frequency <= 0) {
            throw new IllegalArgumentException("Frequency must be positive.");
        }
        double semitonesFromC4 = 12 * (Math.log(frequency / C4_FREQ) / Math.log(2.0));
        return (int) Math.round(semitonesFromC4);
    }

    // Hilfsmethode zur Umwandlung von Frequenz in Notenname
    public static String freqToNoteName(double freq) {
        if (freq <= 0) return "-";
        final String[] noteNames = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int midi = (int) Math.round(69 + 12 * Math.log(freq / 440.0) / Math.log(2));
        int noteIdx = (midi + 1200) % 12; // +1200 für negatives Handling
        int octave = (midi / 12) - 1;
        return noteNames[noteIdx] + octave;
    }
}