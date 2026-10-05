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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class SongTimingTagSanityService {
    public static final double UNKNOWN_AUDIO_DURATION_SECONDS = -1d;

    private static final int MEDLEY_END_GRACE_BEATS = 8;
    private static final double VIDEO_GAP_DURATION_GRACE_SECONDS = 30d;

    public enum Action {
        REMOVE,
        CORRECT,
        REPORT
    }

    public record Finding(UltrastarHeaderTag tag, String value, Action action, String reason) {
    }

    public List<Finding> validate(YassTable table, double audioDurationSeconds) {
        List<Finding> findings = new ArrayList<>();
        if (table == null) {
            return findings;
        }

        boolean hasDuration = isKnownDuration(audioDurationSeconds);
        OptionalInt firstNoteBeat = firstNoteBeat(table);
        OptionalInt lastNoteEndBeat = lastNoteEndBeat(table);

        validateGap(table, findings);
        validateStart(table, audioDurationSeconds, hasDuration, firstNoteBeat, findings);
        validateEnd(table, audioDurationSeconds, hasDuration, findings);
        validateStartEndOrder(table, findings);
        validatePreviewStart(table, audioDurationSeconds, hasDuration, findings);
        validateNegativeNoteBeats(table, findings);
        validateMedley(table, firstNoteBeat, lastNoteEndBeat, findings);
        validateVideoGap(table, audioDurationSeconds, hasDuration, findings);

        return findings;
    }

    public List<Finding> cleanup(YassTable table, double audioDurationSeconds) {
        List<Finding> findings = validate(table, audioDurationSeconds);
        boolean changed = false;

        for (Finding finding : findings) {
            if (finding.action() == Action.REPORT) {
                continue;
            }
            switch (finding.tag()) {
                case GAP -> {
                    table.setGap(0);
                    changed = true;
                }
                case START -> {
                    table.setStart(0);
                    changed = true;
                }
                case END -> {
                    table.setEnd(-1);
                    changed = true;
                }
                case PREVIEWSTART -> changed |= removeTag(table, UltrastarHeaderTag.PREVIEWSTART);
                case VIDEOGAP -> {
                    table.setVideoGap(0);
                    changed = true;
                }
                case MEDLEYSTARTBEAT, MEDLEYENDBEAT -> {
                    changed |= removeTag(table, UltrastarHeaderTag.MEDLEYSTARTBEAT);
                    changed |= removeTag(table, UltrastarHeaderTag.MEDLEYENDBEAT);
                }
                default -> {
                    // Only timing tags are produced by this service.
                }
            }
        }

        if (changed) {
            table.setSaved(false);
        }
        return findings;
    }

    private void validateGap(YassTable table, List<Finding> findings) {
        YassRow row = row(table, UltrastarHeaderTag.GAP);
        if (row == null) {
            return;
        }
        OptionalDouble gap = parseDouble(row);
        if (gap.isEmpty() || gap.getAsDouble() < 0d) {
            findings.add(new Finding(UltrastarHeaderTag.GAP, row.getHeaderComment(), Action.CORRECT,
                    "GAP must be greater than or equal to 0"));
        }
    }

    private void validateStart(YassTable table,
                               double audioDurationSeconds,
                               boolean hasDuration,
                               OptionalInt firstNoteBeat,
                               List<Finding> findings) {
        YassRow row = row(table, UltrastarHeaderTag.START);
        if (row == null) {
            return;
        }
        OptionalDouble start = parseDouble(row);
        if (start.isEmpty() || start.getAsDouble() < 0d) {
            findings.add(new Finding(UltrastarHeaderTag.START, row.getHeaderComment(), Action.REMOVE,
                    "START must be greater than or equal to 0"));
            return;
        }
        if (hasDuration && start.getAsDouble() > audioDurationSeconds) {
            findings.add(new Finding(UltrastarHeaderTag.START, row.getHeaderComment(), Action.REMOVE,
                    "START must not exceed audio duration"));
            return;
        }
        if (firstNoteBeat.isPresent()) {
            double firstNoteStartMs = table.beatToMs(firstNoteBeat.getAsInt());
            double startMs = start.getAsDouble() * 1000d;
            if (startMs >= firstNoteStartMs) {
                findings.add(new Finding(UltrastarHeaderTag.START, row.getHeaderComment(), Action.REMOVE,
                        "START must be before the first singable note"));
            }
        }
    }

    private void validateEnd(YassTable table,
                             double audioDurationSeconds,
                             boolean hasDuration,
                             List<Finding> findings) {
        YassRow row = row(table, UltrastarHeaderTag.END);
        if (row == null) {
            return;
        }
        OptionalDouble end = parseEndSeconds(row);
        if (end.isEmpty() || end.getAsDouble() < 0d) {
            findings.add(new Finding(UltrastarHeaderTag.END, row.getHeaderComment(), Action.REMOVE,
                    "END must be greater than or equal to 0"));
            return;
        }
        if (hasDuration && end.getAsDouble() > audioDurationSeconds) {
            findings.add(new Finding(UltrastarHeaderTag.END, row.getHeaderComment(), Action.REMOVE,
                    "END must not exceed audio duration"));
        }
    }

    private void validateStartEndOrder(YassTable table, List<Finding> findings) {
        YassRow startRow = row(table, UltrastarHeaderTag.START);
        YassRow endRow = row(table, UltrastarHeaderTag.END);
        if (startRow == null || endRow == null || hasFinding(findings, UltrastarHeaderTag.END)) {
            return;
        }
        OptionalDouble start = parseDouble(startRow);
        OptionalDouble end = parseEndSeconds(endRow);
        if (start.isEmpty() || end.isEmpty()) {
            return;
        }
        if (start.getAsDouble() > 0d && end.getAsDouble() > 0d && end.getAsDouble() <= start.getAsDouble()) {
            findings.add(new Finding(UltrastarHeaderTag.END, endRow.getHeaderComment(), Action.REMOVE,
                    "END must be after START"));
        }
    }

    private void validatePreviewStart(YassTable table,
                                      double audioDurationSeconds,
                                      boolean hasDuration,
                                      List<Finding> findings) {
        YassRow row = row(table, UltrastarHeaderTag.PREVIEWSTART);
        if (row == null) {
            return;
        }
        OptionalDouble previewStart = parseDouble(row);
        if (previewStart.isEmpty() || previewStart.getAsDouble() < 0d) {
            findings.add(new Finding(UltrastarHeaderTag.PREVIEWSTART, row.getHeaderComment(), Action.REMOVE,
                    "PREVIEWSTART must be greater than or equal to 0"));
            return;
        }
        if (hasDuration && previewStart.getAsDouble() > audioDurationSeconds) {
            findings.add(new Finding(UltrastarHeaderTag.PREVIEWSTART, row.getHeaderComment(), Action.REMOVE,
                    "PREVIEWSTART must not exceed audio duration"));
        }
    }

    private void validateNegativeNoteBeats(YassTable table, List<Finding> findings) {
        YassRow gapRow = row(table, UltrastarHeaderTag.GAP);
        String gapValue = gapRow != null ? gapRow.getHeaderComment() : Double.toString(table.getGap());
        for (int i = 0; i < table.getRowCount(); i++) {
            YassRow row = table.getRowAt(i);
            if (row == null || !row.isNote() || row.getBeatInt() >= 0) {
                continue;
            }
            if (table.beatToMs(row.getBeatInt()) < 0d) {
                findings.add(new Finding(UltrastarHeaderTag.GAP, gapValue, Action.REPORT,
                        String.format(Locale.US,
                                "Negative note beat %d starts before audio with the configured GAP",
                                row.getBeatInt())));
                return;
            }
        }
    }

    private void validateMedley(YassTable table,
                                OptionalInt firstNoteBeat,
                                OptionalInt lastNoteEndBeat,
                                List<Finding> findings) {
        YassRow startRow = row(table, UltrastarHeaderTag.MEDLEYSTARTBEAT);
        YassRow endRow = row(table, UltrastarHeaderTag.MEDLEYENDBEAT);
        if (startRow == null && endRow == null) {
            return;
        }

        OptionalInt medleyStart = parseInt(startRow);
        OptionalInt medleyEnd = parseInt(endRow);
        boolean invalid = false;
        if (startRow != null && medleyStart.isEmpty()) {
            invalid = true;
        }
        if (endRow != null && medleyEnd.isEmpty()) {
            invalid = true;
        }
        if (medleyStart.isPresent()) {
            int minStart = Math.min(firstNoteBeat.orElse(0), 0);
            invalid |= medleyStart.getAsInt() < minStart;
        }
        if (medleyStart.isPresent() && medleyEnd.isPresent()) {
            invalid |= medleyEnd.getAsInt() <= medleyStart.getAsInt();
        }
        if (medleyEnd.isPresent() && lastNoteEndBeat.isPresent()) {
            invalid |= medleyEnd.getAsInt() > lastNoteEndBeat.getAsInt() + MEDLEY_END_GRACE_BEATS;
        }

        if (invalid) {
            if (startRow != null) {
                findings.add(new Finding(UltrastarHeaderTag.MEDLEYSTARTBEAT, startRow.getHeaderComment(),
                        Action.REMOVE, "Medley timing is outside the song bounds"));
            }
            if (endRow != null) {
                findings.add(new Finding(UltrastarHeaderTag.MEDLEYENDBEAT, endRow.getHeaderComment(),
                        Action.REMOVE, "Medley timing is outside the song bounds"));
            }
        }
    }

    private void validateVideoGap(YassTable table,
                                  double audioDurationSeconds,
                                  boolean hasDuration,
                                  List<Finding> findings) {
        YassRow row = row(table, UltrastarHeaderTag.VIDEOGAP);
        if (row == null) {
            return;
        }
        OptionalDouble videoGap = parseDouble(row);
        if (videoGap.isEmpty()) {
            findings.add(new Finding(UltrastarHeaderTag.VIDEOGAP, row.getHeaderComment(), Action.REMOVE,
                    "VIDEOGAP must be numeric"));
            return;
        }
        if (hasDuration && Math.abs(videoGap.getAsDouble()) > audioDurationSeconds + VIDEO_GAP_DURATION_GRACE_SECONDS) {
            findings.add(new Finding(UltrastarHeaderTag.VIDEOGAP, row.getHeaderComment(), Action.REMOVE,
                    "VIDEOGAP is implausibly large relative to audio duration"));
        }
    }

    private boolean hasFinding(List<Finding> findings, UltrastarHeaderTag tag) {
        for (Finding finding : findings) {
            if (finding.tag() == tag) {
                return true;
            }
        }
        return false;
    }

    private OptionalInt firstNoteBeat(YassTable table) {
        for (int i = 0; i < table.getRowCount(); i++) {
            YassRow row = table.getRowAt(i);
            if (row != null && row.isNote()) {
                return OptionalInt.of(row.getBeatInt());
            }
        }
        return OptionalInt.empty();
    }

    private OptionalInt lastNoteEndBeat(YassTable table) {
        OptionalInt endBeat = OptionalInt.empty();
        for (int i = 0; i < table.getRowCount(); i++) {
            YassRow row = table.getRowAt(i);
            if (row != null && row.isNote()) {
                endBeat = OptionalInt.of(row.getBeatInt() + row.getLengthInt());
            }
        }
        return endBeat;
    }

    private OptionalDouble parseEndSeconds(YassRow row) {
        OptionalDouble endMillis = parseDouble(row);
        return endMillis.isPresent()
                ? OptionalDouble.of(endMillis.getAsDouble() / 1000d)
                : OptionalDouble.empty();
    }

    private OptionalDouble parseDouble(YassRow row) {
        if (row == null) {
            return OptionalDouble.empty();
        }
        try {
            return OptionalDouble.of(Double.parseDouble(row.getHeaderComment().replace(',', '.')));
        } catch (RuntimeException ignored) {
            return OptionalDouble.empty();
        }
    }

    private OptionalInt parseInt(YassRow row) {
        if (row == null) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(row.getHeaderComment().trim()));
        } catch (RuntimeException ignored) {
            return OptionalInt.empty();
        }
    }

    private YassRow row(YassTable table, UltrastarHeaderTag tag) {
        return table.getCommentRow(tag.getTagName());
    }

    private boolean removeTag(YassTable table, UltrastarHeaderTag tag) {
        YassRow row = row(table, tag);
        if (row == null) {
            return false;
        }
        boolean removed = table.getModelData().remove(row);
        if (removed && table.getModel() instanceof YassTableModel model) {
            model.fireTableDataChanged();
        }
        return removed;
    }

    private boolean isKnownDuration(double audioDurationSeconds) {
        return Double.isFinite(audioDurationSeconds) && audioDurationSeconds > 0d;
    }
}
