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
import yass.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultFormatter;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class UsdbSyncerMetaTagCreator extends JDialog {
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
    private final JTextField resultLine = new JTextField();

    private final Map<String, JSpinner> cropSpinners = new HashMap<>();
    private final Map<String, JTextField> textfields = new HashMap<>();

    public UsdbSyncerMetaTagCreator(YassActions a) {
        actions = a;
        YassSongList songList = a.getSongList();
        Vector<YassSong> songs = songList.getSelectedSongs();
        if (songs == null || songs.size() != 1) {
            JOptionPane.showMessageDialog(this, "Nope");
            dispose();
            return;
        } else {
            song = new YassTable();
            songList.loadSongDetails(songList.getFirstSelectedSong(), song);
        }
//        YassProperties prop = actions.getProperties();
        setModal(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        Dimension dim = this.getToolkit().getScreenSize();
        setSize(700, 620);
        setLocation(dim.width / 2 - 350, dim.height / 2 - 320);
        setTitle(I18.get("usdb_syncer_title"));
        setLayout(new BorderLayout());
        URL icon = this.getClass().getResource("/yass/resources/img/usdb_syncer_icon.png");
        setIconImage(new ImageIcon(icon).getImage());
        initSpinners();
        initTextFields();
        add(initPanel(), BorderLayout.PAGE_START);
        updateResultline();
        setVisible(true);
    }

    private void initSpinners() {
        cropSpinners.putIfAbsent("cover-left", coverCropLeft);
        cropSpinners.putIfAbsent("cover-top", coverCropTop);
        cropSpinners.putIfAbsent("cover-width", coverCropWidth);
        cropSpinners.putIfAbsent("cover-height", coverCropHeight);
        cropSpinners.putIfAbsent("background-left", backgroundCropLeft);
        cropSpinners.putIfAbsent("background-top", backgroundCropTop);
        cropSpinners.putIfAbsent("background-width", backgroundCropWidth);
        cropSpinners.putIfAbsent("background-height", backgroundCropHeight);
        cropSpinners.putIfAbsent("background-resize-width", backgroundResizeWidth);
        cropSpinners.putIfAbsent("background-resize-height", backgroundResizeHeight);
        cropSpinners.values()
                    .forEach(this::initSpinner);
        initSpinner(coverRotation);
        initSpinner(coverContrast);
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
        addDocumentListener(videoUrl);
        addDocumentListener(videoUrl);
        addDocumentListener(audioUrl);
        addDocumentListener(coverUrl);
        addDocumentListener(backgroundUrl);
        addDocumentListener(player1);
        addDocumentListener(player2);
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
                                        Arrays.asList(coverCropLeft, coverCropTop, coverCropWidth, coverCropHeight)));
        }
        if (StringUtils.isNotEmpty(backgroundUrl.getText())) {
            result.add(getTextValue("bg", backgroundUrl));
            result.add(getCombinedValue("bg-resize", Arrays.asList(backgroundResizeWidth, backgroundResizeHeight)));
            result.add(getCombinedValue("bg-crop",
                                        Arrays.asList(backgroundCropLeft, backgroundCropTop, backgroundCropWidth,
                                                      backgroundCropHeight)));
        }
        result.add(getTextValue("p1", player1));
        result.add(getTextValue("p2", player2));
        result.add(getSpinnerValue("preview", preview));
        result.add(getCombinedValue("medley", Arrays.asList(medleyStart, medleyEnd), true));
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
        return key + "=" + textValue;
    }

    private String determineImageLink(String url) {
        String imageUrl;
        int fanart = url.toLowerCase().indexOf("fanart.tv/fanart/");
        if (fanart >= 0) {
            imageUrl = url.substring(fanart + 17);
        } else {
            imageUrl = null;
        }
        if (imageUrl != null) {
            return imageUrl;
        }
        return url;
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
        return getCombinedValue(key, spinners, false);
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
        line = resultLine(gbc, line, main);
        main.setSize(1200, 600);
        main.setPreferredSize(new Dimension(1300, 600));
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
        gbc.gridwidth = 10;
        gbc.gridx = 2;
        gbc.gridy = line;
        main.add(coverUrl, gbc);
        line++;
        return line;
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

    private int resultLine(GridBagConstraints gbc, int line, JPanel main) {
        gbc.gridwidth = 12;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(Box.createVerticalStrut(50), gbc);
        line++;
        // --------------------------------------------------------
        gbc.gridwidth = 11;
        gbc.gridx = 0;
        gbc.gridy = line;
        main.add(resultLine, gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 11;
        gbc.gridy = line;
        JButton toClipboard = new JButton(I18.get("usdb_syncer_clipboard"));
        toClipboard.addActionListener(e -> {
            StringSelection stringSelection = new StringSelection(resultLine.getText());
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, null);
        });
        main.add(toClipboard, gbc);
        return ++line;
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
