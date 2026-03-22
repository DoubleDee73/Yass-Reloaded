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

package yass.extras;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import yass.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultFormatter;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class UsdbSyncerMetaTagCreator extends JDialog {


    private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(UsdbSyncerMetaTagCreator.class);
    private YassActions actions;
    private YassTable song;

    private final JTextField videoUrl = new JTextField();
    private final JTextField audioUrl = new JTextField();
    private final JTextField coverUrl = new JTextField();
    private final JSpinner coverRotation = new JSpinner(new SpinnerNumberModel(0, -360, 360, 0.1d));
    private final JSpinner coverContrast = new JSpinner(new SpinnerNumberModel(0, -0, 99.99, 0.1d));
    private final JCheckBox coverContrastAuto = new JCheckBox(I18.get("usdb_syncer_cover_contrast_auto"));
    private final JSpinner coverResize = new JSpinner(new SpinnerNumberModel(0, 0, 1920, 1));
    private final JSpinner coverCropLeft = new JSpinner(new SpinnerNumberModel(0, 0, 9999, 1));
    private final JSpinner coverCropTop = new JSpinner(new SpinnerNumberModel(0, 0, 9999, 1));
    private final JSpinner coverCropWidth = new JSpinner(new SpinnerNumberModel(0, 0, 9999, 1));
    private final JSpinner coverCropHeight = new JSpinner(new SpinnerNumberModel(0, 0, 9999, 1));
    private final JTextField backgroundUrl = new JTextField();
    private final JSpinner backgroundResizeWidth = new JSpinner();
    private final JSpinner backgroundResizeHeight = new JSpinner();
    private final JSpinner backgroundCropLeft = new JSpinner(new SpinnerNumberModel(0, 0, 9999, 1));
    private final JSpinner backgroundCropTop = new JSpinner(new SpinnerNumberModel(0, 0, 9999, 1));
    private final JSpinner backgroundCropWidth = new JSpinner(new SpinnerNumberModel(0, 0, 9999, 1));
    private final JSpinner backgroundCropHeight = new JSpinner(new SpinnerNumberModel(0, 0, 9999, 1));
    private final JTextField player1 = new JTextField();
    private final JTextField player2 = new JTextField();
    private final JSpinner preview = new JSpinner();
    private final JSpinner medleyStart = new JSpinner();
    private final JSpinner medleyEnd = new JSpinner();
    private final JTextField tags = new JTextField();
    private final JTextField resultLine = new JTextField();

    private final Map<String, JSpinner> cropSpinners = new HashMap<>();
    private final Map<String, JTextField> textfields = new HashMap<>();

    private Map<String, String> prefilledTags = new HashMap<>();

    private static List<String> SYNCER_TAGS = List.of("v", "a", "co", "bg", "preview", "medley", "tags", "p1", "p2");
    static final class RestoreState {
        final String video;
        final String commentTag;

        RestoreState(String video, String commentTag) {
            this.video = video;
            this.commentTag = commentTag;
        }
    }

    static final class SaveState {
        final String video;
        final String commentTag;

        SaveState(String video, String commentTag) {
            this.video = video;
            this.commentTag = commentTag;
        }
    }


    public UsdbSyncerMetaTagCreator(YassActions a) {
        actions = a;
        YassSongList songList = a.getSongList();
        Vector<YassSong> songs = songList.getSelectedSongs();
        YassProperties prop = actions.getProperties();
        if (songs == null || songs.size() != 1) {
            JOptionPane.showMessageDialog(this, "Nope");
            dispose();
            return;
        } else {
            song = new YassTable();
            song.init(prop);
            songList.loadSongDetails(songList.getFirstSelectedSong(), song);
        }
        checkExistingUsdbSyncerTags();
        setModal(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        Dimension dim = this.getToolkit().getScreenSize();
        setSize(700, 660);
        setLocation(dim.width / 2 - 350, dim.height / 2 - 320);
        setTitle(I18.get("usdb_syncer_title"));
        setLayout(new BorderLayout());
        setIconImage(actions.getIcon("createSyncerTagsIcon").getImage());
        initPrefilledMap();
        initSpinners();
        initTextFields();
        add(initPanel(), BorderLayout.PAGE_START);
        updateResultline();
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                     .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        getRootPane().getActionMap().put("close", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                dispose();
            }
        });
        setVisible(true);
    }

    private void checkExistingUsdbSyncerTags() {
        String videoLine = StringUtils.trimToEmpty(song.getVideo());
        String commentTag = StringUtils.trimToEmpty(song.getCommentTag());
        if (StringUtils.isEmpty(videoLine) && StringUtils.isEmpty(commentTag)) {
            return;
        }

        if (StringUtils.isNotEmpty(videoLine) && isSyncerTagLine(videoLine)) {
            applyCommentPrefills(videoLine);
        }

        if (StringUtils.isEmpty(commentTag)) {
            return;
        }

        if (commentTag.startsWith("#" + UltrastarHeaderTag.VIDEO.getTagName())) {
            int ok = JOptionPane.showConfirmDialog(this, I18.get("usdb_syncer_restore_desc"),
                                                   I18.get("usdb_syncer_restore_title"),
                                                   JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (ok == JOptionPane.YES_OPTION) {
                RestoreState restoredState = buildRestoreState(song.getVideo(), song.getCommentTag());
                song.setCommentTag(restoredState.commentTag);
                song.setVideo(restoredState.video);
                song.storeFile(song.getDirFilename());
                JOptionPane.showConfirmDialog(this, I18.get("usdb_syncer_restored"),
                                              I18.get("usdb_syncer_restore_title"),
                                              JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE);
            } else if (ok == JOptionPane.CANCEL_OPTION) {
                dispose();
                return;
            }
            return;
        }

        applyCommentPrefills(commentTag);
    }

    private void applyCommentPrefills(String commentTag) {
        if (StringUtils.isBlank(commentTag)) {
            return;
        }
        for (String part : commentTag.split(",")) {
            applyPrefillTag(part, false);
        }
    }

    private String extractCommentValue(String commentTag, String key) {
        if (StringUtils.isBlank(commentTag) || StringUtils.isBlank(key)) {
            return null;
        }
        String prefix = key + "=";
        for (String part : commentTag.split(",")) {
            String trimmed = StringUtils.trimToEmpty(part);
            if (trimmed.startsWith(prefix)) {
                return StringUtils.trimToNull(trimmed.substring(prefix.length()));
            }
        }
        return null;
    }

    private void initPrefilledMap() {
        UsdbSyncerMetaFileLoader loader = new UsdbSyncerMetaFileLoader(song.getDir());
        if (loader.getMetaFile() == null || StringUtils.isEmpty(loader.getMetaFile().getMetaTags())) {
            return;
        }
        String[] tags = loader.getMetaFile().getMetaTags().split(",");
        for (String tag : tags) {
            applyPrefillTag(tag, true);
        }
    }

    private void applyPrefillTag(String tag, boolean onlyIfAbsent) {
        if (StringUtils.isBlank(tag)) {
            return;
        }
        String[] keyValue = tag.split("=", 2);
        if (keyValue.length != 2) {
            return;
        }
        String key = StringUtils.trimToEmpty(keyValue[0]);
        String value = StringUtils.trimToEmpty(keyValue[1]);
        if (StringUtils.isBlank(key) || StringUtils.equalsIgnoreCase(key, "key")) {
            return;
        }
        String[] values = !value.startsWith("-") ? value.split("-") : new String[0];
        if (values.length == 2) {
            putPrefilledValue(key + "-width", values[0], onlyIfAbsent);
            putPrefilledValue(key + "-height", values[1], onlyIfAbsent);
        } else if (values.length == 4) {
            putPrefilledValue(key + "-left", values[0], onlyIfAbsent);
            putPrefilledValue(key + "-top", values[1], onlyIfAbsent);
            putPrefilledValue(key + "-width", values[2], onlyIfAbsent);
            putPrefilledValue(key + "-height", values[3], onlyIfAbsent);
        } else {
            putPrefilledValue(key, value, onlyIfAbsent);
        }
        if ("co-rotation".equalsIgnoreCase(key)) {
            putPrefilledValue("co-rotate", value, onlyIfAbsent);
        } else if ("co-rotate".equalsIgnoreCase(key)) {
            putPrefilledValue("co-rotation", value, onlyIfAbsent);
        }
    }

    private void putPrefilledValue(String key, String value, boolean onlyIfAbsent) {
        if (StringUtils.isBlank(key) || value == null) {
            return;
        }
        if (onlyIfAbsent) {
            prefilledTags.putIfAbsent(key, value);
        } else {
            prefilledTags.put(key, value);
        }
    }

    private void initSpinners() {
        coverCropLeft.setValue(extractIntValue("co-crop-left"));
        cropSpinners.putIfAbsent("cover-left", coverCropLeft);
        coverCropTop.setValue(extractIntValue("co-crop-top"));
        cropSpinners.putIfAbsent("cover-top", coverCropTop);
        coverCropWidth.setValue(extractIntValue("co-crop-width"));
        cropSpinners.putIfAbsent("cover-width", coverCropWidth);
        coverCropHeight.setValue(extractIntValue("co-crop-height"));
        cropSpinners.putIfAbsent("cover-height", coverCropHeight);
        backgroundCropLeft.setValue(extractIntValue("bg-crop-left"));
        cropSpinners.putIfAbsent("background-left", backgroundCropLeft);
        backgroundCropTop.setValue(extractIntValue("bg-crop-top"));
        cropSpinners.putIfAbsent("background-top", backgroundCropTop);
        backgroundCropWidth.setValue(extractIntValue("bg-crop-width"));
        cropSpinners.putIfAbsent("background-width", backgroundCropWidth);
        backgroundCropHeight.setValue(extractIntValue("bg-crop-height"));
        cropSpinners.putIfAbsent("background-height", backgroundCropHeight);
        backgroundResizeWidth.setValue(extractIntValue("bg-resize-width"));
        cropSpinners.putIfAbsent("background-resize-width", backgroundResizeWidth);
        backgroundResizeHeight.setValue(extractIntValue("bg-resize-height"));
        cropSpinners.putIfAbsent("background-resize-height", backgroundResizeHeight);
        cropSpinners.values()
                    .forEach(this::initSpinner);
        coverRotation.setValue(extractDoubleValue("co-rotate"));
        initSpinner(coverRotation);
        coverContrast.setValue(extractDoubleValue("co-contrast"));
        initSpinner(coverContrast);
        coverResize.setValue(extractIntValue("co-resize"));
        initSpinner(coverResize);
        initSpinner(backgroundResizeWidth);
        initSpinner(backgroundResizeHeight);
        initSpinner(preview);
        initSpinner(medleyStart);
        initSpinner(medleyEnd);
    }

    private void initSpinner(JSpinner spinner) {
        JFormattedTextField jftf = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
        ((DefaultFormatter) jftf.getFormatter()).setCommitsOnValidEdit(true);
        addDocumentListener(jftf);
        spinner.addChangeListener(e -> updateResultline());
    }

    private void initTextFields() {
        videoUrl.setText(extractValue("v"));
        addDocumentListener(videoUrl);
        audioUrl.setText(extractValue("a"));
        addDocumentListener(audioUrl);
        coverUrl.setText(extractValue("co"));
        addDocumentListener(coverUrl);
        backgroundUrl.setText(extractValue("bg"));
        addDocumentListener(backgroundUrl);
        player1.setText(extractValue("p1"));
        addDocumentListener(player1);
        player2.setText(extractValue("p2"));
        addDocumentListener(player2);
        tags.setText(extractValue("tags"));
        addDocumentListener(tags);
    }

    private String extractValue(String key) {
        if (prefilledTags == null || !prefilledTags.containsKey(key)) {
            return StringUtils.EMPTY;
        }
        return prefilledTags.get(key);
    }

    private double extractDoubleValue(String key) {
        if (prefilledTags == null || !prefilledTags.containsKey(key)) {
            return 0d;
        }
        return Double.parseDouble(prefilledTags.get(key));
    }

    private double extractIntValue(String key) {
        if (prefilledTags == null || !prefilledTags.containsKey(key)) {
            return 0d;
        }
        return Integer.parseInt(prefilledTags.get(key));
    }

    private void addDocumentListener(JTextField textfield) {
        textfield.getDocument().addDocumentListener(onChangeTextfield());
    }

    private DocumentListener onChangeTextfield() {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateResultline();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateResultline();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateResultline();
            }
        };
    }

    private void updateResultline() {
        List<String> result = new ArrayList<>();
        result.add(getTextValue("v", videoUrl));
        result.add(getTextValue("a", audioUrl));
        if (StringUtils.isNotEmpty(coverUrl.getText())) {
            result.add(getTextValue("co", coverUrl));
            result.add(getSpinnerValue("co-rotation", coverRotation));
            if (coverContrastAuto.isSelected()) {
                result.add("co-contrast=auto");
            } else {
                result.add(getSpinnerValue("co-contrast", coverContrast));
            }
            result.add(getSpinnerValue("co-resize", coverResize));
            result.add(getCombinedValue("co-crop",
                                        List.of(coverCropLeft, coverCropTop, coverCropWidth, coverCropHeight)));
        }
        if (StringUtils.isNotEmpty(backgroundUrl.getText())) {
            result.add(getTextValue("bg", backgroundUrl));
            result.add(getCombinedValue("bg-resize", List.of(backgroundResizeWidth, backgroundResizeHeight)));
            result.add(getCombinedValue("bg-crop",
                                        List.of(backgroundCropLeft, backgroundCropTop, backgroundCropWidth,
                                                backgroundCropHeight)));
        }
        result.add(getTextValue("p1", player1));
        result.add(getTextValue("p2", player2));
        result.add(getSpinnerValue("preview", preview));
        result.add(getCombinedValue("medley", List.of(medleyStart, medleyEnd), true));
        result.add(getTextValue("tags", tags));
        String tagLine = result.stream().filter(StringUtils::isNotEmpty).collect(Collectors.joining(","));
        if (tagLine.isEmpty()) {
            resultLine.setText(StringUtils.EMPTY);
        } else {
            resultLine.setText("#" + UltrastarHeaderTag.VIDEO.getTagName() + tagLine);
        }
        resultLine.repaint();
    }

    private String getTextValue(String key, JTextField textField) {
        if (StringUtils.isEmpty(textField.getText())) {
            return null;
        }
        String textValue;
        if (key.equalsIgnoreCase("v") || key.equalsIgnoreCase("a")) {
            textValue = determineVideoLink(textField.getText());
        } else if (key.equalsIgnoreCase("co") || key.equalsIgnoreCase("bg")) {
            textValue = determineImageLink(textField.getText());
        } else {
            textValue = textField.getText();
        }
        return key + "=" + textValue.replace(",", "%2C");
    }

    private String determineImageLink(String url) {
        String trimmedUrl = StringUtils.trimToEmpty(url);
        String youTubeThumbnailUrl = resolveYouTubeThumbnailUrl(trimmedUrl);
        if (youTubeThumbnailUrl != null) {
            return youTubeThumbnailUrl;
        }
        String imageUrl;
        int fanart = trimmedUrl.toLowerCase().indexOf("fanart.tv/fanart/");
        if (fanart >= 0) {
            imageUrl = trimmedUrl.substring(fanart + 17);
        } else {
            imageUrl = null;
        }
        if (imageUrl != null) {
            return imageUrl;
        }
        return trimmedUrl;
    }

    private String determineVideoLink(String url) {
        String videoUrl;
        if (isYouTube(url.toLowerCase())) {
            videoUrl = extractYoutubeVideoIdFromUrl(url);
        } else if (isVimeo(url.toLowerCase())) {
            videoUrl = extractVimeoVideoId(url);
        } else {
            videoUrl = null;
        }
        if (videoUrl == null) {
            return url;
        } else {
            return videoUrl;
        }
    }

    private boolean isYouTube(String text) {
        return text.contains("youtube.com/") || text.contains("youtu.be/") || text.startsWith("v=");
    }

    private String extractYouTubeId(String value) {
        String trimmed = StringUtils.trimToEmpty(value);
        if (trimmed.matches("[A-Za-z0-9_-]{11}")) {
            return trimmed;
        }
        if (trimmed.startsWith("v=") && trimmed.length() > 2) {
            String candidate = trimmed.substring(2);
            if (candidate.matches("[A-Za-z0-9_-]{11}")) {
                return candidate;
            }
        }
        if (isYouTube(trimmed.toLowerCase(Locale.ROOT))) {
            return extractYoutubeVideoIdFromUrl(trimmed);
        }
        return null;
    }

    private String resolveYouTubeThumbnailUrl(String value) {
        String youTubeId = extractYouTubeId(value);
        if (StringUtils.isBlank(youTubeId)) {
            return null;
        }
        String thumbnailUrl = "https://i.ytimg.com/vi/" + youTubeId + "/maxresdefault.jpg";
        if (YassUtils.isUrlReachable(thumbnailUrl)) {
            return thumbnailUrl;
        }
        return null;
    }

    private String extractYoutubeVideoIdFromUrl(String url) {
        String pattern = "(?<=watch\\?v=|/videos/|embed/|youtu.be/|/v/|/e/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%2F|youtu.be%2F|%2Fv%2F)[^#&?\\n]*";
        Pattern compiledPattern = Pattern.compile(pattern);
        Matcher matcher = compiledPattern.matcher(url);

        if (matcher.find()) {
            return matcher.group();
        }

        return null;
    }

    private boolean isVimeo(String url) {
        return url.contains("vimeo.com");
    }

    private String extractVimeoVideoId(String vimeoUrl) {
        // Regulärer Ausdruck, um die Video-ID zu extrahieren
        Pattern pattern = Pattern.compile(
                "https?://(?:www\\.|player\\.)?vimeo.com/(?:channels/(?:\\w+/)?|groups/([^/]+)/videos/|album/(\\d+)/video/|video/|)(\\d+)(?:$|/|\\?)");
        Matcher matcher = pattern.matcher(vimeoUrl);

        if (matcher.find()) {
            // Gruppe 3 enthält die Video-ID
            return matcher.group(3);
        } else {
            // Wenn keine Übereinstimmung gefunden wurde
            return null;
        }
    }


    private String getSpinnerValue(String key, JSpinner spinner) {
        return getSpinnerValue(key, spinner, false);
    }

    private String getSpinnerValue(String key, JSpinner spinner, boolean includeZero) {
        Object value = spinner.getValue();
        if (value == null) {
            return null;
        }
        String stringValue;
        if (value instanceof Double dblValue) {
            if (dblValue == 0d && !includeZero) {
                return null;
            }
            DecimalFormat format = new DecimalFormat("#####0.###", DecimalFormatSymbols.getInstance(Locale.US));
            stringValue = format.format(dblValue);
        } else {
            int intValue = (int) value;
            if (intValue == 0 && !includeZero) {
                return null;
            }
            stringValue = Integer.toString(intValue);
        }
        if (StringUtils.isEmpty(key)) {
            return stringValue;
        } else {
            return key + "=" + stringValue;
        }
    }

    private String getCombinedValue(String key, List<JSpinner> spinners) {
        return getCombinedValue(key, spinners, true);
    }

    private String getCombinedValue(String key, List<JSpinner> spinners, boolean includeZero) {
        StringJoiner joiner = new StringJoiner("-");
        String temp;
        for (JSpinner spinner : spinners) {
            temp = getSpinnerValue("", spinner, includeZero);
            if (temp == null) {
                return null;
            } else {
                joiner.add(temp);
            }
        }
        if (includeZero && (joiner.toString().equalsIgnoreCase("0-0") ||
                joiner.toString().equals("0-0-0-0"))) {
            return null;
        }
        return key + "=" + joiner;
    }

    private JPanel initPanel() {
        GridBagLayout gridBagLayout = new GridBagLayout();
        GridBagConstraints gbc = new GridBagConstraints();
        JPanel main = new JPanel();
        gridBagLayout.setConstraints(main, gbc);
        main.setLayout(gridBagLayout);
        int line = 0;
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.gridwidth = 2;
        gbc.gridx = 0;
        gbc.gridy = line;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        main.add(new JLabel(I18.get("usdb_syncer_video_url")), gbc);
        gbc.gridwidth = 10;
        gbc.gridx = 2;
        gbc.gridy = line;
        main.add(videoUrl, gbc);
        line++;
        // --------------------------------------------------------
        gbc.gridwidth = 2;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(new JLabel(I18.get("usdb_syncer_audio_url")), gbc);
        gbc.gridwidth = 10;
        gbc.gridx = 2;
        gbc.gridy = line;
        main.add(audioUrl, gbc);
        line++;
        // --------------------------------------------------------
        line = coverUrlLine(gbc, line, main);
        line = coverRotationContrastResizeLine(gbc, line, main);
        line = cropLine(gbc, line, main, "cover");
        // --------------------------------------------------------
        line = backgroundUrlLine(gbc, line, main);
        line = coverBackgroundResizeLine(gbc, line, main);
        line = cropLine(gbc, line, main, "background");
        // --------------------------------------------------------
        line = duetLine(gbc, line, main);
        // --------------------------------------------------------
        line = previewMedleyLine(gbc, line, main);
        // --------------------------------------------------------
        line = tagsLine(gbc, line, main);
        // --------------------------------------------------------
        line = resultLine(gbc, line, main);
        main.setSize(1200, 620);
        main.setPreferredSize(new Dimension(1300, 620));
        main.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        return main;
    }

    private int coverUrlLine(GridBagConstraints gbc, int line, JPanel main) {
        gbc.gridwidth = 12;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(Box.createVerticalStrut(10), gbc);
        line++;
        // --------------------------------------------------------
        gbc.gridwidth = 12;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(createGroupHeaderPanel(I18.get("usdb_syncer_cover")), gbc);
        line++;
        // --------------------------------------------------------
        gbc.gridwidth = 2;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(new JLabel(I18.get("usdb_syncer_cover_url")), gbc);
        gbc.gridwidth = 9;
        gbc.gridx = 2;
        gbc.gridy = line;
        main.add(coverUrl, gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 11;
        gbc.gridy = line;
        main.add(createDownloadButton("COVER"), gbc);
        line++;
        return line;
    }

    private JButton createDownloadButton(String action) {
        JButton downloadButton = new JButton();
        downloadButton.setIcon(actions.getIcon("globe16Icon"));
        downloadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String coUrl = StringUtils.trimToEmpty(coverUrl.getText());
                if (StringUtils.isNotEmpty(coUrl) && !coUrl.toLowerCase(Locale.ROOT).startsWith("http")) {
                    String resolvedCoverUrl = resolveYouTubeThumbnailUrl(coUrl);
                    if (resolvedCoverUrl != null) {
                        coUrl = resolvedCoverUrl;
                        coverUrl.setText(coUrl);
                    }
                }
                if (StringUtils.isNotEmpty(coUrl) && !YassUtils.isUrlReachable(coUrl)) {
                    JOptionPane.showMessageDialog(null, I18.get("url_not_reachable"));
                    return;
                }
                if (StringUtils.isEmpty(coUrl)) {
                    String resolvedVideoThumbnail = resolveYouTubeThumbnailUrl(videoUrl.getText());
                    if (resolvedVideoThumbnail != null) {
                        coUrl = resolvedVideoThumbnail;
                        coverUrl.setText(coUrl);
                    }
                }
                if (StringUtils.isEmpty(coUrl)) {
                    return;
                }
                if (coUrl.startsWith("https://i.ytimg.com") && coverResize.getValue() != null &&
                        ((Number) coverResize.getValue()).intValue() == 0) {
                    coverCropHeight.setValue(720);
                    coverCropWidth.setValue(720);
                    coverCropLeft.setValue(280);
                    coverCropTop.setValue(0);
                }
                if ("COVER".equals(action) && StringUtils.isNotEmpty(coUrl)) {
                    int[] crop = new int[4];
                    if (coverCropHeight.getValue() != null && ((Number)coverCropHeight.getValue()).intValue() > 0 &&
                            coverCropWidth.getValue() != null && ((Number)coverCropWidth.getValue()).intValue() > 0) {
                        crop[0] = ((Number) coverCropLeft.getValue()).intValue();
                        crop[1] = ((Number) coverCropTop.getValue()).intValue();
                        crop[2] = ((Number) coverCropWidth.getValue()).intValue();
                        crop[3] = ((Number) coverCropHeight.getValue()).intValue();
                    } else {
                        crop = null;
                    }
                    int resize = coverResize.getValue() != null ? ((Number)coverResize.getValue()).intValue() : 0;
                    if (resize == 0 && ((Number) coverCropWidth.getValue()).intValue() == 0) {
                        resize = actions.getProperties().getIntProperty("cover-max-width");
                    }
                    File file = downloadAndSaveFile(coverUrl.getText(),
                                                    song.getDirFilename().replace(".txt", " [CO]"),
                                                    crop,
                                                    resize);
                    if (file != null && !file.getName().equalsIgnoreCase(song.getCover())) {
                        song.setCover(file.getName());
                        song.storeFile(song.getDirFilename());
                        JOptionPane.showMessageDialog(null, I18.get("usdb_syncer_cover_saved").replace("%s", file.getName()), I18.get("usdb_syncer_cover"),
                                                      JOptionPane.PLAIN_MESSAGE);
                    }
                }
            }
        });
        return downloadButton;
    }

    private File downloadAndSaveFile(String imageUrl, String destinationFile, int[] crop, int resize) {
        URL url;
        File imageFile;
        try {
            imageUrl = imageUrl.replace("images.fanart.tv", "assets.fanart.tv");
            url = new URI(imageUrl).toURL();
            BufferedImage image = ImageIO.read(url);
            if (crop != null) {
                image = image.getSubimage(crop[0], crop[1], crop[2], crop[3]);
            }
            if (resize > 0 && image.getWidth() != resize) {
                Image resultingImage = image.getScaledInstance(resize, resize, Image.SCALE_SMOOTH);
                BufferedImage outputImage = new BufferedImage(resize, resize, BufferedImage.TYPE_INT_RGB);
                outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);
                image = outputImage;
            }
            imageFile = new File(destinationFile + ".jpg");
            if (imageFile.exists()) {
                log.info(destinationFile + ".jpg already exist");
                return null;
            }
            ImageIO.write(image, "jpeg", imageFile);
        } catch (IOException | URISyntaxException e) {
            return null;
        }
        return imageFile;
    }

    private int coverRotationContrastResizeLine(GridBagConstraints gbc, int line, JPanel main) {
        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(new JLabel(I18.get("usdb_syncer_cover_rotation")), gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 3;
        gbc.gridy = line;
        main.add(coverRotation, gbc);
        line++;
        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(new JLabel(I18.get("usdb_syncer_cover_contrast")), gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 3;
        gbc.gridy = line;
        main.add(coverContrast, gbc);
        gbc.gridwidth = 2;
        gbc.gridx = 4;
        gbc.gridy = line;
        main.add(coverContrastAuto, gbc);
        line++;
        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(new JLabel(I18.get("usdb_syncer_cover_resize")), gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 3;
        gbc.gridy = line;
        main.add(coverResize, gbc);
        line++;
        return line;
    }

    private int cropLine(GridBagConstraints gbc, int line, JPanel main, String prefix) {
        String message = "usdb_syncer_" + prefix + "_crop";
        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(new JLabel(I18.get(message)), gbc);
        gbc.gridwidth = 8;
        gbc.gridx = 3;
        gbc.gridy = line;
        gbc.fill = GridBagConstraints.NONE;
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(new JLabel(I18.get("usdb_syncer_crop_left")));
        panel.add(Box.createHorizontalStrut(10));
        panel.add(createResizeSpinner(prefix + "-left"));
        panel.add(Box.createHorizontalStrut(10));
        panel.add(new JLabel(I18.get("usdb_syncer_crop_top")), gbc);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(createResizeSpinner(prefix + "-top"));
        panel.add(Box.createHorizontalStrut(10));
        panel.add(new JLabel(I18.get("usdb_syncer_crop_width")), gbc);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(createResizeSpinner(prefix + "-width"));
        panel.add(Box.createHorizontalStrut(10));
        panel.add(new JLabel(I18.get("usdb_syncer_crop_height")), gbc);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(createResizeSpinner(prefix + "-height"));
        panel.add(Box.createHorizontalStrut(10));
        main.add(panel, gbc);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        line++;
        return line;
    }

    private int backgroundUrlLine(GridBagConstraints gbc, int line, JPanel main) {
        gbc.gridwidth = 12;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(Box.createVerticalStrut(10), gbc);
        line++;
        // --------------------------------------------------------
        gbc.gridwidth = 12;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(createGroupHeaderPanel(I18.get("usdb_syncer_background")), gbc);
        line++;
        // --------------------------------------------------------
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(new JLabel(I18.get("usdb_syncer_background_url")), gbc);
        gbc.gridwidth = 11;
        gbc.gridx = 1;
        gbc.gridy = line;
        main.add(backgroundUrl, gbc);
        line++;
        return line;
    }

    private int coverBackgroundResizeLine(GridBagConstraints gbc, int line, JPanel main) {
        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(new JLabel(I18.get("usdb_syncer_background_resize")), gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 3;
        gbc.gridy = line;
        main.add(new JLabel(I18.get("usdb_syncer_crop_width")), gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 4;
        gbc.gridy = line;
        main.add(createResizeSpinner("background-resize-width"), gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 5;
        gbc.gridy = line;
        main.add(new JLabel(I18.get("usdb_syncer_crop_height")), gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 6;
        gbc.gridy = line;
        main.add(createResizeSpinner("background-resize-height"), gbc);
        return ++line;
    }

    private int duetLine(GridBagConstraints gbc, int line, JPanel main) {
        String p1 = StringUtils.defaultString(song.getDuetSingerName(0));
        String p2 = StringUtils.defaultString(song.getDuetSingerName(1));
        gbc.gridwidth = 12;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(Box.createVerticalStrut(10), gbc);
        line++;
        // --------------------------------------------------------
        gbc.gridwidth = 12;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(createGroupHeaderPanel(I18.get("usdb_syncer_duet")), gbc);
        line++;
        // --------------------------------------------------------
        gbc.gridwidth = 2;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(new JLabel(I18.get("usdb_syncer_duet_p1")), gbc);
        gbc.gridwidth = 4;
        gbc.gridx = 2;
        gbc.gridy = line;
        player1.setText(p1);
        main.add(player1, gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 6;
        gbc.gridy = line;
        main.add(new JLabel(I18.get("usdb_syncer_duet_p2")), gbc);
        gbc.gridwidth = 5;
        gbc.gridx = 7;
        gbc.gridy = line;
        player2.setText(p2);
        main.add(player2, gbc);
        return ++line;
    }

    private int previewMedleyLine(GridBagConstraints gbc, int line, JPanel main) {
        gbc.gridwidth = 12;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(Box.createVerticalStrut(10), gbc);
        line++;
        // --------------------------------------------------------
        gbc.gridwidth = 12;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(createGroupHeaderPanel(I18.get("usdb_syncer_preview_medley")), gbc);
        line++;
        // --------------------------------------------------------
        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(new JLabel(I18.get("usdb_syncer_preview")), gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 3;
        gbc.gridy = line;
        preview.setModel(new SpinnerNumberModel(Math.max(song.getPreviewStart(), 0), 0, 999.99, 0.01));
        main.add(preview, gbc);
        line++;
        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(new JLabel(I18.get("usdb_syncer_medley")), gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 3;
        gbc.gridy = line;
        main.add(new JLabel(I18.get("usdb_syncer_medley_start")), gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 4;
        gbc.gridy = line;
        medleyStart.setModel(new SpinnerNumberModel(Math.max(song.getMedleyStartBeat(), 0), 0, 99999, 1));
        main.add(medleyStart, gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 5;
        gbc.gridy = line;
        main.add(new JLabel(I18.get("usdb_syncer_medley_end")), gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 6;
        gbc.gridy = line;
        medleyEnd.setModel(new SpinnerNumberModel(Math.max(song.getMedleyEndBeat(), 0), 0, 99999, 1));
        main.add(medleyEnd, gbc);
        return ++line;
    }

    private int tagsLine(GridBagConstraints gbc, int line, JPanel main) {
        gbc.gridwidth = 12;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(Box.createVerticalStrut(10), gbc);
        line++;
        // --------------------------------------------------------
        gbc.gridwidth = 12;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(createGroupHeaderPanel(I18.get("usdb_syncer_tags")), gbc);
        line++;
        // --------------------------------------------------------
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(new JLabel(I18.get("usdb_syncer_tags_label")), gbc);
        gbc.gridwidth = 11;
        gbc.gridx = 1;
        gbc.gridy = line;
        main.add(tags, gbc);
        if (StringUtils.isNotEmpty(song.getTags())) {
            tags.setText(song.getTags());
        }
        line++;
        return line;
    }

    private int resultLine(GridBagConstraints gbc, int line, JPanel main) {
        gbc.gridwidth = 12;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(Box.createVerticalStrut(50), gbc);
        line++;
        // --------------------------------------------------------
        gbc.gridwidth = 10;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(resultLine, gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 10;
        gbc.gridy = line;
        JButton toClipboard = new JButton();
        toClipboard.setToolTipText(I18.get("usdb_syncer_clipboard"));
        toClipboard.setIcon(actions.getIcon("pasteMelody16Icon"));
        toClipboard.addActionListener(e -> {
            StringSelection stringSelection = new StringSelection(resultLine.getText());
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, null);
        });
        main.add(toClipboard, gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 11;
        gbc.gridy = line;
        JButton btnSave = new JButton();
        btnSave.setToolTipText(I18.get("usdb_syncer_save"));
        btnSave.setIcon(actions.getIcon("save16Icon"));
        btnSave.addActionListener(e -> {
            String result = resultLine.getText();
            if (StringUtils.isEmpty(result)) {
                LOGGER.finer("Skipped saving USDB Syncer Tags " + song.getDirFilename() + ". No result line");
            }
            if (result.startsWith("#VIDEO:")) {
                result = resultLine.getText().substring(7);
            }
            saveSong(result);
        });
        main.add(btnSave, gbc);
        return ++line;
    }

    private void saveSong(String text) {
        SaveState saveState = buildSaveState(song.getVideo(), song.getCommentTag(), text);
        song.setCommentTag(saveState.commentTag);
        song.setVideo(saveState.video);
        song.storeFile(song.getDirFilename());
        dispose();
    }

    static RestoreState buildRestoreState(String currentVideo, String existingComment) {
        String restoredVideo = extractPreservedVideo(existingComment);
        String preservedPayload = extractPreservedCommentPayload(existingComment);
        String mergedPayload = removeTagKeys(mergeTagLines(currentVideo, preservedPayload), Set.of("p1", "p2"));
        return new RestoreState(StringUtils.trimToEmpty(restoredVideo), StringUtils.trimToEmpty(mergedPayload));
    }

    static SaveState buildSaveState(String currentVideo, String existingComment, String newSyncerVideo) {
        String preservedVideo = extractPreservedVideo(existingComment);
        String preservedCommentPayload = extractPreservedCommentPayload(existingComment);

        if (StringUtils.isNotEmpty(currentVideo) && !isSyncerTagLineStatic(currentVideo) && StringUtils.isEmpty(preservedVideo)) {
            preservedVideo = currentVideo;
        }

        String mergedComment = mergeNonSyncerCommentPayloadStatic(preservedCommentPayload);
        if (StringUtils.isNotEmpty(preservedVideo)) {
            mergedComment = "#" + UltrastarHeaderTag.VIDEO.getTagName() + preservedVideo
                    + (StringUtils.isNotEmpty(mergedComment) ? "|" + mergedComment : StringUtils.EMPTY);
        }

        return new SaveState(StringUtils.trimToEmpty(newSyncerVideo), StringUtils.trimToEmpty(mergedComment));
    }

    private static String mergeTagLines(String primary, String secondary) {
        LinkedHashMap<String, String> merged = new LinkedHashMap<>();
        applyTagMap(merged, secondary, false);
        applyTagMap(merged, primary, true);
        return merged.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(","));
    }

    private static String removeTagKeys(String tagLine, Set<String> keysToRemove) {
        if (StringUtils.isBlank(tagLine)) {
            return StringUtils.EMPTY;
        }
        LinkedHashMap<String, String> merged = new LinkedHashMap<>();
        applyTagMap(merged, tagLine, true);
        for (String key : keysToRemove) {
            merged.remove(key);
        }
        return merged.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(","));
    }

    private static void applyTagMap(Map<String, String> target, String tagLine, boolean overwrite) {
        if (StringUtils.isBlank(tagLine)) {
            return;
        }
        for (String rawPart : tagLine.split(",")) {
            String part = StringUtils.trimToEmpty(rawPart);
            if (StringUtils.isEmpty(part)) {
                continue;
            }
            String[] keyValue = part.split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            String key = StringUtils.trimToEmpty(keyValue[0]);
            String value = StringUtils.trimToEmpty(keyValue[1]);
            if (StringUtils.isEmpty(key)) {
                continue;
            }
            if (overwrite || !target.containsKey(key)) {
                target.put(key, value);
            }
        }
    }

    private boolean isSyncerTagLine(String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        return isSyncerTagLineStatic(value);
    }

    static boolean isSyncerTagLineStatic(String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        return SYNCER_TAGS.stream().anyMatch(tag -> value.startsWith(tag + "="));
    }

    private static boolean isSyncerCommentKey(String key) {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        return SYNCER_TAGS.stream().anyMatch(tag -> key.equals(tag) || key.startsWith(tag + "-"));
    }

    static String extractPreservedVideo(String commentTag) {
        if (StringUtils.isBlank(commentTag) || !commentTag.startsWith("#" + UltrastarHeaderTag.VIDEO.getTagName())) {
            return null;
        }
        String raw = commentTag.substring(UltrastarHeaderTag.VIDEO.getTagName().length() + 1);
        if (raw.contains("|")) {
            raw = raw.substring(0, raw.indexOf('|'));
        } else {
            int commaBeforeTag = findLegacyVideoPayloadSeparator(raw);
            if (commaBeforeTag >= 0) {
                raw = raw.substring(0, commaBeforeTag);
            }
        }
        return StringUtils.trimToNull(raw);
    }

    static String extractPreservedCommentPayload(String commentTag) {
        if (StringUtils.isBlank(commentTag)) {
            return StringUtils.EMPTY;
        }
        if (!commentTag.startsWith("#" + UltrastarHeaderTag.VIDEO.getTagName())) {
            return commentTag;
        }
        int separator = commentTag.indexOf('|');
        if (separator >= 0) {
            if (separator + 1 >= commentTag.length()) {
                return StringUtils.EMPTY;
            }
            return StringUtils.trimToEmpty(commentTag.substring(separator + 1));
        }
        String raw = commentTag.substring(UltrastarHeaderTag.VIDEO.getTagName().length() + 1);
        int commaBeforeTag = findLegacyVideoPayloadSeparator(raw);
        if (commaBeforeTag < 0 || commaBeforeTag + 1 >= raw.length()) {
            return StringUtils.EMPTY;
        }
        return StringUtils.trimToEmpty(raw.substring(commaBeforeTag + 1));
    }

    private static int findLegacyVideoPayloadSeparator(String raw) {
        if (StringUtils.isBlank(raw)) {
            return -1;
        }
        for (String tag : SYNCER_TAGS) {
            int idx = raw.indexOf("," + tag + "=");
            if (idx >= 0) {
                return idx;
            }
        }
        int keyIdx = raw.indexOf(",key=");
        if (keyIdx >= 0) {
            return keyIdx;
        }
        return -1;
    }

    static String mergeNonSyncerCommentPayloadStatic(String commentPayload) {
        if (StringUtils.isBlank(commentPayload)) {
            return StringUtils.EMPTY;
        }
        List<String> mergedParts = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        for (String rawPart : commentPayload.split(",")) {
            String part = StringUtils.trimToEmpty(rawPart);
            if (StringUtils.isEmpty(part)) {
                continue;
            }
            String key = part;
            int equals = part.indexOf('=');
            if (equals >= 0) {
                key = part.substring(0, equals).trim();
            }
            if (isSyncerCommentKey(key)) {
                continue;
            }
            if (seenKeys.add(key)) {
                mergedParts.add(part);
            }
        }
        return String.join(",", mergedParts);
    }

    private JSpinner createResizeSpinner(String key) {
        JSpinner spinner = cropSpinners.get(key);
        JFormattedTextField jftf = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
        jftf.setColumns(3);
        return spinner;
    }

    private Box createGroupHeaderPanel(String title) {
        Box box = Box.createHorizontalBox();
        JLabel lab = new JLabel(title);
        Font f = lab.getFont();
        float size = f.getSize();
        f = f.deriveFont(size + 2);
        lab.setFont(f);
        box.add(lab);
        return box;
    }
}
