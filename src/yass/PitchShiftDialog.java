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

package yass;

import yass.analysis.PitchDetector.TuningOffsetAnalysis;
import yass.analysis.PitchShiftMetadata;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.Serial;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Modal dialog for the song-local global pitch-shift correction
 * ({@code Extras > Pitch Shift...}). It lets the user inspect any stored
 * correction, explicitly analyze the currently selected audio track for a tuning
 * offset, and apply the recommended correction or a manual cents value. The value
 * is persisted to the song's {@code #COMMENT} via
 * {@link YassTable#setPitchShiftCents(double)}.
 * <p>
 * Analysis works on whichever audio track is loaded, not only {@code #VOCALS}: on
 * a stem-separated song the isolated vocal is often re-tuned toward the grid by the
 * separation model, so the full mix can carry the true offset better. When the
 * estimate's spread (MAD) is high, the detected value is shown with a
 * low-confidence note.
 * <p>
 * This is the visual/metadata feature only; it does not render corrected audio.
 * <p>
 * Detection is injected as an {@code analysisSupplier} so the dialog stays free
 * of audio plumbing and the analysis runs off the EDT. The supplier may return a
 * {@code TuningOffsetAnalysis} (available or unavailable) or {@code null} on
 * failure.
 */
public class PitchShiftDialog extends JDialog {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient YassTable table;
    private final transient Callable<TuningOffsetAnalysis> analysisSupplier;
    private final boolean analysisAvailable;
    private final transient Consumer<YassTable> onChanged;
    private final transient Runnable renderAction;
    private final boolean renderBackendAvailable;

    private final JLabel storedLabel = new JLabel();
    private final JLabel detectedLabel = new JLabel();
    private final JLabel recommendationLabel = new JLabel();
    private final JTextField manualField = new JTextField(8);
    private final JButton analyzeButton = new JButton(I18.get("pitch_shift_analyze"));
    private final JButton applyRecommendedButton = new JButton(I18.get("pitch_shift_apply_recommended"));
    private final JButton applyManualButton = new JButton(I18.get("pitch_shift_apply_manual"));
    private final JButton removeButton = new JButton(I18.get("pitch_shift_remove"));
    private final JButton renderButton = new JButton(I18.get("pitch_shift_render"));

    private double recommendedCorrectionCents;

    /**
     * @param owner             dialog owner window
     * @param table             the current song; correction is read from and written to its {@code #COMMENT}
     * @param analysisSupplier  runs tuning-offset analysis off the EDT; may return an unavailable result or {@code null}
     * @param analysisAvailable whether analysis is possible right now (Aubio configured and vocal audio selected)
     * @param unavailableReason tooltip explaining why analysis is disabled, shown when {@code analysisAvailable} is false
     * @param onChanged         invoked after the stored correction changes, so the editor can mark unsaved and repaint
     * @param renderAction      runs the offline audio bake (slice 4b); {@code null} disables the render button
     * @param renderBackendAvailable whether FFmpeg is available to render shifted audio
     */
    public PitchShiftDialog(Window owner,
                            YassTable table,
                            Callable<TuningOffsetAnalysis> analysisSupplier,
                            boolean analysisAvailable,
                            String unavailableReason,
                            Consumer<YassTable> onChanged,
                            Runnable renderAction,
                            boolean renderBackendAvailable) {
        super(owner, I18.get("pitch_shift_title"), ModalityType.APPLICATION_MODAL);
        this.table = table;
        this.analysisSupplier = analysisSupplier;
        this.analysisAvailable = analysisAvailable;
        this.onChanged = onChanged;
        this.renderAction = renderAction;
        this.renderBackendAvailable = renderBackendAvailable;

        analyzeButton.setEnabled(analysisAvailable);
        if (!analysisAvailable && unavailableReason != null && !unavailableReason.isBlank()) {
            analyzeButton.setToolTipText(unavailableReason);
        }

        add(buildContent(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        analyzeButton.addActionListener(e -> analyze());
        applyRecommendedButton.addActionListener(e -> applyCents(recommendedCorrectionCents));
        applyManualButton.addActionListener(e -> applyManual());
        removeButton.addActionListener(e -> removeCorrection());
        renderButton.addActionListener(e -> runRender());

        refreshStored();
        clearDetected();

        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel buildContent() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 8, 6, 8);
        c.anchor = GridBagConstraints.LINE_START;

        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel(I18.get("pitch_shift_stored")), c);
        c.gridx = 1;
        panel.add(storedLabel, c);

        c.gridx = 0;
        c.gridy = 1;
        panel.add(new JLabel(I18.get("pitch_shift_detected")), c);
        c.gridx = 1;
        panel.add(detectedLabel, c);

        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        panel.add(recommendationLabel, c);
        c.gridwidth = 1;

        c.gridx = 0;
        c.gridy = 3;
        panel.add(new JLabel(I18.get("pitch_shift_manual")), c);
        c.gridx = 1;
        panel.add(manualField, c);

        return panel;
    }

    private JPanel buildButtons() {
        JPanel buttons = new JPanel();
        buttons.add(analyzeButton);
        buttons.add(applyRecommendedButton);
        buttons.add(applyManualButton);
        buttons.add(removeButton);
        buttons.add(renderButton);
        JButton close = new JButton(I18.get("pitch_shift_close"));
        close.addActionListener(e -> dispose());
        buttons.add(close);
        return buttons;
    }

    private void refreshStored() {
        OptionalDouble stored = table == null ? OptionalDouble.empty() : table.getPitchShiftCents();
        boolean hasCorrection = stored.isPresent() && stored.getAsDouble() != 0.0;
        if (stored.isPresent()) {
            storedLabel.setText(formatCentsAndSemitones(stored.getAsDouble()));
            removeButton.setEnabled(true);
        } else {
            storedLabel.setText(I18.get("pitch_shift_none"));
            removeButton.setEnabled(false);
        }
        // Rendering bakes a stored correction into the audio files; it needs both a non-zero
        // correction and an available FFmpeg backend. Explain a disabled button via tooltip
        // rather than hiding it.
        boolean canRender = renderAction != null && renderBackendAvailable && hasCorrection;
        renderButton.setEnabled(canRender);
        if (!renderBackendAvailable) {
            renderButton.setToolTipText(I18.get("pitch_shift_render_unavailable_backend"));
        } else if (!hasCorrection) {
            renderButton.setToolTipText(I18.get("pitch_shift_render_unavailable_no_correction"));
        } else {
            renderButton.setToolTipText(null);
        }
    }

    private void runRender() {
        if (renderAction == null) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this, I18.get("pitch_shift_render_confirm"),
                                                   I18.get("pitch_shift_title"), JOptionPane.OK_CANCEL_OPTION,
                                                   JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        // The action handles its own off-EDT work, progress, and result messaging; the dialog
        // closes so the editor can reflect the new audio tags and cleared correction.
        renderAction.run();
        dispose();
    }

    private void clearDetected() {
        detectedLabel.setText(I18.get("pitch_shift_not_analyzed"));
        recommendationLabel.setText(" ");
        applyRecommendedButton.setEnabled(false);
    }

    private void analyze() {
        analyzeButton.setEnabled(false);
        detectedLabel.setText(I18.get("pitch_shift_analyzing"));
        recommendationLabel.setText(" ");
        new SwingWorker<TuningOffsetAnalysis, Void>() {
            @Override
            protected TuningOffsetAnalysis doInBackground() throws Exception {
                return analysisSupplier == null ? null : analysisSupplier.call();
            }

            @Override
            protected void done() {
                analyzeButton.setEnabled(analysisAvailable);
                TuningOffsetAnalysis analysis;
                try {
                    analysis = get();
                } catch (Exception ex) {
                    analysis = null;
                }
                showAnalysis(analysis);
            }
        }.execute();
    }

    private void showAnalysis(TuningOffsetAnalysis analysis) {
        applyRecommendedButton.setEnabled(false);
        if (analysis == null || !analysis.available()) {
            detectedLabel.setText(I18.get("pitch_shift_analysis_failed"));
            recommendationLabel.setText(analysis == null ? " " : reasonOrBlank(analysis.reason()));
            return;
        }
        double offset = analysis.estimatedOffsetCents();
        boolean confident = PitchShiftMetadata.isEstimateConfident(analysis.concentration());
        detectedLabel.setText(formatCentsAndSemitones(offset)
                + (confident ? "" : "  " + I18.get("pitch_shift_low_confidence")));
        if (PitchShiftMetadata.isCorrectionRecommended(offset)) {
            recommendedCorrectionCents = analysis.suggestedCorrectionCents();
            applyRecommendedButton.setEnabled(true);
            recommendationLabel.setText(I18.get("pitch_shift_recommendation") + " "
                    + formatCentsAndSemitones(recommendedCorrectionCents));
        } else {
            recommendationLabel.setText(I18.get("pitch_shift_no_correction"));
        }
    }

    private void applyManual() {
        String raw = manualField.getText() == null ? "" : manualField.getText().trim().replace(',', '.');
        if (raw.isEmpty()) {
            return;
        }
        double cents;
        try {
            cents = Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, I18.get("pitch_shift_invalid_number"),
                                          I18.get("pitch_shift_title"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        applyCents(cents);
    }

    private void applyCents(double cents) {
        if (table == null) {
            return;
        }
        table.setPitchShiftCents(cents);
        refreshStored();
        notifyChanged();
    }

    private void removeCorrection() {
        if (table == null) {
            return;
        }
        table.removePitchShiftCents();
        refreshStored();
        notifyChanged();
    }

    private void notifyChanged() {
        if (onChanged != null) {
            onChanged.accept(table);
        }
    }

    private static String reasonOrBlank(String reason) {
        return reason == null || reason.isBlank() ? " " : reason;
    }

    /**
     * Formats a cents value with its semitone equivalent, e.g. {@code +18.0 cents (+0.18 semitones)}.
     */
    static String formatCentsAndSemitones(double cents) {
        double semitones = PitchShiftMetadata.centsToSemitones(cents);
        return String.format(Locale.ROOT, "%+.1f %s (%+.2f %s)",
                             cents, I18.get("pitch_shift_unit_cents"),
                             semitones, I18.get("pitch_shift_unit_semitones"));
    }
}
