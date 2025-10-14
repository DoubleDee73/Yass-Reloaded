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
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import yass.I18;
import yass.YassProperties;
import yass.YassTable;
import yass.musicbrainz.MusicBrainz;
import yass.musicbrainz.MusicBrainzInfo;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Description of the Class
 *
 * @author Saruta
 */
@Getter
@Setter
public class CreateSongWizard extends Wizard {
    static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    MP3 mp3 = null;
    Header header = null;
    Edition edition = null;
    Melody melody = null;
    Lyrics lyrics = null;
    LyricsForMIDI lyricsformidi = null;
    MIDI midi = null;
    Tap tap = null;
    YouTube youtube = null;

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
        WizardMidiMode midiMode = WizardMidiMode.valueOf(yassProperties.getProperty("wizard-skip-midi", "USE_MIDI"));

        if (StringUtils.isNotEmpty(yassProperties.getProperty("ytdlp-version"))) {
            registerWizardPanel(YouTube.ID, new WizardPanelDescriptor(YouTube.ID, youtube = new YouTube(this)) {
                public Object getNextPanelDescriptor() {
                    return midiMode == WizardMidiMode.SKIP_MIDI ? Lyrics.ID : Melody.ID;
                }
    
                public Object getBackPanelDescriptor() {
                    return null;
                }
    
                public void aboutToDisplayPanel() {
                    youtube.setYouTubeUrl(getValue("youtube"));
                }

                public void aboutToHidePanel() {
                    youtube.setYouTubeUrl(youtube.getYouTubeUrl());
                    youtube.downloadFromYouTube();
                }
            });
        }
        registerWizardPanel(Melody.ID, new WizardPanelDescriptor(Melody.ID, melody = new Melody(this)) {
            public Object getNextPanelDescriptor() {
                if (melody.getFilename() != null && new File(melody.getFilename()).exists()) {
                    return MIDI.ID;
                }
                return Lyrics.ID;
            }

            public Object getBackPanelDescriptor() {
                return youtube != null ? YouTube.ID : null;
            }
            
            public void aboutToDisplayPanel() {
                melody.setFilename(getValue("melody"));
            }
        });
        melody.setWizardMidiMode(midiMode);
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
                String filename = getValue("filename");
                mp3.setFilename(filename);
            }
            public void aboutToHidePanel() {
                String filename = getValue("filename");
                if (StringUtils.isEmpty(filename)) {
                    return;
                }
                String temp = filename.substring(0, filename.lastIndexOf(".")) + ".mp4";
                if (Path.of(temp).toFile().exists()) {
                    setValue("video", temp);
                }
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
                String artist = getValue("artist");
                String title = cleanTitle(getValue("title"));
                header.setGenres(getValue("genres"), getValue("genres-more"));
                header.setLanguages(getValue("languages"), getValue("languages-more"), getValue("language"));
                header.setLanguage(getValue("language"));
                header.setTitle(title);
                header.setArtist(artist);
                header.setBPM(getValue("bpm"));
                if (StringUtils.isNotEmpty(artist) && !artist.equals("UnknownArtist") &&
                        StringUtils.isNotEmpty(title) && !title.equals("UnknownTitle")) {

                    MusicBrainz mb = new MusicBrainz();
                    final MusicBrainzInfo info;
                    try {
                        info = mb.queryMusicBrainz(artist, title);
                        if (info.getYear() != null && StringUtils.isEmpty(header.getYear())) {
                            header.setYear(info.getYear());
                        }
                        String genreFromMusicBrainz = null;
                        if (info.getGenres() != null && !info.getGenres().isEmpty() && (StringUtils.isEmpty(
                                header.getGenre()) || header.getGenre().equalsIgnoreCase("unknown"))) {
                            genreFromMusicBrainz = info.getGenres().get(0);
                            setValue("genre", genreFromMusicBrainz);
                        }
                        header.setGenre(genreFromMusicBrainz != null ? genreFromMusicBrainz : getValue("genre"));
                    } catch (Exception e) {
                        LOGGER.log(Level.INFO, "Failed to query MusicBrainz", e);
                    }
                } else {
                    header.setGenre(getValue("genre"));
                    header.setYear(getValue("year"));
                }
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
                setValue("year", header.getYear());
                setProperty("creator", header.getCreator());
                setProperty("wizard-midi",
                            melody != null && StringUtils.isNotEmpty(melody.getFilename()) ? "true" : "");
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
        boolean showYouTube = StringUtils.isNotEmpty(yassProperties.getProperty("ytdlp-version"));
        String s = showYouTube ? getValue("youtube") : getValue("melody");
        if (StringUtils.isNotEmpty(s)) {
            if (!showYouTube) {
                setCurrentPanel(MIDI.ID);
                melody.setFilename(s);
                midi.setFilename(s);
                setValue("melodytable", "");
                midi.startRendering();
            } else {
                setCurrentPanel(Melody.ID);
            }
        } else {
            setCurrentPanel(showYouTube ? YouTube.ID : Melody.ID);
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

    /**
     * Removes common video-related suffixes from a song title.
     * e.g., "My Song (Official Music Video)" becomes "My Song".
     *
     * @param title The original title.
     * @return The cleaned title.
     */
    private String cleanTitle(String title) {
        if (StringUtils.isEmpty(title)) {
            return title;
        }
        // This regex removes text in brackets or parentheses that contains keywords like "video", "lyric", "official", etc.
        return title.replaceAll("(?i)\\s*[(\\[{].*?\\b(official|video|lyric|audio|visualizer|performance|session|HD|HQ|4K|1080p|720p)\\b.*?[)\\]}]", "").trim();
    }
}
