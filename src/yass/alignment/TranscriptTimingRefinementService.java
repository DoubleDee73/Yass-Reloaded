package yass.alignment;

import org.apache.commons.lang3.StringUtils;
import yass.analysis.PitchDetector;
import yass.integration.transcription.openai.OpenAiTranscriptSegment;
import yass.integration.transcription.openai.OpenAiTranscriptWord;
import yass.integration.transcription.openai.OpenAiTranscriptionResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

public class TranscriptTimingRefinementService {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    public static final int MAX_INITIAL_OFFSET_MS = 5_000;
    public static final int LONG_SILENCE_MS = 700;
    // Vocal-entry detection used when the GAP is unknown (0): a real first sung note rises from
    // near-silence into strong, sustained energy. Dialog / instrumental bleed before the song lacks
    // this silence-then-jump shape, so it is skipped. The silence is checked in a window that ends
    // a little before the candidate (VOCAL_ENTRY_LEAD_MS), so the note's own attack ramp is not
    // mistaken for pre-song signal; once a qualifying frame is found, the onset is walked back to the
    // start of its attack.
    private static final int VOCAL_ENTRY_QUIET_LOOKBACK_MS = 200;
    private static final int VOCAL_ENTRY_LEAD_MS = 120;
    private static final double VOCAL_ENTRY_QUIET_RATIO = 0.15d;
    private static final double VOCAL_ENTRY_STRONG_RATIO = 0.40d;
    private static final int FRAME_TAIL_MS = 40;
    private static final int NEXT_ANCHOR_PRE_ROLL_MS = 1_000;
    private static final int CONTINUOUS_SIGNAL_GAP_MS = 300;
    private static final int LOCAL_ANCHOR_WINDOW_MS = 250;
    private static final int OCCUPANCY_SAMPLE_MS = 10;
    private static final double OCCUPANCY_REQUIRED_RATIO = 0.5d;
    private static final int MIN_GRID_STEP_MS = 10;
    private static final int DEBUG_PHRASE_LIMIT = 6;
    private static final int ONSET_BUCKET_MS = 25;
    private static final int ONSET_SIGNAL_START_SNAP_MS = 45;
    private static final int MIN_ONSET_GAP_MS = 120;
    private static final int OVER_FRAGMENTED_WINDOW_RATIO = 2;
    private static final int ENERGY_BLOCK_GAP_MS = 60;
    private static final int PITCH_BAND_OCTAVES = 2;
    private static final int SEMITONES_PER_OCTAVE = 12;
    private static final double PITCH_BAND_MIN_COVERAGE = 0.5d;
    private static final int PITCH_SPLIT_THRESHOLD_SEMITONES = 2;
    private static final double ENERGY_THRESHOLD_RATIO = 0.25d;
    private static final double END_ENERGY_THRESHOLD_RATIO = 0.45d;
    private static final double WINDOW_ENERGY_THRESHOLD_RATIO = 0.20d;
    private static final double ONSET_ENERGY_THRESHOLD_RATIO = 0.18d;
    private static final double ONSET_ENERGY_RISE_RATIO = 0.10d;
    private static final double REJECTED_CLUSTER_ONSET_THRESHOLD_RATIO = 0.45d;

    public TimingRefinementAnalysis analyze(OpenAiTranscriptionResult transcript,
                                            List<PitchDetector.PitchData> vocalFrames,
                                            int currentGapMs) {
        return analyze(transcript, vocalFrames, currentGapMs, 0d);
    }

    public TimingRefinementAnalysis analyze(OpenAiTranscriptionResult transcript,
                                            List<PitchDetector.PitchData> vocalFrames,
                                            int currentGapMs,
                                            double bpm) {
        List<OpenAiTranscriptSegment> phrases = collectPhrases(transcript);
        if (phrases.isEmpty()) {
            return TimingRefinementAnalysis.unavailable(currentGapMs, "no transcript phrases");
        }
        List<Integer> timingAnchorsMs = collectTimingAnchors(transcript);
        List<PitchDetector.PitchData> bandFrames = filterToDominantPitchBand(vocalFrames);
        List<PitchDetector.PitchData> usableFrames = collectUsableFrames(bandFrames);
        List<PitchDetector.PitchData> significantFrames = collectSignificantFrames(usableFrames);
        if (significantFrames.isEmpty()) {
            return TimingRefinementAnalysis.unavailable(currentGapMs, "no significant vocal frames");
        }

        int firstTranscriptStartMs = findFirstTranscriptStartMs(phrases);
        PitchDetector.PitchData firstVocalOnset = findInitialVocalOnset(significantFrames, usableFrames, currentGapMs);
        int firstVocalOnsetMs = frameStartMs(firstVocalOnset);
        int gapDeltaMs = firstVocalOnsetMs - currentGapMs;
        int transcriptToAudioOffsetMs = firstVocalOnsetMs - firstTranscriptStartMs;
        boolean accepted = true;
        int proposedGapMs = firstVocalOnsetMs;
        String rejectionReason = "";

        List<Integer> localPhraseStartsMs = new ArrayList<>(phrases.size());
        for (OpenAiTranscriptSegment phrase : phrases) {
            int transcriptStartMs = phraseStartMs(phrase);
            int adjustedStartMs = accepted ? transcriptStartMs + transcriptToAudioOffsetMs : transcriptStartMs;
            int transcriptEndMs = phraseEndMs(phrase);
            int adjustedEndMs = accepted ? transcriptEndMs + transcriptToAudioOffsetMs : transcriptEndMs;
            localPhraseStartsMs.add(detectPhraseStartNearTranscriptAnchor(usableFrames,
                    transcriptStartMs,
                    adjustedStartMs,
                    adjustedEndMs));
        }

        List<PhraseTiming> phraseTimings = new ArrayList<>();
        for (int index = 0; index < phrases.size(); index++) {
            OpenAiTranscriptSegment phrase = phrases.get(index);
            int transcriptStartMs = phraseStartMs(phrase);
            int adjustedStartMs = accepted ? transcriptStartMs + transcriptToAudioOffsetMs : transcriptStartMs;
            int transcriptEndMs = phraseEndMs(phrase);
            int adjustedEndMs = accepted ? transcriptEndMs + transcriptToAudioOffsetMs : transcriptEndMs;
            Integer nextAnchorMs = null;
            Integer detectedStartMs = localPhraseStartsMs.get(index);
            boolean hasLocalPhraseStart = detectedStartMs != null;
            if (detectedStartMs == null) {
                detectedStartMs = findPhraseStartAfterAnchor(significantFrames, adjustedStartMs, adjustedEndMs);
            }
            if (index == 0) {
                Integer detectedOpeningStartMs = detectOpeningPhraseStart(usableFrames,
                        transcriptStartMs,
                        adjustedEndMs,
                        bpm);
                if (detectedOpeningStartMs != null) {
                    detectedStartMs = detectedOpeningStartMs;
                }
            } else if (!hasLocalPhraseStart && !phraseTimings.isEmpty()) {
                PhraseTiming previousTiming = phraseTimings.get(phraseTimings.size() - 1);
                int previousPhraseEndMs = previousTiming.detectedVocalEndMs() != null
                        ? previousTiming.detectedVocalEndMs()
                        : previousTiming.adjustedEndMs();
                int currentStartAnchorMs = detectedStartMs != null ? detectedStartMs : adjustedStartMs;
                Integer detectedBoundaryStartMs = findPhraseStartBetweenBoundaries(significantFrames,
                        previousPhraseEndMs,
                        currentStartAnchorMs);
                if (detectedBoundaryStartMs != null) {
                    detectedStartMs = detectedBoundaryStartMs;
                }
            }
            Integer detectedEndMs = null;
            String decision = "no following phrase anchor";
            Integer nextTranscriptAnchorMs = findNextTimingAnchor(timingAnchorsMs, transcriptStartMs);
            if (nextTranscriptAnchorMs != null) {
                Integer nextLocalPhraseStartMs = index + 1 < localPhraseStartsMs.size()
                        ? localPhraseStartsMs.get(index + 1)
                        : null;
                int adjustedNextAnchorMs = accepted ? nextTranscriptAnchorMs + transcriptToAudioOffsetMs
                        : nextTranscriptAnchorMs;
                nextAnchorMs = nextLocalPhraseStartMs != null
                        && nextLocalPhraseStartMs > adjustedStartMs
                        && nextLocalPhraseStartMs < adjustedNextAnchorMs
                        ? nextLocalPhraseStartMs
                        : adjustedNextAnchorMs;
                VocalEndDetection detection = findVocalEndBeforeNextAnchor(usableFrames, adjustedStartMs, nextAnchorMs);
                detectedEndMs = detection.endMs();
                decision = detection.decision();
            }
            int signalWindowStartMs = detectedStartMs != null ? detectedStartMs : adjustedStartMs;
            int signalWindowEndMs = detectedEndMs != null ? detectedEndMs : adjustedEndMs;
            List<SignalWindow> signalWindows = detectSignalWindows(usableFrames,
                    signalWindowStartMs,
                    signalWindowEndMs,
                    bpm);
            List<Integer> rawOnsetAnchorsMs = detectOnsetAnchors(usableFrames,
                    signalWindowStartMs,
                    signalWindowEndMs);
            List<Integer> onsetAnchorsMs = rawOnsetAnchorsMs;
            if (bpm > 0d && phrase.getWords() != null && !phrase.getWords().isEmpty()) {
                int wordCount = phrase.getWords().size();
                signalWindows = trimTrailingNextPhraseWindows(signalWindows, wordCount, nextAnchorMs);
                List<SignalWindow> weakSupportWindows = findWeakSupportWindows(signalWindows, usableFrames);
                if (!weakSupportWindows.isEmpty()
                        && canRemoveWeakSupportWindows(rawOnsetAnchorsMs,
                        weakSupportWindows,
                        signalWindowStartMs,
                        wordCount)) {
                    signalWindows = removeWindows(signalWindows, weakSupportWindows);
                    rawOnsetAnchorsMs = removeOnsetsInsideWindows(rawOnsetAnchorsMs, weakSupportWindows);
                }
                rawOnsetAnchorsMs = trimTrailingNextPhraseOnsets(rawOnsetAnchorsMs, wordCount, nextAnchorMs);
                rawOnsetAnchorsMs = snapOnsetsToFollowingSignalStarts(rawOnsetAnchorsMs, signalWindows);
                rawOnsetAnchorsMs = collapseDuplicateOnsetsPerSignalWindow(rawOnsetAnchorsMs,
                        signalWindows,
                        wordCount);
                rawOnsetAnchorsMs = leadInternalOnsetsByOneBeat(rawOnsetAnchorsMs,
                        signalWindows,
                        wordCount,
                        bpm);
                onsetAnchorsMs = rawOnsetAnchorsMs;
                List<SignalWindow> occupancyWindows = detectBeatOccupancyWindows(usableFrames,
                        signalWindowStartMs,
                        signalWindowEndMs,
                        bpm);
                boolean occupancyContainsNextPhraseCluster = hasUnsafeTrailingNextPhraseCluster(occupancyWindows,
                        wordCount,
                        nextAnchorMs);
                occupancyWindows = trimTrailingNextPhraseWindows(occupancyWindows, wordCount, nextAnchorMs);
                if (!weakSupportWindows.isEmpty()) {
                    occupancyWindows = removeWindowsOverlapping(occupancyWindows, weakSupportWindows);
                }
                boolean occupancyChosen = !occupancyContainsNextPhraseCluster && shouldUseOccupancyWindows(wordCount,
                        occupancyWindows,
                        signalWindows,
                        onsetAnchorsMs.size());
                logOccupancyCandidate(index,
                        phrase.getText(),
                        wordCount,
                        occupancyWindows,
                        signalWindows,
                        rawOnsetAnchorsMs,
                        describeOccupancyDecision(wordCount, occupancyWindows, signalWindows, occupancyChosen));
                if (occupancyChosen) {
                    signalWindows = occupancyWindows;
                    onsetAnchorsMs = occupancyWindows.stream().map(SignalWindow::startMs).toList();
                }
            }
            phraseTimings.add(new PhraseTiming(index,
                    transcriptStartMs,
                    adjustedStartMs,
                    adjustedEndMs,
                    detectedStartMs,
                    nextAnchorMs,
                    detectedEndMs,
                    onsetAnchorsMs,
                    rawOnsetAnchorsMs,
                    signalWindows,
                    phrase.getText(),
                    decision));
        }

        return new TimingRefinementAnalysis(accepted,
                currentGapMs,
                firstTranscriptStartMs,
                firstVocalOnsetMs,
                gapDeltaMs,
                transcriptToAudioOffsetMs,
                proposedGapMs,
                rejectionReason,
                phraseTimings);
    }

    private PitchDetector.PitchData findInitialVocalOnset(List<PitchDetector.PitchData> significantFrames,
                                                          List<PitchDetector.PitchData> usableFrames,
                                                          int currentGapMs) {
        // GAP unknown (0): the audio may carry dialog or instrumental before the song (e.g. ripped
        // from a music video). Trusting the first significant frame would anchor on that noise, so
        // instead find the first real vocal entry: a frame that rises from near-silence into strong,
        // sustained energy.
        if (currentGapMs <= 0) {
            PitchDetector.PitchData vocalEntry = findFirstVocalEntry(significantFrames, usableFrames);
            if (vocalEntry != null) {
                return vocalEntry;
            }
            return significantFrames.get(0);
        }
        // GAP set: trust the hint and look within a window left and right of it, so the user can
        // react when the offset is off.
        List<PitchDetector.PitchData> expectedWindowFrames = collectFramesInRange(significantFrames,
                currentGapMs - MAX_INITIAL_OFFSET_MS,
                currentGapMs + MAX_INITIAL_OFFSET_MS);
        if (!expectedWindowFrames.isEmpty()) {
            return expectedWindowFrames.get(0);
        }
        return significantFrames.get(0);
    }

    /**
     * Finds the first significant frame that marks a genuine vocal entry: it is strong (energy at
     * least {@link #VOCAL_ENTRY_STRONG_RATIO} of peak) and the window ending {@link
     * #VOCAL_ENTRY_LEAD_MS} ms before it (spanning {@link #VOCAL_ENTRY_QUIET_LOOKBACK_MS} ms) is
     * quiet (under {@link #VOCAL_ENTRY_QUIET_RATIO} of peak). The lead gap keeps the note's own
     * attack ramp from being read as pre-song signal. The returned onset is walked back to the start
     * of the attack. Returns {@code null} when no such entry exists (e.g. continuous signal with no
     * leading silence), letting the caller fall back to the first significant frame.
     */
    private PitchDetector.PitchData findFirstVocalEntry(List<PitchDetector.PitchData> significantFrames,
                                                        List<PitchDetector.PitchData> usableFrames) {
        if (significantFrames == null || significantFrames.isEmpty()) {
            return null;
        }
        double peakEnergy = 0d;
        for (PitchDetector.PitchData frame : usableFrames) {
            if (frame != null && Double.isFinite(frame.energy())) {
                peakEnergy = Math.max(peakEnergy, frame.energy());
            }
        }
        if (peakEnergy <= 0d) {
            return null;
        }
        double quietThreshold = peakEnergy * VOCAL_ENTRY_QUIET_RATIO;
        double strongThreshold = peakEnergy * VOCAL_ENTRY_STRONG_RATIO;
        for (PitchDetector.PitchData candidate : significantFrames) {
            if (candidate == null || !Double.isFinite(candidate.energy())
                    || candidate.energy() < strongThreshold) {
                continue;
            }
            int candidateMs = frameStartMs(candidate);
            if (hasQuietLeadIn(usableFrames, candidateMs, quietThreshold)) {
                return walkBackToAttackStart(usableFrames, candidateMs, quietThreshold);
            }
        }
        return null;
    }

