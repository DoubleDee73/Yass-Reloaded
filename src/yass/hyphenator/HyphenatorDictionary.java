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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import yass.*;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
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

    public HyphenatorDictionary(YassActions yassActions) {
        this(yassActions, true);
    }
    public HyphenatorDictionary(YassActions yassActions, boolean visible) {
        setTitle(I18.get("lib_edit_hyphenations"));
        URL icon = this.getClass().getResource("/yass/resources/img/Hyphenate24.gif");
        setIconImage(new ImageIcon(icon).getImage());
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
        addWindowListener(hideWindow());
        txtSearch.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    doSearch();
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
}
