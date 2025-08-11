/*
 * Yass - Karaoke Editor
 * Copyright (C) 2009 Saruta
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

package yass.wizard;

import com.nexes.wizard.Wizard;
import com.nexes.wizard.WizardPanelDescriptor;
import org.apache.commons.lang3.StringUtils;
import yass.I18;
import yass.YassProperties;
import yass.YassTable;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Description of the Class
 *
 * @author Saruta
 */
public class CreateSongWizard extends Wizard {
    MP3 mp3 = null;
    Header header = null;
    Edition edition = null;
    Melody melody = null;
    Lyrics lyrics = null;
    LyricsForMIDI lyricsformidi = null;
    MIDI midi = null;
    Tap tap = null;
    
    private YassProperties yassProperties;

    /**
     * Constructor for the CreateSongWizard object
     *
     * @param parent Description of the Parameter
     */
    public CreateSongWizard(Component parent, YassProperties yassProperties) {
        super(JOptionPane.getFrameForComponent(parent));
        getDialog().setTitle(I18.get("create_title"));
        this.yassProperties = yassProperties;

        registerWizardPanel(Melody.ID, new WizardPanelDescriptor(Melody.ID, melody = new Melody(this)) {
            public Object getNextPanelDescriptor() {
                if (melody.getFilename() != null && new File(melody.getFilename()).exists()) {
                    return MIDI.ID;
                }
                return Lyrics.ID;
            }


            public Object getBackPanelDescriptor() {
                return null;
            }


            public void aboutToDisplayPanel() {
                melody.setFilename(getValue("melody"));
            }
        });
        registerWizardPanel(Lyrics.ID, new WizardPanelDescriptor(Lyrics.ID, lyrics = new Lyrics(this, yassProperties)) {
            public Object getNextPanelDescriptor() {
                return MP3.ID;
            }


            public Object getBackPanelDescriptor() {
                return Melody.ID;
            }


            public void aboutToDisplayPanel() {
                setValue("melodytable", "");
                lyrics.setText(getValue("lyrics"));
            }


            public void aboutToHidePanel() {
                setValue("lyrics", lyrics.getText());
                setValue("melodytable", lyrics.getTable());
                setValue("bpm", "300");
            }
        });
        registerWizardPanel(MIDI.ID, new WizardPanelDescriptor(MIDI.ID, midi = new MIDI(this)) {
            public Object getNextPanelDescriptor() {
                if (melody.getFilename() != null && new File(melody.getFilename()).exists()) {
                    String txt = midi.getText();
                    if (txt != null) {
                        YassTable t = new YassTable();
                        t.setText(txt);
                        if (t.hasLyrics()) {
                            setValue("haslyrics", "yes");
                            return MP3.ID;
                        }
                    }
                }
                setValue("haslyrics", "no");
                return LyricsForMIDI.ID;
            }


            public Object getBackPanelDescriptor() {
                return Melody.ID;
            }


            public void aboutToDisplayPanel() {
                midi.setFilename(getValue("melody"));
                setValue("melodytable", "");
                midi.startRendering();
            }


            public void aboutToHidePanel() {
                if (melody.getFilename() != null && new File(melody.getFilename()).exists()) {
                    setValue("melodytable", midi.getText());
                    setValue("bpm", midi.getMaxBPM());
                } else {
                    setValue("melodytable", "");
                }
                midi.stopRendering();
            }
        });
        registerWizardPanel(LyricsForMIDI.ID,
                            new WizardPanelDescriptor(LyricsForMIDI.ID, lyricsformidi = new LyricsForMIDI(this)) {
                                public Object getNextPanelDescriptor() {
                                    return MP3.ID;
                                }


                                public Object getBackPanelDescriptor() {
                                    return MIDI.ID;
                                }


                                public void aboutToDisplayPanel() {
                                    lyricsformidi.setHyphenations(getValue("hyphenations"));
                                    lyricsformidi.setTable(getValue("melodytable"));
                                    lyricsformidi.setText(getValue("lyrics"));
                                    lyricsformidi.requestFocus();
                                }


                                public void aboutToHidePanel() {
                                    setValue("lyrics", lyricsformidi.getText());
                                    setValue("melodytable", lyricsformidi.getTable());
                                }
                            });
        registerWizardPanel(MP3.ID, new WizardPanelDescriptor(MP3.ID, mp3 = new MP3(this)) {
            public Object getNextPanelDescriptor() {
                return Header.ID;
            }


            public Object getBackPanelDescriptor() {
                if (melody.getFilename() != null && new File(melody.getFilename()).exists()) {
                    String hasLyrics = getValue("haslyrics");
                    if (hasLyrics != null && hasLyrics.equals("yes")) {
                        return MIDI.ID;
                    }
                    return LyricsForMIDI.ID;
                }
                return Lyrics.ID;
            }


            public void aboutToDisplayPanel() {
                mp3.setFilename(getValue("filename"));
            }
        });
        registerWizardPanel(Header.ID, new WizardPanelDescriptor(Header.ID, header = new Header(this)) {
            public Object getNextPanelDescriptor() {
                return Edition.ID;
            }


            public Object getBackPanelDescriptor() {
                return MP3.ID;
            }


            public void aboutToDisplayPanel() {
                header.setGenres(getValue("genres"), getValue("genres-more"));
                header.setLanguages(getValue("languages"), getValue("languages-more"), getValue("language"));
                header.setLanguage(getValue("language"));
                header.setTitle(getValue("title"));
                header.setArtist(getValue("artist"));
                header.setBPM(getValue("bpm"));
                header.setGenre(getValue("genre"));
                String creator = getValue("creator");
                if (StringUtils.isEmpty(creator)) {
                    creator = getProperty("creator");
                }
                header.setCreator(creator);
            }


            public void aboutToHidePanel() {
                header.ensureTableCommit();
                setValue("title", header.getTitle());
                setValue("artist", header.getArtist());
                setValue("genre", header.getGenre());
                setValue("language", header.getLanguage());
                setValue("bpm", header.getBPM());
                setValue("creator", header.getCreator());
                setProperty("creator", header.getCreator());
            }
        });
        registerWizardPanel(Edition.ID, new WizardPanelDescriptor(Edition.ID, edition = new Edition(this)) {
            public Object getNextPanelDescriptor() {
                return Tap.ID;
            }


            public Object getBackPanelDescriptor() {
                return Header.ID;
            }


            public void aboutToDisplayPanel() {
                edition.setSongDir(getValue("songdir"));
                edition.setFolder(getValue("folder"));
            }


            public void aboutToHidePanel() {
                setValue("folder", edition.getFolder());
                setValue("edition", edition.getEdition());
            }
        });
        registerWizardPanel(Tap.ID, new WizardPanelDescriptor(Tap.ID, tap = new Tap(this)) {
            public Object getNextPanelDescriptor() {
                return FINISH;
            }


            public Object getBackPanelDescriptor() {
                return Header.ID;
            }


            public void aboutToDisplayPanel() {
                tap.updateTable();
            }
        });
    }


    /**
     * Description of the Method
     */
    public void show() {
        String s = getValue("melody");
        if (s != null && s.trim().length() > 0) {
            setCurrentPanel(MIDI.ID);
            melody.setFilename(s);
            midi.setFilename(s);
            setValue("melodytable", "");
            midi.startRendering();
        } else {
            setCurrentPanel(Melody.ID);
        }
        setModal(true);
        getDialog().pack();
        getDialog().setSize(new Dimension(600, 480));
        getDialog().setLocationRelativeTo(null);
        getDialog().setVisible(true);
    }


    /**
     * Description of the Method
     */
    public void hide() {
        getDialog().setVisible(false);
    }

    @Override
    public String getValue(String s) {
        return super.getValue(s);
    }

    @Override
    public void setValue(String s, String val) {
        super.setValue(s, val);
    }
    
    public String getProperty(String key) {
        return yassProperties != null ? yassProperties.getProperty(key) : null;
    }
    
    public boolean getBooleanProperty(String key) {
        return yassProperties != null && yassProperties.getBooleanProperty(key);
    }
    
    public void setProperty(String key, String value) {
        if (yassProperties != null) {
            yassProperties.setProperty(key, value);
            yassProperties.store();
        }
    }
}