    private boolean hasQuietLeadIn(List<PitchDetector.PitchData> usableFrames,
                                   int candidateMs,
                                   double quietThreshold) {
        int windowEndMs = candidateMs - VOCAL_ENTRY_LEAD_MS;
        int windowStartMs = windowEndMs - VOCAL_ENTRY_QUIET_LOOKBACK_MS;
        for (PitchDetector.PitchData frame : usableFrames) {
            int frameMs = frameStartMs(frame);
            if (frameMs < windowStartMs) {
                continue;
            }
            if (frameMs >= windowEndMs) {
                break;
            }
            if (Double.isFinite(frame.energy()) && frame.energy() > quietThreshold) {
                return false;
            }
        }
        return true;
    }

    /**
     * Given a strong frame whose lead-in is quiet, returns the frame at the start of its attack: the
     * earliest contiguous frame before {@code candidateMs} that is still above the quiet threshold.
     * That is the true note onset, rather than the mid-attack frame that first crossed "strong".
     */
    private PitchDetector.PitchData walkBackToAttackStart(List<PitchDetector.PitchData> usableFrames,
                                                          int candidateMs,
                                                          double quietThreshold) {
        PitchDetector.PitchData attackStart = null;
        for (PitchDetector.PitchData frame : usableFrames) {
            int frameMs = frameStartMs(frame);
            if (frameMs > candidateMs) {
                break;
            }
            if (Double.isFinite(frame.energy()) && frame.energy() > quietThreshold) {
                if (attackStart == null) {
                    attackStart = frame;
                }
            } else {
                attackStart = null;
            }
        }
        return attackStart != null ? attackStart : nearestFrameAt(usableFrames, candidateMs);
    }

    private PitchDetector.PitchData nearestFrameAt(List<PitchDetector.PitchData> frames, int targetMs) {
        PitchDetector.PitchData best = null;
        int bestDist = Integer.MAX_VALUE;
        for (PitchDetector.PitchData frame : frames) {
            int dist = Math.abs(frameStartMs(frame) - targetMs);
            if (dist < bestDist) {
                bestDist = dist;
                best = frame;
            }
        }
        return best;
    }

    private void logOccupancyCandidate(int phraseIndex,
                                       String text,
                                       int wordCount,
                                       List<SignalWindow> occupancyWindows,
                                       List<SignalWindow> signalWindows,
                                       List<Integer> onsetAnchorsMs,
                                       String decision) {
        LOGGER.info("[TranscriptAlignDebug] phrase=" + phraseIndex
                + " occupancyCandidate text=\"" + text + "\""
                + " wordCount=" + wordCount
                + " occupancyWindows=" + occupancyWindows
                + " signalWindows=" + signalWindows
                + " rawOnsets=" + onsetAnchorsMs
                + " decision=" + decision);
    }

    private boolean shouldUseOccupancyWindows(int wordCount,
                                              List<SignalWindow> occupancyWindows,
                                              List<SignalWindow> signalWindows,
                                              int onsetCount) {
        if (occupancyWindows == null || occupancyWindows.isEmpty() || wordCount <= 0) {
            return false;
        }
        if (occupancyWindows.size() == wordCount) {
            return true;
        }
        if (occupancyWindows.size() < wordCount && onsetCount >= wordCount) {
            return false;
        }
        int minimumUndercoveredWindowCount = Math.max(2, (wordCount + 1) / 2);
        if (occupancyWindows.size() >= 2
                && occupancyWindows.size() < wordCount
                && occupancyWindows.size() >= minimumUndercoveredWindowCount
                && ((signalWindows != null && signalWindows.size() == 1)
                || occupancyWindows.size() < onsetCount)) {
            return true;
        }
        if (signalWindows != null
                && occupancyWindows.size() >= 2
                && occupancyWindows.size() < wordCount
                && occupancyWindows.size() < signalWindows.size()
                && occupancyWindows.size() >= Math.max(2, (wordCount + 1) / 2)) {
            return true;
        }
        return false;
    }

    private List<SignalWindow> trimTrailingNextPhraseWindows(List<SignalWindow> windows,
                                                             int wordCount,
                                                             Integer nextAnchorMs) {
        int clusterStart = findTrailingNextPhraseWindowClusterStart(windows, nextAnchorMs);
        if (clusterStart < 0 || clusterStart < wordCount) {
            return windows;
        }
        return new ArrayList<>(windows.subList(0, clusterStart));
    }

    private boolean hasUnsafeTrailingNextPhraseCluster(List<SignalWindow> windows,
                                                       int wordCount,
                                                       Integer nextAnchorMs) {
        int clusterStart = findTrailingNextPhraseWindowClusterStart(windows, nextAnchorMs);
        return clusterStart >= 0 && clusterStart < wordCount;
    }

    private int findTrailingNextPhraseWindowClusterStart(List<SignalWindow> windows,
                                                         Integer nextAnchorMs) {
        if (windows == null || windows.size() < 2 || nextAnchorMs == null) {
            return -1;
        }
        SignalWindow trailing = windows.get(windows.size() - 1);
        if (!isNearNextAnchor(trailing.startMs(), nextAnchorMs)) {
            return -1;
        }
        int clusterStart = windows.size() - 1;
        while (clusterStart > 0) {
            SignalWindow previous = windows.get(clusterStart - 1);
            SignalWindow current = windows.get(clusterStart);
            if (current.startMs() - previous.endMs() > CONTINUOUS_SIGNAL_GAP_MS) {
                break;
            }
            clusterStart--;
        }
        if (clusterStart <= 0) {
            return -1;
        }
        SignalWindow previous = windows.get(clusterStart - 1);
        SignalWindow firstClusterWindow = windows.get(clusterStart);
        return isLikelyNextPhraseCluster(previous.endMs(), firstClusterWindow.startMs(), nextAnchorMs)
                ? clusterStart
                : -1;
    }

    private List<SignalWindow> findWeakSupportWindows(List<SignalWindow> windows,
                                                      List<PitchDetector.PitchData> frames) {
        if (windows == null || windows.size() < 3 || frames == null || frames.isEmpty()) {
            return List.of();
        }
        List<Double> averageEnergies = new ArrayList<>(windows.size());
        for (SignalWindow window : windows) {
            averageEnergies.add(averageEnergyInWindow(frames, window));
        }
        List<SignalWindow> weakWindows = new ArrayList<>();
        for (int index = 1; index < windows.size() - 1; index++) {
            SignalWindow window = windows.get(index);
            int lengthMs = window.endMs() - window.startMs();
            if (lengthMs <= 0 || lengthMs > 250) {
                continue;
            }
            double currentEnergy = averageEnergies.get(index);
            double neighborEnergy = Math.max(averageEnergies.get(index - 1), averageEnergies.get(index + 1));
            if (neighborEnergy <= 0d) {
                continue;
            }
            if (currentEnergy <= neighborEnergy * 0.40d) {
                weakWindows.add(window);
            }
        }
        return weakWindows;
    }

    private boolean canRemoveWeakSupportWindows(List<Integer> onsetAnchorsMs,
                                                List<SignalWindow> weakSupportWindows,
                                                int refinedStartMs,
                                                int wordCount) {
        if (weakSupportWindows == null || weakSupportWindows.isEmpty()) {
            return false;
        }
        List<Integer> filteredOnsets = removeOnsetsInsideWindows(onsetAnchorsMs, weakSupportWindows);
        return estimatedNormalizedOnsetCount(filteredOnsets, refinedStartMs) >= wordCount;
    }

    private int estimatedNormalizedOnsetCount(List<Integer> onsetAnchorsMs,
                                              int refinedStartMs) {
        if (onsetAnchorsMs == null || onsetAnchorsMs.isEmpty()) {
            return 0;
        }
        int count = onsetAnchorsMs.size();
        Integer firstAnchor = onsetAnchorsMs.get(0);
        if (firstAnchor != null && firstAnchor > refinedStartMs) {
            count++;
        }
        return count;
    }

    private List<Integer> snapOnsetsToFollowingSignalStarts(List<Integer> onsetAnchorsMs,
                                                            List<SignalWindow> signalWindows) {
        if (onsetAnchorsMs == null || onsetAnchorsMs.isEmpty()
                || signalWindows == null || signalWindows.isEmpty()) {
            return onsetAnchorsMs;
        }
        List<Integer> snapped = new ArrayList<>(onsetAnchorsMs.size());
        for (Integer onsetMs : onsetAnchorsMs) {
            if (onsetMs == null) {
                continue;
            }
            snapped.add(findFollowingSignalStart(onsetMs, signalWindows));
        }
        return snapped;
    }

    private int findFollowingSignalStart(int onsetMs,
                                         List<SignalWindow> signalWindows) {
        int bestStartMs = onsetMs;
        int bestDeltaMs = Integer.MAX_VALUE;
        for (SignalWindow window : signalWindows) {
            int deltaMs = window.startMs() - onsetMs;
            if (deltaMs <= 0 || deltaMs > ONSET_SIGNAL_START_SNAP_MS) {
                continue;
            }
            if (deltaMs < bestDeltaMs) {
                bestDeltaMs = deltaMs;
                bestStartMs = window.startMs();
            }
        }
        return bestStartMs;
    }

    private List<Integer> collapseDuplicateOnsetsPerSignalWindow(List<Integer> onsetAnchorsMs,
                                                                 List<SignalWindow> signalWindows,
                                                                 int wordCount) {
        if (onsetAnchorsMs == null || onsetAnchorsMs.isEmpty()
                || signalWindows == null || signalWindows.size() < wordCount) {
            return onsetAnchorsMs;
        }
        List<Integer> collapsed = new ArrayList<>(onsetAnchorsMs.size());
        SignalWindow previousWindow = null;
        for (Integer onsetMs : onsetAnchorsMs) {
            if (onsetMs == null) {
                continue;
            }
            SignalWindow window = findContainingSignalWindow(onsetMs, signalWindows);
            if (window != null && window.equals(previousWindow)) {
                continue;
            }
            collapsed.add(onsetMs);
            previousWindow = window;
        }
        return collapsed;
    }

    private SignalWindow findContainingSignalWindow(int onsetMs,
                                                    List<SignalWindow> signalWindows) {
        for (SignalWindow window : signalWindows) {
            if (onsetMs == window.startMs()) {
                return window;
            }
        }
        for (SignalWindow window : signalWindows) {
            if (onsetMs > window.startMs() && onsetMs <= window.endMs()) {
                return window;
            }
        }
        return null;
    }

    private List<Integer> leadInternalOnsetsByOneBeat(List<Integer> onsetAnchorsMs,
                                                      List<SignalWindow> signalWindows,
                                                      int wordCount,
                                                      double bpm) {
        if (onsetAnchorsMs == null || onsetAnchorsMs.isEmpty()
                || signalWindows == null || signalWindows.size() < wordCount || bpm <= 0d) {
            return onsetAnchorsMs;
        }
        int internalBeatMs = Math.max(1, (int) Math.round(60000d / (4d * bpm)));
        List<Integer> adjusted = new ArrayList<>(onsetAnchorsMs.size());
        Integer previous = null;
        for (Integer onsetMs : onsetAnchorsMs) {
            if (onsetMs == null) {
                continue;
            }
            int adjustedOnsetMs = onsetMs;
            SignalWindow window = findContainingSignalWindow(onsetMs, signalWindows);
            if (window != null && onsetMs - window.startMs() >= internalBeatMs * 3) {
                int candidateMs = Math.max(window.startMs(), onsetMs - internalBeatMs);
                if (previous == null || candidateMs - previous >= MIN_ONSET_GAP_MS) {
                    adjustedOnsetMs = candidateMs;
                }
            }
            adjusted.add(adjustedOnsetMs);
            previous = adjustedOnsetMs;
        }
        return adjusted;
    }

    private double averageEnergyInWindow(List<PitchDetector.PitchData> frames,
                                         SignalWindow window) {
        if (frames == null || frames.isEmpty() || window == null) {
            return 0d;
        }
        double sum = 0d;
        int count = 0;
        for (PitchDetector.PitchData frame : frames) {
            int frameMs = frameStartMs(frame);
            if (frameMs < window.startMs()) {
                continue;
            }
            if (frameMs >= window.endMs()) {
                break;
            }
            if (Double.isFinite(frame.energy())) {
                sum += frame.energy();
                count++;
            }
        }
        return count == 0 ? 0d : sum / count;
    }

    private List<SignalWindow> removeWindows(List<SignalWindow> windows,
                                             List<SignalWindow> windowsToRemove) {
        if (windows == null || windows.isEmpty() || windowsToRemove == null || windowsToRemove.isEmpty()) {
            return windows;
        }
        List<SignalWindow> filtered = new ArrayList<>();
        for (SignalWindow window : windows) {
            if (!windowsToRemove.contains(window)) {
                filtered.add(window);
            }
        }
        return filtered;
    }

    private List<SignalWindow> removeWindowsOverlapping(List<SignalWindow> windows,
                                                        List<SignalWindow> windowsToRemove) {
        if (windows == null || windows.isEmpty() || windowsToRemove == null || windowsToRemove.isEmpty()) {
            return windows;
        }
        List<SignalWindow> filtered = new ArrayList<>();
        for (SignalWindow window : windows) {
            boolean overlapsRemovedWindow = false;
            for (SignalWindow removedWindow : windowsToRemove) {
                if (windowsOverlap(window, removedWindow)) {
                    overlapsRemovedWindow = true;
                    break;
                }
            }
            if (!overlapsRemovedWindow) {
                filtered.add(window);
            }
        }
        return filtered;
    }

    private boolean windowsOverlap(SignalWindow left,
                                   SignalWindow right) {
        return left != null
                && right != null
                && left.startMs() < right.endMs()
                && right.startMs() < left.endMs();
    }

    private List<Integer> removeOnsetsInsideWindows(List<Integer> onsetAnchorsMs,
                                                    List<SignalWindow> windowsToRemove) {
        if (onsetAnchorsMs == null || onsetAnchorsMs.isEmpty()
                || windowsToRemove == null || windowsToRemove.isEmpty()) {
            return onsetAnchorsMs;
        }
        List<Integer> filtered = new ArrayList<>();
        for (Integer onsetMs : onsetAnchorsMs) {
            if (onsetMs == null || isInsideAnyWindow(onsetMs, windowsToRemove)) {
                continue;
            }
            filtered.add(onsetMs);
        }
        return filtered;
    }

    private boolean isInsideAnyWindow(int onsetMs,
                                      List<SignalWindow> windows) {
        for (SignalWindow window : windows) {
            if (onsetMs >= window.startMs() && onsetMs <= window.endMs()) {
                return true;
            }
        }
        return false;
    }

    private List<Integer> trimTrailingNextPhraseOnsets(List<Integer> onsetAnchorsMs,
                                                       int wordCount,
                                                       Integer nextAnchorMs) {
        int clusterStart = findTrailingNextPhraseOnsetClusterStart(onsetAnchorsMs, nextAnchorMs);
        if (clusterStart < 0 || clusterStart < wordCount) {
            return onsetAnchorsMs;
        }
        return new ArrayList<>(onsetAnchorsMs.subList(0, clusterStart));
    }

