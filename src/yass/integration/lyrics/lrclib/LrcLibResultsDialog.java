package yass.integration.lyrics.lrclib;

import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class LrcLibResultsDialog extends JDialog {
    private final JTable table;
    private final List<LrcLibCandidate> candidates;
    private Result result;

    public LrcLibResultsDialog(Window owner,
                               String title,
                               String applyLabel,
                               String compareLabel,
                               boolean compareEnabled,
                               List<LrcLibCandidate> candidates,
                               Long preferredCandidateId) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.candidates = candidates;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        setSize(760, 360);
        setLocationRelativeTo(owner);

        table = new JTable(new CandidateTableModel(candidates));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(260);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setPreferredWidth(70);
        DefaultTableCellRenderer centeredRenderer = new DefaultTableCellRenderer();
        centeredRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(2).setCellRenderer(centeredRenderer);
        table.getColumnModel().getColumn(3).setCellRenderer(centeredRenderer);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 2) {
                    applySelection(false);
                }
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);

        JButton applyButton = new JButton(applyLabel);
        applyButton.addActionListener(e -> applySelection(false));
        JButton compareButton = new JButton(compareLabel);
        compareButton.setEnabled(compareEnabled);
        compareButton.addActionListener(e -> applySelection(true));
        JButton cancelButton = new JButton(UIManager.getString("OptionPane.cancelButtonText"));
        cancelButton.addActionListener(e -> {
            result = null;
            dispose();
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(compareButton);
        buttonPanel.add(applyButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(applyButton);

        preselect(preferredCandidateId);
    }

    public Result showDialog() {
        setVisible(true);
        return result;
    }

    private void preselect(Long preferredCandidateId) {
        if (candidates.isEmpty()) {
            return;
        }
        int preferredModelIndex = 0;
        if (preferredCandidateId != null) {
            for (int i = 0; i < candidates.size(); i++) {
                if (candidates.get(i).getId() == preferredCandidateId.longValue()) {
                    preferredModelIndex = i;
                    break;
                }
            }
        }
        int preferredViewIndex = table.convertRowIndexToView(preferredModelIndex);
        table.setRowSelectionInterval(preferredViewIndex, preferredViewIndex);
        table.scrollRectToVisible(table.getCellRect(preferredViewIndex, 0, true));
    }

    private void applySelection(boolean compareWithTranscript) {
        int selectedViewRow = table.getSelectedRow();
        if (selectedViewRow < 0) {
            return;
        }
        int selectedModelRow = table.convertRowIndexToModel(selectedViewRow);
        result = new Result(candidates.get(selectedModelRow), compareWithTranscript);
        dispose();
    }

    public record Result(LrcLibCandidate candidate, boolean compareWithTranscript) {
    }

    private static final class CandidateTableModel extends AbstractTableModel {
        private final List<LrcLibCandidate> candidates;
        private final String[] columns = new String[]{"Track", "Album", "Duration", "Synced"};

        private CandidateTableModel(List<LrcLibCandidate> candidates) {
            this.candidates = candidates;
        }

        @Override
        public int getRowCount() {
            return candidates.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            LrcLibCandidate candidate = candidates.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> StringUtils.defaultIfBlank(candidate.getArtistName(), "")
                        + (StringUtils.isNotBlank(candidate.getTrackName()) ? " - " + candidate.getTrackName() : "");
                case 1 -> candidate.getAlbumName();
                case 2 -> formatDuration(candidate.getDurationSeconds());
                case 3 -> candidate.hasSyncedLyrics() ? "Yes" : "No";
                default -> "";
            };
        }

        private String formatDuration(int durationSeconds) {
            int minutes = durationSeconds / 60;
            int seconds = durationSeconds % 60;
            return String.format("%d:%02d", minutes, seconds);
        }
    }
}
