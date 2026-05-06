package yass.usdb;

import org.apache.commons.lang3.StringUtils;
import yass.I18;
import yass.YassActions;
import yass.YassSongList;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class UsdbImportQueueDialog extends JDialog {
    private static final String ARROW_UP = " \u25b2";
    private static final String ARROW_DOWN = " \u25bc";
    private static final Color MATCH_GREEN = new Color(0, 128, 0);
    private static final Color MATCH_YELLOW = new Color(0xFB, 0xBB, 0x2B);

    private final UsdbImportQueueService service;
    private final QueueTableModel tableModel = new QueueTableModel();
    private final JTable table = new JTable(tableModel);
    private final JLabel summaryLabel = new JLabel(" ");
    private final JTextArea generalArea = new JTextArea(10, 60);
    private final JTextArea detailArea = new JTextArea(12, 60);
    private final JButton cancelSongButton = new JButton(I18.get("usdb_queue_cancel_song"));
    private final JButton removeFinishedButton = new JButton(I18.get("usdb_queue_remove_finished"));
    private String selectedJobId;
    private int sortColumn = 1;
    private boolean sortAscending = true;

    public UsdbImportQueueDialog(Window owner, UsdbImportQueueService service) {
        super(owner, I18.get("lib_usdb_import_song"), ModalityType.MODELESS);
        this.service = service;
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        add(createHeaderPanel(), BorderLayout.NORTH);
        add(createCenterPanel(), BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);
        generalArea.setEditable(false);
        generalArea.setLineWrap(true);
        generalArea.setWrapStyleWord(true);
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> refreshSelection());
        table.setDefaultRenderer(Object.class, new QueueCellRenderer());
        table.setDefaultRenderer(Integer.class, new QueueCellRenderer());
        table.setRowHeight(22);
        table.getColumnModel().getColumn(0).setPreferredWidth(38);
        table.getColumnModel().getColumn(0).setMaxWidth(48);
        installHeaderSorting();
        updateColumnHeaders();
        installEscapeClose();
        setSize(980, 620);
        setLocationRelativeTo(owner);
        service.addListener(this::refresh);
        refresh();
    }

    private void installEscapeClose() {
        getRootPane().registerKeyboardAction(
                e -> setVisible(false),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(summaryLabel, BorderLayout.CENTER);
        return panel;
    }

    private JComponent createCenterPanel() {
        JScrollPane tableScroll = new JScrollPane(table);
        JScrollPane generalScroll = new JScrollPane(generalArea);
        generalScroll.setBorder(BorderFactory.createTitledBorder(I18.get("usdb_queue_general_status")));
        JScrollPane detailScroll = new JScrollPane(detailArea);
        detailScroll.setBorder(BorderFactory.createTitledBorder(I18.get("usdb_queue_song_status")));
        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, generalScroll, detailScroll);
        rightSplit.setResizeWeight(0.45);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, rightSplit);
        split.setResizeWeight(0.50);
        return split;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton(I18.get("edit_audio_separate_close"));
        closeButton.addActionListener(e -> setVisible(false));
        cancelSongButton.addActionListener(e -> service.cancelJob(getSelectedJob(), this));
        removeFinishedButton.addActionListener(e -> service.removeFinishedAndCancelledJobs());
        panel.add(cancelSongButton);
        panel.add(removeFinishedButton);
        panel.add(closeButton);
        return panel;
    }

    private void installHeaderSorting() {
        JTableHeader header = table.getTableHeader();
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewColumn = table.columnAtPoint(e.getPoint());
                if (viewColumn < 0) {
                    return;
                }
                if (sortColumn == viewColumn) {
                    sortAscending = !sortAscending;
                } else {
                    sortColumn = viewColumn;
                    sortAscending = true;
                }
                updateColumnHeaders();
                refresh();
            }
        });
    }

    private void updateColumnHeaders() {
        for (int i = 0; i < tableModel.getColumnCount(); i++) {
            String name = tableModel.getBaseColumnName(i);
            if (i == sortColumn) {
                name = name + (sortAscending ? ARROW_UP : ARROW_DOWN);
            }
            table.getColumnModel().getColumn(i).setHeaderValue(name);
        }
        table.getTableHeader().repaint();
    }

    private void refresh() {
        List<UsdbImportQueueJob> jobs = new ArrayList<>(service.snapshot());
        String preservedSelectionId = selectedJobId;
        jobs.sort(buildComparator());
        tableModel.setJobs(jobs);
        summaryLabel.setText(I18.get("usdb_queue_summary_prefix") + " " + jobs.size() + " | "
                + I18.get("usdb_queue_summary_active") + " " + jobs.stream()
                .filter(job -> job.getState() == UsdbImportQueueJob.State.IMPORTING
                        || job.getState() == UsdbImportQueueJob.State.FINALIZING
                        || job.getState() == UsdbImportQueueJob.State.SEPARATING
                        || job.getState() == UsdbImportQueueJob.State.WAITING_SEPARATION)
                .count());
        generalArea.setText(buildGeneralQueueLog(jobs));
        generalArea.setCaretPosition(generalArea.getDocument().getLength());
        restoreSelection(preservedSelectionId);
        if (!jobs.isEmpty() && table.getSelectedRow() < 0) {
            table.setRowSelectionInterval(0, 0);
        } else {
            refreshSelection();
        }
        removeFinishedButton.setEnabled(jobs.stream().anyMatch(UsdbImportQueueJob::isRemovable));
    }

    private Comparator<UsdbImportQueueJob> buildComparator() {
        Comparator<UsdbImportQueueJob> comparator = switch (sortColumn) {
            case 0 -> Comparator.comparingInt(this::matchRank);
            case 1 -> Comparator.comparing(job -> StringUtils.defaultString(job.getDisplayName()), String.CASE_INSENSITIVE_ORDER);
            case 2 -> Comparator.comparing(job -> StringUtils.defaultString(tableModel.modeText(job)), String.CASE_INSENSITIVE_ORDER);
            case 3 -> Comparator.comparing(job -> StringUtils.defaultString(tableModel.stateText(job)), String.CASE_INSENSITIVE_ORDER);
            case 4 -> Comparator.comparing(job -> StringUtils.defaultString(job.getCurrentSongStatus()), String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparingLong(UsdbImportQueueJob::getLastUpdatedAt);
        };
        if (!sortAscending) {
            comparator = comparator.reversed();
        }
        return Comparator.comparingInt((UsdbImportQueueJob job) -> job.isTerminal() ? 1 : 0)
                .thenComparing(comparator)
                .thenComparingLong(UsdbImportQueueJob::getLastUpdatedAt);
    }

    private void refreshSelection() {
        int row = table.getSelectedRow();
        if (row < 0) {
            detailArea.setText("");
            selectedJobId = null;
            cancelSongButton.setEnabled(false);
            return;
        }
        UsdbImportQueueJob job = tableModel.getJobAt(row);
        if (job == null) {
            detailArea.setText("");
            selectedJobId = null;
            cancelSongButton.setEnabled(false);
            return;
        }
        selectedJobId = job.getId();
        detailArea.setText(job.getDetailLog());
        detailArea.setCaretPosition(detailArea.getDocument().getLength());
        cancelSongButton.setEnabled(!job.isTerminal() && !job.isCancellationRequested());
    }

    private String buildGeneralQueueLog(List<UsdbImportQueueJob> jobs) {
        StringBuilder builder = new StringBuilder();
        for (UsdbImportQueueJob job : jobs) {
            String log = job.getGeneralLog();
            if (StringUtils.isBlank(log)) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(System.lineSeparator()).append(System.lineSeparator());
            }
            builder.append(job.getDisplayName()).append(System.lineSeparator());
            builder.append(log);
        }
        return builder.toString();
    }

    private void restoreSelection(String jobId) {
        if (jobId == null) {
            return;
        }
        int row = tableModel.indexOfJob(jobId);
        if (row >= 0) {
            table.getSelectionModel().setSelectionInterval(row, row);
        }
    }

    private UsdbImportQueueJob getSelectedJob() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return null;
        }
        return tableModel.getJobAt(row);
    }

    private MatchStatus getMatchStatus(UsdbImportQueueJob job) {
        if (job == null) {
            return MatchStatus.NONE;
        }
        YassActions actions = service.getActions();
        YassSongList songList = actions != null ? actions.getSongList() : null;
        if (songList != null && songList.hasSongWithUsdbInLibrary(job.getSummary().artist(), job.getSummary().title())) {
            return MatchStatus.EXACT;
        }
        if (songList != null && songList.hasSongInLibrary(job.getSummary().artist(), job.getSummary().title())) {
            return MatchStatus.TITLE_ARTIST;
        }
        return MatchStatus.NONE;
    }

    private int matchRank(UsdbImportQueueJob job) {
        return switch (getMatchStatus(job)) {
            case EXACT -> 2;
            case TITLE_ARTIST -> 1;
            case NONE -> 0;
        };
    }

    private enum MatchStatus {
        NONE,
        TITLE_ARTIST,
        EXACT
    }

    private final class QueueCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            setHorizontalAlignment(LEFT);
            UsdbImportQueueJob job = tableModel.getJobAt(row);
            if (column == 0) {
                MatchStatus matchStatus = getMatchStatus(job);
                setHorizontalAlignment(CENTER);
                setText(switch (matchStatus) {
                    case EXACT -> "\u25cf";
                    case TITLE_ARTIST -> "\u25cf";
                    case NONE -> "";
                });
                if (matchStatus == MatchStatus.EXACT) {
                    setForeground(MATCH_GREEN);
                } else if (matchStatus == MatchStatus.TITLE_ARTIST) {
                    setForeground(MATCH_YELLOW);
                }
                setToolTipText(switch (matchStatus) {
                    case EXACT -> I18.get("usdb_queue_match_exact");
                    case TITLE_ARTIST -> I18.get("usdb_queue_match_artist_title");
                    case NONE -> null;
                });
                return this;
            }
            if (column == 3 && job != null) {
                setText(tableModel.stateText(job));
                return this;
            }
            if (column == 2 && job != null) {
                setText(tableModel.modeText(job));
                return this;
            }
            setToolTipText(null);
            return this;
        }
    }

    private static final class QueueTableModel extends AbstractTableModel {
        private final String[] columnKeys = {
                "usdb_queue_col_match",
                "usdb_queue_col_song",
                "usdb_queue_col_mode",
                "usdb_queue_col_state",
                "usdb_queue_col_current"
        };
        private List<UsdbImportQueueJob> jobs = new ArrayList<>();

        @Override
        public int getRowCount() {
            return jobs.size();
        }

        @Override
        public int getColumnCount() {
            return columnKeys.length;
        }

        @Override
        public String getColumnName(int column) {
            return getBaseColumnName(column);
        }

        public String getBaseColumnName(int column) {
            return I18.get(columnKeys[column]);
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Integer.class : String.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            UsdbImportQueueJob job = jobs.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> 0;
                case 1 -> job.getDisplayName();
                case 2 -> modeText(job);
                case 3 -> stateText(job);
                case 4 -> job.getCurrentSongStatus();
                default -> "";
            };
        }

        public String modeText(UsdbImportQueueJob job) {
            return job.isSeparateAfterImport() ? I18.get("usdb_queue_mode_import_separate") : I18.get("usdb_queue_mode_import");
        }

        public String stateText(UsdbImportQueueJob job) {
            return switch (job.getState()) {
                case QUEUED -> I18.get("usdb_queue_state_queued");
                case IMPORTING -> I18.get("usdb_queue_state_importing");
                case WAITING_SEPARATION -> I18.get("usdb_queue_state_waiting_separation");
                case SEPARATING -> I18.get("usdb_queue_state_separating");
                case FINALIZING -> I18.get("usdb_queue_state_finalizing");
                case CANCELED -> I18.get("usdb_queue_state_canceled");
                case DONE -> I18.get("usdb_queue_state_done");
                case FAILED -> I18.get("usdb_queue_state_failed");
            };
        }

        public void setJobs(List<UsdbImportQueueJob> jobs) {
            this.jobs = new ArrayList<>(jobs);
            fireTableDataChanged();
        }

        public UsdbImportQueueJob getJobAt(int index) {
            if (index < 0 || index >= jobs.size()) {
                return null;
            }
            return jobs.get(index);
        }

        public int indexOfJob(String jobId) {
            for (int i = 0; i < jobs.size(); i++) {
                if (jobs.get(i).getId().equals(jobId)) {
                    return i;
                }
            }
            return -1;
        }
    }
}
