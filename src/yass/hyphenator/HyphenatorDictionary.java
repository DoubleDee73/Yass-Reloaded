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

package yass.hyphenator;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import yass.*;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class HyphenatorDictionary extends JDialog {
    private JLabel lblLanguage;
    private JComboBox<HyphenatedLanguage> cboLanguage;
    private JLabel lblSearch;
    private JTextField txtSearch;
    private JList<HyphenatedWord> lstWords;
    private JLabel lblEditWord;
    private JTextField txtNewWord;
    private JButton btnSeparator;
    private JButton btnDeleteWord;
    private JButton btnSave;
    private JButton btnClose;
    private JPanel mainPanel;
    private JButton btnSearch;

    private final YassProperties yassProperties;
    private YassActions yassActions;

    private YassHyphenator yassHyphenator;
    private boolean saveAndClose = false;
    private boolean separatorTyped = false;

    public HyphenatorDictionary(YassActions yassActions) {
        this(yassActions, true);
    }

    public HyphenatorDictionary(YassActions yassActions, boolean visible) {
        setTitle(I18.get("lib_edit_hyphenations"));
        setIconImage(yassActions.getIcon("hyphenate24Icon").getImage());
        this.yassActions = yassActions;
        this.yassProperties = yassActions.getProperties();
        this.lblLanguage.setText(I18.get("hyphenator_language"));
        this.btnSearch.setText(I18.get("hyphenator_search"));
        this.lblSearch.setText(I18.get("hyphenator_search_term"));
        this.lblEditWord.setText(I18.get("hyphenator_edit_word"));
        this.btnDeleteWord.setText(I18.get("hyphenator_delete_word"));
        this.btnSeparator.setText(I18.get("hyphenator_separator"));
        this.btnSave.setText(I18.get("hyphenator_save"));
        this.btnClose.setText(I18.get("hyphenator_close"));
        Locale songLocale;
        if (yassActions.getView() == YassActions.VIEW_EDIT && yassActions.getTable() != null) {
            songLocale = YassUtils.determineLocale(yassActions.getTable().getLanguage());
        } else {
            songLocale = null;
        }
        initLanguages(songLocale);
        add(mainPanel);
        setSize(800, 400);
        Dimension dim = this.getToolkit().getScreenSize();
        setLocation(dim.width / 2 - 400, dim.height / 2 - 200);
        setVisible(visible);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        cboLanguage.addItemListener(changeLanguage());
        btnClose.addActionListener(e -> close());
        btnSearch.addActionListener(performSearch());
        btnDeleteWord.addActionListener(deleteWord());
        lstWords.addListSelectionListener(wordSelected());
        btnSeparator.addActionListener(toggleSeparator());
        btnSave.addActionListener(saveWord());
        btnSave.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    saveDictionaries();
                    setSaveAndClose(false);
                    close();
                }
            }
        });
        addWindowListener(hideWindow());
        txtSearch.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    doSearch();
                }
            }
        });
        txtNewWord.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    btnSave.requestFocus();
                }
            }

            @Override
            public void keyPressed(KeyEvent e) {
                separatorTyped = e.getKeyChar() == '|';
            }

            @Override
            public void keyTyped(KeyEvent e) {
                if (separatorTyped) {
                    e.setKeyChar('•');
                }
            }
        });
    }

    private ActionListener toggleSeparator() {
        return e -> {
            int caret = txtNewWord.getCaretPosition();
            StringBuilder text = new StringBuilder(txtNewWord.getText());
            if (StringUtils.isEmpty(text) || caret == 0) {
                return;
            }
            if (caret == text.length()) {
                if (text.charAt(caret - 1) != '•') {
                    text.append('•');
                    caret++;
                } else {
                    return;
                }
            } else if (text.charAt(caret) == '•') {
                text.deleteCharAt(caret);
            } else if (text.charAt(caret - 1) == '•') {
                text.deleteCharAt(caret - 1);
            } else {
                text.insert(caret, '•');
            }
            txtNewWord.setText(text.toString());
            txtNewWord.requestFocus();
            txtNewWord.setCaretPosition(caret);
        };
    }

    public void clearTextFields() {
        txtNewWord.setText("");
        txtSearch.setText("");
    }

    @NotNull
    private ListSelectionListener wordSelected() {
        return e -> {
            if (!e.getValueIsAdjusting()) {
                if (lstWords.getSelectedValue() == null) {
                    txtNewWord.setText("");
                } else {
                    txtNewWord.setText(lstWords.getSelectedValue().toString());
                }
            }
        };
    }

    @NotNull
    private ActionListener performSearch() {
        return e -> {
            doSearch();
        };
    }

    private void doSearch() {
        HyphenatedLanguage currentLanguage = (HyphenatedLanguage) cboLanguage.getSelectedItem();
        if (currentLanguage == null) {
            return;
        }
        String searchTerm = txtSearch.getText();
        if (StringUtils.isEmpty(searchTerm)) {
            initWords(yassHyphenator.getFallbackHyphenations());
        } else {
            Map<String, String> filter = currentLanguage.getHyphenations()
                                                        .entrySet()
                                                        .stream()
                                                        .filter(filter(searchTerm))
                                                        .collect(
                                                                Collectors.toMap(Map.Entry::getKey,
                                                                                 Map.Entry::getValue));
            initWords(filter);
            if (filter.isEmpty()) {
                txtNewWord.setText(searchTerm);
                txtNewWord.requestFocus();
            }
        }
    }

    private Predicate<Map.Entry<String, String>> filter(String searchTerm) {
        if (searchTerm.startsWith("*")) {
            return it -> it.getKey().contains(searchTerm.substring(1));
        } else {
            return it -> it.getKey().startsWith(searchTerm);
        }
    }

    private ItemListener changeLanguage() {
        return e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                HyphenatedLanguage currentLanguage = (HyphenatedLanguage) e.getItem();
                changeLanguage(currentLanguage);
            }
        };
    }

    private void changeLanguage(HyphenatedLanguage currentLanguage) {
        if (currentLanguage.getHyphenations() == null || currentLanguage.getHyphenations().isEmpty()) {
            currentLanguage.init(yassHyphenator);
        }
        initWords(currentLanguage.getHyphenations());
    }

    public void changeLanguage(String language) {
        if (cboLanguage == null || cboLanguage.getSelectedItem() == null) {
            return;
        }
        HyphenatedLanguage selectedLanguage = (HyphenatedLanguage) cboLanguage.getSelectedItem();
        if (selectedLanguage.getLanguageLocale().getLanguage().equalsIgnoreCase(language)) {
            return;
        }
        for (int i = 0; i < cboLanguage.getItemCount(); i++) {
            HyphenatedLanguage lang = cboLanguage.getItemAt(i);
            if (lang.getLanguageLocale().getLanguage().equalsIgnoreCase(language)) {
                cboLanguage.setSelectedIndex(i);
                return;
            }
        }
    }

    private ActionListener saveWord() {
        return e -> {
            HyphenatedLanguage currentLanguage = getCurrentLanguage();
            if (currentLanguage == null) {
                return;
            }

            String wordToSave = txtNewWord.getText();
            String unhyphenated = addWordToDictionary(wordToSave);
            if (StringUtils.isEmpty(unhyphenated)) {
                return;
            }
            initWords(currentLanguage.getHyphenations());
            if (saveAndClose) {
                setSaveAndClose(false);
                close();
            }
        };
    }

    public void selectWord(String word) {
        for (int i = 0; i < lstWords.getModel().getSize(); i++) {
            if (lstWords.getModel().getElementAt(i).getUnHyphenated().equals(word.replace("•", ""))) {
                lstWords.setSelectedIndex(i);
                lstWords.ensureIndexIsVisible(i);
                break;
            }
        }
    }

    public HyphenatedLanguage getCurrentLanguage() {
        return (HyphenatedLanguage) cboLanguage.getSelectedItem();
    }

    public String addWordToDictionary(String word) {
        HyphenatedLanguage currentLanguage = getCurrentLanguage();
        String wordToSave = word.toLowerCase().trim();
        if (wordToSave.endsWith("•")) {
            wordToSave = wordToSave.substring(0, wordToSave.length() - 1);
        }
        if (StringUtils.isEmpty(wordToSave)) {
            return "";
        }
        String unhyphenated = wordToSave.replace("•", "");
        String entryKey = currentLanguage.getHyphenations().get(unhyphenated);
        currentLanguage.setDirty(!wordToSave.equals(entryKey));
        currentLanguage.getHyphenations().put(unhyphenated, wordToSave);
        return unhyphenated;
    }

    @NotNull
    private ActionListener deleteWord() {
        return e -> {
            HyphenatedLanguage currentLanguage = (HyphenatedLanguage) cboLanguage.getSelectedItem();
            if (currentLanguage == null) {
                return;
            }
            int selectedIndex = lstWords.getSelectedIndex();
            if (selectedIndex >= 0) {
                if (currentLanguage.getHyphenations().remove(lstWords.getSelectedValue().getUnHyphenated()) != null) {
                    currentLanguage.setDirty(true);
                }
                ((DefaultListModel<HyphenatedWord>) lstWords.getModel()).remove(selectedIndex);
            }
        };
    }

    public void reinitWords() {
        initWords(getCurrentLanguage().getHyphenations());
    }

    private void initWords(Map<String, String> fallbackHyphenations) {
        DefaultListModel<HyphenatedWord> wordModel = new DefaultListModel<>();
        if (fallbackHyphenations == null) {
            return;
        }
        List<HyphenatedWord> entries = fallbackHyphenations.entrySet()
                                                           .stream()
                                                           .map(it -> new HyphenatedWord(it.getKey(), it.getValue()))
                                                           .sorted()
                                                           .toList();
        wordModel.addAll(entries);
        lstWords.setModel(wordModel);
    }


    private void initLanguages(Locale songLocale) {
        String hyphenations = yassProperties.getProperty("hyphenations");
        String[] languages = hyphenations.split("[|]");
        String songLanguage = songLocale != null ? songLocale.getLanguage().toUpperCase() : null;
        if (songLocale != null && !hyphenations.contains(songLanguage)) {
            List<String> tempList = new ArrayList<>(Arrays.stream(languages).toList());
            tempList.add(songLanguage);
            languages = tempList.toArray(new String[0]);
            yassProperties.setProperty("hyphenation", String.join("|", languages));
        }
        if (yassActions.getTable() != null && yassActions.getTable().getHyphenator() != null) {
            yassHyphenator = yassActions.getTable().getHyphenator();
        }
        if (yassHyphenator == null) {
            yassHyphenator = new YassHyphenator(String.join("|", languages));
        }
        Locale yassLocale = Locale.of(yassProperties.getProperty("yass-language"));
        List<HyphenatedLanguage> cboLanguages = new ArrayList<>();
        String dictionaryDir = yassProperties.getProperty("user-dicts", yassProperties.getUserDir());
        boolean changed = false;
        for (String language : languages) {
            String path = yassProperties.getProperty("hyphenations_" + language);
            if (StringUtils.isEmpty(path)) {
                if (songLocale != null && language.equals(songLocale.getLanguage().toUpperCase())) {
                    path = dictionaryDir + File.separator + YassUtils.determineDisplayLanguage(language) + ".txt";
                    yassProperties.setProperty("hyphenations_" + language, path);
                    changed = true;
                } else {
                    continue;
                }
            }
            Locale languageLocale = Locale.of(language.toLowerCase());
            cboLanguages.add(
                    new HyphenatedLanguage(languageLocale, languageLocale.getDisplayLanguage(yassLocale), path));
        }
        if (changed) {
            yassProperties.store();
        }
        yassHyphenator.setYassProperties(yassProperties);
        cboLanguage.setModel(new DefaultComboBoxModel<>(cboLanguages.toArray(new HyphenatedLanguage[0])));
        for (int i = 0; i < cboLanguage.getItemCount(); i++) {
            HyphenatedLanguage currentItem = cboLanguage.getItemAt(i);
            String currentLanguage = currentItem.getLanguageLocale().getLanguage();
            if ((songLocale != null && songLocale.getLanguage().equals(currentLanguage)) ||
                    (songLocale == null && currentLanguage.equals(Locale.ENGLISH.getLanguage()))) {
                cboLanguage.setSelectedIndex(i);
                currentItem.init(yassHyphenator);
                initWords(yassHyphenator.getFallbackHyphenations());
                break;
            }
        }
    }

    public void focusTxtWord() {
        txtNewWord.requestFocus();
    }

    private void close() {
        setVisible(false);
    }


    private WindowListener hideWindow() {
        return new WindowListener() {
            @Override
            public void windowOpened(WindowEvent e) {

            }

            @Override
            public void windowClosing(WindowEvent e) {
            }

            @Override
            public void windowClosed(WindowEvent e) {
            }

            @Override
            public void windowIconified(WindowEvent e) {
            }

            @Override
            public void windowDeiconified(WindowEvent e) {
            }

            @Override
            public void windowActivated(WindowEvent e) {
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                saveDictionaries();
            }
        };
    }

    public void saveDictionaries() {
        for (int i = 0; i < cboLanguage.getItemCount(); i++) {
            HyphenatedLanguage language = cboLanguage.getItemAt(i);
            if (language.isDirty()) {
                yassHyphenator.setFallbackHyphenations(language.getHyphenations());
                new DictionarySaver(Paths.get(language.getDictionaryPath()), language.getHyphenations());
            }
        }
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridLayoutManager(5, 4, new Insets(5, 5, 5, 5), -1, -1));
        mainPanel.setPreferredSize(new Dimension(600, 400));
        lblLanguage = new JLabel();
        lblLanguage.setText("Language");
        mainPanel.add(lblLanguage,
                      new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                          GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                          null, null, 0, false));
        cboLanguage = new JComboBox();
        final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
        cboLanguage.setModel(defaultComboBoxModel1);
        mainPanel.add(cboLanguage,
                      new GridConstraints(0, 1, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                          GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                          null, null, 0, false));
        lblSearch = new JLabel();
        lblSearch.setText("Search Term");
        mainPanel.add(lblSearch, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                     null, null, null, 0, false));
        txtSearch = new JTextField();
        mainPanel.add(txtSearch,
                      new GridConstraints(1, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                          GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                          new Dimension(150, -1), null, 0, false));
        lblEditWord = new JLabel();
        lblEditWord.setText("Add/Edit Word");
        mainPanel.add(lblEditWord,
                      new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                          GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                          null, null, 0, false));
        txtNewWord = new JTextField();
        mainPanel.add(txtNewWord,
                      new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                          GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                          new Dimension(150, -1), null, 0, false));
        btnSeparator = new JButton();
        btnSeparator.setText("Separator");
        mainPanel.add(btnSeparator,
                      new GridConstraints(3, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                          GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnDeleteWord = new JButton();
        btnDeleteWord.setText("Delete Word");
        mainPanel.add(btnDeleteWord,
                      new GridConstraints(3, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                          GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnSave = new JButton();
        btnSave.setText("Save");
        mainPanel.add(btnSave,
                      new GridConstraints(4, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                          GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnClose = new JButton();
        btnClose.setText("Close");
        mainPanel.add(btnClose,
                      new GridConstraints(4, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                          GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        mainPanel.add(scrollPane1,
                      new GridConstraints(2, 1, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                          null, null, null, 0, false));
        lstWords = new JList();
        final DefaultListModel defaultListModel1 = new DefaultListModel();
        lstWords.setModel(defaultListModel1);
        lstWords.setVisibleRowCount(50);
        scrollPane1.setViewportView(lstWords);
        btnSearch = new JButton();
        btnSearch.setText("Search");
        mainPanel.add(btnSearch,
                      new GridConstraints(1, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                          GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }

    private static class DictionarySaver {
        private final Thread thread;

        public DictionarySaver(Path path, Map<String, String> entries) {
            thread = new Thread(() -> {

                String contents = entries.values()
                                         .stream()
                                         .sorted()
                                         .collect(Collectors.joining(System.lineSeparator()));
                try {
                    Files.writeString(path, contents, StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    System.out.print("IO Exception while try to write: " + path);
                    ex.printStackTrace();
                }
            });
            thread.start();
        }
    }

    public void setSaveAndClose(boolean saveAndClose) {
        btnSave.setText(I18.get(saveAndClose ? "hyphenator_save_close" : "hyphenator_save"));
        this.saveAndClose = saveAndClose;
    }
}