    private int findTrailingNextPhraseOnsetClusterStart(List<Integer> onsetAnchorsMs,
                                                        Integer nextAnchorMs) {
        if (onsetAnchorsMs == null || onsetAnchorsMs.size() < 2 || nextAnchorMs == null) {
            return -1;
        }
        int trailingStartMs = onsetAnchorsMs.get(onsetAnchorsMs.size() - 1);
        if (!isNearNextAnchor(trailingStartMs, nextAnchorMs)) {
            return -1;
        }
        int clusterStart = onsetAnchorsMs.size() - 1;
        while (clusterStart > 0) {
            int previous = onsetAnchorsMs.get(clusterStart - 1);
            int current = onsetAnchorsMs.get(clusterStart);
            if (current - previous > CONTINUOUS_SIGNAL_GAP_MS) {
                break;
            }
            clusterStart--;
        }
        if (clusterStart <= 0) {
            return -1;
        }
        int previous = onsetAnchorsMs.get(clusterStart - 1);
        int firstClusterOnset = onsetAnchorsMs.get(clusterStart);
        return isLikelyNextPhraseCluster(previous, firstClusterOnset, nextAnchorMs) ? clusterStart : -1;
    }

    private boolean isLikelyNextPhraseCluster(int previousEndMs,
                                              int trailingStartMs,
                                              int nextAnchorMs) {
        return trailingStartMs > previousEndMs
                && trailingStartMs - previousEndMs > CONTINUOUS_SIGNAL_GAP_MS
                && nextAnchorMs - trailingStartMs >= 0
                && nextAnchorMs - trailingStartMs <= NEXT_ANCHOR_PRE_ROLL_MS;
    }

    private boolean isNearNextAnchor(int startMs,
                                     int nextAnchorMs) {
        return nextAnchorMs - startMs >= 0
                && nextAnchorMs - startMs <= NEXT_ANCHOR_PRE_ROLL_MS;
    }

    private String describeOccupancyDecision(int wordCount,
                                             List<SignalWindow> occupancyWindows,
                                             List<SignalWindow> signalWindows,
                                             boolean occupancyChosen) {
        int occupancyCount = occupancyWindows == null ? 0 : occupancyWindows.size();
        int signalCount = signalWindows == null ? 0 : signalWindows.size();
        if (occupancyChosen) {
            if (occupancyCount == wordCount) {
                return "chosen: exact word-count match";
            }
            return "chosen: coarse occupancy windows " + occupancyCount
                    + " beat(s) replaced noisier signal windows " + signalCount;
        }
        return "rejected: occupancy window count " + occupancyCount
                + ", signal window count " + signalCount
                + ", word count " + wordCount;
    }

    public boolean shouldRefineForAlignment(OpenAiTranscriptionResult transcript) {
        if (transcript == null) {
            return false;
        }
        String timingSource = StringUtils.defaultIfBlank(transcript.getTimingSourceTag(), transcript.getSourceTag());
        if ("#SUBTITLES".equalsIgnoreCase(timingSource)
                || "#LRCLIB".equalsIgnoreCase(timingSource)
                || "#LRC".equalsIgnoreCase(timingSource)) {
            return true;
        }
        return transcript.getCacheFile() != null
                && StringUtils.endsWithIgnoreCase(transcript.getCacheFile().getName(), ".openai.json");
    }

    public OpenAiTranscriptionResult refineForAlignment(OpenAiTranscriptionResult transcript,
                                                        List<PitchDetector.PitchData> vocalFrames,
                                                        int currentGapMs,
                                                        String refinedTimingSourceTag) {
        return refineForAlignment(transcript, vocalFrames, currentGapMs, refinedTimingSourceTag, 0d);
    }

    public OpenAiTranscriptionResult refineForAlignment(OpenAiTranscriptionResult transcript,
                                                        List<PitchDetector.PitchData> vocalFrames,
                                                        int currentGapMs,
                                                        String refinedTimingSourceTag,
                                                        double bpm) {
        if (!shouldRefineForAlignment(transcript)) {
            return transcript;
        }
        TimingRefinementAnalysis analysis = analyze(transcript, vocalFrames, currentGapMs, bpm);
        if (transcript == null || !analysis.accepted() || analysis.phrases().isEmpty()) {
            return transcript;
        }

        List<OpenAiTranscriptSegment> originalSegments = transcript.getSegments();
        if (originalSegments == null || originalSegments.isEmpty()) {
            return rebuildTranscriptWithoutSegments(transcript, analysis, refinedTimingSourceTag);
        }

        List<OpenAiTranscriptSegment> refinedSegments = new ArrayList<>(originalSegments.size());
        List<OpenAiTranscriptWord> refinedWords = new ArrayList<>();
        int phraseCursor = 0;
        int[] topLevelWordCursor = new int[]{0};
        for (OpenAiTranscriptSegment segment : originalSegments) {
            if (!isUsablePhrase(segment) || phraseCursor >= analysis.phrases().size()) {
                refinedSegments.add(segment);
                continue;
            }
            PhraseTiming timing = analysis.phrases().get(phraseCursor++);
            List<OpenAiTranscriptWord> phraseWords = resolvePhraseWords(segment,
                    transcript.getWords(),
                    timing.transcriptStartMs(),
                    timing.nextAnchorMs(),
                    topLevelWordCursor);
            List<OpenAiTranscriptWord> updatedWords = refineWordsForPhrase(phraseWords, timing, segment);
            refinedWords.addAll(updatedWords);
            int refinedStartMs = phraseStartMs(timing, segment);
            int refinedEndMs = phraseEndMs(timing, segment, refinedStartMs);
            refinedSegments.add(new OpenAiTranscriptSegment(refinedStartMs, refinedEndMs, segment.getText(), updatedWords));
        }

        if (refinedWords.isEmpty()) {
            refinedWords = rebuildTopLevelWordsFromSegments(refinedSegments);
        }

        return new OpenAiTranscriptionResult(transcript.getSourceAudioFile(),
                transcript.getUploadAudioFile(),
                transcript.getSourceTag(),
                transcript.getTranscriptText(),
                refinedWords,
                refinedSegments,
                transcript.getLyricTokens(),
                transcript.isFromCache(),
                transcript.getCacheFile(),
                transcript.getTextSourceTag(),
                StringUtils.defaultIfBlank(refinedTimingSourceTag, transcript.getTimingSourceTag()));
    }

    private List<OpenAiTranscriptSegment> collectPhrases(OpenAiTranscriptionResult transcript) {
        if (transcript == null) {
            return List.of();
        }
        List<OpenAiTranscriptSegment> phrases = new ArrayList<>();
        if (transcript.getSegments() != null) {
            for (OpenAiTranscriptSegment segment : transcript.getSegments()) {
                if (isUsablePhrase(segment)) {
                    phrases.add(segment);
                }
            }
        }
        if (!phrases.isEmpty()) {
            phrases.sort(Comparator.comparingInt(this::phraseStartMs));
            return phrases;
        }
        if (containsUsableWords(transcript.getWords())) {
            phrases.add(new OpenAiTranscriptSegment(findFirstWordStartMs(transcript.getWords()),
                    findLastWordEndMs(transcript.getWords()),
                    transcript.getTranscriptText(),
                    transcript.getWords()));
        }
        return phrases;
    }

    private List<Integer> collectTimingAnchors(OpenAiTranscriptionResult transcript) {
        if (transcript == null || transcript.getSegments() == null) {
            return List.of();
        }
        List<Integer> anchors = new ArrayList<>();
        for (OpenAiTranscriptSegment segment : transcript.getSegments()) {
            if (segment != null && segment.getStartMs() >= 0) {
                anchors.add(phraseStartMs(segment));
            }
        }
        anchors.sort(Integer::compareTo);
        return anchors;
    }

    private Integer findNextTimingAnchor(List<Integer> anchorsMs, int currentStartMs) {
        if (anchorsMs == null) {
            return null;
        }
        for (Integer anchorMs : anchorsMs) {
            if (anchorMs != null && anchorMs > currentStartMs) {
                return anchorMs;
            }
        }
        return null;
    }

    private Integer detectPhraseStartNearTranscriptAnchor(List<PitchDetector.PitchData> usableFrames,
                                                         int transcriptStartMs,
                                                         int adjustedStartMs,
                                                         int adjustedEndMs) {
        int searchStartMs = Math.max(0, Math.min(transcriptStartMs, adjustedStartMs) - 1_000);
        int searchEndMs = Math.max(adjustedEndMs, Math.max(transcriptStartMs, adjustedStartMs) + 1_000);
        List<PitchDetector.PitchData> searchFrames = collectFramesInRange(usableFrames, searchStartMs, searchEndMs);
        if (searchFrames.isEmpty()) {
            return null;
        }
        List<PitchDetector.PitchData> localSignificantFrames = collectLocallySignificantFrames(searchFrames,
                transcriptStartMs,
                LOCAL_ANCHOR_WINDOW_MS);
        if (localSignificantFrames.isEmpty()) {
            return null;
        }
        int nextIndex = findFirstFrameIndexAfter(localSignificantFrames, transcriptStartMs);
        if (nextIndex >= 0) {
            int nextMs = frameStartMs(localSignificantFrames.get(nextIndex));
            if (nextMs - transcriptStartMs <= 1_000) {
                int clusterStartMs = clusterStartMs(localSignificantFrames, nextIndex);
                if (isAcceptableLocalClusterStart(clusterStartMs, transcriptStartMs)) {
                    return clusterStartMs;
                }
                Integer followingClusterStartMs = nextClusterStartMs(localSignificantFrames, nextIndex);
                if (followingClusterStartMs != null && followingClusterStartMs - transcriptStartMs <= 1_000) {
                    return followingClusterStartMs;
                }
                followingClusterStartMs = nextOnsetStartAfterRejectedCluster(searchFrames, transcriptStartMs);
                if (followingClusterStartMs != null) {
                    return followingClusterStartMs;
                }
                followingClusterStartMs = nextSignificantClusterStartAfterRejectedCluster(searchFrames,
                        localSignificantFrames,
                        nextIndex,
                        transcriptStartMs);
                if (followingClusterStartMs != null) {
                    return followingClusterStartMs;
                }
            }
        }
        int closestIndex = findClosestFrameIndex(localSignificantFrames, transcriptStartMs, LOCAL_ANCHOR_WINDOW_MS);
        if (closestIndex >= 0) {
            int clusterStartMs = clusterStartMs(localSignificantFrames, closestIndex);
            if (isAcceptableLocalClusterStart(clusterStartMs, transcriptStartMs)) {
                return clusterStartMs;
            }
            Integer followingClusterStartMs = nextClusterStartMs(localSignificantFrames, closestIndex);
            if (followingClusterStartMs != null && followingClusterStartMs - transcriptStartMs <= 1_000) {
                return followingClusterStartMs;
            }
            followingClusterStartMs = nextOnsetStartAfterRejectedCluster(searchFrames, transcriptStartMs);
            if (followingClusterStartMs != null) {
                return followingClusterStartMs;
            }
            followingClusterStartMs = nextSignificantClusterStartAfterRejectedCluster(searchFrames,
                    localSignificantFrames,
                    closestIndex,
                    transcriptStartMs);
            if (followingClusterStartMs != null) {
                return followingClusterStartMs;
            }
        }
        return null;
    }

    private boolean isAcceptableLocalClusterStart(int clusterStartMs, int transcriptStartMs) {
        return clusterStartMs >= transcriptStartMs
                || transcriptStartMs - clusterStartMs <= LOCAL_ANCHOR_WINDOW_MS;
    }

    private Integer nextOnsetStartAfterRejectedCluster(List<PitchDetector.PitchData> searchFrames,
                                                       int transcriptStartMs) {
        List<Integer> onsets = detectOnsetAnchors(searchFrames, transcriptStartMs, transcriptStartMs + 1_000);
        double postAnchorMaxEnergy = maxEnergyInRange(searchFrames, transcriptStartMs, transcriptStartMs + 1_000);
        double minimumOnsetEnergy = postAnchorMaxEnergy * REJECTED_CLUSTER_ONSET_THRESHOLD_RATIO;
        for (Integer onsetMs : onsets) {
            if (onsetMs != null
                    && onsetMs - transcriptStartMs >= MIN_ONSET_GAP_MS) {
                Integer supportedFrameMs = firstFrameAtOrAboveEnergy(searchFrames,
                        onsetMs,
                        onsetMs + MIN_ONSET_GAP_MS,
                        minimumOnsetEnergy);
                if (supportedFrameMs != null) {
                    return supportedFrameMs;
                }
            }
        }
        return null;
    }

    private Integer nextSignificantClusterStartAfterRejectedCluster(List<PitchDetector.PitchData> searchFrames,
                                                                   List<PitchDetector.PitchData> clusterFrames,
                                                                   int clusterIndex,
                                                                   int transcriptStartMs) {
        if (searchFrames == null || searchFrames.isEmpty() || clusterFrames == null || clusterIndex < 0) {
            return null;
        }
        int clusterEndMs = clusterEndMs(clusterFrames, clusterIndex);
        int searchStartMs = Math.max(transcriptStartMs, clusterEndMs + MIN_ONSET_GAP_MS);
        int searchEndMs = transcriptStartMs + 1_000;
        List<PitchDetector.PitchData> followingFrames = collectFramesInRange(searchFrames, searchStartMs, searchEndMs);
        List<PitchDetector.PitchData> significantFollowingFrames = collectSignificantFrames(followingFrames);
        return significantFollowingFrames.isEmpty() ? null : frameStartMs(significantFollowingFrames.get(0));
    }

    private Integer detectOpeningPhraseStart(List<PitchDetector.PitchData> usableFrames,
                                             int transcriptStartMs,
                                             int adjustedEndMs,
                                             double bpm) {
        int searchStartMs = Math.max(0, transcriptStartMs - 1_000);
        int searchEndMs = Math.max(adjustedEndMs, transcriptStartMs + 1_000);
        List<PitchDetector.PitchData> searchFrames = collectFramesInRange(usableFrames, searchStartMs, searchEndMs);
        if (searchFrames.isEmpty()) {
            return null;
        }
        List<PitchDetector.PitchData> localSignificantFrames = collectLocallySignificantFrames(searchFrames,
                transcriptStartMs,
                LOCAL_ANCHOR_WINDOW_MS);
        if (localSignificantFrames.isEmpty()) {
            return null;
        }

        int candidateIndex = findClosestFrameIndex(localSignificantFrames, transcriptStartMs, LOCAL_ANCHOR_WINDOW_MS);
        if (candidateIndex >= 0) {
            return clusterStartMs(localSignificantFrames, candidateIndex);
        }

        int previousIndex = findLastFrameIndexBefore(localSignificantFrames, transcriptStartMs);
        if (previousIndex >= 0 && transcriptStartMs - frameStartMs(localSignificantFrames.get(previousIndex)) <= 1_000) {
            return clusterStartMs(localSignificantFrames, previousIndex);
        }

        int nextIndex = findFirstFrameIndexAfter(localSignificantFrames, transcriptStartMs);
        if (nextIndex >= 0) {
            return clusterStartMs(localSignificantFrames, nextIndex);
        }
        return null;
    }

    private Integer findPhraseStartAfterAnchor(List<PitchDetector.PitchData> frames,
                                               int adjustedStartMs,
                                               int adjustedEndMs) {
        int latestRelevantStartMs = adjustedEndMs > adjustedStartMs ? adjustedEndMs : Integer.MAX_VALUE;
        for (PitchDetector.PitchData frame : frames) {
            int frameMs = frameStartMs(frame);
            if (frameMs < adjustedStartMs) {
                continue;
            }
            if (frameMs > latestRelevantStartMs) {
                break;
            }
            return frameMs;
        }
        return null;
    }

    private Integer findPhraseStartBetweenBoundaries(List<PitchDetector.PitchData> frames,
                                                     int previousPhraseEndMs,
                                                     int currentStartAnchorMs) {
        if (frames == null || frames.isEmpty()) {
            return null;
        }
        int earliestRelevantStartMs = Math.max(0, previousPhraseEndMs + 1);
        if (currentStartAnchorMs <= earliestRelevantStartMs) {
            return null;
        }
        for (int index = 0; index < frames.size(); index++) {
            PitchDetector.PitchData frame = frames.get(index);
            int frameMs = frameStartMs(frame);
            if (frameMs < earliestRelevantStartMs) {
                continue;
            }
            if (frameMs >= currentStartAnchorMs) {
                break;
            }
            return Math.max(earliestRelevantStartMs, clusterStartMs(frames, index));
        }
        return null;
    }

