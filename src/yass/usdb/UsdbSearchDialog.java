package yass.usdb;

import org.apache.commons.lang3.StringUtils;
import yass.I18;
import yass.YassActions;
import yass.YassSongList;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UsdbSearchDialog extends JDialog {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final Color MATCH_GREEN = new Color(0, 128, 0);
    private static final Color MATCH_YELLOW = new Color(0xFB, 0xBB, 0x2B);
    private static final Color MATCH_RED = new Color(0xD6, 0x33, 0x33);

    private final YassActions actions;
    private final CompareContext compareContext;
    private final JTextField queryField = new JTextField(40);
    private final JLabel statusLabel = new JLabel(" ");
    private final JTextArea detailsArea = new JTextArea(8, 30);
    private final JCheckBox separateAfterImportCheck = new JCheckBox("Audio separation after import");
    private final SearchTableModel tableModel = new SearchTableModel();
    private final JTable resultTable = new JTable(tableModel);
    private final JButton importButton = new JButton(I18.get("lib_usdb_import_song"));
    private final JButton compareButton = new JButton(I18.get("usdb_compare_search_compare_current"));
    private final JButton createMetaTagsButton = new JButton(I18.get("lib_syncer_tags"));
    private final JButton addSongButton = new JButton(I18.get("usdb_compare_search_add_new_song"));
    private final JPopupMenu contextMenu = new JPopupMenu();
    private final JMenuItem contextImportItem = new JMenuItem(I18.get("lib_usdb_import_song"));
    private final JMenuItem contextCompareUsdbItem = new JMenuItem(I18.get("usdb_edit_compare"));
    private final JMenuItem contextOpenUsdbItem = new JMenuItem(I18.get("lib_visit_usdb"));
    private final JMenuItem contextCancelImportItem = new JMenuItem(I18.get("usdb_search_cancel_import"));
    private final UsdbImportQueueService.Listener queueListener = this::onQueueChanged;
    private List<UsdbSongSummary> currentResults = List.of();

    public UsdbSearchDialog(YassActions actions) {
        this(actions, null);
    }

    public UsdbSearchDialog(YassActions actions, CompareContext compareContext) {
        super(actions.createOwnerFrame(), I18.get("lib_usdb_search"), false);
        this.actions = actions;
        this.compareContext = compareContext;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setTitle(compareContext == null
                ? I18.get("lib_usdb_search")
                : I18.get("usdb_compare_search_title")
                .replace("{0}", compareContext.artist())
                .replace("{1}", compareContext.title()));
        setLayout(new BorderLayout(10, 10));
        add(createSearchPanel(), BorderLayout.NORTH);
        add(createCenterPanel(), BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);
        ImageIcon icon = actions.getIcon("usdbBrowserIcon");
        if (icon != null) {
            setIconImage(icon.getImage());
        }
        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        separateAfterImportCheck.setEnabled(actions.hasConfiguredSeparation());
        separateAfterImportCheck.setSelected(actions.hasConfiguredSeparation());
        resultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultTable.getSelectionModel().addListSelectionListener(this::onSelectionChanged);
        resultTable.setAutoCreateRowSorter(true);
        resultTable.setRowHeight(20);
        resultTable.setDefaultRenderer(Object.class, new SearchCellRenderer());
        resultTable.setDefaultRenderer(Integer.class, new SearchCellRenderer());
        resultTable.setDefaultRenderer(Boolean.class, new SearchCellRenderer());
        resultTable.getColumnModel().getColumn(0).setPreferredWidth(38);
        resultTable.getColumnModel().getColumn(0).setMaxWidth(48);
        if (compareContext == null) {
            installContextMenu();
        }
        actions.getUsdbImportQueueService().addListener(queueListener);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                actions.getUsdbImportQueueService().removeListener(queueListener);
            }
        });
        installEscapeClose();
        if (compareContext != null) {
            prefillFromSearchTerm(compareContext.artist() + " " + compareContext.title());
        }
        setSize(860, 520);
        setLocationRelativeTo(getOwner());
    }

    private void installEscapeClose() {
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel(I18.get("lib_find")), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(queryField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        JButton searchButton = new JButton(I18.get("lib_find"));
        searchButton.addActionListener(e -> runSearch());
        panel.add(searchButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(statusLabel, gbc);
        getRootPane().setDefaultButton(searchButton);
        return panel;
    }

    private JSplitPane createCenterPanel() {
        JScrollPane resultsScroll = new JScrollPane(resultTable);
        JScrollPane detailsScroll = new JScrollPane(detailsArea);
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, resultsScroll, detailsScroll);
        splitPane.setResizeWeight(0.7);
        return splitPane;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton(I18.get("edit_audio_separate_close"));
        closeButton.addActionListener(e -> dispose());
        if (compareContext != null) {
            compareButton.addActionListener(e -> compareSelectedSongWithCurrent());
            createMetaTagsButton.addActionListener(e -> createMetaTagsForCurrentSong());
            addSongButton.addActionListener(e -> addCurrentSongToUsdb());
            panel.add(compareButton);
            panel.add(createMetaTagsButton);
            panel.add(addSongButton);
            panel.add(closeButton);
            return panel;
        }
        JButton loginButton = new JButton(I18.get("lib_usdb_login"));
        loginButton.addActionListener(e -> {
            if (new UsdbLoginDialog(actions).showDialog()) {
                statusLabel.setText(I18.get("lib_usdb_login_success"));
            }
        });
        JButton refreshSyncerButton = new JButton(I18.get("lib_usdb_syncer_check_song_list"));
        refreshSyncerButton.setEnabled(actions.isUsdbSyncerConfigured());
        refreshSyncerButton.addActionListener(e -> refreshSyncerSongList());
        importButton.addActionListener(e -> onImportButtonPressed());
        JButton browserButton = new JButton(I18.get("lib_visit_usdb"));
        browserButton.addActionListener(e -> openSelectedSongInBrowser());
        panel.add(loginButton);
        panel.add(refreshSyncerButton);
        panel.add(separateAfterImportCheck);
        panel.add(importButton);
        panel.add(browserButton);
        panel.add(closeButton);
        return panel;
    }

    private void installContextMenu() {
        contextImportItem.addActionListener(e -> importSelectedSongFromContext());
        contextCompareUsdbItem.addActionListener(e -> compareSelectedSongFromContext());
        contextOpenUsdbItem.addActionListener(e -> openSelectedSongInBrowser());
        contextCancelImportItem.addActionListener(e -> cancelSelectedImport());
        contextMenu.add(contextImportItem);
        contextMenu.add(contextCompareUsdbItem);
        contextMenu.add(contextOpenUsdbItem);
        contextMenu.add(contextCancelImportItem);

        resultTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowContextMenu(e);
            }
        });
    }

    private void maybeShowContextMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int row = resultTable.rowAtPoint(e.getPoint());
        if (row >= 0) {
            resultTable.setRowSelectionInterval(row, row);
        }
        updateActionState();
        contextMenu.show(resultTable, e.getX(), e.getY());
    }

    private void runSearch() {
        statusLabel.setText(I18.get("lib_usdb_search_running"));
        currentResults = List.of();
        tableModel.fireTableDataChanged();
        detailsArea.setText("");
        updateActionState();
        SwingWorker<List<UsdbSongSummary>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<UsdbSongSummary> doInBackground() throws Exception {
                SearchRequest request = resolveSearchRequest(queryField.getText(), compareContext);
                LOGGER.info("USDB dialog search resolved artist='" + request.artist()
                        + "' title='" + request.title() + "'");
                return actions.searchUsdbSongs(request.artist(), request.title());
            }

            @Override
            protected void done() {
                try {
                    currentResults = get();
                    tableModel.fireTableDataChanged();
                    statusLabel.setText(I18.get("lib_usdb_search_results").replace("{0}", String.valueOf(currentResults.size())));
                    if (!currentResults.isEmpty()) {
                        resultTable.setRowSelectionInterval(0, 0);
                    }
                    updateActionState();
                } catch (Exception ex) {
                    statusLabel.setText(ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private SearchRequest resolveSearchRequest(String query, CompareContext compareContext) {
        if (compareContext != null) {
            return new SearchRequest(compareContext.artist(), compareContext.title());
        }
        String queryText = StringUtils.trimToEmpty(query);
        return new SearchRequest(queryText, queryText);
    }

    private void onSelectionChanged(ListSelectionEvent event) {
        if (event.getValueIsAdjusting()) {
            return;
        }
        updateActionState();
        int index = getSelectedModelIndex();
        if (index < 0 || index >= currentResults.size()) {
            return;
        }
        UsdbSongSummary selected = currentResults.get(index);
        detailsArea.setText(I18.get("lib_usdb_loading_details"));
        SwingWorker<UsdbSongDetails, Void> worker = new SwingWorker<>() {
            @Override
            protected UsdbSongDetails doInBackground() throws Exception {
                return actions.getUsdbSongDetails(selected.songId());
            }

            @Override
            protected void done() {
                try {
                    UsdbSongDetails details = get();
                    StringBuilder sb = new StringBuilder();
                    sb.append("ID: ").append(selected.songId()).append('\n');
                    sb.append(I18.get("lib_artist")).append(": ").append(selected.artist()).append('\n');
                    sb.append(I18.get("lib_title")).append(": ").append(selected.title()).append('\n');
                    if (StringUtils.isNotBlank(selected.edition())) {
                        sb.append(I18.get("lib_edition")).append(": ").append(selected.edition()).append('\n');
                    }
                    if (StringUtils.isNotBlank(details.bpm())) {
                        sb.append("BPM: ").append(details.bpm()).append('\n');
                    }
                    if (StringUtils.isNotBlank(details.gap())) {
                        sb.append("GAP: ").append(details.gap()).append('\n');
                    }
                    if (StringUtils.isNotBlank(details.coverUrl())) {
                        sb.append("Cover: ").append(details.coverUrl()).append('\n');
                    }
                    sb.append("URL: ").append(details.pageUrl());
                    detailsArea.setText(sb.toString());
                    detailsArea.setCaretPosition(0);
                } catch (Exception ex) {
                    detailsArea.setText(ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void onImportButtonPressed() {
        UsdbSongSummary selected = getSelectedSong();
        if (selected == null) {
            return;
        }
        MatchStatus status = getMatchStatus(selected);
        if (status != MatchStatus.NONE) {
            actions.getUsdbImportQueueService().showDialog(actions.createOwnerFrame());
            return;
        }
        enqueueSong(selected, null);
    }

    private void importSelectedSongFromContext() {
        UsdbSongSummary selected = getSelectedSong();
        if (selected == null) {
            return;
        }
        MatchStatus status = getMatchStatus(selected);
        if (status == MatchStatus.QUEUED) {
            return;
        }
        if (status == MatchStatus.EXACT) {
            int option = JOptionPane.showConfirmDialog(this,
                    I18.get("usdb_search_import_update_txt"),
                    I18.get("lib_usdb_import_song"),
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (option == JOptionPane.OK_OPTION) {
                enqueueSong(selected, UsdbImportConflictChoice.REPLACE);
            }
            return;
        }
        if (status == MatchStatus.TITLE_ARTIST) {
            Object[] options = {
                    I18.get("usdb_search_import_new_song"),
                    I18.get("usdb_search_import_overwrite"),
                    I18.get("tool_correct_cancel")
            };
            int option = JOptionPane.showOptionDialog(this,
                    I18.get("usdb_search_import_existing_song"),
                    I18.get("lib_usdb_import_song"),
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]);
            if (option == 0) {
                enqueueSong(selected, UsdbImportConflictChoice.CREATE_NEW_VERSION);
            } else if (option == 1) {
                enqueueSong(selected, UsdbImportConflictChoice.REPLACE);
            }
            return;
        }
        enqueueSong(selected, null);
    }

    private void cancelSelectedImport() {
        UsdbSongSummary selected = getSelectedSong();
        if (selected == null) {
            return;
        }
        UsdbImportQueueJob job = actions.getUsdbImportQueueService().findActiveJobFor(selected);
        if (job != null) {
            actions.getUsdbImportQueueService().cancelJob(job, this);
        }
    }

    private void compareSelectedSongFromContext() {
        UsdbSongSummary selected = getSelectedSong();
        if (selected == null) {
            return;
        }
        YassSongList songList = actions.getSongList();
        if (songList == null) {
            return;
        }
        String songFile = songList.getMatchingSongFile(selected.artist(), selected.title());
        if (StringUtils.isBlank(songFile)) {
            return;
        }
        dispose();
        actions.compareAndSubmitUsdbSongEdit(songFile, selected.songId());
    }

    private void enqueueSong(UsdbSongSummary selected, UsdbImportConflictChoice conflictChoice) {
        statusLabel.setText("Adding import to queue...");
        boolean separateAfterImport = separateAfterImportCheck.isSelected();
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                actions.enqueueUsdbSongImport(selected, separateAfterImport, conflictChoice);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    statusLabel.setText("Import queued.");
                    updateActionState();
                } catch (Exception ex) {
                    Throwable root = ex.getCause() != null ? ex.getCause() : ex;
                    LOGGER.log(Level.WARNING, "USDB song import failed for song id=" + selected.songId(), root);
                    statusLabel.setText("Queueing failed.");
                    JOptionPane.showMessageDialog(UsdbSearchDialog.this,
                            root.getMessage(),
                            I18.get("lib_usdb_import_song"),
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void openSelectedSongInBrowser() {
        UsdbSongSummary selected = getSelectedSong();
        if (selected == null) {
            return;
        }
        actions.openUsdbSongInBrowser(selected.songId());
    }

    private void refreshSyncerSongList() {
        if (!actions.isUsdbSyncerConfigured()) {
            statusLabel.setText(I18.get("lib_usdb_syncer_not_configured"));
            return;
        }
        statusLabel.setText(I18.get("lib_usdb_syncer_refresh_running"));
        SwingWorker<Integer, Void> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return actions.refreshUsdbSyncerSongList();
            }

            @Override
            protected void done() {
                try {
                    int count = get();
                    statusLabel.setText(I18.get("lib_usdb_syncer_refresh_success").replace("{0}",
                            count >= 0 ? String.valueOf(count) : "?"));
                } catch (Exception ex) {
                    statusLabel.setText(I18.get("lib_usdb_syncer_refresh_failed"));
                    JOptionPane.showMessageDialog(UsdbSearchDialog.this,
                            ex.getMessage(),
                            I18.get("lib_usdb_syncer_check_song_list"),
                            JOptionPane.WARNING_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void onQueueChanged() {
        tableModel.fireTableRowsUpdated(0, Math.max(0, currentResults.size() - 1));
        updateActionState();
    }

    private void updateActionState() {
        UsdbSongSummary selected = getSelectedSong();
        MatchStatus status = getMatchStatus(selected);
        boolean hasSelection = selected != null;
        if (compareContext != null) {
            compareButton.setEnabled(hasSelection);
            createMetaTagsButton.setEnabled(true);
            addSongButton.setEnabled(true);
            return;
        }
        importButton.setEnabled(hasSelection);
        importButton.setText(status == MatchStatus.NONE ? I18.get("lib_usdb_import_song") : I18.get("usdb_search_open_queue"));
        contextImportItem.setEnabled(hasSelection && status != MatchStatus.QUEUED);
        contextCompareUsdbItem.setVisible(shouldShowCompareWithUsdb(status));
        contextCompareUsdbItem.setEnabled(hasSelection && shouldShowCompareWithUsdb(status));
        contextOpenUsdbItem.setEnabled(hasSelection);
        contextCancelImportItem.setEnabled(hasSelection && status == MatchStatus.QUEUED);
    }

    private UsdbSongSummary getSelectedSong() {
        int index = getSelectedModelIndex();
        if (index < 0 || index >= currentResults.size()) {
            return null;
        }
        return currentResults.get(index);
    }

    private int getSelectedModelIndex() {
        int viewIndex = resultTable.getSelectedRow();
        if (viewIndex < 0) {
            return -1;
        }
        return resultTable.convertRowIndexToModel(viewIndex);
    }

    public void prefillFromSearchTerm(String searchTerm) {
        String trimmed = StringUtils.trimToEmpty(searchTerm);
        if (StringUtils.isBlank(trimmed)) {
            return;
        }
        queryField.setText(trimmed);
    }

    public void runSearchNow() {
        LOGGER.info("USDB dialog runSearchNow compareContext=" + (compareContext != null));
        runSearch();
    }

    private void compareSelectedSongWithCurrent() {
        UsdbSongSummary selected = getSelectedSong();
        if (selected == null || compareContext == null) {
            return;
        }
        dispose();
        actions.compareAndSubmitUsdbSongEdit(compareContext.songFile(), selected.songId());
    }

    private void createMetaTagsForCurrentSong() {
        if (compareContext == null) {
            return;
        }
        try {
            actions.openCreateSyncerTagsForSongFile(compareContext.songFile());
        } catch (Exception ex) {
            Throwable root = ex.getCause() != null ? ex.getCause() : ex;
            JOptionPane.showMessageDialog(this,
                    StringUtils.defaultIfBlank(root.getMessage(), root.toString()),
                    I18.get("lib_syncer_tags"),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addCurrentSongToUsdb() {
        if (compareContext == null) {
            return;
        }
        LOGGER.info("USDB dialog addCurrentSongToUsdb songFile=" + compareContext.songFile()
                + " artist='" + compareContext.artist() + "' title='" + compareContext.title() + "'");
        statusLabel.setText(I18.get("usdb_compare_search_add_running"));
        compareButton.setEnabled(false);
        createMetaTagsButton.setEnabled(false);
        addSongButton.setEnabled(false);
        SwingWorker<UsdbSongAddService.AddSongResult, Void> worker = new SwingWorker<>() {
            @Override
            protected UsdbSongAddService.AddSongResult doInBackground() throws Exception {
                LOGGER.info("USDB dialog addCurrentSongToUsdb background start");
                return actions.addSongToUsdb(compareContext.songFile());
            }

            @Override
            protected void done() {
                try {
                    UsdbSongAddService.AddSongResult result = get();
                    LOGGER.info("USDB dialog addCurrentSongToUsdb success verifySongId=" + result.verifySongId()
                            + " submitSongId=" + result.submitSongId());
                    statusLabel.setText(I18.get("usdb_compare_search_add_success"));
                    dispose();
                    actions.reviewPendingUsdbSong(compareContext.songFile(), result.verifySongId());
                } catch (Exception ex) {
                    Throwable root = ex.getCause() != null ? ex.getCause() : ex;
                    LOGGER.log(Level.WARNING, "USDB dialog addCurrentSongToUsdb failed", root);
                    statusLabel.setText(I18.get("usdb_compare_search_add_failed"));
                    JOptionPane.showMessageDialog(UsdbSearchDialog.this,
                            StringUtils.defaultIfBlank(root.getMessage(), root.toString()),
                            I18.get("usdb_compare_search_add_new_song"),
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    updateActionState();
                }
            }
        };
        worker.execute();
    }

    private MatchStatus getMatchStatus(UsdbSongSummary summary) {
        if (summary == null) {
            return MatchStatus.NONE;
        }
        UsdbImportQueueService queueService = actions.getUsdbImportQueueService();
        if (queueService.hasActiveJobFor(summary)) {
            return MatchStatus.QUEUED;
        }
        YassSongList songList = actions.getSongList();
        if (songList == null) {
            return MatchStatus.NONE;
        }
        if (songList.hasSongWithUsdbInLibrary(summary.artist(), summary.title())) {
            return MatchStatus.EXACT;
        }
        if (songList.hasSongInLibrary(summary.artist(), summary.title())) {
            return MatchStatus.TITLE_ARTIST;
        }
        return MatchStatus.NONE;
    }

    static boolean shouldShowCompareWithUsdb(MatchStatus status) {
        return status == MatchStatus.EXACT || status == MatchStatus.TITLE_ARTIST;
    }

    enum MatchStatus {
        NONE,
        TITLE_ARTIST,
        EXACT,
        QUEUED
    }

    public record CompareContext(String songFile, String artist, String title) {
    }

    private record SearchRequest(String artist, String title) {
    }

    private final class SearchCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            if (column == 0) {
                int modelRow = table.convertRowIndexToModel(row);
                MatchStatus matchStatus = modelRow >= 0 && modelRow < currentResults.size()
                        ? getMatchStatus(currentResults.get(modelRow))
                        : MatchStatus.NONE;
                setHorizontalAlignment(CENTER);
                setText(matchStatus == MatchStatus.NONE ? "" : "\u25cf");
                if (matchStatus == MatchStatus.EXACT) {
                    setForeground(MATCH_GREEN);
                } else if (matchStatus == MatchStatus.TITLE_ARTIST) {
                    setForeground(MATCH_YELLOW);
                } else if (matchStatus == MatchStatus.QUEUED) {
                    setForeground(MATCH_RED);
                }
                setToolTipText(switch (matchStatus) {
                    case EXACT -> I18.get("usdb_queue_match_exact");
                    case TITLE_ARTIST -> I18.get("usdb_queue_match_artist_title");
                    case QUEUED -> I18.get("usdb_search_match_queued");
                    case NONE -> null;
                });
                return this;
            }
            if (column == 1 || column == 6 || column == 7 || column == 8) {
                setHorizontalAlignment(CENTER);
            } else {
                setHorizontalAlignment(LEFT);
            }
            setToolTipText(null);
            return this;
        }
    }

    private final class SearchTableModel extends AbstractTableModel {
        private final String[] columns = {
                I18.get("usdb_search_col_match"),
                I18.get("usdb_search_col_id"),
                I18.get("usdb_search_col_artist"),
                I18.get("usdb_search_col_title"),
                I18.get("usdb_search_col_edition"),
                I18.get("usdb_search_col_language"),
                I18.get("usdb_search_col_rating"),
                I18.get("usdb_search_col_views"),
                I18.get("usdb_search_col_golden")
        };

        @Override
        public int getRowCount() {
            return currentResults.size();
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
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 0, 1 -> Integer.class;
                case 8 -> Boolean.class;
                default -> String.class;
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            UsdbSongSummary result = currentResults.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> switch (getMatchStatus(result)) {
                    case QUEUED -> 3;
                    case EXACT -> 2;
                    case TITLE_ARTIST -> 1;
                    case NONE -> 0;
                };
                case 1 -> result.songId();
                case 2 -> result.artist();
                case 3 -> result.title();
                case 4 -> result.edition();
                case 5 -> result.language();
                case 6 -> result.rating();
                case 7 -> result.views();
                case 8 -> result.goldenNotes();
                default -> "";
            };
        }
    }
}
