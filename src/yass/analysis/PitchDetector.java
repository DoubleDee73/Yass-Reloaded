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
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PitchDetector {

    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final double A4_FREQ = 440.0;
    private static final double C4_FREQ = A4_FREQ * Math.pow(2.0, -9.0 / 12.0); // approx 261.626 Hz

    public static List<PitchData> detectPitch(File tempWavFile,
                                              YassProperties properties) {
        List<PitchData> rawPitchData = new ArrayList<>();
        List<PitchData> finalPitchData;
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
                                int semitonesFromC4 = frequencyToNote(pitch);
                                // The default CLI output doesn't include confidence, so we use 1.0 for detected 
                                // pitches.
                                rawPitchData.add(new PitchData(time, semitonesFromC4, freqToNoteName(pitch)));
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                LOGGER.info("Aubio pitch detection found " + rawPitchData.size() + " raw pitched frames.");
                // Wende die neue Kontur-Korrektur an
                finalPitchData = correctOctaveErrorsWithFixedSegmentation(rawPitchData);
                LOGGER.info(
                        "Applied pitch contour correction. Resulting " + finalPitchData.size() + " pitched frames.");
                return finalPitchData;
            } else {
                // Handle errors by logging them
                LOGGER.severe("aubio pitch process failed with exit code " + exitCode + ". Check logs for details.");
                return Collections.emptyList();
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE,
                       "Failed to run Aubio for pitch detection. Is 'aubiopitch' in the system PATH, or configured in" +
                               " options?",
                       e);
            return Collections.emptyList();
        }
    }

    /**
     * Korrigiert Oktavfehler durch Segmentierung der Tonhöhenkontur in musikalische Phrasen.
     * <p>
     * Der Algorithmus funktioniert in drei Schritten:
     * 1. <b>Segmentierung</b>: Die rohen Tonhöhendaten werden in Segmente (Phrasen) unterteilt. Ein neues Segment
     * beginnt nach einer Pause von mehr als {@code SILENCE_THRESHOLD_MS}.
     * 2. <b>Analyse</b>: Für jedes Segment wird die "dominante Oktave" bestimmt, indem ein Histogramm
     * der Oktaven aller Noten im Segment erstellt wird.
     * 3. <b>Korrektur</b>: Jede Note im Segment wird zur dominanten Oktave verschoben, falls sie ein
     * Oktav-Ausreißer ist. Dies bewahrt die relative Melodiekontur, korrigiert aber große Sprünge.
     * <p>
     * Zusätzlich werden harte Grenzen (E2-C6) angewendet, um extrem unrealistische Tonhöhen zu eliminieren.
     *
     * @param rawPitchData Die rohe Liste der von Aubio erkannten Tonhöhen.
     * @return Eine neue Liste von PitchData mit korrigierten Tonhöhen.
     */
    public static List<PitchData> correctOctaveErrorsWithFixedSegmentation(List<PitchData> rawPitchData) {
        if (rawPitchData == null || rawPitchData.isEmpty()) {
            return Collections.emptyList();
        }

        final List<PitchData> correctedData = new ArrayList<>();
        final float SEGMENT_DURATION_MS = 125.0f; // 125ms Segmente
        final int LOWER_BOUND_MIDI = 40; // E2
        final int UPPER_BOUND_MIDI = 84; // C6

        // 1. Segmentierung in feste Zeitabschnitte
        // Finde den Startzeitpunkt des ersten erkannten Tons
        float firstPitchTime = rawPitchData.get(0).time();

        // Verwende eine TreeMap, um die Segmente nach ihrer Startzeit zu sortieren
        Map<Long, List<PitchData>> segmentsMap = new TreeMap<>();

        for (PitchData pd : rawPitchData) {
            // Berechne den Startzeitpunkt des 125ms-Fensters, in das dieser Pitch fällt
            // Die Zeit wird relativ zum ersten Pitch berechnet, dann in 125ms-Blöcke gerastert
            long relativeTimeMs = (long) ((pd.time() - firstPitchTime) * 1000);
            long segmentStartRelativeMs = (long) (Math.floor(relativeTimeMs / SEGMENT_DURATION_MS) * SEGMENT_DURATION_MS);
            long segmentStartAbsoluteMs = (long) (firstPitchTime * 1000 + segmentStartRelativeMs);

            segmentsMap.computeIfAbsent(segmentStartAbsoluteMs, k -> new ArrayList<>()).add(pd);
        }

        // 2. Analyse und Korrektur jedes Segments
        for (List<PitchData> segment : segmentsMap.values()) {

            if (segment.isEmpty()) {
                continue;
            }

            // 1. Finde die dominante Oktave für dieses Segment
            Map<Integer, Integer> octaveHistogram = new HashMap<>();
            for (PitchData pd : segment) {
                int midiNote = pd.pitch() + 60;
                int octave = midiNote / 12;
                octaveHistogram.put(octave, octaveHistogram.getOrDefault(octave, 0) + 1);
            }

            int dominantOctave = octaveHistogram.entrySet().stream()
                                                .max(Map.Entry.comparingByValue())
                                                .map(Map.Entry::getKey)
                                                .orElse(segment.get(0).pitch() + 60 / 12); // Fallback

            // 2. Korrigiere jede Note im Segment basierend auf der dominanten Oktave
            for (PitchData pd : segment) {
                int currentMidiNote = pd.pitch() + 60;
                int correctedMidiNote = currentMidiNote;

                // Verschiebe die Note in die Nähe der dominanten Oktave
                // Solange die Note mehr als eine halbe Oktave von der Mitte der dominanten Oktave entfernt ist
                while (correctedMidiNote - (dominantOctave * 12 + 6) > 6) {
                    correctedMidiNote -= 12;
                }
                while (correctedMidiNote - (dominantOctave * 12 + 6) < -6) {
                    correctedMidiNote += 12;
                }

                // 3. Wende harte Grenzen an (E2-C6)
                while (correctedMidiNote < LOWER_BOUND_MIDI) {
                    correctedMidiNote += 12;
                }
                while (correctedMidiNote > UPPER_BOUND_MIDI) {
                    correctedMidiNote -= 12;
                }

                // Erstelle neues PitchData-Objekt, falls eine Korrektur stattgefunden hat
                if (correctedMidiNote != currentMidiNote) {
                    int correctedPitch = correctedMidiNote - 60;
                    double correctedFreq = C4_FREQ * Math.pow(2.0, correctedPitch / 12.0);
                    correctedData.add(new PitchData(pd.time(), correctedPitch, freqToNoteName(correctedFreq)));
                    LOGGER.finest(String.format("Corrected octave for note at %.2fs from %d (MIDI %d) to %d (MIDI %d) in segment starting at %.2fms",
                                                pd.time(), pd.pitch(), currentMidiNote, correctedPitch, correctedMidiNote, segment.get(0).time() * 1000));
                } else {
                    correctedData.add(pd); // Keine Korrektur, füge Originaldaten hinzu
                }
            }
        }

        // Sortiere die Daten am Ende neu, da die Verarbeitung pro Segment die Reihenfolge ändern könnte (obwohl 
        // unwahrscheinlich)
        correctedData.sort(Comparator.comparing(PitchData::time));
        return correctedData;
    }


    /**
     * A simple record to hold the results of pitch detection at a specific time.
     *
     * @param time  The timestamp in seconds.
     * @param pitch The detected pitch in semitones relative to C4 (C4 = 0).
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
        if (freq <= 0) {
            return "-";
        }
        final String[] noteNames = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int midi = (int) Math.round(69 + 12 * Math.log(freq / 440.0) / Math.log(2));
        int noteIdx = (midi + 1200) % 12; // +1200 für negatives Handling
        int octave = (midi / 12) - 1;
        return noteNames[noteIdx] + octave;
    }
}