    private List<PitchDetector.PitchData> collectUsableFrames(List<PitchDetector.PitchData> frames) {
        if (frames == null || frames.isEmpty()) {
            return List.of();
        }
        List<PitchDetector.PitchData> usable = new ArrayList<>();
        for (PitchDetector.PitchData frame : frames) {
            if (frame != null && Double.isFinite(frame.energy()) && frame.energy() > 0d) {
                usable.add(frame);
            }
        }
        usable.sort(Comparator.comparing(PitchDetector.PitchData::time));
        return usable;
    }

    private List<PitchDetector.PitchData> filterToDominantPitchBand(List<PitchDetector.PitchData> frames) {
        return dominantPitchBandFilter(frames);
    }

    /**
     * Drops frames whose detected pitch sits outside the two octaves that hold the majority of the
     * voice. aubio mis-detects note onsets (consonant transients, breath, sibilants) an octave or
     * more above the true fundamental; those out-of-band frames pull both onset detection and the
     * assigned note pitch toward noise. The keep-band is the best contiguous {@code
     * PITCH_BAND_OCTAVES}-wide window of pitched frames. Unpitched (energy-only) frames are always
     * kept. If the band would retain too little of the signal (unusual register spread), filtering is
     * skipped so a legitimately wide-range phrase is never starved.
     *
     * <p>Shared by the timing path (onset/window detection) and the melody-alignment path (note
     * heights), so both reject the same octave-error garbage.</p>
     */
    public static List<PitchDetector.PitchData> dominantPitchBandFilter(List<PitchDetector.PitchData> frames) {
        if (frames == null || frames.isEmpty()) {
            return frames == null ? List.of() : frames;
        }
        java.util.Map<Integer, Integer> framesPerOctave = new java.util.HashMap<>();
        int pitchedCount = 0;
        for (PitchDetector.PitchData frame : frames) {
            if (frame == null || frame.rawFrequency() <= 0d) {
                continue;
            }
            int octaveIndex = Math.floorDiv(frame.pitch(), SEMITONES_PER_OCTAVE);
            framesPerOctave.merge(octaveIndex, 1, Integer::sum);
            pitchedCount++;
        }
        if (pitchedCount == 0 || framesPerOctave.isEmpty()) {
            return frames;
        }
        int minOctave = framesPerOctave.keySet().stream().min(Integer::compareTo).orElse(0);
        int maxOctave = framesPerOctave.keySet().stream().max(Integer::compareTo).orElse(0);
        int bestStartOctave = minOctave;
        int bestCoverage = -1;
        for (int startOctave = minOctave; startOctave <= maxOctave; startOctave++) {
            int coverage = 0;
            for (int offset = 0; offset < PITCH_BAND_OCTAVES; offset++) {
                coverage += framesPerOctave.getOrDefault(startOctave + offset, 0);
            }
            if (coverage > bestCoverage) {
                bestCoverage = coverage;
                bestStartOctave = startOctave;
            }
        }
        if (bestCoverage < pitchedCount * PITCH_BAND_MIN_COVERAGE) {
            return frames;
        }
        int lowSemitone = bestStartOctave * SEMITONES_PER_OCTAVE;
        int highSemitone = (bestStartOctave + PITCH_BAND_OCTAVES) * SEMITONES_PER_OCTAVE - 1;
        List<PitchDetector.PitchData> banded = new ArrayList<>(frames.size());
        for (PitchDetector.PitchData frame : frames) {
            if (frame == null) {
                continue;
            }
            // Keep unpitched frames (energy-only): they still carry the energy envelope used for
            // onsets/windows and were never the octave-error source.
            if (frame.rawFrequency() <= 0d
                    || (frame.pitch() >= lowSemitone && frame.pitch() <= highSemitone)) {
                banded.add(frame);
            }
        }
        LOGGER.info("[TranscriptPitchBand] keepBand=[" + lowSemitone + "," + highSemitone
                + "] kept=" + banded.size() + "/" + frames.size()
                + " pitchedCoverage=" + bestCoverage + "/" + pitchedCount);
        return banded;
    }

    private List<PitchDetector.PitchData> collectSignificantFrames(List<PitchDetector.PitchData> frames) {
        if (frames == null || frames.isEmpty()) {
            return List.of();
        }
        double maxEnergy = 0d;
        for (PitchDetector.PitchData frame : frames) {
            if (frame != null && Double.isFinite(frame.energy())) {
                maxEnergy = Math.max(maxEnergy, frame.energy());
            }
        }
        if (maxEnergy <= 0d) {
            return List.of();
        }
        double threshold = maxEnergy * ENERGY_THRESHOLD_RATIO;
        List<PitchDetector.PitchData> significant = new ArrayList<>();
        for (PitchDetector.PitchData frame : frames) {
            if (frame != null && Double.isFinite(frame.energy()) && frame.energy() >= threshold) {
                significant.add(frame);
            }
        }
        significant.sort(Comparator.comparing(PitchDetector.PitchData::time));
        return significant;
    }

    private List<PitchDetector.PitchData> collectFramesInRange(List<PitchDetector.PitchData> frames,
                                                               int startMs,
                                                               int endMs) {
        if (frames == null || frames.isEmpty() || endMs < startMs) {
            return List.of();
        }
        List<PitchDetector.PitchData> inRange = new ArrayList<>();
        for (PitchDetector.PitchData frame : frames) {
            int frameMs = frameStartMs(frame);
            if (frameMs < startMs) {
                continue;
            }
            if (frameMs > endMs) {
                break;
            }
            inRange.add(frame);
        }
        return inRange;
    }

    private Integer firstFrameAtOrAboveEnergy(List<PitchDetector.PitchData> frames,
                                              int startMs,
                                              int endMs,
                                              double minimumEnergy) {
        if (frames == null || frames.isEmpty() || endMs < startMs) {
            return null;
        }
        for (PitchDetector.PitchData frame : frames) {
            if (frame == null) {
                continue;
            }
            int frameMs = frameStartMs(frame);
            if (frameMs < startMs) {
                continue;
            }
            if (frameMs > endMs) {
                break;
            }
            if (Double.isFinite(frame.energy()) && frame.energy() >= minimumEnergy) {
                return frameMs;
            }
        }
        return null;
    }

    private double maxEnergyInRange(List<PitchDetector.PitchData> frames,
                                    int startMs,
                                    int endMs) {
        if (frames == null || frames.isEmpty() || endMs < startMs) {
            return 0d;
        }
        double maxEnergy = 0d;
        for (PitchDetector.PitchData frame : frames) {
            if (frame == null) {
                continue;
            }
            int frameMs = frameStartMs(frame);
            if (frameMs < startMs) {
                continue;
            }
            if (frameMs > endMs) {
                break;
            }
            if (frame != null && Double.isFinite(frame.energy())) {
                maxEnergy = Math.max(maxEnergy, frame.energy());
            }
        }
        return maxEnergy;
    }

    private List<PitchDetector.PitchData> collectLocallySignificantFrames(List<PitchDetector.PitchData> frames,
                                                                          int anchorMs,
                                                                          int localWindowMs) {
        if (frames == null || frames.isEmpty()) {
            return List.of();
        }
        double localMaxEnergy = 0d;
        double fallbackMaxEnergy = 0d;
        int localStartMs = Math.max(0, anchorMs - localWindowMs);
        int localEndMs = anchorMs + localWindowMs;
        for (PitchDetector.PitchData frame : frames) {
            if (frame == null || !Double.isFinite(frame.energy())) {
                continue;
            }
            int frameMs = frameStartMs(frame);
            fallbackMaxEnergy = Math.max(fallbackMaxEnergy, frame.energy());
            if (frameMs >= localStartMs && frameMs <= localEndMs) {
                localMaxEnergy = Math.max(localMaxEnergy, frame.energy());
            }
        }
        double thresholdReference = localMaxEnergy > 0d ? localMaxEnergy : fallbackMaxEnergy;
        if (thresholdReference <= 0d) {
            return List.of();
        }
        double threshold = thresholdReference * ENERGY_THRESHOLD_RATIO;
        List<PitchDetector.PitchData> significant = new ArrayList<>();
        for (PitchDetector.PitchData frame : frames) {
            if (frame != null && Double.isFinite(frame.energy()) && frame.energy() >= threshold) {
                significant.add(frame);
            }
        }
        significant.sort(Comparator.comparing(PitchDetector.PitchData::time));
        return significant;
    }

