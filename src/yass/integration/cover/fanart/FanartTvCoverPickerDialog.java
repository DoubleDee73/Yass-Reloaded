package yass.integration.cover.fanart;

import org.apache.commons.lang3.StringUtils;
import yass.I18;
import yass.VersionUtils;
import yass.YassUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FanartTvCoverPickerDialog extends JDialog {
    private static final int PREVIEW_SIZE = 250;
    private static final int CARD_WIDTH = PREVIEW_SIZE + 28;
    private static final int CARD_TEXT_HEIGHT = 56;
    private static final int IMAGE_TIMEOUT_MS = 8000;
    private static final int IMAGE_CONNECT_RETRIES = 2;
    private static final int IMAGE_RETRY_DELAY_MS = 250;
    private static final int MAX_CONNECT_EXCEPTIONS = 5;
    static final Color DEFAULT_TILE_BACKGROUND = new Color(232, 232, 232);
    static final Color SELECTED_TILE_BACKGROUND = new Color(201, 224, 255);
    static final Color DEFAULT_TILE_BORDER_COLOR = new Color(180, 180, 180);
    static final Color SELECTED_TILE_BORDER_COLOR = new Color(70, 130, 210);
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final Map<String, ImageIcon> PREVIEW_ICON_CACHE = new ConcurrentHashMap<>();
    private static final String PREVIEW_LOADING_PROPERTY = "fanart.preview.loading";
    private static final String PREVIEW_LOADED_PROPERTY = "fanart.preview.loaded";
    private static final String PREVIEW_LABEL_PROPERTY = "fanart.preview.label";
    private static final String TEXT_LABEL_PROPERTY = "fanart.text.label";
    private static final ExecutorService PREVIEW_EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "fanart-preview-loader");
        thread.setDaemon(true);
        return thread;
    });

    static {
        ImageIO.scanForPlugins();
    }

    private FanartTvCoverCandidate selectedCandidate;
    private final List<JToggleButton> candidateButtons = new ArrayList<>();
    private final String downloadLabel;
    private final AtomicInteger previewConnectExceptionCount = new AtomicInteger();
    private final AtomicBoolean previewLoadingAborted = new AtomicBoolean(false);
    private String lastSearchQuery = "";
    private int lastSearchMatchIndex = -1;
    private boolean downloadConfirmed;

    public FanartTvCoverPickerDialog(Window owner,
                                     String title,
                                     String downloadLabel,
                                     List<FanartTvCoverCandidate> candidates) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.downloadLabel = downloadLabel;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        setSize(700, 520);
        setLocationRelativeTo(owner);

        JPanel imagePanel = new JPanel();
        imagePanel.setLayout(new BoxLayout(imagePanel, BoxLayout.X_AXIS));
        ButtonGroup group = new ButtonGroup();
        JScrollPane scrollPane = new JScrollPane(imagePanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(32);
        ChangeListener lazyLoadListener = event -> triggerVisiblePreviewLoads(scrollPane);
        scrollPane.getViewport().addChangeListener(lazyLoadListener);

        JTextField searchField = new JTextField();
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                resetSearchCycle(searchField.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                resetSearchCycle(searchField.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                resetSearchCycle(searchField.getText());
            }
        });
        searchField.addActionListener(e -> jumpToNextCandidate(searchField.getText(), scrollPane));

        JPanel topPanel = new JPanel(new BorderLayout(6, 6));
        topPanel.add(new JLabel("Search album:"), BorderLayout.WEST);
        topPanel.add(searchField, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        int preferredIndex = 0;
        for (int i = 0; i < candidates.size(); i++) {
            FanartTvCoverCandidate candidate = candidates.get(i);
            JToggleButton button = createCandidateButton(candidate);
            group.add(button);
            candidateButtons.add(button);
            imagePanel.add(button);
            imagePanel.add(Box.createHorizontalStrut(10));
            if (candidate.isPreferred()) {
                preferredIndex = i;
            }
        }

        if (!candidateButtons.isEmpty()) {
            JToggleButton preferredButton = candidateButtons.get(preferredIndex);
            preferredButton.setSelected(true);
            selectedCandidate = candidates.get(preferredIndex);
            SwingUtilities.invokeLater(() -> {
                scrollPane.getViewport().scrollRectToVisible(preferredButton.getBounds());
                triggerVisiblePreviewLoads(scrollPane);
            });
        }

        add(scrollPane, BorderLayout.CENTER);

        JButton downloadButton = new JButton(downloadLabel);
        downloadButton.addActionListener(e -> confirmDownloadAndClose());
        JButton cancelButton = new JButton(UIManager.getString("OptionPane.cancelButtonText"));
        cancelButton.addActionListener(e -> {
            selectedCandidate = null;
            downloadConfirmed = false;
            dispose();
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(downloadButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(null);
    }

    public FanartTvCoverCandidate showDialog() {
        setVisible(true);
        return downloadConfirmed ? selectedCandidate : null;
    }

    private JToggleButton createCandidateButton(FanartTvCoverCandidate candidate) {
        JToggleButton button = new JToggleButton();
        button.setLayout(new BorderLayout(4, 4));
        button.setPreferredSize(new Dimension(CARD_WIDTH, PREVIEW_SIZE + CARD_TEXT_HEIGHT + 24));
        button.setFocusable(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.putClientProperty("candidate", candidate);

        JLabel imageLabel = new JLabel("Loading...", SwingConstants.CENTER);
        imageLabel.setPreferredSize(new Dimension(PREVIEW_SIZE, PREVIEW_SIZE));
        imageLabel.setOpaque(true);
        imageLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handlePreviewContextClick(e, candidate, button);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handlePreviewContextClick(e, candidate, button);
            }
        });
        button.add(imageLabel, BorderLayout.CENTER);
        button.putClientProperty(PREVIEW_LABEL_PROPERTY, imageLabel);
        button.putClientProperty(PREVIEW_LOADING_PROPERTY, Boolean.FALSE);
        button.putClientProperty(PREVIEW_LOADED_PROPERTY, Boolean.FALSE);

        StringBuilder labelBuilder = new StringBuilder();
        labelBuilder.append("<b>")
                .append(escapeHtml(StringUtils.defaultIfBlank(candidate.getAlbumName(), I18.get("group_album_unspecified"))))
                .append("</b>");
        if (candidate.getLikes() > 0) {
            labelBuilder.append("<br>");
            labelBuilder.append("Likes: ").append(candidate.getLikes());
        }
        JLabel textLabel = new JLabel("<html><div style='text-align:center;width:" + PREVIEW_SIZE + "px;'>"
                + StringUtils.defaultIfBlank(labelBuilder.toString(), "&nbsp;")
                + "</div></html>", SwingConstants.CENTER);
        textLabel.setVerticalAlignment(SwingConstants.TOP);
        textLabel.setPreferredSize(new Dimension(PREVIEW_SIZE, CARD_TEXT_HEIGHT));
        textLabel.setOpaque(true);
        button.add(textLabel, BorderLayout.SOUTH);
        button.putClientProperty(TEXT_LABEL_PROPERTY, textLabel);

        String previewKey = getPreviewCacheKey(candidate);
        button.setToolTipText(StringUtils.defaultIfBlank(candidate.getPreviewImageUrl(), candidate.getImageUrl()));
        button.addActionListener(e -> selectedCandidate = candidate);
        button.addChangeListener(e -> applyTileStyle(button, button.isSelected()));
        applyTileStyle(button, false);

        ImageIcon cachedIcon = PREVIEW_ICON_CACHE.get(previewKey);
        if (cachedIcon != null) {
            imageLabel.setText("");
            imageLabel.setIcon(cachedIcon);
            button.putClientProperty(PREVIEW_LOADED_PROPERTY, Boolean.TRUE);
            return button;
        }
        return button;
    }

    static void applyTileStyle(JToggleButton button, boolean selected) {
        if (button == null) {
            return;
        }
        Color background = selected ? SELECTED_TILE_BACKGROUND : DEFAULT_TILE_BACKGROUND;
        Color borderColor = selected ? SELECTED_TILE_BORDER_COLOR : DEFAULT_TILE_BORDER_COLOR;

        button.setBackground(background);
        button.setBorder(BorderFactory.createLineBorder(borderColor, selected ? 3 : 1));

        Object imageLabelValue = button.getClientProperty(PREVIEW_LABEL_PROPERTY);
        if (imageLabelValue instanceof JLabel imageLabel) {
            imageLabel.setBackground(background);
        }

        Object textLabelValue = button.getClientProperty(TEXT_LABEL_PROPERTY);
        if (textLabelValue instanceof JLabel textLabel) {
            textLabel.setBackground(background);
        }
    }

    private void triggerVisiblePreviewLoads(JScrollPane scrollPane) {
        Rectangle visibleRect = scrollPane.getViewport().getViewRect();
        for (JToggleButton button : candidateButtons) {
            if (!shouldLoadPreview(visibleRect, button.getBounds())) {
                continue;
            }
            Object candidateValue = button.getClientProperty("candidate");
            Object labelValue = button.getClientProperty(PREVIEW_LABEL_PROPERTY);
            if (!(candidateValue instanceof FanartTvCoverCandidate candidate) || !(labelValue instanceof JLabel imageLabel)) {
                continue;
            }
            startPreviewLoadIfNeeded(button, imageLabel, candidate);
        }
    }

    private void startPreviewLoadIfNeeded(JToggleButton button, JLabel imageLabel, FanartTvCoverCandidate candidate) {
        if (previewLoadingAborted.get()) {
            imageLabel.setText("Preview unavailable");
            button.putClientProperty(PREVIEW_LOADED_PROPERTY, Boolean.TRUE);
            return;
        }
        if (Boolean.TRUE.equals(button.getClientProperty(PREVIEW_LOADED_PROPERTY))
                || Boolean.TRUE.equals(button.getClientProperty(PREVIEW_LOADING_PROPERTY))) {
            return;
        }

        String previewKey = getPreviewCacheKey(candidate);
        ImageIcon cachedIcon = PREVIEW_ICON_CACHE.get(previewKey);
        if (cachedIcon != null) {
            imageLabel.setText("");
            imageLabel.setIcon(cachedIcon);
            button.putClientProperty(PREVIEW_LOADED_PROPERTY, Boolean.TRUE);
            return;
        }

        button.putClientProperty(PREVIEW_LOADING_PROPERTY, Boolean.TRUE);
        PREVIEW_EXECUTOR.execute(() -> {
            try {
                BufferedImage image = loadPreviewImage(candidate);
                if (image == null) {
                    SwingUtilities.invokeLater(() -> {
                        LOGGER.info("fanart.tv preview unavailable for URL: " + previewKey);
                        imageLabel.setText("Preview unavailable");
                        button.putClientProperty(PREVIEW_LOADING_PROPERTY, Boolean.FALSE);
                        button.putClientProperty(PREVIEW_LOADED_PROPERTY, Boolean.TRUE);
                    });
                    return;
                }
                candidate.setPreviewImage(image);
                BufferedImage scaled = YassUtils.fitImageIntoSquare(image, PREVIEW_SIZE, button.getBackground(), true);
                ImageIcon icon = new ImageIcon(scaled);
                PREVIEW_ICON_CACHE.put(previewKey, icon);
                SwingUtilities.invokeLater(() -> {
                    imageLabel.setText("");
                    imageLabel.setIcon(icon);
                    button.putClientProperty(PREVIEW_LOADING_PROPERTY, Boolean.FALSE);
                    button.putClientProperty(PREVIEW_LOADED_PROPERTY, Boolean.TRUE);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    LOGGER.log(Level.INFO, "fanart.tv preview loading failed for URL: " + previewKey, e);
                    imageLabel.setText("Preview unavailable");
                    button.putClientProperty(PREVIEW_LOADING_PROPERTY, Boolean.FALSE);
                    button.putClientProperty(PREVIEW_LOADED_PROPERTY, Boolean.TRUE);
                });
            }
        });
    }

    static boolean shouldLoadPreview(Rectangle visibleRect, Rectangle buttonBounds) {
        if (visibleRect == null || buttonBounds == null) {
            return false;
        }
        return visibleRect.intersects(buttonBounds);
    }

    protected BufferedImage loadPreviewImage(FanartTvCoverCandidate candidate) throws Exception {
        try {
            BufferedImage preview = loadPreviewImageFromUrl(candidate.getPreviewImageUrl());
            if (preview != null) {
                return preview;
            }
        } catch (Exception e) {
            LOGGER.log(Level.INFO, "fanart.tv preview fetch failed, falling back to original image URL: "
                    + candidate.getPreviewImageUrl() + ", route=" + describeProxyRoute(candidate.getPreviewImageUrl()), e);
        }
        try {
            return loadPreviewImageFromUrl(candidate.getImageUrl());
        } catch (Exception e) {
            LOGGER.log(Level.INFO, "fanart.tv original image fetch failed after preview fallback: "
                    + candidate.getImageUrl() + ", route=" + describeProxyRoute(candidate.getImageUrl()), e);
            return null;
        }
    }

    protected BufferedImage loadPreviewImageFromUrl(String imageUrl) throws Exception {
        if (previewLoadingAborted.get()) {
            return null;
        }
        if (StringUtils.isBlank(imageUrl)) {
            return null;
        }
        for (int attempt = 1; attempt <= IMAGE_CONNECT_RETRIES; attempt++) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URI(imageUrl).toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(IMAGE_TIMEOUT_MS);
                connection.setReadTimeout(IMAGE_TIMEOUT_MS);
                connection.setRequestProperty("Accept", "image/webp,image/png,image/jpeg,image/*,*/*;q=0.8");
                connection.setRequestProperty("User-Agent", "Yass Reloaded/" + VersionUtils.getVersion()
                        + " ( https://github.com/DoubleDee73/Yass-Reloaded )");
                connection.setRequestProperty("Connection", "close");
                LOGGER.info("fanart.tv preview request: url=" + imageUrl + ", attempt=" + attempt
                        + ", route=" + describeProxyRoute(imageUrl)
                        + ", useSystemProxies=" + System.getProperty("java.net.useSystemProxies")
                        + ", https.proxyHost=" + StringUtils.defaultString(System.getProperty("https.proxyHost"))
                        + ", socksProxyHost=" + StringUtils.defaultString(System.getProperty("socksProxyHost")));
                int status = connection.getResponseCode();
                String contentType = connection.getContentType();
                LOGGER.info("fanart.tv preview response status=" + status + ", contentType=" + contentType + ", url=" + imageUrl);
                if (status < 200 || status >= 300) {
                    return null;
                }
                try (InputStream inputStream = connection.getInputStream()) {
                    BufferedImage image = YassUtils.readImage(inputStream);
                    if (image == null) {
                        LOGGER.info("fanart.tv preview decode returned null for contentType=" + contentType + ", url=" + imageUrl);
                    }
                    return image;
                }
            } catch (ConnectException e) {
                int failureCount = previewConnectExceptionCount.incrementAndGet();
                LOGGER.log(Level.INFO, "fanart.tv preview connect failed: url=" + imageUrl
                        + ", attempt=" + attempt + "/" + IMAGE_CONNECT_RETRIES
                        + ", route=" + describeProxyRoute(imageUrl)
                        + ", connectFailures=" + failureCount + "/" + MAX_CONNECT_EXCEPTIONS, e);
                if (failureCount > MAX_CONNECT_EXCEPTIONS && previewLoadingAborted.compareAndSet(false, true)) {
                    LOGGER.warning("fanart.tv preview loading aborted after too many connection failures: "
                            + failureCount + " > " + MAX_CONNECT_EXCEPTIONS);
                    return null;
                }
                if (attempt >= IMAGE_CONNECT_RETRIES) {
                    throw e;
                }
                Thread.sleep(IMAGE_RETRY_DELAY_MS);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        return null;
    }

    private String getPreviewCacheKey(FanartTvCoverCandidate candidate) {
        return StringUtils.defaultIfBlank(candidate.getPreviewImageUrl(), candidate.getImageUrl());
    }

    static String describeProxyRoute(String imageUrl) {
        if (StringUtils.isBlank(imageUrl)) {
            return "<blank-url>";
        }
        try {
            ProxySelector proxySelector = ProxySelector.getDefault();
            if (proxySelector == null) {
                return "<no-proxy-selector>";
            }
            List<Proxy> proxies = proxySelector.select(new URI(imageUrl));
            if (proxies == null || proxies.isEmpty()) {
                return "[]";
            }
            return proxies.toString();
        } catch (URISyntaxException e) {
            return "<invalid-uri>";
        } catch (Exception e) {
            return "<proxy-route-error:" + e.getClass().getSimpleName() + ">";
        }
    }

    private void resetSearchCycle(String query) {
        String normalizedQuery = normalizeSearchQuery(query);
        if (!StringUtils.equals(normalizedQuery, lastSearchQuery)) {
            lastSearchQuery = normalizedQuery;
            lastSearchMatchIndex = -1;
        }
    }

    private void jumpToNextCandidate(String query, JScrollPane scrollPane) {
        String normalizedQuery = normalizeSearchQuery(query);
        if (StringUtils.isBlank(normalizedQuery)) {
            return;
        }

        int nextIndex = findNextMatchingCandidateIndex(normalizedQuery, lastSearchMatchIndex);
        if (nextIndex < 0) {
            return;
        }

        lastSearchQuery = normalizedQuery;
        lastSearchMatchIndex = nextIndex;
        selectCandidateButton(nextIndex, scrollPane);
    }

    static int findNextMatchingIndex(List<String> candidates, String query, int previousIndex) {
        String normalizedQuery = normalizeSearchQuery(query);
        if (candidates == null || candidates.isEmpty() || StringUtils.isBlank(normalizedQuery)) {
            return -1;
        }
        int candidateCount = candidates.size();
        int startIndex = previousIndex >= 0 ? (previousIndex + 1) % candidateCount : 0;
        for (int offset = 0; offset < candidateCount; offset++) {
            int index = (startIndex + offset) % candidateCount;
            String albumName = StringUtils.defaultString(candidates.get(index)).toLowerCase(Locale.ROOT);
            if (albumName.contains(normalizedQuery)) {
                return index;
            }
        }
        return -1;
    }

    private int findNextMatchingCandidateIndex(String normalizedQuery, int previousIndex) {
        List<String> albumNames = new ArrayList<>(candidateButtons.size());
        for (JToggleButton button : candidateButtons) {
            Object value = button.getClientProperty("candidate");
            if (value instanceof FanartTvCoverCandidate candidate) {
                albumNames.add(candidate.getAlbumName());
            } else {
                albumNames.add("");
            }
        }
        return findNextMatchingIndex(albumNames, normalizedQuery, previousIndex);
    }

    private void selectCandidateButton(int buttonIndex, JScrollPane scrollPane) {
        if (buttonIndex < 0 || buttonIndex >= candidateButtons.size()) {
            return;
        }
        JToggleButton button = candidateButtons.get(buttonIndex);
        Object value = button.getClientProperty("candidate");
        if (!(value instanceof FanartTvCoverCandidate candidate)) {
            return;
        }
        button.setSelected(true);
        selectedCandidate = candidate;
        SwingUtilities.invokeLater(() -> {
            scrollPane.getViewport().scrollRectToVisible(button.getBounds());
            triggerVisiblePreviewLoads(scrollPane);
        });
    }

    private static String normalizeSearchQuery(String query) {
        return StringUtils.trimToEmpty(query).toLowerCase(Locale.ROOT);
    }

    private void handlePreviewContextClick(MouseEvent event, FanartTvCoverCandidate candidate, JToggleButton button) {
        if (!event.isPopupTrigger()) {
            return;
        }
        button.setSelected(true);
        selectedCandidate = candidate;
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem downloadItem = new JMenuItem(StringUtils.defaultIfBlank(downloadLabel, "Download"));
        downloadItem.addActionListener(e -> confirmDownloadAndClose());
        JMenuItem copyUrlItem = new JMenuItem(I18.get("lib_search_fanarttv_cover_copy_url"));
        copyUrlItem.addActionListener(e -> copyToClipboard(candidate.getImageUrl()));
        popupMenu.add(downloadItem);
        popupMenu.add(copyUrlItem);
        popupMenu.show(event.getComponent(), event.getX(), event.getY());
    }

    private void confirmDownloadAndClose() {
        if (selectedCandidate == null && !candidateButtons.isEmpty()) {
            Object value = candidateButtons.get(0).getClientProperty("candidate");
            if (value instanceof FanartTvCoverCandidate candidate) {
                selectedCandidate = candidate;
            }
        }
        downloadConfirmed = selectedCandidate != null;
        dispose();
    }

    private void copyToClipboard(String value) {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clipboard != null) {
                clipboard.setContents(new StringSelection(StringUtils.defaultString(value)), null);
            }
        } catch (Exception ignored) {
        }
    }

    private String escapeHtml(String value) {
        return StringUtils.defaultString(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
