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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * Pure helper for the song-local global pitch-shift correction, stored as a
 * pseudo tag inside {@code #COMMENT} (for example {@code key=Am,pitchShiftCents=-18.0}).
 * <p>
 * The {@code #COMMENT} value is a comma-separated list of {@code key=value} pairs.
 * This class reads, writes, and removes only the {@code pitchShiftCents} pair while
 * leaving every other pair (such as {@code key=}) untouched. It mirrors the grammar
 * used by {@code YassTable.getKeyFromComment}/{@code setKeyInComment} so there is one
 * shared notion of how comment properties are encoded.
 * <p>
 * The class is intentionally free of UI and I/O so the comment-key handling is
 * tested in isolation and not duplicated across actions, dialogs, and the model.
 */
public final class PitchShiftMetadata {

    /** The comment pseudo-tag key under which the correction is stored. */
    public static final String COMMENT_KEY = "pitchShiftCents";

    /** 100 cents = 1 semitone. */
    public static final double CENTS_PER_SEMITONE = 100.0;

    /**
     * Offsets with an absolute value below this threshold are treated as
     * "no correction recommended": generally not meaningfully audible, and not
     * worth the UI noise or metadata churn. This gates recommendation and
     * automatic application only; an explicit manual value may still be stored.
     */
    public static final double RECOMMENDATION_THRESHOLD_CENTS = 5.0;

    /**
     * Minimum circular concentration (resultant length R, in [0,1]) for a tuning-offset estimate
     * to be treated as reliable. R near 1 means the analyzed frames agree on a single tuning; R
     * near 0 means the offsets are scattered with no dominant tuning (noise, or relative/drifting
     * detuning that no single global shift can fix), and the UI should flag it as low confidence.
     * <p>
     * Empirically, separated AC/DC stems sit around R=0.1-0.22; a cleanly tuned source lands far
     * higher. Mirrors {@code PitchDetector.MIN_TUNING_CONCENTRATION}.
     */
    public static final double CONFIDENCE_CONCENTRATION_THRESHOLD = 0.25;

    private PitchShiftMetadata() {
    }

    /**
     * Reads the {@code pitchShiftCents} value from a {@code #COMMENT} value.
     *
     * @param comment the raw {@code #COMMENT} value, may be {@code null}
     * @return the stored cents value, or empty if the key is absent or unparseable
     */
    public static OptionalDouble parse(String comment) {
        if (comment == null || comment.isBlank()) {
            return OptionalDouble.empty();
        }
        for (String part : comment.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(COMMENT_KEY + "=")) {
                String raw = trimmed.substring(COMMENT_KEY.length() + 1).trim();
                try {
                    return OptionalDouble.of(Double.parseDouble(raw));
                } catch (NumberFormatException ex) {
                    return OptionalDouble.empty();
                }
            }
        }
        return OptionalDouble.empty();
    }

    /**
     * Returns a {@code #COMMENT} value with {@code pitchShiftCents} set to {@code cents},
     * preserving all other comment properties. If the key already exists it is replaced
     * in place; otherwise it is appended.
     *
     * @param comment the existing {@code #COMMENT} value, may be {@code null}
     * @param cents   the cents value to store
     * @return the updated comment value
     */
    public static String upsert(String comment, double cents) {
        String formatted = COMMENT_KEY + "=" + formatCents(cents);
        if (comment == null || comment.isBlank()) {
            return formatted;
        }
        List<String> parts = new ArrayList<>();
        boolean replaced = false;
        for (String part : comment.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith(COMMENT_KEY + "=")) {
                parts.add(formatted);
                replaced = true;
            } else {
                parts.add(trimmed);
            }
        }
        if (!replaced) {
            parts.add(formatted);
        }
        return String.join(",", parts);
    }

    /**
     * Returns a {@code #COMMENT} value with the {@code pitchShiftCents} property removed,
     * preserving all other comment properties. Returns the input unchanged (modulo
     * trimming of empty segments) when the key is absent.
     *
     * @param comment the existing {@code #COMMENT} value, may be {@code null}
     * @return the comment value without the pitch-shift property, or {@code null} if the
     * input was {@code null}
     */
    public static String remove(String comment) {
        if (comment == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String part : comment.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty() || trimmed.startsWith(COMMENT_KEY + "=")) {
                continue;
            }
            parts.add(trimmed);
        }
        return String.join(",", parts);
    }

    /**
     * Converts cents to semitones (100 cents = 1 semitone).
     */
    public static double centsToSemitones(double cents) {
        return cents / CENTS_PER_SEMITONE;
    }

    /**
     * Converts semitones to cents (1 semitone = 100 cents).
     */
    public static double semitonesToCents(double semitones) {
        return semitones * CENTS_PER_SEMITONE;
    }

    /**
     * Whether a detected offset is large enough to recommend or auto-apply a correction.
     * Offsets below {@link #RECOMMENDATION_THRESHOLD_CENTS} are treated as no correction.
     */
    public static boolean isCorrectionRecommended(double offsetCents) {
        return Math.abs(offsetCents) >= RECOMMENDATION_THRESHOLD_CENTS;
    }

    /**
     * Whether a tuning-offset estimate with the given circular concentration is reliable enough to
     * present without a low-confidence warning.
     *
     * @param concentration the resultant length R reported by the tuning analysis
     * @return {@code true} when R is at or above {@link #CONFIDENCE_CONCENTRATION_THRESHOLD}
     */
    public static boolean isEstimateConfident(double concentration) {
        return concentration >= CONFIDENCE_CONCENTRATION_THRESHOLD;
    }

    /**
     * Formats a cents value for storage, e.g. {@code -18.0}. Uses {@link Locale#ROOT}
     * so the decimal separator is always a dot regardless of the user's locale.
     */
    static String formatCents(double cents) {
        return String.format(Locale.ROOT, "%.1f", cents);
    }
}