    private int findClosestFrameIndex(List<PitchDetector.PitchData> frames,
                                      int anchorMs,
                                      int maxDistanceMs) {
        int bestIndex = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int index = 0; index < frames.size(); index++) {
            int distance = Math.abs(frameStartMs(frames.get(index)) - anchorMs);
            if (distance <= maxDistanceMs && distance < bestDistance) {
                bestDistance = distance;
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private int findLastFrameIndexBefore(List<PitchDetector.PitchData> frames, int anchorMs) {
        for (int index = frames.size() - 1; index >= 0; index--) {
            if (frameStartMs(frames.get(index)) < anchorMs) {
                return index;
            }
        }
        return -1;
    }

    private int findFirstFrameIndexAfter(List<PitchDetector.PitchData> frames, int anchorMs) {
        for (int index = 0; index < frames.size(); index++) {
            if (frameStartMs(frames.get(index)) > anchorMs) {
                return index;
            }
        }
        return -1;
    }

    private int clusterStartMs(List<PitchDetector.PitchData> frames, int index) {
        int startIndex = index;
        while (startIndex > 0) {
            int currentMs = frameStartMs(frames.get(startIndex));
            int previousMs = frameStartMs(frames.get(startIndex - 1));
            if (currentMs - previousMs > CONTINUOUS_SIGNAL_GAP_MS) {
                break;
            }
            startIndex--;
        }
        return frameStartMs(frames.get(startIndex));
    }

    private int clusterEndMs(List<PitchDetector.PitchData> frames, int index) {
        int endIndex = index;
        while (endIndex + 1 < frames.size()) {
            int currentMs = frameStartMs(frames.get(endIndex));
            int nextMs = frameStartMs(frames.get(endIndex + 1));
            if (nextMs - currentMs >= MIN_ONSET_GAP_MS) {
                break;
            }
            endIndex++;
        }
        return frameStartMs(frames.get(endIndex));
    }

    private Integer nextClusterStartMs(List<PitchDetector.PitchData> frames, int index) {
        if (frames == null || index < 0) {
            return null;
        }
        for (int cursor = index + 1; cursor < frames.size(); cursor++) {
            int currentMs = frameStartMs(frames.get(cursor));
            int previousMs = frameStartMs(frames.get(cursor - 1));
            if (currentMs - previousMs > CONTINUOUS_SIGNAL_GAP_MS) {
                return currentMs;
            }
        }
        return null;
    }

    private VocalEndDetection findVocalEndBeforeNextAnchor(List<PitchDetector.PitchData> frames,
                                                           int startMs,
                                                           int nextAnchorMs) {
        List<PitchDetector.PitchData> phraseFrames = new ArrayList<>();
        for (PitchDetector.PitchData frame : frames) {
            int frameMs = frameStartMs(frame);
            if (frameMs < startMs) {
                continue;
            }
            if (frameMs >= nextAnchorMs) {
                break;
            }
            phraseFrames.add(frame);
        }
        if (phraseFrames.isEmpty()) {
            return new VocalEndDetection(null, "no significant vocal end before next anchor");
        }
        double phraseMaxEnergy = 0d;
        for (PitchDetector.PitchData frame : phraseFrames) {
            if (frame != null && Double.isFinite(frame.energy())) {
                phraseMaxEnergy = Math.max(phraseMaxEnergy, frame.energy());
            }
        }
        double endThreshold = phraseMaxEnergy * END_ENERGY_THRESHOLD_RATIO;
        List<PitchDetector.PitchData> endFrames = new ArrayList<>();
        for (PitchDetector.PitchData frame : phraseFrames) {
            if (frame != null && Double.isFinite(frame.energy()) && frame.energy() >= endThreshold) {
                endFrames.add(frame);
            }
        }
        if (endFrames.isEmpty()) {
            endFrames = phraseFrames;
        }
        int confidentTailIndex = phraseFrames.indexOf(endFrames.get(endFrames.size() - 1));
        int extendedTailIndex = extendContinuousTail(phraseFrames, confidentTailIndex);
        int confidentTailMs = frameStartMs(phraseFrames.get(confidentTailIndex));
        int extendedTailMs = frameStartMs(phraseFrames.get(extendedTailIndex));
        if (extendedTailIndex > confidentTailIndex && nextAnchorMs - extendedTailMs < MIN_ONSET_GAP_MS) {
            return new VocalEndDetection(clampVocalEndBeforeNextAnchor(confidentTailMs + FRAME_TAIL_MS, nextAnchorMs),
                    "detected vocal end before next phrase bridge");
        }
        if (nextAnchorMs - extendedTailMs >= LONG_SILENCE_MS
                && nextAnchorMs - extendedTailMs <= NEXT_ANCHOR_PRE_ROLL_MS
                && extendedTailIndex > confidentTailIndex) {
            return new VocalEndDetection(clampVocalEndBeforeNextAnchor(confidentTailMs + FRAME_TAIL_MS, nextAnchorMs),
                    "detected vocal end before long silence");
        }
        for (int index = extendedTailIndex; index > 0; index--) {
            int rightMs = frameStartMs(phraseFrames.get(index));
            int leftMs = frameStartMs(phraseFrames.get(index - 1));
            if (rightMs - leftMs >= LONG_SILENCE_MS
                    && nextAnchorMs - rightMs <= NEXT_ANCHOR_PRE_ROLL_MS) {
                return new VocalEndDetection(clampVocalEndBeforeNextAnchor(leftMs + FRAME_TAIL_MS, nextAnchorMs),
                        "detected vocal end before long silence");
            }
        }
        PitchDetector.PitchData candidate = phraseFrames.get(extendedTailIndex);
        return new VocalEndDetection(clampVocalEndBeforeNextAnchor(frameStartMs(candidate) + FRAME_TAIL_MS,
                nextAnchorMs), "detected vocal end");
    }

    private int clampVocalEndBeforeNextAnchor(int endMs, int nextAnchorMs) {
        return Math.min(endMs, Math.max(0, nextAnchorMs - 1));
    }

    private int extendContinuousTail(List<PitchDetector.PitchData> phraseFrames, int startIndex) {
        if (phraseFrames == null || phraseFrames.isEmpty()) {
            return 0;
        }
        int tailIndex = Math.max(0, startIndex);
        while (tailIndex + 1 < phraseFrames.size()) {
            int currentMs = frameStartMs(phraseFrames.get(tailIndex));
            int nextMs = frameStartMs(phraseFrames.get(tailIndex + 1));
            if (nextMs - currentMs > CONTINUOUS_SIGNAL_GAP_MS) {
                break;
            }
            tailIndex++;
        }
        return tailIndex;
    }

    private int findFirstTranscriptStartMs(List<OpenAiTranscriptSegment> phrases) {
        return phrases.isEmpty() ? 0 : phraseStartMs(phrases.get(0));
    }

    private int phraseStartMs(OpenAiTranscriptSegment segment) {
        if (segment == null) {
            return 0;
        }
        int wordStartMs = findFirstWordStartMs(segment.getWords());
        if (wordStartMs >= 0) {
            return wordStartMs;
        }
        return Math.max(0, segment.getStartMs());
    }

    private int findFirstWordStartMs(List<OpenAiTranscriptWord> words) {
        if (words == null) {
            return -1;
        }
        for (OpenAiTranscriptWord word : words) {
            if (word != null && StringUtils.isNotBlank(word.getText()) && word.getStartMs() >= 0) {
                return word.getStartMs();
            }
        }
        return -1;
    }

    private int findLastWordEndMs(List<OpenAiTranscriptWord> words) {
        int endMs = 0;
        if (words != null) {
            for (OpenAiTranscriptWord word : words) {
                if (word != null) {
                    endMs = Math.max(endMs, Math.max(word.getStartMs(), word.getEndMs()));
                }
            }
        }
        return endMs;
    }

    private int phraseEndMs(OpenAiTranscriptSegment segment) {
        if (segment == null) {
            return 0;
        }
        int wordEndMs = findLastWordEndMs(segment.getWords());
        if (wordEndMs > 0) {
            return wordEndMs;
        }
        return Math.max(phraseStartMs(segment) + 1, segment.getEndMs());
    }

    private boolean containsUsableWords(List<OpenAiTranscriptWord> words) {
        if (words == null) {
            return false;
        }
        for (OpenAiTranscriptWord word : words) {
            if (word != null && StringUtils.isNotBlank(word.getText())) {
                return true;
            }
        }
        return false;
    }

    private boolean isUsablePhrase(OpenAiTranscriptSegment segment) {
        return segment != null
                && (containsUsableWords(segment.getWords()) || StringUtils.isNotBlank(segment.getText()));
    }

    private int frameStartMs(PitchDetector.PitchData frame) {
        return Math.max(0, Math.round(frame.time() * 1000.0f));
    }

    private OpenAiTranscriptionResult rebuildTranscriptWithoutSegments(OpenAiTranscriptionResult transcript,
                                                                       TimingRefinementAnalysis analysis,
                                                                       String refinedTimingSourceTag) {
        PhraseTiming timing = analysis.phrases().get(0);
        List<OpenAiTranscriptWord> refinedWords = refineWordsForPhrase(transcript.getWords(), timing,
                new OpenAiTranscriptSegment(timing.transcriptStartMs(), timing.adjustedEndMs(),
                        transcript.getTranscriptText(), transcript.getWords()));
        return new OpenAiTranscriptionResult(transcript.getSourceAudioFile(),
                transcript.getUploadAudioFile(),
                transcript.getSourceTag(),
                transcript.getTranscriptText(),
                refinedWords,
                transcript.getSegments(),
                transcript.getLyricTokens(),
                transcript.isFromCache(),
                transcript.getCacheFile(),
                transcript.getTextSourceTag(),
                StringUtils.defaultIfBlank(refinedTimingSourceTag, transcript.getTimingSourceTag()));
    }

    private List<OpenAiTranscriptWord> resolvePhraseWords(OpenAiTranscriptSegment segment,
                                                          List<OpenAiTranscriptWord> topLevelWords,
                                                          int transcriptStartMs,
                                                          Integer nextAnchorMs,
                                                          int[] topLevelWordCursor) {
        if (containsUsableWords(segment.getWords())) {
            if (topLevelWordCursor != null) {
                topLevelWordCursor[0] = Math.min(topLevelWords == null ? 0 : topLevelWords.size(),
                        topLevelWordCursor[0] + segment.getWords().size());
            }
            return segment.getWords();
        }
        if (topLevelWords == null || topLevelWordCursor == null) {
            return List.of();
        }
        List<OpenAiTranscriptWord> words = new ArrayList<>();
        int upperBoundMs = nextAnchorMs != null ? nextAnchorMs : Integer.MAX_VALUE;
        while (topLevelWordCursor[0] < topLevelWords.size()) {
            OpenAiTranscriptWord word = topLevelWords.get(topLevelWordCursor[0]);
            if (word == null) {
                topLevelWordCursor[0]++;
                continue;
            }
            if (word.getStartMs() < transcriptStartMs) {
                topLevelWordCursor[0]++;
                continue;
            }
            if (word.getStartMs() >= upperBoundMs) {
                break;
            }
            words.add(word);
            topLevelWordCursor[0]++;
        }
        return words;
    }

    private List<OpenAiTranscriptWord> refineWordsForPhrase(List<OpenAiTranscriptWord> words,
                                                            PhraseTiming timing,
                                                            OpenAiTranscriptSegment segment) {
        if (words == null || words.isEmpty()) {
            return List.of();
        }
        int refinedStartMs = phraseStartMs(timing, segment);
        int refinedEndMs = phraseEndMs(timing, segment, refinedStartMs);
        List<OpenAiTranscriptWord> onsetWords = refineWordsFromOnsetAnchors(words,
                timing.onsetAnchorsMs(),
                refinedStartMs,
                refinedEndMs,
                timing.signalWindows());
        List<OpenAiTranscriptWord> exactSignalWindowWords = List.of();
        if (timing.signalWindows() != null && timing.signalWindows().size() == words.size()) {
            exactSignalWindowWords = refineWordsFromSignalWindows(words,
                    timing.signalWindows(),
                    refinedStartMs,
                    refinedEndMs,
                    timing.rawOnsetAnchorsMs());
            if (!exactSignalWindowWords.isEmpty()
                    && (!onsetWords.isEmpty() ? !shouldPreferOnsetWords(onsetWords, exactSignalWindowWords)
                                              : true)) {
                anchorWordsToPhraseBounds(exactSignalWindowWords, refinedStartMs, refinedEndMs);
                logPhraseRefinement(timing, "signal-windows-exact", exactSignalWindowWords);
                return exactSignalWindowWords;
            }
        }
        if (!onsetWords.isEmpty()) {
            List<OpenAiTranscriptWord> signalWindowWords = refineWordsFromSignalWindows(words,
                    timing.signalWindows(),
                    refinedStartMs,
                    refinedEndMs,
                    timing.rawOnsetAnchorsMs());
            if (!signalWindowWords.isEmpty()
                    && shouldPreferSignalWindowsOverOnsets(words, timing.signalWindows(), onsetWords,
                    signalWindowWords)) {
                anchorWordsToPhraseBounds(signalWindowWords, refinedStartMs, refinedEndMs);
                logPhraseRefinement(timing, "signal-windows-over-onsets", signalWindowWords);
                return signalWindowWords;
            }
            anchorWordsToPhraseBounds(onsetWords, refinedStartMs, refinedEndMs);
            logPhraseRefinement(timing, "onset-anchors", onsetWords);
            return onsetWords;
        }
        List<OpenAiTranscriptWord> signalWindowWords = refineWordsFromSignalWindows(words,
                timing.signalWindows(),
                refinedStartMs,
                refinedEndMs,
                timing.rawOnsetAnchorsMs());
        if (!signalWindowWords.isEmpty()) {
            anchorWordsToPhraseBounds(signalWindowWords, refinedStartMs, refinedEndMs);
            logPhraseRefinement(timing, "signal-windows", signalWindowWords);
            return signalWindowWords;
        }
        int refinedSpanMs = Math.max(1, refinedEndMs - refinedStartMs);
        int originalStartMs = phraseStartMs(segment);
        int originalEndMs = phraseEndMs(segment);
        int originalSpanMs = Math.max(1, originalEndMs - originalStartMs);

        List<OpenAiTranscriptWord> refinedWords = new ArrayList<>(words.size());
        int previousEndMs = refinedStartMs;
        for (int index = 0; index < words.size(); index++) {
            OpenAiTranscriptWord word = words.get(index);
            double startRatio = words.size() == 1 ? 0d : clampRatio((double) (word.getStartMs() - originalStartMs) / originalSpanMs);
            double endRatio = words.size() == 1 ? 1d : clampRatio((double) (word.getEndMs() - originalStartMs) / originalSpanMs);
            int wordStartMs = refinedStartMs + (int) Math.round(refinedSpanMs * startRatio);
            int wordEndMs = refinedStartMs + (int) Math.round(refinedSpanMs * endRatio);
            wordStartMs = Math.max(wordStartMs, previousEndMs);
            if (wordEndMs <= wordStartMs) {
                wordEndMs = wordStartMs + 1;
            }
            if (index == words.size() - 1) {
                wordEndMs = Math.max(wordStartMs + 1, refinedEndMs);
            }
            refinedWords.add(new OpenAiTranscriptWord(word.getText(),
                    word.getNormalizedText(),
                    wordStartMs,
                    wordEndMs,
                    word.getScore()));
            previousEndMs = wordEndMs;
        }
        anchorWordsToPhraseBounds(refinedWords, refinedStartMs, refinedEndMs);
        logPhraseRefinement(timing, "ratio-fallback", refinedWords);
        return refinedWords;
    }

    private void logPhraseRefinement(PhraseTiming timing,
                                     String strategy,
                                     List<OpenAiTranscriptWord> refinedWords) {
        if (timing == null || timing.index() >= DEBUG_PHRASE_LIMIT) {
            return;
        }
        StringBuilder words = new StringBuilder();
        for (OpenAiTranscriptWord word : refinedWords) {
            if (words.length() > 0) {
                words.append(", ");
            }
            words.append(word.getText())
                 .append(':')
                 .append(word.getStartMs())
                 .append('-')
                 .append(word.getEndMs());
        }
        LOGGER.info("[TranscriptAlignDebug] phrase=" + timing.index()
                + " strategy=" + strategy
                + " text=\"" + timing.text() + "\""
                + " detectedStart=" + timing.detectedStartMs()
                + " detectedEnd=" + timing.detectedVocalEndMs()
                + " onsets=" + timing.onsetAnchorsMs()
                + " windows=" + timing.signalWindows()
                + " words=[" + words + "]");
    }

    private List<OpenAiTranscriptWord> refineWordsFromSignalWindows(List<OpenAiTranscriptWord> words,
                                                                    List<SignalWindow> signalWindows,
                                                                    int refinedStartMs,
                                                                    int refinedEndMs,
                                                                    List<Integer> onsetAnchorsMs) {
        if (words == null || words.isEmpty() || signalWindows == null || signalWindows.isEmpty()) {
            return List.of();
        }
        int wordCount = words.size();
        int windowCount = signalWindows.size();
        List<OpenAiTranscriptWord> refinedWords = new ArrayList<>(wordCount);
        if (windowCount == wordCount) {
            for (int index = 0; index < wordCount; index++) {
                SignalWindow window = signalWindows.get(index);
                refinedWords.add(copyWord(words.get(index), window.startMs(), window.endMs()));
            }
            anchorWordsToPhraseBounds(refinedWords, refinedStartMs, refinedEndMs);
            return refinedWords;
        }
        if (windowCount < wordCount) {
            List<Integer> wordsPerWindow = distributeWordsByWindowCapacity(wordCount, signalWindows, onsetAnchorsMs);
            wordsPerWindow = rebalanceSupportWindows(words, signalWindows, wordsPerWindow);
            int wordCursor = 0;
            for (int windowIndex = 0; windowIndex < windowCount; windowIndex++) {
                int count = wordsPerWindow.get(windowIndex);
                if (count <= 0) {
                    continue;
                }
                SignalWindow window = signalWindows.get(windowIndex);
                int effectiveEndMs = window.endMs();
                int lookahead = windowIndex + 1;
                while (lookahead < windowCount && wordsPerWindow.get(lookahead) == 0) {
                    effectiveEndMs = signalWindows.get(lookahead).endMs();
                    lookahead++;
                }
                List<OpenAiTranscriptWord> bucket = words.subList(wordCursor, wordCursor + count);
                refinedWords.addAll(subdivideWordsIntoWindow(bucket,
                        window.startMs(),
                        effectiveEndMs,
                        filterInternalOnsetAnchors(onsetAnchorsMs, window.startMs(), effectiveEndMs),
                        effectiveEndMs > window.endMs()));
                wordCursor += count;
            }
            anchorWordsToPhraseBounds(refinedWords, refinedStartMs, refinedEndMs);
            return refinedWords;
        }

        List<Integer> windowsPerWord = distributeWindowsAcrossWords(words, signalWindows);
        int windowCursor = 0;
        for (int wordIndex = 0; wordIndex < wordCount; wordIndex++) {
            int count = windowsPerWord.get(wordIndex);
            SignalWindow startWindow = signalWindows.get(windowCursor);
            SignalWindow endWindow = signalWindows.get(windowCursor + count - 1);
            refinedWords.add(copyWord(words.get(wordIndex), startWindow.startMs(), endWindow.endMs()));
            windowCursor += count;
        }
        anchorWordsToPhraseBounds(refinedWords, refinedStartMs, refinedEndMs);
        return refinedWords;
    }

    private boolean shouldPreferSignalWindowsOverOnsets(List<OpenAiTranscriptWord> words,
                                                        List<SignalWindow> signalWindows,
                                                        List<OpenAiTranscriptWord> onsetWords,
                                                        List<OpenAiTranscriptWord> signalWindowWords) {
        if (words == null || signalWindows == null || onsetWords == null || signalWindowWords == null) {
            return false;
        }
        if (words.size() < 2 || signalWindows.size() <= words.size()
                || onsetWords.size() != words.size() || signalWindowWords.size() != words.size()) {
            return false;
        }
        // When the half-beat grid shatters a line into far more windows than words, the
        // window->word distribution is a positional guess that ignores onset locations and can
        // drag a word well past its sung onset. A clean onset-per-word set (guaranteed by the
        // size checks above) is more reliable here, so keep the onsets.
        if (signalWindows.size() > words.size() * OVER_FRAGMENTED_WINDOW_RATIO) {
            return false;
        }
        for (int index = 1; index < words.size(); index++) {
            int onsetStartMs = onsetWords.get(index).getStartMs();
            int signalStartMs = signalWindowWords.get(index).getStartMs();
            if (onsetStartMs - signalStartMs >= MIN_ONSET_GAP_MS) {
                return true;
            }
        }
        return false;
    }

    private List<Integer> distributeWindowsAcrossWords(List<OpenAiTranscriptWord> words,
                                                       List<SignalWindow> signalWindows) {
        int wordCount = words == null ? 0 : words.size();
        int windowCount = signalWindows == null ? 0 : signalWindows.size();
        if (wordCount <= 0 || windowCount <= 0) {
            return List.of();
        }
        if (windowCount <= wordCount) {
            return distributeEvenly(windowCount, wordCount);
        }
        List<Integer> windowsPerWord = new ArrayList<>(wordCount);
        for (int index = 0; index < wordCount; index++) {
            windowsPerWord.add(1);
        }
        int remaining = windowCount - wordCount;
        while (remaining-- > 0) {
            int targetIndex = findBestWordForExtraWindow(words, windowsPerWord);
            windowsPerWord.set(targetIndex, windowsPerWord.get(targetIndex) + 1);
        }
        return windowsPerWord;
    }

    private int findBestWordForExtraWindow(List<OpenAiTranscriptWord> words,
                                           List<Integer> windowsPerWord) {
        int bestIndex = 0;
        int bestDeficit = Integer.MIN_VALUE;
        int bestSyllables = Integer.MIN_VALUE;
        for (int index = 0; index < words.size(); index++) {
            int syllables = estimateSyllableCount(words.get(index));
            int deficit = syllables - windowsPerWord.get(index);
            if (deficit > bestDeficit
                    || (deficit == bestDeficit && syllables > bestSyllables)) {
                bestIndex = index;
                bestDeficit = deficit;
                bestSyllables = syllables;
            }
        }
        return bestIndex;
    }

    private List<OpenAiTranscriptWord> refineWordsFromOnsetAnchors(List<OpenAiTranscriptWord> words,
                                                                   List<Integer> onsetAnchorsMs,
                                                                   int refinedStartMs,
                                                                   int refinedEndMs,
                                                                   List<SignalWindow> signalWindows) {
        if (words == null || words.isEmpty() || onsetAnchorsMs == null || onsetAnchorsMs.isEmpty()) {
            return List.of();
        }
        List<Integer> normalizedAnchors = normalizeOnsetAnchorsForWords(onsetAnchorsMs, refinedStartMs, words.size());
        if (normalizedAnchors.size() != words.size()) {
            return List.of();
        }
        List<SignalWindow> energyBlocks = coalesceEnergyBlocks(signalWindows);
        // A melismatic word fills one long energy block with its own internal onsets and steals the
        // following words. The plain onset placement then crowds extra words into that block and
        // starves a later one. Detect this by comparing how the onsets group words across blocks
        // with a syllable-balanced partition: when they disagree, the syllable partition is the
        // melisma-aware grouping, so regroup the words onto blocks by it.
        if (energyBlocks.size() >= 2 && energyBlocks.size() < words.size()) {
            List<Integer> onsetGrouping = countAnchorsPerBlock(normalizedAnchors, energyBlocks);
            List<Integer> syllableGrouping = partitionWordsAcrossBlocksBySyllables(words, energyBlocks);
            if (syllableGrouping != null
                    && onsetGroupingStarvesBlock(onsetGrouping, syllableGrouping)) {
                List<OpenAiTranscriptWord> blockGrouped = refineWordsByEnergyBlocks(words,
                        energyBlocks,
                        onsetAnchorsMs,
                        syllableGrouping,
                        refinedStartMs,
                        refinedEndMs);
                if (blockGrouped != null) {
                    anchorWordsToPhraseBounds(blockGrouped, refinedStartMs, refinedEndMs);
                    return blockGrouped;
                }
            }
        }
        snapOrphanedAnchorsToUnusedEnergyBlocks(normalizedAnchors, signalWindows);
        List<OpenAiTranscriptWord> refinedWords = new ArrayList<>(words.size());
        for (int index = 0; index < words.size(); index++) {
            int startMs = Math.max(refinedStartMs, normalizedAnchors.get(index));
            int endMs;
            if (index == words.size() - 1) {
                endMs = refinedEndMs;
            } else {
                int nextStartMs = normalizedAnchors.get(index + 1);
                endMs = startMs + Math.max(1, (nextStartMs - startMs) / 2);
                // When the next word begins in a separate energy block, the silence between blocks
                // marks the real note boundary. Hold this word until its own block ends (clamped
                // before the next word) instead of cutting at the midpoint, which would truncate a
                // sustained syllable while the singer is still on it.
                Integer blockEndMs = energyBlockEndForExtension(energyBlocks, startMs, nextStartMs);
                if (blockEndMs != null && blockEndMs > endMs) {
                    endMs = Math.min(blockEndMs, nextStartMs - 1);
                }
            }
            refinedWords.add(copyWord(words.get(index), startMs, endMs));
        }
        anchorWordsToPhraseBounds(refinedWords, refinedStartMs, refinedEndMs);
        return refinedWords;
    }

    // Groups words onto continuous energy blocks before placing them, so a melismatic word (one word
    // sung across a long block with several internal onsets) claims its whole block instead of
    // letting those internal onsets capture the following words. Words are partitioned across blocks
    // by a syllable-balanced split (a 3-syllable word weighs as much as 3 one-syllable words), then
    // each block's words are placed with the same onset/ratio subdivision used elsewhere. A single
    // word in a block starts on its first internal onset (not the block edge) to preserve onset
    // precision. Returns null when the partition cannot be formed.
    private List<OpenAiTranscriptWord> refineWordsByEnergyBlocks(List<OpenAiTranscriptWord> words,
                                                                 List<SignalWindow> energyBlocks,
                                                                 List<Integer> onsetAnchorsMs,
                                                                 List<Integer> wordsPerBlock,
                                                                 int refinedStartMs,
                                                                 int refinedEndMs) {
        if (wordsPerBlock == null) {
            return null;
        }
        List<OpenAiTranscriptWord> refined = new ArrayList<>(words.size());
        int wordCursor = 0;
        for (int blockIndex = 0; blockIndex < energyBlocks.size(); blockIndex++) {
            int count = wordsPerBlock.get(blockIndex);
            if (count <= 0) {
                continue;
            }
            SignalWindow block = energyBlocks.get(blockIndex);
            int blockStartMs = Math.max(refinedStartMs, block.startMs());
            int blockEndMs = blockIndex == energyBlocks.size() - 1
                    ? Math.max(blockStartMs + 1, refinedEndMs)
                    : block.endMs();
            List<OpenAiTranscriptWord> bucket = words.subList(wordCursor, wordCursor + count);
            List<Integer> internalOnsets = filterInternalOnsetAnchors(onsetAnchorsMs, blockStartMs, blockEndMs);
            if (count == 1) {
                // Start the word on the first onset at or after the block start: when the block
                // begins on an onset that is the word's onset; when the block's leading energy has
                // no onset (e.g. a sustained tail bleeding in), the first real onset inside is.
                int onsetStartMs = firstOnsetAtOrAfter(onsetAnchorsMs, blockStartMs, blockEndMs, blockStartMs);
                refined.add(copyWord(bucket.get(0), onsetStartMs, blockEndMs));
            } else {
                refined.addAll(subdivideWordsIntoWindow(bucket, blockStartMs, blockEndMs, internalOnsets, false));
            }
            wordCursor += count;
        }
        return refined.size() == words.size() ? refined : null;
    }

    // True when the onset grouping over-crowds a block that the syllable-balanced grouping would
    // keep lighter (by 2+ words) while leaving a later block under-filled. That is the melisma
    // signature: a sustained word's internal onsets pulled extra words into its block and starved a
    // later one. An even reshuffle that only shifts a single word between adjacent blocks (dense,
    // evenly sung lines) does not trip this and is left to the existing onset/window placement.
    private boolean onsetGroupingStarvesBlock(List<Integer> onsetGrouping, List<Integer> syllableGrouping) {
        if (onsetGrouping == null || syllableGrouping == null
                || onsetGrouping.size() != syllableGrouping.size()) {
            return false;
        }
        for (int index = 0; index < onsetGrouping.size(); index++) {
            int overcrowd = onsetGrouping.get(index) - syllableGrouping.get(index);
            if (overcrowd < 2) {
                continue;
            }
            for (int laterIndex = index + 1; laterIndex < onsetGrouping.size(); laterIndex++) {
                if (onsetGrouping.get(laterIndex) < syllableGrouping.get(laterIndex)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Counts how many word anchors fall in each energy block (anchors before the first block or
    // after the last are clamped into the nearest block so the totals still sum to the word count).
    private List<Integer> countAnchorsPerBlock(List<Integer> anchorsMs, List<SignalWindow> energyBlocks) {
        int[] anchorsPerBlock = new int[energyBlocks.size()];
        for (Integer anchorMs : anchorsMs) {
            if (anchorMs == null) {
                continue;
            }
            int blockIndex = indexOfContainingOrNearestBlock(anchorMs, energyBlocks);
            anchorsPerBlock[blockIndex]++;
        }
        List<Integer> counts = new ArrayList<>(energyBlocks.size());
        for (int count : anchorsPerBlock) {
            counts.add(count);
        }
        return counts;
    }

    private int indexOfContainingOrNearestBlock(int ms, List<SignalWindow> energyBlocks) {
        for (int blockIndex = 0; blockIndex < energyBlocks.size(); blockIndex++) {
            SignalWindow block = energyBlocks.get(blockIndex);
            if (ms >= block.startMs() && ms < block.endMs()) {
                return blockIndex;
            }
            if (ms < block.startMs()) {
                return blockIndex;
            }
        }
        return energyBlocks.size() - 1;
    }

    private int firstOnsetAtOrAfter(List<Integer> onsetAnchorsMs, int fromMs, int toMs, int fallbackMs) {
        if (onsetAnchorsMs == null) {
            return fallbackMs;
        }
        for (Integer onsetMs : onsetAnchorsMs) {
            if (onsetMs != null && onsetMs >= fromMs && onsetMs < toMs) {
                return onsetMs;
            }
        }
        return fallbackMs;
    }

    // Contiguously partitions words across energy blocks so each block's syllable load tracks its
    // duration share of the phrase. Dynamic program minimizing summed |groupSyllables - target|.
    private List<Integer> partitionWordsAcrossBlocksBySyllables(List<OpenAiTranscriptWord> words,
                                                                List<SignalWindow> energyBlocks) {
        int wordCount = words.size();
        int blockCount = energyBlocks.size();
        if (blockCount > wordCount) {
            return null;
        }
        int[] syllablePrefix = new int[wordCount + 1];
        for (int index = 0; index < wordCount; index++) {
            syllablePrefix[index + 1] = syllablePrefix[index] + estimateSyllableCount(words.get(index));
        }
        int totalSyllables = syllablePrefix[wordCount];
        long totalDurationMs = 0;
        for (SignalWindow block : energyBlocks) {
            totalDurationMs += Math.max(1, block.endMs() - block.startMs());
        }
        double[] targetSyllables = new double[blockCount];
        for (int blockIndex = 0; blockIndex < blockCount; blockIndex++) {
            SignalWindow block = energyBlocks.get(blockIndex);
            double share = (double) Math.max(1, block.endMs() - block.startMs()) / totalDurationMs;
            targetSyllables[blockIndex] = totalSyllables * share;
        }
        double[][] cost = new double[blockCount + 1][wordCount + 1];
        int[][] back = new int[blockCount + 1][wordCount + 1];
        for (double[] row : cost) {
            java.util.Arrays.fill(row, Double.POSITIVE_INFINITY);
        }
        cost[0][0] = 0d;
        for (int blockIndex = 1; blockIndex <= blockCount; blockIndex++) {
            for (int wordIndex = blockIndex; wordIndex <= wordCount - (blockCount - blockIndex); wordIndex++) {
                for (int split = blockIndex - 1; split < wordIndex; split++) {
                    if (Double.isInfinite(cost[blockIndex - 1][split])) {
                        continue;
                    }
                    int groupSyllables = syllablePrefix[wordIndex] - syllablePrefix[split];
                    double candidate = cost[blockIndex - 1][split]
                            + Math.abs(groupSyllables - targetSyllables[blockIndex - 1]);
                    if (candidate < cost[blockIndex][wordIndex]) {
                        cost[blockIndex][wordIndex] = candidate;
                        back[blockIndex][wordIndex] = split;
                    }
                }
            }
        }
        if (Double.isInfinite(cost[blockCount][wordCount])) {
            return null;
        }
        int[] counts = new int[blockCount];
        int wordIndex = wordCount;
        for (int blockIndex = blockCount; blockIndex >= 1; blockIndex--) {
            int split = back[blockIndex][wordIndex];
            counts[blockIndex - 1] = wordIndex - split;
            wordIndex = split;
        }
        List<Integer> distribution = new ArrayList<>(blockCount);
        for (int count : counts) {
            distribution.add(count);
        }
        return distribution;
    }

    // Onset normalization keeps the earliest onset when it merges a close pair, which can leave a
    // word stranded inside the previous word's sustained energy block (no onset of its own) while a
    // genuine, separately-onset energy block right before the next word goes unused. When that
    // happens, move the stranded word onto the unused block's start so it lands on real vocals.
    private void snapOrphanedAnchorsToUnusedEnergyBlocks(List<Integer> anchorsMs,
                                                         List<SignalWindow> signalWindows) {
        if (anchorsMs == null || anchorsMs.size() < 2) {
            return;
        }
        List<SignalWindow> energyBlocks = coalesceEnergyBlocks(signalWindows);
        if (energyBlocks.size() < 2) {
            return;
        }
        for (int index = 1; index < anchorsMs.size(); index++) {
            int currentStartMs = anchorsMs.get(index);
            int previousStartMs = anchorsMs.get(index - 1);
            SignalWindow currentBlock = findContainingEnergyBlock(currentStartMs, energyBlocks);
            SignalWindow previousBlock = findContainingEnergyBlock(previousStartMs, energyBlocks);
            // Only act when this word shares the previous word's energy block (it has no onset of
            // its own) and there is room before the next word's anchor.
            if (currentBlock == null || currentBlock != previousBlock) {
                continue;
            }
            int upperBoundMs = index + 1 < anchorsMs.size() ? anchorsMs.get(index + 1) : Integer.MAX_VALUE;
            SignalWindow unusedBlock = findUnusedEnergyBlockBetween(energyBlocks,
                    currentBlock.endMs(),
                    upperBoundMs,
                    anchorsMs);
            if (unusedBlock != null) {
                anchorsMs.set(index, unusedBlock.startMs());
            }
        }
    }

    private List<SignalWindow> coalesceEnergyBlocks(List<SignalWindow> signalWindows) {
        if (signalWindows == null || signalWindows.isEmpty()) {
            return List.of();
        }
        List<SignalWindow> blocks = new ArrayList<>();
        int blockStartMs = signalWindows.get(0).startMs();
        int blockEndMs = signalWindows.get(0).endMs();
        for (int index = 1; index < signalWindows.size(); index++) {
            SignalWindow window = signalWindows.get(index);
            if (window.startMs() - blockEndMs > ENERGY_BLOCK_GAP_MS) {
                blocks.add(new SignalWindow(blockStartMs, blockEndMs));
                blockStartMs = window.startMs();
            }
            blockEndMs = Math.max(blockEndMs, window.endMs());
        }
        blocks.add(new SignalWindow(blockStartMs, blockEndMs));
        return blocks;
    }

    // Returns the end of the energy block that this word starts in, but only when the next word
    // begins in a later, separate block (so the gap between them is a real note boundary). Returns
    // null when both words share a block or no block contains the word start.
    private Integer energyBlockEndForExtension(List<SignalWindow> energyBlocks,
                                               int wordStartMs,
                                               int nextWordStartMs) {
        if (energyBlocks == null || energyBlocks.size() < 2) {
            return null;
        }
        SignalWindow currentBlock = findContainingEnergyBlock(wordStartMs, energyBlocks);
        SignalWindow nextBlock = findContainingEnergyBlock(nextWordStartMs, energyBlocks);
        if (currentBlock == null || currentBlock == nextBlock) {
            return null;
        }
        return currentBlock.endMs();
    }

    private SignalWindow findContainingEnergyBlock(int ms, List<SignalWindow> energyBlocks) {
        for (SignalWindow block : energyBlocks) {
            if (ms >= block.startMs() && ms < block.endMs()) {
                return block;
            }
        }
        return null;
    }

    private SignalWindow findUnusedEnergyBlockBetween(List<SignalWindow> energyBlocks,
                                                      int afterMs,
                                                      int beforeMs,
                                                      List<Integer> anchorsMs) {
        for (SignalWindow block : energyBlocks) {
            if (block.startMs() < afterMs || block.startMs() >= beforeMs) {
                continue;
            }
            if (!energyBlockHoldsAnchor(block, anchorsMs)) {
                return block;
            }
        }
        return null;
    }

    private boolean energyBlockHoldsAnchor(SignalWindow block, List<Integer> anchorsMs) {
        for (Integer anchorMs : anchorsMs) {
            if (anchorMs != null && anchorMs >= block.startMs() && anchorMs < block.endMs()) {
                return true;
            }
        }
        return false;
    }

    private List<Integer> normalizeOnsetAnchorsForWords(List<Integer> onsetAnchorsMs,
                                                        int refinedStartMs,
                                                        int wordCount) {
        if (onsetAnchorsMs == null || onsetAnchorsMs.isEmpty() || wordCount <= 0) {
            return List.of();
        }
        List<Integer> anchors = new ArrayList<>(onsetAnchorsMs);
        if (anchors.get(0) > refinedStartMs) {
            anchors.add(0, refinedStartMs);
        } else if (anchors.get(0) != refinedStartMs) {
            anchors.set(0, refinedStartMs);
        }
        while (anchors.size() > wordCount) {
            int mergeIndex = findClosestOnsetPairIndex(anchors);
            if (mergeIndex < 0 || mergeIndex + 1 >= anchors.size()) {
                break;
            }
            anchors.remove(mergeIndex + 1);
        }
        return anchors;
    }

    private boolean shouldPreferOnsetWords(List<OpenAiTranscriptWord> onsetWords,
                                           List<OpenAiTranscriptWord> signalWindowWords) {
        if (onsetWords == null || signalWindowWords == null || onsetWords.size() != signalWindowWords.size()) {
            return false;
        }
        int maxStartDelta = 0;
        for (int index = 0; index < onsetWords.size(); index++) {
            maxStartDelta = Math.max(maxStartDelta,
                    Math.abs(onsetWords.get(index).getStartMs() - signalWindowWords.get(index).getStartMs()));
        }
        return maxStartDelta > ONSET_BUCKET_MS;
    }

    private int findClosestOnsetPairIndex(List<Integer> anchors) {
        if (anchors == null || anchors.size() < 2) {
            return -1;
        }
        int bestIndex = -1;
        int bestGap = Integer.MAX_VALUE;
        for (int index = 0; index < anchors.size() - 1; index++) {
            int gap = anchors.get(index + 1) - anchors.get(index);
            if (gap < bestGap) {
                bestGap = gap;
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private List<OpenAiTranscriptWord> subdivideWordsIntoWindow(List<OpenAiTranscriptWord> words,
                                                                int windowStartMs,
                                                                int windowEndMs,
                                                                List<Integer> internalOnsetAnchorsMs,
                                                                boolean preferRatioForLateOnsets) {
        if (words == null || words.isEmpty()) {
            return List.of();
        }
        if (words.size() == 1) {
            return List.of(copyWord(words.get(0), windowStartMs, windowEndMs));
        }
        if (internalOnsetAnchorsMs != null && internalOnsetAnchorsMs.size() >= words.size() - 1) {
            return subdivideWordsIntoWindowByOnsets(words,
                    windowStartMs,
                    windowEndMs,
                    internalOnsetAnchorsMs,
                    preferRatioForLateOnsets);
        }
        int originalStartMs = words.stream().mapToInt(OpenAiTranscriptWord::getStartMs).min().orElse(windowStartMs);
        int originalEndMs = words.stream().mapToInt(OpenAiTranscriptWord::getEndMs).max().orElse(windowEndMs);
        int originalSpanMs = Math.max(1, originalEndMs - originalStartMs);
        int windowSpanMs = Math.max(1, windowEndMs - windowStartMs);
        List<OpenAiTranscriptWord> refined = new ArrayList<>(words.size());
        int previousEndMs = windowStartMs;
        for (int index = 0; index < words.size(); index++) {
            OpenAiTranscriptWord word = words.get(index);
            double startRatio = clampRatio((double) (word.getStartMs() - originalStartMs) / originalSpanMs);
            double endRatio = clampRatio((double) (word.getEndMs() - originalStartMs) / originalSpanMs);
            int startMs = windowStartMs + (int) Math.round(windowSpanMs * startRatio);
            int endMs = windowStartMs + (int) Math.round(windowSpanMs * endRatio);
            startMs = Math.max(startMs, previousEndMs);
            if (endMs <= startMs) {
                endMs = startMs + 1;
            }
            if (index == words.size() - 1) {
                endMs = Math.max(startMs + 1, windowEndMs);
            }
            refined.add(copyWord(word, startMs, endMs));
            previousEndMs = endMs;
        }
        return refined;
    }

    private List<Integer> filterInternalOnsetAnchors(List<Integer> onsetAnchorsMs,
                                                     int windowStartMs,
                                                     int windowEndMs) {
        if (onsetAnchorsMs == null || onsetAnchorsMs.isEmpty()) {
            return List.of();
        }
        List<Integer> filtered = new ArrayList<>();
        for (Integer onsetMs : onsetAnchorsMs) {
            if (onsetMs == null) {
                continue;
            }
            if (onsetMs > windowStartMs && onsetMs < windowEndMs) {
                filtered.add(onsetMs);
            }
        }
        return filtered;
    }

    private List<OpenAiTranscriptWord> subdivideWordsIntoWindowByOnsets(List<OpenAiTranscriptWord> words,
                                                                        int windowStartMs,
                                                                        int windowEndMs,
                                                                        List<Integer> internalOnsetAnchorsMs,
                                                                        boolean preferRatioForLateOnsets) {
        List<OpenAiTranscriptWord> refined = new ArrayList<>(words.size());
        List<Integer> starts = new ArrayList<>(words.size());
        starts.add(windowStartMs);
        int previousStartMs = windowStartMs;
        int anchorCursor = 0;
        int originalStartMs = words.stream().mapToInt(OpenAiTranscriptWord::getStartMs).min().orElse(windowStartMs);
        int originalEndMs = words.stream().mapToInt(OpenAiTranscriptWord::getEndMs).max().orElse(windowEndMs);
        int originalSpanMs = Math.max(1, originalEndMs - originalStartMs);
        int windowSpanMs = Math.max(1, windowEndMs - windowStartMs);
        for (int index = 1; index < words.size(); index++) {
            OpenAiTranscriptWord word = words.get(index);
            double startRatio = clampRatio((double) (word.getStartMs() - originalStartMs) / originalSpanMs);
            int ratioStartMs = windowStartMs + (int) Math.round(windowSpanMs * startRatio);
            int candidateStartMs = Math.max(previousStartMs + 1, ratioStartMs);
            while (anchorCursor < internalOnsetAnchorsMs.size()) {
                int anchorMs = internalOnsetAnchorsMs.get(anchorCursor++);
                if (anchorMs - previousStartMs >= MIN_ONSET_GAP_MS) {
                    if (!preferRatioForLateOnsets || Math.abs(anchorMs - ratioStartMs) <= MIN_ONSET_GAP_MS) {
                        candidateStartMs = anchorMs;
                    }
                    break;
                }
            }
            int latestAllowedStartMs = windowEndMs - (words.size() - index);
            int startMs = Math.max(previousStartMs + 1, Math.min(candidateStartMs, latestAllowedStartMs));
            starts.add(startMs);
            previousStartMs = startMs;
        }
        for (int index = 0; index < words.size(); index++) {
            int startMs = starts.get(index);
            int endMs = index == words.size() - 1 ? windowEndMs : starts.get(index + 1);
            refined.add(copyWord(words.get(index), startMs, endMs));
        }
        return refined;
    }

    private List<Integer> rebalanceSupportWindows(List<OpenAiTranscriptWord> words,
                                                  List<SignalWindow> signalWindows,
                                                  List<Integer> wordsPerWindow) {
        if (words == null || words.isEmpty() || signalWindows == null || signalWindows.isEmpty()
                || wordsPerWindow == null || wordsPerWindow.size() != signalWindows.size()) {
            return wordsPerWindow;
        }
        List<Integer> rebalanced = new ArrayList<>(wordsPerWindow);
        int wordCursor = 0;
        for (int windowIndex = 0; windowIndex < signalWindows.size(); windowIndex++) {
            int count = rebalanced.get(windowIndex);
            if (count <= 0) {
                continue;
            }
            if (windowIndex > 0 && windowIndex < signalWindows.size() - 1 && count == 1) {
                OpenAiTranscriptWord assignedWord = words.get(wordCursor);
                if (estimateSyllableCount(assignedWord) > 1
                        && rebalanced.get(windowIndex - 1) > 0
                        && isSupportWindow(signalWindows, windowIndex)) {
                    rebalanced.set(windowIndex - 1, rebalanced.get(windowIndex - 1) + 1);
                    rebalanced.set(windowIndex, 0);
                    continue;
                }
            }
            wordCursor += count;
        }
        return rebalanced;
    }

    private boolean isSupportWindow(List<SignalWindow> signalWindows, int windowIndex) {
        if (signalWindows == null || windowIndex <= 0 || windowIndex >= signalWindows.size() - 1) {
            return false;
        }
        int currentLength = signalWindows.get(windowIndex).endMs() - signalWindows.get(windowIndex).startMs();
        int previousLength = signalWindows.get(windowIndex - 1).endMs() - signalWindows.get(windowIndex - 1).startMs();
        int nextLength = signalWindows.get(windowIndex + 1).endMs() - signalWindows.get(windowIndex + 1).startMs();
        return currentLength <= 250
                && currentLength <= Math.max(1, Math.round(previousLength * 0.75f))
                && currentLength <= Math.max(1, Math.round(nextLength * 0.35f));
    }

    private int estimateSyllableCount(OpenAiTranscriptWord word) {
        if (word == null || StringUtils.isBlank(word.getText())) {
            return 1;
        }
        String normalized = word.getNormalizedText();
        String text = StringUtils.isNotBlank(normalized) ? normalized : word.getText();
        String lettersOnly = text.toLowerCase(Locale.ROOT).replaceAll("[^a-zäöüáàâãåæéèêëíìîïóòôõøœúùûüýÿ']", "");
        if (lettersOnly.isEmpty()) {
            return 1;
        }
        int groups = 0;
        boolean inVowelGroup = false;
        for (int index = 0; index < lettersOnly.length(); index++) {
            char c = lettersOnly.charAt(index);
            boolean vowel = "aeiouyäöüáàâãåæéèêëíìîïóòôõøœúùûüýÿ".indexOf(c) >= 0;
            if (vowel && !inVowelGroup) {
                groups++;
            }
            inVowelGroup = vowel;
        }
        return Math.max(1, groups);
    }

    private void ensureMonotonicEnds(List<OpenAiTranscriptWord> words, int refinedEndMs) {
        if (words == null || words.isEmpty()) {
            return;
        }
        List<OpenAiTranscriptWord> adjusted = new ArrayList<>(words.size());
        int previousEndMs = words.get(0).getStartMs();
        for (int index = 0; index < words.size(); index++) {
            OpenAiTranscriptWord word = words.get(index);
            int startMs = Math.max(word.getStartMs(), previousEndMs == word.getStartMs() ? word.getStartMs() : previousEndMs);
            int endMs = Math.max(startMs + 1, word.getEndMs());
            if (index == words.size() - 1) {
                endMs = Math.max(startMs + 1, refinedEndMs);
            }
            adjusted.add(copyWord(word, startMs, endMs));
            previousEndMs = endMs;
        }
        words.clear();
        words.addAll(adjusted);
    }

    private void anchorWordsToPhraseBounds(List<OpenAiTranscriptWord> words,
                                           int refinedStartMs,
                                           int refinedEndMs) {
        if (words == null || words.isEmpty()) {
            return;
        }
        OpenAiTranscriptWord first = words.get(0);
        if (first.getStartMs() != refinedStartMs) {
            words.set(0, copyWord(first, refinedStartMs, Math.max(refinedStartMs + 1, first.getEndMs())));
        }
        ensureMonotonicEnds(words, refinedEndMs);
    }

    private OpenAiTranscriptWord copyWord(OpenAiTranscriptWord word, int startMs, int endMs) {
        return new OpenAiTranscriptWord(word.getText(),
                word.getNormalizedText(),
                startMs,
                Math.max(startMs + 1, endMs),
                word.getScore());
    }

    private List<SignalWindow> detectSignalWindows(List<PitchDetector.PitchData> usableFrames,
                                                   int startMs,
                                                   int endMs,
                                                   double bpm) {
        if (usableFrames == null || usableFrames.isEmpty() || endMs <= startMs) {
            return List.of();
        }
        List<PitchDetector.PitchData> phraseFrames = new ArrayList<>();
        double phraseMaxEnergy = 0d;
        for (PitchDetector.PitchData frame : usableFrames) {
            int frameMs = frameStartMs(frame);
            if (frameMs < startMs) {
                continue;
            }
            if (frameMs > endMs) {
                break;
            }
            phraseFrames.add(frame);
            if (Double.isFinite(frame.energy())) {
                phraseMaxEnergy = Math.max(phraseMaxEnergy, frame.energy());
            }
        }
        if (phraseFrames.isEmpty()) {
            return List.of();
        }
        double windowThreshold = phraseMaxEnergy * WINDOW_ENERGY_THRESHOLD_RATIO;
        if (bpm > 0d) {
            return detectSignalWindowsOnGrid(phraseFrames, startMs, endMs, windowThreshold, bpm);
        }
        List<SignalWindow> windows = new ArrayList<>();
        Integer windowStartMs = null;
        Integer previousFrameMs = null;
        for (PitchDetector.PitchData frame : phraseFrames) {
            int frameMs = frameStartMs(frame);
            if (!Double.isFinite(frame.energy()) || frame.energy() < windowThreshold) {
                continue;
            }
            if (windowStartMs == null) {
                windowStartMs = frameMs;
                previousFrameMs = frameMs;
                continue;
            }
            if (frameMs - previousFrameMs > CONTINUOUS_SIGNAL_GAP_MS) {
                windows.add(new SignalWindow(windowStartMs, previousFrameMs + FRAME_TAIL_MS));
                windowStartMs = frameMs;
            }
            previousFrameMs = frameMs;
        }
        if (windowStartMs != null && previousFrameMs != null) {
            windows.add(new SignalWindow(windowStartMs, previousFrameMs + FRAME_TAIL_MS));
        }
        return windows;
    }

    private List<Integer> detectOnsetAnchors(List<PitchDetector.PitchData> usableFrames,
                                             int startMs,
                                             int endMs) {
        if (usableFrames == null || usableFrames.isEmpty() || endMs <= startMs) {
            return List.of();
        }
        int bucketCount = Math.max(1, (int) Math.ceil((endMs - startMs) / (double) ONSET_BUCKET_MS));
        double[] bucketMax = new double[bucketCount];
        int[] bucketPitchSum = new int[bucketCount];
        int[] bucketPitchCount = new int[bucketCount];
        for (PitchDetector.PitchData frame : usableFrames) {
            int frameMs = frameStartMs(frame);
            if (frameMs < startMs || frameMs > endMs) {
                continue;
            }
            int bucketIndex = Math.min(bucketCount - 1, Math.max(0, (frameMs - startMs) / ONSET_BUCKET_MS));
            bucketMax[bucketIndex] = Math.max(bucketMax[bucketIndex], frame.energy());
            if (frame.rawFrequency() > 0d) {
                bucketPitchSum[bucketIndex] += frame.pitch();
                bucketPitchCount[bucketIndex]++;
            }
        }
        double phraseMax = 0d;
        for (double bucket : bucketMax) {
            phraseMax = Math.max(phraseMax, bucket);
        }
        if (phraseMax <= 0d) {
            return List.of();
        }
        double activeThreshold = phraseMax * ONSET_ENERGY_THRESHOLD_RATIO;
        double riseThreshold = phraseMax * ONSET_ENERGY_RISE_RATIO;
        List<Integer> onsets = new ArrayList<>();
        int lastOnsetMs = Integer.MIN_VALUE;
        for (int bucketIndex = 0; bucketIndex < bucketCount; bucketIndex++) {
            double currentEnergy = bucketMax[bucketIndex];
            if (currentEnergy < activeThreshold) {
                continue;
            }
            double previousEnergy = bucketIndex > 0 ? bucketMax[bucketIndex - 1] : 0d;
            boolean previousActive = bucketIndex > 0 && previousEnergy >= activeThreshold;
            int currentPitch = representativePitch(bucketIndex, bucketPitchSum, bucketPitchCount);
            int previousPitch = representativePitch(bucketIndex - 1, bucketPitchSum, bucketPitchCount);
            boolean pitchJump = previousPitch != Integer.MIN_VALUE
                    && currentPitch != Integer.MIN_VALUE
                    && Math.abs(currentPitch - previousPitch) >= PITCH_SPLIT_THRESHOLD_SEMITONES;
            boolean energyJump = currentEnergy - previousEnergy >= riseThreshold && currentEnergy > previousEnergy;
            if (!previousActive || energyJump || pitchJump) {
                int onsetMs = startMs + bucketIndex * ONSET_BUCKET_MS;
                if (lastOnsetMs == Integer.MIN_VALUE || onsetMs - lastOnsetMs >= MIN_ONSET_GAP_MS) {
                    onsets.add(onsetMs);
                    lastOnsetMs = onsetMs;
                }
            }
        }
        return onsets;
    }

    private List<SignalWindow> detectBeatOccupancyWindows(List<PitchDetector.PitchData> usableFrames,
                                                          int startMs,
                                                          int endMs,
                                                          double bpm) {
        if (usableFrames == null || usableFrames.isEmpty() || endMs <= startMs || bpm <= 0d) {
            return List.of();
        }
        List<PitchDetector.PitchData> phraseFrames = collectFramesInRange(usableFrames, startMs, endMs);
        if (phraseFrames.isEmpty()) {
            return List.of();
        }
        double phraseMaxEnergy = 0d;
        for (PitchDetector.PitchData frame : phraseFrames) {
            if (frame != null && Double.isFinite(frame.energy())) {
                phraseMaxEnergy = Math.max(phraseMaxEnergy, frame.energy());
            }
        }
        if (phraseMaxEnergy <= 0d) {
            return List.of();
        }

        int sampleCount = Math.max(1, (int) Math.ceil((endMs - startMs) / (double) OCCUPANCY_SAMPLE_MS));
        double[] sampleMax = new double[sampleCount];
        for (PitchDetector.PitchData frame : phraseFrames) {
            int frameMs = frameStartMs(frame);
            int sampleIndex = Math.min(sampleCount - 1, Math.max(0, (frameMs - startMs) / OCCUPANCY_SAMPLE_MS));
            sampleMax[sampleIndex] = Math.max(sampleMax[sampleIndex], frame.energy());
        }
        double activeThreshold = phraseMaxEnergy * WINDOW_ENERGY_THRESHOLD_RATIO;
        double internalBeatMs = 60000d / (4d * bpm);
        int beatCount = Math.max(1, (int) Math.ceil((endMs - startMs) / internalBeatMs));
        boolean[] occupiedBeats = new boolean[beatCount];
        for (int beatIndex = 0; beatIndex < beatCount; beatIndex++) {
            int beatStartMs = startMs + (int) Math.round(beatIndex * internalBeatMs);
            int beatEndMs = Math.min(endMs, startMs + (int) Math.round((beatIndex + 1) * internalBeatMs));
            int firstSample = Math.min(sampleCount - 1, Math.max(0, (beatStartMs - startMs) / OCCUPANCY_SAMPLE_MS));
            int lastSampleExclusive = Math.min(sampleCount, Math.max(firstSample + 1,
                    (int) Math.ceil((beatEndMs - startMs) / (double) OCCUPANCY_SAMPLE_MS)));
            int activeSamples = 0;
            int totalSamples = Math.max(1, lastSampleExclusive - firstSample);
            for (int sampleIndex = firstSample; sampleIndex < lastSampleExclusive; sampleIndex++) {
                if (sampleMax[sampleIndex] >= activeThreshold) {
                    activeSamples++;
                }
            }
            occupiedBeats[beatIndex] = ((double) activeSamples / (double) totalSamples) >= OCCUPANCY_REQUIRED_RATIO;
        }

        List<SignalWindow> windows = new ArrayList<>();
        int currentStartBeat = -1;
        for (int beatIndex = 0; beatIndex < beatCount; beatIndex++) {
            if (occupiedBeats[beatIndex]) {
                if (currentStartBeat < 0) {
                    currentStartBeat = beatIndex;
                }
                continue;
            }
            if (currentStartBeat >= 0) {
                windows.add(new SignalWindow(startMs + (int) Math.round(currentStartBeat * internalBeatMs),
                        startMs + (int) Math.round(beatIndex * internalBeatMs)));
                currentStartBeat = -1;
            }
        }
        if (currentStartBeat >= 0) {
            windows.add(new SignalWindow(startMs + (int) Math.round(currentStartBeat * internalBeatMs), endMs));
        }
        return windows;
    }

    private List<SignalWindow> detectSignalWindowsOnGrid(List<PitchDetector.PitchData> phraseFrames,
                                                         int startMs,
                                                         int endMs,
                                                         double windowThreshold,
                                                         double bpm) {
        double beatMs = 60000d / bpm;
        int stepMs = Math.max(MIN_GRID_STEP_MS, (int) Math.round(beatMs / 2d));
        int bucketCount = Math.max(1, (int) Math.ceil((endMs - startMs) / (double) stepMs));
        double[] bucketMax = new double[bucketCount];
        int[] bucketPitchSum = new int[bucketCount];
        int[] bucketPitchCount = new int[bucketCount];
        for (PitchDetector.PitchData frame : phraseFrames) {
            int frameMs = frameStartMs(frame);
            int bucketIndex = Math.min(bucketCount - 1, Math.max(0, (frameMs - startMs) / stepMs));
            bucketMax[bucketIndex] = Math.max(bucketMax[bucketIndex], frame.energy());
            if (frame.rawFrequency() > 0d) {
                bucketPitchSum[bucketIndex] += frame.pitch();
                bucketPitchCount[bucketIndex]++;
            }
        }
        List<SignalWindow> windows = new ArrayList<>();
        Integer windowStartMs = null;
        Integer windowStartBucket = null;
        Integer previousBucketIndex = null;
        for (int bucketIndex = 0; bucketIndex < bucketCount; bucketIndex++) {
            if (bucketMax[bucketIndex] < windowThreshold) {
                if (windowStartMs != null && previousBucketIndex != null && windowStartBucket != null) {
                    windows.addAll(splitWindowOnPitchJumps(windowStartMs,
                            windowStartBucket,
                            previousBucketIndex,
                            startMs,
                            endMs,
                            stepMs,
                            bucketPitchSum,
                            bucketPitchCount));
                    windowStartMs = null;
                    windowStartBucket = null;
                    previousBucketIndex = null;
                }
                continue;
            }
            int bucketStartMs = startMs + bucketIndex * stepMs;
            if (windowStartMs == null) {
                windowStartMs = bucketStartMs;
                windowStartBucket = bucketIndex;
            }
            previousBucketIndex = bucketIndex;
        }
        if (windowStartMs != null && previousBucketIndex != null && windowStartBucket != null) {
            windows.addAll(splitWindowOnPitchJumps(windowStartMs,
                    windowStartBucket,
                    previousBucketIndex,
                    startMs,
                    endMs,
                    stepMs,
                    bucketPitchSum,
                    bucketPitchCount));
        }
        return windows;
    }

    private List<SignalWindow> splitWindowOnPitchJumps(int windowStartMs,
                                                       int startBucketIndex,
                                                       int endBucketIndex,
                                                       int gridStartMs,
                                                       int endMs,
                                                       int stepMs,
                                                       int[] bucketPitchSum,
                                                       int[] bucketPitchCount) {
        List<SignalWindow> windows = new ArrayList<>();
        int currentStartMs = windowStartMs;
        int previousPitch = representativePitch(startBucketIndex, bucketPitchSum, bucketPitchCount);
        for (int bucketIndex = startBucketIndex + 1; bucketIndex <= endBucketIndex; bucketIndex++) {
            int currentPitch = representativePitch(bucketIndex, bucketPitchSum, bucketPitchCount);
            if (previousPitch != Integer.MIN_VALUE
                    && currentPitch != Integer.MIN_VALUE
                    && Math.abs(currentPitch - previousPitch) >= PITCH_SPLIT_THRESHOLD_SEMITONES) {
                int splitMs = gridStartMs + bucketIndex * stepMs;
                if (splitMs > currentStartMs) {
                    windows.add(new SignalWindow(currentStartMs, splitMs));
                    currentStartMs = splitMs;
                }
            }
            if (currentPitch != Integer.MIN_VALUE) {
                previousPitch = currentPitch;
            }
        }
        int finalEndMs = Math.min(endMs, gridStartMs + (endBucketIndex + 1) * stepMs);
        windows.add(new SignalWindow(currentStartMs, finalEndMs));
        return windows;
    }

    private int representativePitch(int bucketIndex, int[] bucketPitchSum, int[] bucketPitchCount) {
        if (bucketIndex < 0 || bucketIndex >= bucketPitchCount.length || bucketPitchCount[bucketIndex] <= 0) {
            return Integer.MIN_VALUE;
        }
        return Math.round((float) bucketPitchSum[bucketIndex] / (float) bucketPitchCount[bucketIndex]);
    }

    private int phraseStartMs(PhraseTiming timing, OpenAiTranscriptSegment segment) {
        if (timing.detectedStartMs() != null) {
            return timing.detectedStartMs();
        }
        return Math.max(0, timing.adjustedStartMs());
    }

    private int phraseEndMs(PhraseTiming timing, OpenAiTranscriptSegment segment, int refinedStartMs) {
        int fallbackEndMs = Math.max(refinedStartMs + 1,
                timing.adjustedEndMs() > 0 ? timing.adjustedEndMs() : phraseEndMs(segment));
        if (timing.detectedVocalEndMs() == null) {
            return fallbackEndMs;
        }
        return Math.max(refinedStartMs + 1, Math.min(fallbackEndMs, timing.detectedVocalEndMs()));
    }

    private List<OpenAiTranscriptWord> rebuildTopLevelWordsFromSegments(List<OpenAiTranscriptSegment> segments) {
        List<OpenAiTranscriptWord> words = new ArrayList<>();
        if (segments == null) {
            return words;
        }
        for (OpenAiTranscriptSegment segment : segments) {
            if (segment != null && segment.getWords() != null) {
                words.addAll(segment.getWords());
            }
        }
        return words;
    }

    private double clampRatio(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0d;
        }
        return Math.max(0d, Math.min(1d, value));
    }

    private List<Integer> distributeEvenly(int total, int parts) {
        List<Integer> distribution = new ArrayList<>(parts);
        int base = total / parts;
        int remainder = total % parts;
        for (int index = 0; index < parts; index++) {
            distribution.add(base + (index < remainder ? 1 : 0));
        }
        return distribution;
    }

    private List<Integer> distributeWordsByWindowLength(int totalWords, List<SignalWindow> signalWindows) {
        if (signalWindows == null || signalWindows.isEmpty()) {
            return List.of();
        }
        int windowCount = signalWindows.size();
        List<Integer> distribution = new ArrayList<>(windowCount);
        for (int index = 0; index < windowCount; index++) {
            distribution.add(1);
        }
        int remaining = totalWords - windowCount;
        if (remaining <= 0) {
            return distribution;
        }
        List<Integer> order = new ArrayList<>(windowCount);
        for (int index = 0; index < windowCount; index++) {
            order.add(index);
        }
        order.sort((left, right) -> {
            int rightLength = signalWindows.get(right).endMs() - signalWindows.get(right).startMs();
            int leftLength = signalWindows.get(left).endMs() - signalWindows.get(left).startMs();
            int compare = Integer.compare(rightLength, leftLength);
            if (compare != 0) {
                return compare;
            }
            return Integer.compare(left, right);
        });
        int orderCursor = 0;
        while (remaining-- > 0) {
            int targetIndex = order.get(orderCursor % order.size());
            distribution.set(targetIndex, distribution.get(targetIndex) + 1);
            orderCursor++;
        }
        return distribution;
    }

    private List<Integer> distributeWordsByWindowCapacity(int totalWords,
                                                          List<SignalWindow> signalWindows,
                                                          List<Integer> onsetAnchorsMs) {
        if (signalWindows == null || signalWindows.isEmpty()) {
            return List.of();
        }
        int windowCount = signalWindows.size();
        List<Integer> distribution = new ArrayList<>(windowCount);
        List<Integer> extraCapacity = new ArrayList<>(windowCount);
        for (int index = 0; index < windowCount; index++) {
            distribution.add(1);
            SignalWindow window = signalWindows.get(index);
            int capacity = countUsefulInternalOnsetAnchors(onsetAnchorsMs, window.startMs(), window.endMs());
            extraCapacity.add(capacity);
        }
        int remaining = totalWords - windowCount;
        if (remaining <= 0) {
            return distribution;
        }
        while (remaining-- > 0) {
            int targetIndex = findBestWindowForExtraWord(signalWindows, extraCapacity, distribution);
            distribution.set(targetIndex, distribution.get(targetIndex) + 1);
            if (extraCapacity.get(targetIndex) > 0) {
                extraCapacity.set(targetIndex, extraCapacity.get(targetIndex) - 1);
            }
        }
        return distribution;
    }

    private int countUsefulInternalOnsetAnchors(List<Integer> onsetAnchorsMs,
                                                int windowStartMs,
                                                int windowEndMs) {
        if (onsetAnchorsMs == null || onsetAnchorsMs.isEmpty()) {
            return 0;
        }
        int previousAcceptedMs = windowStartMs;
        int usefulCount = 0;
        for (Integer onsetMs : onsetAnchorsMs) {
            if (onsetMs == null || onsetMs <= windowStartMs || onsetMs >= windowEndMs) {
                continue;
            }
            if (onsetMs - previousAcceptedMs < MIN_ONSET_GAP_MS) {
                continue;
            }
            usefulCount++;
            previousAcceptedMs = onsetMs;
        }
        return usefulCount;
    }

    private int findBestWindowForExtraWord(List<SignalWindow> signalWindows,
                                           List<Integer> extraCapacity,
                                           List<Integer> currentDistribution) {
        int bestIndex = 0;
        for (int index = 1; index < signalWindows.size(); index++) {
            int bestCapacity = extraCapacity.get(bestIndex);
            int candidateCapacity = extraCapacity.get(index);
            if (candidateCapacity != bestCapacity) {
                if (candidateCapacity > bestCapacity) {
                    bestIndex = index;
                }
                continue;
            }
            int bestLength = signalWindows.get(bestIndex).endMs() - signalWindows.get(bestIndex).startMs();
            int candidateLength = signalWindows.get(index).endMs() - signalWindows.get(index).startMs();
            if (candidateLength != bestLength) {
                if (candidateLength > bestLength) {
                    bestIndex = index;
                }
                continue;
            }
            if (currentDistribution.get(index) < currentDistribution.get(bestIndex)) {
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private record VocalEndDetection(Integer endMs, String decision) {
    }

    private record SignalWindow(int startMs, int endMs) {
    }

    public record TimingRefinementAnalysis(boolean accepted,
                                           int currentGapMs,
                                           int firstTranscriptStartMs,
                                           int firstVocalOnsetMs,
                                           int gapDeltaMs,
                                           int transcriptToAudioOffsetMs,
                                           int proposedGapMs,
                                           String rejectionReason,
                                           List<PhraseTiming> phrases) {
        private static TimingRefinementAnalysis unavailable(int currentGapMs, String reason) {
            return new TimingRefinementAnalysis(false,
                    currentGapMs,
                    -1,
                    -1,
                    0,
                    0,
                    currentGapMs,
                    reason,
                    List.of());
        }
    }

    public record PhraseTiming(int index,
                               int transcriptStartMs,
                               int adjustedStartMs,
                               int adjustedEndMs,
                               Integer detectedStartMs,
                               Integer nextAnchorMs,
                               Integer detectedVocalEndMs,
                               List<Integer> onsetAnchorsMs,
                               List<Integer> rawOnsetAnchorsMs,
                               List<SignalWindow> signalWindows,
                               String text,
                               String decision) {
    }
}
