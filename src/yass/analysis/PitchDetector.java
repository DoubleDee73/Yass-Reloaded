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
import yass.musicalkey.MusicalKeyEnum;

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

    public static List<PitchData> detectPitch(File tempWavFile, YassProperties properties) {
        return detectPitchWithRaw(tempWavFile, properties, MusicalKeyEnum.UNDEFINED).processedPitchData();
    }

    public static List<PitchData> detectPitch(File tempWavFile,
                                              YassProperties properties,
                                              MusicalKeyEnum musicalKey) {
        return detectPitchWithRaw(tempWavFile, properties, musicalKey).processedPitchData();
    }

    public static PitchDetectionResult detectPitchWithRaw(File tempWavFile,
                                                          YassProperties properties,
                                                          MusicalKeyEnum musicalKey) {
        List<PitchData> rawPitchData = new ArrayList<>();
        List<PitchData> locallyNormalizedPitchData;
        List<PitchData> viterbiPitchData;
        List<PitchData> transposedPitchData;
        List<PitchData> finalPitchData;
        try {
            String aubioCommand = "aubiopitch";
            String aubioPath = properties.getProperty("aubioPath");
            if (aubioPath != null && !aubioPath.isEmpty()) {
                aubioCommand = new File(aubioPath, aubioCommand).getAbsolutePath();
            } else {
                return new PitchDetectionResult(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
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
                                rawPitchData.add(new PitchData(time, semitonesFromC4, freqToNoteName(pitch), pitch));
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                LOGGER.info("Aubio pitch detection found " + rawPitchData.size() + " raw pitched frames.");
                locallyNormalizedPitchData = normalizeRawPitchOctaves(rawPitchData);
                viterbiPitchData = viterbiSmooth(locallyNormalizedPitchData, musicalKey);
                transposedPitchData = List.copyOf(viterbiPitchData);
                finalPitchData = List.copyOf(transposedPitchData);
                LOGGER.info("Viterbi smoothing complete. Resulting " + finalPitchData.size() + " pitched frames.");
                return new PitchDetectionResult(List.copyOf(rawPitchData), List.copyOf(locallyNormalizedPitchData), List.copyOf(viterbiPitchData), List.copyOf(transposedPitchData), finalPitchData);
            } else {
                // Handle errors by logging them
                LOGGER.severe("aubio pitch process failed with exit code " + exitCode + ". Check logs for details.");
                return new PitchDetectionResult(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE,
                       "Failed to run Aubio for pitch detection. Is 'aubiopitch' in the system PATH, or configured in" +
                               " options?",
                       e);
            return new PitchDetectionResult(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }
    }

    /**
     * Korrigiert Oktavfehler durch Segmentierung der TonhÃƒÂ¶henkontur in musikalische Phrasen.
     * <p>
     * Der Algorithmus funktioniert in drei Schritten:
     * 1. <b>Segmentierung</b>: Die rohen TonhÃƒÂ¶hendaten werden in Segmente (Phrasen) unterteilt. Ein neues Segment
     * beginnt nach einer Pause von mehr als {@code SILENCE_THRESHOLD_MS}.
     * 2. <b>Analyse</b>: FÃƒÂ¼r jedes Segment wird die "dominante Oktave" bestimmt, indem ein Histogramm
     * der Oktaven aller Noten im Segment erstellt wird.
     * 3. <b>Korrektur</b>: Jede Note im Segment wird zur dominanten Oktave verschoben, falls sie ein
     * Oktav-AusreiÃƒÅ¸er ist. Dies bewahrt die relative Melodiekontur, korrigiert aber groÃƒÅ¸e SprÃƒÂ¼nge.
     * <p>
     * ZusÃƒÂ¤tzlich werden harte Grenzen (E2-C6) angewendet, um extrem unrealistische TonhÃƒÂ¶hen zu eliminieren.
     *
     * @param rawPitchData Die rohe Liste der von Aubio erkannten TonhÃƒÂ¶hen.
     * @return Eine neue Liste von PitchData mit korrigierten TonhÃƒÂ¶hen.
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
            // Berechne den Startzeitpunkt des 125ms-Fensters, in das dieser Pitch fÃƒÂ¤llt
            // Die Zeit wird relativ zum ersten Pitch berechnet, dann in 125ms-BlÃƒÂ¶cke gerastert
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

            // 1. Finde die dominante Oktave fÃƒÂ¼r dieses Segment
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

                // Verschiebe die Note in die NÃƒÂ¤he der dominanten Oktave
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
                    correctedData.add(new PitchData(pd.time(), correctedPitch, freqToNoteName(correctedFreq), correctedFreq));
                    LOGGER.finest(String.format("Corrected octave for note at %.2fs from %d (MIDI %d) to %d (MIDI %d) in segment starting at %.2fms",
                                                pd.time(), pd.pitch(), currentMidiNote, correctedPitch, correctedMidiNote, segment.get(0).time() * 1000));
                } else {
                    correctedData.add(pd); // Keine Korrektur, fÃƒÂ¼ge Originaldaten hinzu
                }
            }
        }

        // Sortiere die Daten am Ende neu, da die Verarbeitung pro Segment die Reihenfolge ÃƒÂ¤ndern kÃƒÂ¶nnte (obwohl 
        // unwahrscheinlich)
        correctedData.sort(Comparator.comparing(PitchData::time));
        return correctedData;
    }

    /**
     * Smooths raw aubio pitch detections using the Viterbi algorithm on a Hidden Markov Model.
     * <p>
     * Hidden states are individual semitones in the range E2–C6 (MIDI 40–84).
     * Observations are the raw detected pitches. The HMM uses:
     * <ul>
     *   <li><b>Emission:</b> Gaussian with σ=1.5 semitones centred on the observed pitch.</li>
     *   <li><b>Transition:</b> exponential decay by semitone distance, with an additional
     *       penalty for octave jumps (±12 semitones) to suppress spurious octave errors
     *       while still allowing genuine leaps when the evidence is sustained.</li>
     *   <li><b>Gap reset:</b> the forward pass restarts after silence gaps longer than
     *       {@code GAP_RESET_SECONDS}, so each phrase is decoded independently.</li>
     * </ul>
     * All probabilities are computed in log-space to avoid floating-point underflow.
     * When a {@code musicalKey} is provided, in-key pitches receive a small log-bonus
     * in the emission probability, acting as a tie-breaker when aubio detects a pitch
     * exactly between two semitones.
     */
    private static List<PitchData> normalizeRawPitchOctaves(List<PitchData> rawPitchData) {
        if (rawPitchData == null || rawPitchData.isEmpty()) {
            return Collections.emptyList();
        }

        final float WINDOW_SECONDS = 0.30f;
        final float GAP_RESET_SECONDS = 0.12f;
        final int MIN_PITCH = -20; // E2 relative to C4
        final int MAX_PITCH = 24;  // C6 relative to C4
        List<PitchData> normalized = new ArrayList<>(rawPitchData.size());
        int windowStart = 0;
        int windowEnd = 0;

        for (int i = 0; i < rawPitchData.size(); i++) {
            PitchData current = rawPitchData.get(i);
            if (i > 0 && Math.abs(current.time() - rawPitchData.get(i - 1).time()) > GAP_RESET_SECONDS) {
                windowStart = i;
                windowEnd = i;
            }

            float minTime = current.time() - WINDOW_SECONDS / 2.0f;
            float maxTime = current.time() + WINDOW_SECONDS / 2.0f;
            while (windowStart < rawPitchData.size() && rawPitchData.get(windowStart).time() < minTime) {
                windowStart++;
            }
            while (windowEnd < rawPitchData.size() && rawPitchData.get(windowEnd).time() <= maxTime) {
                windowEnd++;
            }

            List<Integer> localPitches = new ArrayList<>(Math.max(1, windowEnd - windowStart));
            for (int j = windowStart; j < windowEnd; j++) {
                localPitches.add(rawPitchData.get(j).pitch());
            }
            if (localPitches.isEmpty()) {
                normalized.add(current);
                continue;
            }
            Collections.sort(localPitches);
            int localMedian = localPitches.get(localPitches.size() / 2);
            int normalizedPitch = normalizePitchNearReference(current.pitch(), localMedian);
            while (normalizedPitch < MIN_PITCH) {
                normalizedPitch += 12;
            }
            while (normalizedPitch > MAX_PITCH) {
                normalizedPitch -= 12;
            }
            if (normalizedPitch == current.pitch()) {
                normalized.add(current);
            } else {
                double normalizedFreq = C4_FREQ * Math.pow(2.0, normalizedPitch / 12.0);
                normalized.add(new PitchData(current.time(), normalizedPitch, freqToNoteName(normalizedFreq), normalizedFreq));
            }
        }
        return normalized;
    }

    private static List<PitchData> viterbiSmooth(List<PitchData> rawPitchData, MusicalKeyEnum musicalKey) {
        if (rawPitchData == null || rawPitchData.isEmpty()) {
            return Collections.emptyList();
        }

        final int MIDI_LOW  = 40; // E2
        final int MIDI_HIGH = 84; // C6
        final int N = MIDI_HIGH - MIDI_LOW + 1; // 45 states
        final float GAP_RESET_SECONDS = 0.12f;

        // Emission: log P(observed | state) — Gaussian, σ = 1.5 semitones
        final double SIGMA = 1.5;
        final double LOG_SIGMA_NORM = -Math.log(SIGMA * Math.sqrt(2 * Math.PI));
        // Small log-bonus for pitches in the musical key — acts as tie-breaker only
        final double IN_KEY_LOG_BONUS = Math.log(1.05);

        // Transition: base decay per semitone distance
        final double TRANSITION_DECAY = 0.5; // log factor per semitone of distance
        // Extra log-penalty for octave jumps to suppress harmonic errors
        final double OCTAVE_JUMP_LOG_PENALTY = Math.log(0.05);

        int T = rawPitchData.size();
        double[] logViterbi = new double[N];
        int[][] backpointer = new int[T][N];

        // Precompute log-transition matrix: logTrans[from][to]
        // Stored as a flat array for cache efficiency
        double[] logTrans = new double[N * N];
        for (int from = 0; from < N; from++) {
            double rowSum = 0;
            double[] raw = new double[N];
            for (int to = 0; to < N; to++) {
                int dist = Math.abs(from - to);
                double logP = -TRANSITION_DECAY * dist;
                if (dist == 12) {
                    logP += OCTAVE_JUMP_LOG_PENALTY;
                }
                raw[to] = Math.exp(logP);
                rowSum += raw[to];
            }
            for (int to = 0; to < N; to++) {
                logTrans[from * N + to] = Math.log(raw[to] / rowSum);
            }
        }

        List<PitchData> result = new ArrayList<>(T);

        // Process the sequence in contiguous phrases separated by silence gaps
        int phraseStart = 0;
        while (phraseStart < T) {
            // Find the end of this phrase (next gap or end of data)
            int phraseEnd = phraseStart + 1;
            while (phraseEnd < T &&
                    rawPitchData.get(phraseEnd).time() - rawPitchData.get(phraseEnd - 1).time() <= GAP_RESET_SECONDS) {
                phraseEnd++;
            }

            int phraseLen = phraseEnd - phraseStart;
            int[][] bp = new int[phraseLen][N];

            // --- Initialisation: uniform prior, apply emission for first frame ---
            int obs0midi = rawPitchData.get(phraseStart).pitch() + 60;
            for (int s = 0; s < N; s++) {
                int stateMidi = MIDI_LOW + s;
                double diff = stateMidi - obs0midi;
                double logEmit0 = LOG_SIGMA_NORM - (diff * diff) / (2 * SIGMA * SIGMA);
                if (musicalKey != null && musicalKey.isInKey(stateMidi)) {
                    logEmit0 += IN_KEY_LOG_BONUS;
                }
                logViterbi[s] = logEmit0;
                // uniform prior: no adjustment needed (log(1/N) cancels in argmax)
                bp[0][s] = s;
            }

            // --- Recursion ---
            double[] newLogViterbi = new double[N];
            for (int t = 1; t < phraseLen; t++) {
                int obsMidi = rawPitchData.get(phraseStart + t).pitch() + 60;
                for (int to = 0; to < N; to++) {
                    int stateMidi = MIDI_LOW + to;
                    double diff = stateMidi - obsMidi;
                    double logEmit = LOG_SIGMA_NORM - (diff * diff) / (2 * SIGMA * SIGMA);
                    if (musicalKey != null && musicalKey.isInKey(stateMidi)) {
                        logEmit += IN_KEY_LOG_BONUS;
                    }
                    double best = Double.NEGATIVE_INFINITY;
                    int bestFrom = 0;
                    for (int from = 0; from < N; from++) {
                        double score = logViterbi[from] + logTrans[from * N + to];
                        if (score > best) {
                            best = score;
                            bestFrom = from;
                        }
                    }
                    newLogViterbi[to] = best + logEmit;
                    bp[t][to] = bestFrom;
                }
                System.arraycopy(newLogViterbi, 0, logViterbi, 0, N);
            }

            // --- Backtrack ---
            int[] stateSeq = new int[phraseLen];
            double bestFinal = Double.NEGATIVE_INFINITY;
            for (int s = 0; s < N; s++) {
                if (logViterbi[s] > bestFinal) {
                    bestFinal = logViterbi[s];
                    stateSeq[phraseLen - 1] = s;
                }
            }
            for (int t = phraseLen - 2; t >= 0; t--) {
                stateSeq[t] = bp[t + 1][stateSeq[t + 1]];
            }

            // --- Emit smoothed PitchData for this phrase ---
            for (int t = 0; t < phraseLen; t++) {
                int smoothedMidi = MIDI_LOW + stateSeq[t];
                int smoothedPitch = smoothedMidi - 60;
                PitchData original = rawPitchData.get(phraseStart + t);
                if (smoothedPitch == original.pitch()) {
                    result.add(original);
                } else {
                    double smoothedFreq = C4_FREQ * Math.pow(2.0, smoothedPitch / 12.0);
                    result.add(new PitchData(original.time(), smoothedPitch, freqToNoteName(smoothedFreq), smoothedFreq));
                }
            }

            phraseStart = phraseEnd;
        }

        return result;
    }

    /**
     * Transposes the entire pitch list by a whole number of octaves so that the median pitch
     * falls within the vocal target range C4–E6 (semitones 0–28 relative to C4).
     * The shift is applied uniformly to all frames (no per-note adjustment).
     */
    private static List<PitchData> transposeToVocalRange(List<PitchData> pitchData) {
        if (pitchData == null || pitchData.isEmpty()) {
            return pitchData;
        }

        // C4 = 0, E6 = 28 (semitones relative to C4)
        final int VOCAL_LOW  = 0;   // C4
        final int VOCAL_HIGH = 28;  // E6
        final int VOCAL_MID  = (VOCAL_LOW + VOCAL_HIGH) / 2; // 14 ~ D5

        // Compute median pitch
        List<Integer> sorted = pitchData.stream()
                .map(PitchData::pitch)
                .sorted()
                .toList();
        int median = sorted.get(sorted.size() / 2);

        // Find the octave shift (multiple of 12) that brings the median closest to the target midpoint
        int shift = 0;
        int bestDistance = Math.abs(median - VOCAL_MID);
        for (int candidate = -48; candidate <= 48; candidate += 12) {
            int distance = Math.abs(median + candidate - VOCAL_MID);
            if (distance < bestDistance) {
                bestDistance = distance;
                shift = candidate;
            }
        }

        if (shift == 0) {
            return pitchData;
        }

        LOGGER.info(String.format("Transposing detected pitches by %+d semitones (median %d -> %d) to fit C4-E6 range.",
                shift, median, median + shift));

        final int finalShift = shift;
        return pitchData.stream()
                .map(pd -> {
                    int newPitch = pd.pitch() + finalShift;
                    double newFreq = C4_FREQ * Math.pow(2.0, newPitch / 12.0);
                    return new PitchData(pd.time(), newPitch, freqToNoteName(newFreq), newFreq);
                })
                .toList();
    }

    private static List<PitchData> stabilizePitchContour(List<PitchData> pitchData) {
        if (pitchData == null || pitchData.isEmpty()) {
            return Collections.emptyList();
        }

        final float WINDOW_SECONDS = 0.35f;
        final float GAP_RESET_SECONDS = 0.12f;
        final List<PitchData> stabilizedData = new ArrayList<>(pitchData.size());
        int windowStart = 0;
        int windowEnd = 0;
        Integer previousStablePitch = null;

        for (int i = 0; i < pitchData.size(); i++) {
            PitchData current = pitchData.get(i);
            if (i > 0 && Math.abs(current.time() - pitchData.get(i - 1).time()) > GAP_RESET_SECONDS) {
                previousStablePitch = null;
            }

            float minTime = current.time() - WINDOW_SECONDS / 2.0f;
            float maxTime = current.time() + WINDOW_SECONDS / 2.0f;

            while (windowStart < pitchData.size() && pitchData.get(windowStart).time() < minTime) {
                windowStart++;
            }
            while (windowEnd < pitchData.size() && pitchData.get(windowEnd).time() <= maxTime) {
                windowEnd++;
            }

            Map<Integer, Integer> pitchHistogram = new HashMap<>();
            for (int j = windowStart; j < windowEnd; j++) {
                PitchData sample = pitchData.get(j);
                pitchHistogram.put(sample.pitch(), pitchHistogram.getOrDefault(sample.pitch(), 0) + 1);
            }

            int stablePitch = chooseStablePitch(pitchHistogram, current.pitch(), previousStablePitch);
            previousStablePitch = stablePitch;

            if (stablePitch == current.pitch()) {
                stabilizedData.add(current);
            } else {
                double stabilizedFreq = C4_FREQ * Math.pow(2.0, stablePitch / 12.0);
                stabilizedData.add(new PitchData(current.time(), stablePitch, freqToNoteName(stabilizedFreq), stabilizedFreq));
            }
        }

        return stabilizedData;
    }

    private static int chooseStablePitch(Map<Integer, Integer> pitchHistogram, int fallbackPitch,
                                         Integer previousStablePitch) {
        if (pitchHistogram.isEmpty()) {
            return fallbackPitch;
        }

        int referencePitch = previousStablePitch != null ? previousStablePitch : fallbackPitch;
        int bestPitch = fallbackPitch;
        int bestCount = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (Map.Entry<Integer, Integer> entry : pitchHistogram.entrySet()) {
            int candidatePitch = normalizePitchNearReference(entry.getKey(), referencePitch);
            int candidateCount = entry.getValue();
            int candidateDistance = Math.abs(candidatePitch - referencePitch);
            if (candidateCount > bestCount) {
                bestPitch = candidatePitch;
                bestCount = candidateCount;
                bestDistance = candidateDistance;
                continue;
            }
            if (candidateCount == bestCount) {
                if (candidateDistance < bestDistance) {
                    bestPitch = candidatePitch;
                    bestDistance = candidateDistance;
                    continue;
                }
                if (candidateDistance == bestDistance &&
                        Math.abs(candidatePitch - fallbackPitch) < Math.abs(bestPitch - fallbackPitch)) {
                    bestPitch = candidatePitch;
                }
            }
        }
        return bestPitch;
    }

    private static int normalizePitchNearReference(int pitch, int referencePitch) {
        int normalized = pitch;
        while (normalized - referencePitch > 6) {
            normalized -= 12;
        }
        while (referencePitch - normalized > 6) {
            normalized += 12;
        }
        return normalized;
    }


    /**
     * A simple record to hold the results of pitch detection at a specific time.
     *
     * @param time  The timestamp in seconds.
     * @param pitch The detected pitch in semitones relative to C4 (C4 = 0).
     */
    public record PitchData(float time, int pitch, String noteName, double rawFrequency) {
    }

    public record PitchDetectionResult(List<PitchData> rawPitchData,
                                       List<PitchData> locallyNormalizedPitchData,
                                       List<PitchData> viterbiPitchData,
                                       List<PitchData> transposedPitchData,
                                       List<PitchData> processedPitchData) {
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
        int noteIdx = (midi + 1200) % 12; // +1200 fÃƒÂ¼r negatives Handling
        int octave = (midi / 12) - 1;
        return noteNames[noteIdx] + octave;
    }
}
