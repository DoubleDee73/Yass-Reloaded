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

package yass;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class YassProperties extends Properties {
    private static final long serialVersionUID = -8189893110989853544L;
    private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private final String userDir = System.getProperty("user.home");
    private final String yassDir = ".yass";
    private final String userProps = "user.xml";
    private Hashtable<Object, Object> defaultProperties = null;

    public YassProperties() {
        super();
        load();
    }

    public boolean checkVersion() {
        String v = getProperty("yass-version");
        boolean old = v == null;
        if (!old) {
            old = v.startsWith("0.");
        }
        if (!old) {
            old = v.startsWith("1.");
        }
        if (!old) {
            old = v.startsWith("2.0");
        }
        if (!old) {
            old = v.startsWith("2.1.0");
        }
        if (!old) {
            old = v.startsWith("2.1.1");
        }
        return old;
    }

    public String getUserDir() {
        return userDir + File.separator + yassDir;
    }

    public String getDefaultProperty(String key) {
        if (defaultProperties == null) {
            defaultProperties = new Hashtable<>();
            setDefaultProperties(defaultProperties);
        }
        return (String)defaultProperties.get(key);
    }

    public void load() {
        String propFile = userDir + File.separator + yassDir + File.separator + userProps;
        LOGGER.info("Loading properties: " + propFile);

        try {
            FileInputStream fis = new FileInputStream(propFile);
            loadFromXML(fis);
            fis.close();
            loadDevices();

            if (getProperty("note-naming-h") == null)
                setProperty("note-naming-h", "DE RU PL NO FI SE");

            if (getProperty("key-17") == null)
                setProperty("key-17", "B");
            if (getProperty("key-18") == null)
                setProperty("key-18", "N");
            if (getProperty("before_next_ms") == null)
                setProperty("before_next_ms", "300");
            setupTags();
            setupHyphenationDictionaries();
            return;
        } catch (Exception e) {
            // not exists
        }
        // user props not found; fall back to defaults
        setDefaultProperties(this);
        loadDevices();
        setupHyphenationDictionaries();
    }

    private void setupTags() {
        URL tagDefinition = YassMain.class.getResource("/yass/resources/tags.txt");
        if (tagDefinition == null) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(Path.of(tagDefinition.toURI()));
            setProperty("tags-tag", lines.stream().sorted().collect(Collectors.joining("|")));
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private void loadDevices() {
        String[] mics = YassCaptureAudio.getDeviceNames();
        StringBuffer sb = new StringBuffer();
        int i = 0;
        int n = mics.length;
        for (String m : mics) {
            sb.append(m);
            if (i != n - 1) sb.append("|");
            i++;
        }
        String newMics = sb.toString();
        String oldMics = getProperty("control-mics");

        String mic = getProperty("control-mic");
        if (mic == null || mic.trim().length() < 1) {
            for (String m : mics) {
                if (m.contains("USBMIC")) {
                    mic = m;
                    put("control-mic", mic);
                    break;
                }
            }
        }
        if (!oldMics.equals(newMics)) {
            put("control-mics", newMics);
            store();
        }
        LOGGER.info("Mics: " + newMics);
        LOGGER.info("Selecting Mic: " + mic);
    }

    public void setDefaultProperties(Hashtable<Object,Object> p) {

        p.putIfAbsent("default-programs", "C:/Program Files/Ultrastar Deluxe|C:/Program Files (x86)/UltraStar Deluxe|C:/Program Files/Ultrastar|C:/Programme/Ultrastar Deluxe|C:/Ultrastar Deluxe|C:/Programme/Ultrastar|C:/Ultrastar|D:/Ultrastar|E:/Ultrastar|F:/Ultrastar|/home/.ultrastardx|/home/.ultrastar|C:/Program Files (x86)/UltraStar Deluxe WorldParty|C:/Program Files/UltraStar Deluxe WorldParty|D:/UltraStar Deluxe WorldParty|C:/Program Files (x86)/Vocaluxe|C:/Program Files/Vocaluxe|D:/Vocaluxe|C:/Program Files (x86)/Performous|C:/Program Files/Performous");
        // dirs
        p.putIfAbsent("song-directory", userDir + File.separator + "Songs");
        p.putIfAbsent("playlist-directory", userDir + File.separator + "Playlists");
        p.putIfAbsent("cover-directory", userDir + File.separator + "Covers");
        p.putIfAbsent("import-directory", "");

        p.putIfAbsent("yass-version", YassActions.VERSION);
        p.putIfAbsent("ultrastar-format-version", UltrastarHeaderTagVersion.UNITY.version);
        p.putIfAbsent("ffmpegPath", "");

        p.putIfAbsent("yass-language", "default");
        p.putIfAbsent("yass-languages", "default|en|de|hu|pl|es");

        //filetype association
        p.putIfAbsent("song-filetype", ".txt");
        p.putIfAbsent("playlist-filetype", ".upl");

        p.putIfAbsent("audio-files", ".mp3");
        p.putIfAbsent("image-files", ".jpg|.jpeg");
        p.putIfAbsent("video-files", ".mpg|.mpeg|.avi|.divx");
        p.putIfAbsent("cover-id", "[CO]");
        p.putIfAbsent("background-id", "[BG]");
        p.putIfAbsent("video-id", "[VD#*]");
        p.putIfAbsent("videodir-id", "[VIDEO]");

        p.putIfAbsent("correct-uncommon-pagebreaks", "unknown");
        p.putIfAbsent("correct-uncommon-spacing", "");

        p.putIfAbsent("articles", "EN:the |a |an :DE:der |die |das |ein |eine :FR:le |la |les |l'|un |une |des :ES:el |la |los |las:HU:a |az ");
        p.putIfAbsent("use-articles", "true");

        // print
        p.putIfAbsent("songlist-pdf", userDir + File.separator + yassDir + File.separator + "songlist.pdf");

        // cache
        p.putIfAbsent("songlist-cache", userDir + File.separator + yassDir + File.separator + "songlist.txt");
        p.putIfAbsent("playlist-cache", userDir + File.separator + yassDir + File.separator + "playlists.txt");
        p.putIfAbsent("songlist-imagecache", userDir + File.separator + yassDir + File.separator + "covers-cache");
        p.putIfAbsent("temp-dir", userDir + File.separator + yassDir + File.separator + "temp");
        p.putIfAbsent("lyrics-cache", userDir + File.separator + yassDir + File.separator + "lyrics.txt");

        // metadata
        p.putIfAbsent("language-tag", "English|EN|German|DE|Spanish|ES|French|FR|Other|NN");
        p.putIfAbsent("language-more-tag", "Chinese|CH|Croatian|HR|Danish|DA|Hungarian|HU|Italian|IT|Japanese|JA|Korean|KR|Polish|PL|Russian|RU|Swedish|SV|Turkish|TR");
        // ISO 639-2 http|//www.loc.gov/standards/iso639-2/php/English_list.php
        p.putIfAbsent("genre-tag", "Blues|Darkwave|Musical|Metal|Oldies|Pop|Punk|Reggae|Rock|Other");
        p.putIfAbsent("genre-more-tag", "Acid|Alternative|Anime|Classical|Country|Dance|Death Metal|Disco|Electronic|Folk|Funk|Game|Gangsta|Gospel|Gothic|Grunge|Hip-Hop|House|Industrial|Jazz|JPop|MORE|New Age|Noise|R&B|Rave|Rap|Retro|Rock & Roll|Showtunes|Ska|Soundtrack|Soul|Techno|Trance|Tribal|Vocal");
        // http|//de.wikipedia.org/wiki/Liste_der_ID3-Genres
        p.putIfAbsent("edition-tag", "Birthday Party|Child's Play|Christmas|Greatest Hits|Halloween|Hits of the 60s|Kuschelrock|New Year's Eve|Summer|Sodamakers");
        // http|//de.wikipedia.org/wiki/Compilation
        p.putIfAbsent("tags-tag", "");
        p.putIfAbsent("note-naming-h", "DE RU PL NO FI SE");
        p.putIfAbsent("duetsinger-tag", "P");
        // errors
        p.putIfAbsent("valid-tags", "TITLE ARTIST LANGUAGE EDITION <br> GENRE ALBUM YEAR CREATOR AUTHOR ID <br> MP3 COVER BACKGROUND VIDEO VIDEOGAP START END DUETSINGERP1 DUETSINGERP2 DUETSINGERP3 DUETSINGERP4 P1 P2 P3 P4 RELATIVE BPM GAP LENGTH PREVIEWSTART MEDLEYSTARTBEAT MEDLEYENDBEAT ENCODING COMMENT ");
        p.putIfAbsent("valid-lines", "#:*FRG-EP");
        p.putIfAbsent("max-points", "7500");
        p.putIfAbsent("max-golden", "1250");
        p.putIfAbsent("max-linebonus", "1000");
        p.putIfAbsent("golden-allowed-variance", "250");
        p.putIfAbsent("freestyle-counts", "false");
        p.putIfAbsent("touching-syllables", "false");
        p.putIfAbsent("correct-uncommon-pagebreaks-fix", "0");

        p.putIfAbsent("font-file", "Candara Bold");
        p.putIfAbsent("font-files", "Arial Bold|Candara Bold|Roboto Regular");
        p.putIfAbsent("font-file-custom", "");
        p.putIfAbsent("font-size", "28");
        p.putIfAbsent("char-spacing", "0");
        p.putIfAbsent("text-max-width", "800");

        p.putIfAbsent("cover-max-size", "128");
        p.putIfAbsent("cover-min-width", "200");
        p.putIfAbsent("cover-max-width", "800");
        p.putIfAbsent("cover-ratio", "100");
        p.putIfAbsent("use-cover-ratio", "true");

        p.putIfAbsent("background-max-size", "1024");
        p.putIfAbsent("background-min-width", "800");
        p.putIfAbsent("background-max-width", "1024");
        p.putIfAbsent("background-ratio", "133");
        p.putIfAbsent("use-background-ratio", "true");

        //mic
        p.putIfAbsent("control-mic", "");
        p.putIfAbsent("control-mics", "");

        //editor
        p.putIfAbsent("lyrics-font-size", "14");

        p.putIfAbsent("note-color-8", "#dd9966"); // warning
        p.putIfAbsent("note-color-7", "#dd6666"); // error
        p.putIfAbsent("note-color-6", "#ffccff"); // freestyle
        p.putIfAbsent("note-color-5", "#66aa66"); // rap
        p.putIfAbsent("note-color-4", "#bbffbb"); // golden rap
        p.putIfAbsent("note-color-3", "#f0f066"); // golden
        p.putIfAbsent("note-color-2", "#4488cc"); // active
        p.putIfAbsent("note-color-1", "#777777"); // shade
        p.putIfAbsent("note-color-0", "#dddddd"); // note
        p.putIfAbsent("shade-notes", "true");
        p.putIfAbsent("dark-mode", "false");

        p.putIfAbsent("color-7", "#cccccc");
        p.putIfAbsent("color-6", "#444444");
        p.putIfAbsent("color-5", "#88cccc");
        p.putIfAbsent("color-4", "#cc88cc");
        p.putIfAbsent("color-3", "#f09944");
        p.putIfAbsent("color-2", "#f0f044");
        p.putIfAbsent("color-1", "#88cc44");
        p.putIfAbsent("color-0", "#4488cc");

        p.putIfAbsent("hi-color-7", "#669966");
        p.putIfAbsent("hi-color-6", "#666699");
        p.putIfAbsent("hi-color-5", "#994d99");
        p.putIfAbsent("hi-color-4", "#99994d");
        p.putIfAbsent("hi-color-3", "#4d9999");
        p.putIfAbsent("hi-color-2", "#4d4d99");
        p.putIfAbsent("hi-color-1", "#4d994d");
        p.putIfAbsent("hi-color-0", "#4d4d4d");

        p.putIfAbsent("group-title", "all|#|A|B|C|D|E|F|G|H|I|J|K|L|M|N|O|P|Q|R|S|T|U|V|W|X|Y|Z");
        p.putIfAbsent("group-artist", "all|generic|others");
        p.putIfAbsent("group-genre", "all|unspecified|generic");
        p.putIfAbsent("group-language", "all|unspecified|English;German|generic");
        p.putIfAbsent("group-edition", "all|unspecified|generic");
        p.putIfAbsent("group-album", "all|unspecified|generic");
        p.putIfAbsent("group-playlist", "all|generic|unspecified");
        p.putIfAbsent("group-year", "all|unspecified|1930-1939|1940-1949|1950-1959|1960-1969|1970-1979|1980-1989|1990-1999|2000-2009|2010-2019|2020-2029|generic");
        p.putIfAbsent("group-folder", "all|generic");
        p.putIfAbsent("group-length", "all|unspecified|0 - 1:30|1:31 - 3:00|3:01 - 4:30|4:31 - 6:00|6:01 -");
        p.putIfAbsent("group-files", "all|duplicates|video|no_video|no_video_background|no_background|no_cover|uncommon_filenames|uncommon_cover_size|uncommon_background_size");
        p.putIfAbsent("group-format", "all|encoding_utf8|encoding_ansi|encoding_other|audio_vbr|audio_ogg");
        p.putIfAbsent("group-tags", "all|previewstart|medleystartbeat|start|end|relative|gap<5|gap>30|gap>60|bpm<200|bpm>400");
        p.putIfAbsent("group-errors", "all|all_errors|critical_errors|major_errors|tag_errors|file_errors|page_errors|text_errors");
        p.putIfAbsent("group-duets", "all|solos|duets|trios|quartets|choirs");
        p.putIfAbsent("group-stats", "all|pages_common|pages_pages_0-30|pages_pages_31-80|pages_pages_81-|golden_golden_3-20|rap_rap_1-|freestyle_freestyle_0-30|notesperpage_notesperpage_0-5|notesperpage_notesperpage_5.1-|speeddist_slow|speeddist_averagespeed|speeddist_fast|pitchrange_monoton|pitchrange_melodic|pitchrange_smooth|pitchrange_bumpy|speeddist_longbreath");
        p.putIfAbsent("group-instrument", "all|white|black|12-white|8-white|25-keys|13-keys");

        p.putIfAbsent("group-min", "3");

        p.putIfAbsent("hyphenations", "EN|DE|ES|FR|IT|PL|PT|RU|TR|ZH");
        p.putIfAbsent("dicts", "EN|DE");
        p.putIfAbsent("dict-map", "English|EN|German|DE|French|EN|Croatian|EN|Hungarian|EN|Italian|EN|Japanese|EN|Polish|EN|Russian|EN|Spanish|EN|Swedish|EN|Turkish|EN");
        p.putIfAbsent("user-dicts", userDir + File.separator + yassDir);

        p.putIfAbsent("utf8-without-bom", "true");
        p.putIfAbsent("utf8-always", "true");
        p.putIfAbsent("usdbsyncer-always-pin", "false");
        //p.put("duet-sequential", "true");

        p.putIfAbsent("floatable", "false");
        p.putIfAbsent("options_autosave_interval", "300");

        p.putIfAbsent("mouseover", "false");
        p.putIfAbsent("sketching", "false");
        p.putIfAbsent("sketching-playback", "false");
        p.putIfAbsent("show-note-heightnum", "false");
        p.putIfAbsent("show-note-height", "true");
        p.putIfAbsent("show-note-length", "true");
        p.putIfAbsent("show-note-beat", "false");
        p.putIfAbsent("show-note-scale", "false");
        p.putIfAbsent("auto-trim", "false");
        p.putIfAbsent("playback-buttons", "true");
        p.putIfAbsent("record-timebase", "2");

        p.putIfAbsent("use-sample", "true");
        p.putIfAbsent("typographic-apostrophes", "false");
        p.putIfAbsent("capitalize-rows", "false");

        // 0=next_note, 1=prev_note, 2=page_down, 3=page_up, 4=init, 5=init_next, 6=right, 7=left, 8=up, 9=down, 10=lengthen, 11=shorten, 12=play, 13=play_page, 14=scroll_left, 15=scroll_right, 16=one_page
        p.putIfAbsent("key-0", "NUMPAD6");
        p.putIfAbsent("key-1", "NUMPAD4");
        p.putIfAbsent("key-2", "NUMPAD2");
        p.putIfAbsent("key-3", "NUMPAD8");

        p.putIfAbsent("key-4", "NUMPAD0");
        p.putIfAbsent("key-5", "DECIMAL");

        p.putIfAbsent("key-6", "NUMPAD3");
        p.putIfAbsent("key-7", "NUMPAD1");
        p.putIfAbsent("key-8", "SUBTRACT");
        p.putIfAbsent("key-9", "ADD");
        p.putIfAbsent("key-10", "NUMPAD9");
        p.putIfAbsent("key-11", "NUMPAD7");

        p.putIfAbsent("key-12", "NUMPAD5");
        p.putIfAbsent("key-13", "P");
        p.putIfAbsent("key-14", "H");
        p.putIfAbsent("key-15", "J");
        p.putIfAbsent("key-16", "K");
        p.putIfAbsent("key-17", "B");
        p.putIfAbsent("key-18", "N");

        p.putIfAbsent("screenkey-0", "ESCAPE");
        p.putIfAbsent("screenkey-1", "PAUSE");
        p.putIfAbsent("screenkey-2", "F5");

        p.putIfAbsent("screenkey-3", "UP");
        p.putIfAbsent("screenkey-4", "RIGHT");
        p.putIfAbsent("screenkey-5", "DOWN");
        p.putIfAbsent("screenkey-6", "LEFT");
        p.putIfAbsent("screenkey-7", "ENTER");

        p.putIfAbsent("screenkey-8", "A");
        p.putIfAbsent("screenkey-9", "S");
        p.putIfAbsent("screenkey-10", "D");
        p.putIfAbsent("screenkey-11", "F");
        p.putIfAbsent("screenkey-12", "G");
        p.putIfAbsent("screenkey-13", "H");
        p.putIfAbsent("screenkey-14", "J");
        p.putIfAbsent("screenkey-15", "K");
        p.putIfAbsent("screenkey-16", "L");
        p.putIfAbsent("screenkey-17", "OEM_3");
        p.putIfAbsent("screenkey-18", "OEM_7");
        p.putIfAbsent("screenkey-19", "OEM_2");

        p.putIfAbsent("screenkey-20", "UP|NUMPAD");
        p.putIfAbsent("screenkey-21", "RIGHT|NUMPAD");
        p.putIfAbsent("screenkey-22", "DOWN|NUMPAD");
        p.putIfAbsent("screenkey-23", "LEFT|NUMPAD");
        p.putIfAbsent("screenkey-24", "ENTER|NUMPAD");

        p.putIfAbsent("screenkey-25", "---");
        p.putIfAbsent("screenkey-26", "---");
        p.putIfAbsent("screenkey-27", "---");
        p.putIfAbsent("screenkey-28", "---");
        p.putIfAbsent("screenkey-29", "---");
        p.putIfAbsent("screenkey-30", "---");
        p.putIfAbsent("screenkey-31", "---");
        p.putIfAbsent("screenkey-32", "---");
        p.putIfAbsent("screenkey-33", "---");
        p.putIfAbsent("screenkey-34", "---");
        p.putIfAbsent("screenkey-35", "---");
        p.putIfAbsent("screenkey-36", "---");

        p.putIfAbsent("screenkey-37", "W");
        p.putIfAbsent("screenkey-38", "D");
        p.putIfAbsent("screenkey-39", "S");
        p.putIfAbsent("screenkey-40", "A");
        p.putIfAbsent("screenkey-41", "SPACE");

        p.putIfAbsent("screenkey-42", "---");
        p.putIfAbsent("screenkey-43", "---");
        p.putIfAbsent("screenkey-44", "---");
        p.putIfAbsent("screenkey-45", "---");
        p.putIfAbsent("screenkey-46", "---");
        p.putIfAbsent("screenkey-47", "---");
        p.putIfAbsent("screenkey-48", "---");
        p.putIfAbsent("screenkey-49", "---");
        p.putIfAbsent("screenkey-50", "---");
        p.putIfAbsent("screenkey-51", "---");
        p.putIfAbsent("screenkey-52", "---");
        p.putIfAbsent("screenkey-53", "---");

        p.putIfAbsent("player1_difficulty", "1");
        p.putIfAbsent("player2_difficulty", "1");
        p.putIfAbsent("player3_difficulty", "1");
        p.putIfAbsent("game_sorting", "edition");
        p.putIfAbsent("game_group", "all");
        p.putIfAbsent("game_mode", "lines");

        p.putIfAbsent("screen-groups", "title|artist|genre|language|edition|folder|playlist|year");

        // advanced
        p.putIfAbsent("before_next_ms", "300");
        p.putIfAbsent("seek-in-offset", "0");
        p.putIfAbsent("seek-out-offset", "0");
        p.putIfAbsent("seek-in-offset-ms", "0");
        p.putIfAbsent("seek-out-offset-ms", "0");
        p.putIfAbsent("print-plugins", "yass.print.PrintBlocks|yass.print.PrintPlain|yass.print.PrintPlainLandscape|yass.print.PrintDetails");
        p.putIfAbsent("filter-plugins", "yass.filter.YassTitleFilter|yass.filter.YassArtistFilter|yass.filter.YassLanguageFilter|yass.filter.YassEditionFilter|yass.filter.YassGenreFilter|yass.filter.YassAlbumFilter|yass.filter.YassPlaylistFilter|yass.filter.YassYearFilter|yass.filter.YassLengthFilter|yass.filter.YassFolderFilter|yass.filter.YassFilesFilter|yass.filter.YassFormatFilter|yass.filter.YassTagsFilter|yass.filter.YassMultiPlayerFilter|yass.filter.YassInstrumentFilter|yass.filter.YassErrorsFilter|yass.filter.YassStatsFilter");
        p.putIfAbsent("stats-plugins", "yass.stats.YassBasicStats|yass.stats.YassTimeStats|yass.stats.YassPitchStats");
        p.putIfAbsent("screen-plugins", "yass.screen.YassStartScreen|yass.screen.YassSelectGameScreen|yass.screen.YassSelectControllerScreen|yass.screen.YassSelectDeviceScreen|yass.screen.YassSelectDifficultyScreen|yass.screen.YassSelectSortingScreen|yass.screen.YassSelectGroupScreen|yass.screen.YassSelectSongScreen|yass.screen.YassPlaySongScreen|yass.screen.YassViewScoreScreen|yass.screen.YassHighScoreScreen|yass.screen.YassEnterScoreScreen|yass.screen.YassJukeboxScreen|yass.screen.YassCreditsScreen|yass.screen.YassStatsScreen");
        p.putIfAbsent("jukebox", "highscore:topten-beginner|highscore:topten-standard|highscore:topten-expert|jukebox:topten|credits:yass|credits:thirdparty|credits:thanks|jukebox:random");

        p.putIfAbsent("debug-memory", "false");
        p.putIfAbsent("debug-score", "false");
        p.putIfAbsent("debug-waveform", "false");

        //piano
        p.putIfAbsent("piano-volume", "100");

        //non-editable
        p.putIfAbsent("welcome", "true");
        p.putIfAbsent("recent-files", "");
    }

    public String getProperty(String key, String[] var, String[] val) {
        String s = getProperty(key);
        for (int i = 0; i < var.length; i++) {
            s = YassUtils.replace(s, var[i], val[i]);
        }
        return s;
    }

    public String getProperty(String key, String var, String val) {
        String s = getProperty(key);
        s = YassUtils.replace(s, var, val);
        return s;
    }

    public String getProperty(String key, String var1, String val1, String var2, String val2) {
        String s = getProperty(key);
        s = YassUtils.replace(s, var1, val1);
        s = YassUtils.replace(s, var2, val2);
        return s;
    }

    public String getProperty(String key, String var1, String val1, String var2, String val2, String var3, String val3) {
        String s = getProperty(key);
        s = YassUtils.replace(s, var1, val1);
        s = YassUtils.replace(s, var2, val2);
        s = YassUtils.replace(s, var3, val3);
        return s;
    }

    public String getProperty(String key, String var1, String val1, String var2, String val2, String var3, String val3, String var4, String val4) {
        String s = getProperty(key);
        s = YassUtils.replace(s, var1, val1);
        s = YassUtils.replace(s, var2, val2);
        s = YassUtils.replace(s, var3, val3);
        s = YassUtils.replace(s, var4, val4);
        return s;
    }

    public String getProperty(String key, String var1, String val1, String var2, String val2, String var3, String val3, String var4, String val4, String var5, String val5) {
        String s = getProperty(key);
        s = YassUtils.replace(s, var1, val1);
        s = YassUtils.replace(s, var2, val2);
        s = YassUtils.replace(s, var3, val3);
        s = YassUtils.replace(s, var4, val4);
        s = YassUtils.replace(s, var5, val5);
        return s;
    }

    public void store() {
        String propDir = userDir + File.separator + yassDir;
        File propDirFile = new File(propDir);
        if (!propDirFile.exists()) {
            boolean ok = propDirFile.mkdir();
            if (!ok) {
                JOptionPane.showMessageDialog(null, "Cannot write properties to " + propDir, "Store properties", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        String propFile = propDir + File.separator + userProps;
        try {
            FileOutputStream fos = new FileOutputStream(propFile);
            storeToXML(fos, null);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Error in storing properties to " + propFile, "Store properties", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean getBooleanProperty(String s) {
        String p = getProperty(s);
        return p != null && p.equals("true");
    }

    public int getIntProperty(String key) {
        String strValue = getProperty(key);
        return NumberUtils.toInt(strValue, 0);
    }

    public boolean isUncommonSpacingAfter() {
        return "after".equals(getProperty("correct-uncommon-spacing"));
    }

    public boolean isLegacyDuet() {
        if (isUnityOrNewer()) {
            return false;
        }
        return "DUETSINGERP".equals(getProperty("duetsinger-tag", "P"));
    }

    public UltrastarHeaderTagVersion getUsFormatVersion() {
        return UltrastarHeaderTagVersion.getFormatVersion(getProperty("ultrastar-format-version"));
    }

    public void setupHyphenationDictionaries() {
        String hyphenationLanguages = getProperty("hyphenations");
        if (StringUtils.isEmpty(hyphenationLanguages)) {
            LOGGER.info("No hyphenation languages have been setup, skipping this now...");
            return;
        }
        Map<String, String> languageMap = initLanguageMap();
        Set<Path> paths = findPath(List.of("UltraStar-Creator"));
        if (paths == null || paths.isEmpty()) {
            LOGGER.info("No valid program paths were found, skipping this now...");
            return;
        }
        String[] languages = hyphenationLanguages.split("\\|");
        boolean changes = false;
        for (String language : languages) {
            String prop = getProperty("hyphenations_" + language);
            if (StringUtils.isEmpty(prop)) {
                if (languageMap.get(language) == null) {
                    LOGGER.fine("Language " + language + " is not supported, skipping this now...");
                    continue;
                }
                for (Path path : paths) {
                    Path dictionary = Path.of(path.toString(), languageMap.get(language));
                    if (Files.exists(dictionary)) {
                        changes = true;
                        setProperty("hyphenations_" + language, dictionary.toString());
                        continue;
                    }
                    LOGGER.info("Dictionary for " + language + " was not supported, skipping this now...");
                }
            }
        }
        if (changes) {
            store();
        }
    }

    private Map<String, String> initLanguageMap() {
        Map<String, String> languageMap = new HashMap<>();
        languageMap.put("EN", "English.txt");
        languageMap.put("FR", "French.txt");
        languageMap.put("DE", "German.txt");
        languageMap.put("IT", "Italian.txt");
        languageMap.put("PL", "Polish.txt");
        languageMap.put("PT", "Portuguese.txt");
        languageMap.put("ES", "Spanish.txt");
        languageMap.put("SE", "Swedish.txt");
        return languageMap;
    }

    private Set<Path> findPath(List<String> additionalPrograms) {
        String defaultPaths = getProperty("default-programs");
        if (StringUtils.isEmpty(defaultPaths)) {
            return null;
        }
        Set<Path> validPaths = new HashSet<>();
        Set<String> parentPaths = new HashSet<>();
        String[] paths = defaultPaths.split("\\|");
        for (String path : paths) {
            Path tempPath = Path.of(path);
            if (!Files.exists(tempPath)) {
                continue;
            }
            parentPaths.add(tempPath.toString());
            parentPaths.add(tempPath.getParent().toString());
            validPaths.add(tempPath);
        }
        if (validPaths.isEmpty()) {
            return null;
        }
        for (String additionalProgram : additionalPrograms) {
             for (String parentPath : parentPaths) {
                 Path tempPath = Path.of(parentPath, additionalProgram);
                 if (!Files.exists(tempPath)) {
                     continue;
                 }
                 validPaths.add(tempPath);
             }
        }
        return validPaths;
    }

    public boolean isUnityOrNewer() {
        return getUsFormatVersion().getNumericVersion() >= 1d;
    }

    public boolean isShinyOrNewer() {
        return getUsFormatVersion().getNumericVersion() >= 1.1d;
    }
}

