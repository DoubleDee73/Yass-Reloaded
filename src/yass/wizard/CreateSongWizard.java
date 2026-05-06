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
import yass.*;
import yass.ffmpeg.FFMPEGLocator;
import yass.integration.separation.SeparationPreference;
import yass.integration.separation.SeparationRequest;
import yass.integration.separation.SeparationResult;
import yass.integration.separation.SeparationService;
import yass.integration.separation.audioseparator.AudioSeparatorSeparationService;
import yass.integration.separation.mvsep.MvsepSeparationService;
import yass.integration.lyrics.lrclib.LrcLibCandidate;
import yass.integration.lyrics.lrclib.LrcLibQueryDialog;
import yass.integration.lyrics.lrclib.LrcLibResultsDialog;
import yass.integration.lyrics.lrclib.LrcLibSearchQuery;
import yass.integration.lyrics.lrclib.LrcLibSearchResponse;
import yass.integration.lyrics.lrclib.LrcLibSearchService;
import yass.alignment.TranscriptTruthRewriteService;
import yass.integration.transcription.TranscriptionEngine;
import yass.integration.transcription.openai.OpenAiTranscriptionRequest;
import yass.integration.transcription.openai.OpenAiTranscriptionResult;
import yass.integration.transcription.openai.OpenAiTranscriptionService;
import yass.integration.transcription.whisperx.WhisperXTranscriptionRequest;
import yass.integration.transcription.whisperx.WhisperXTranscriptionService;
import yass.musicbrainz.MusicBrainz;
import yass.musicbrainz.MusicBrainzInfo;

import javax.swing.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
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
    Tap tap;
    YouTube youtube = null;

    private YassProperties yassProperties;
    private WizardTranscriptionState wizardTranscriptionState;
    private JButton separateAndTranscribeButton;
    private boolean lyricsLrcLibPromptShown;

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
                setLyricsActionButtonsVisible(true);
                if (lyrics != null) {
                    lyrics.setSearchLrcLibAction(new AbstractAction(I18.get("create_lyrics_lrclib_search")) {
                        @Override
                        public void actionPerformed(java.awt.event.ActionEvent e) {
                            startLrcLibSearchFromLyrics();
                        }
                    });
                    lyrics.setPasteLyricsAction(new AbstractAction(I18.get("create_lyrics_paste")) {
                        @Override
                        public void actionPerformed(java.awt.event.ActionEvent e) {
                            pasteLyricsFromClipboard();
                        }
                    });
                }
                setValue("melodytable", "");
                if (wizardTranscriptionState != null && wizardTranscriptionState.getTranscriptionResult() != null) {
                    applyWizardTranscriptionState(wizardTranscriptionState);
                } else {
                    lyrics.setTranscriptionResult(null);
                    lyrics.setText(getValue("lyrics"));
                    lyrics.setSubtitleFile(getValue("subtitle"));
                    lyrics.refreshIntegrationAvailability();
                }
                maybePromptLrcLibSearchOnFirstLyricsDisplay();
            }


            public void aboutToHidePanel() {
                setLyricsActionButtonsVisible(false);
                if (lyrics != null) {
                    lyrics.setSearchLrcLibAction(null);
                    lyrics.setPasteLyricsAction(null);
                }
                setValue("lyrics", lyrics.getText());
                setValue("melodytable", lyrics.getTable());
            }
        });
        initWizardFooterButtons();

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
                                getValue("genre")) || "unknown".equals(getValue("genre")))) {
                            genreFromMusicBrainz = String.join(", ", info.getGenres());
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
                String creator = StringUtils.trimToEmpty(getValue("creator"));
                if (StringUtils.isEmpty(creator)) {
                    creator = StringUtils.trimToEmpty(getProperty("creator"));
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
                String creator = StringUtils.trimToEmpty(header.getCreator());
                setValue("creator", creator);
                setValue("year", header.getYear());
                setProperty("creator", creator);
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
            if (StringUtils.isBlank(value)) {
                yassProperties.remove(key);
            } else {
                yassProperties.setProperty(key, value);
            }
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
        return cleanTitleStatic(title);
    }

    private static String cleanTitleStatic(String title) {
        if (StringUtils.isEmpty(title)) {
            return title;
        }
        // This regex removes text in brackets or parentheses that contains keywords like "video", "lyric", "official", etc.
        return title.replaceAll("(?i)\\s*[(\\[{].*?\\b(official|video|lyric|audio|visualizer|performance|session|HD|HQ|4K|1080p|720p)\\b.*?[)\\]}]", "").trim();
    }

    /**
     * Returns the instance of the Lyrics panel.
     * @return The Lyrics panel.
     */
    public Lyrics getLyricsPanel() {
        return lyrics;
    }

    private void initWizardFooterButtons() {
        if (separateAndTranscribeButton != null) {
            return;
        }
        separateAndTranscribeButton = new JButton(I18.get("create_lyrics_separate_transcribe"));
        separateAndTranscribeButton.addActionListener(e -> startSeparateAndTranscribeFromLyrics());
        separateAndTranscribeButton.setVisible(false);
        separateAndTranscribeButton.setToolTipText(I18.get("create_lyrics_separate_transcribe_tooltip"));

        JPanel bottomLeftPanel = getBottomLeftPanel();
        if (bottomLeftPanel != null) {
            bottomLeftPanel.add(separateAndTranscribeButton);
        }
    }

    void setLyricsActionButtonsVisible(boolean visible) {
        if (separateAndTranscribeButton != null) {
            separateAndTranscribeButton.setVisible(visible);
        }
    }

    public void refreshSeparateAndTranscribeButtonState() {
        if (separateAndTranscribeButton != null) {
            String reason = getSeparateAndTranscribeUnavailableReason();
            boolean enabled = reason == null;
            separateAndTranscribeButton.setEnabled(enabled);
            separateAndTranscribeButton.setToolTipText(enabled ? I18.get("create_lyrics_separate_transcribe_tooltip") : reason);
        }
    }

    public boolean canSeparateAndTranscribeInWizard() {
        return getSeparateAndTranscribeUnavailableReason() == null;
    }

    public String getSeparateAndTranscribeUnavailableReason() {
        String filename = getValue("filename");
        if (StringUtils.isBlank(filename) || !new File(filename).isFile()) {
            return I18.get("create_lyrics_separate_transcribe_requires_audio");
        }
        boolean mvsepConfigured = StringUtils.isNotBlank(getProperty("mvsep-api-token"));
        boolean audioSepConfigured = StringUtils.isNotBlank(StringUtils.defaultIfBlank(getProperty(AudioSeparatorSeparationService.PROP_PYTHON),
                                                                                      getProperty(PythonRuntimeSupport.PROP_DEFAULT_PYTHON)))
                && Boolean.parseBoolean(StringUtils.defaultIfBlank(getProperty(AudioSeparatorSeparationService.PROP_HEALTH_OK), "false"));
        if (!mvsepConfigured && !audioSepConfigured) {
            return I18.get("create_lyrics_separate_transcribe_requires_mvsep");
        }
        if (!hasAnyTranscriptionEngine()) {
            return I18.get("edit_align_transcription_missing_engine");
        }
        if (getPreferredTranscriptionEngine() == TranscriptionEngine.WHISPERX
                && !Boolean.parseBoolean(StringUtils.defaultIfBlank(getProperty("whisperx-health-ok"), "false"))) {
            return I18.get("create_lyrics_separate_transcribe_requires_healthcheck");
        }
        return null;
    }

    private boolean hasOpenAiApiKey() {
        return StringUtils.isNotBlank(getProperty("openai-api-key"));
    }

    private boolean hasWhisperXConfiguration() {
        boolean useModule = Boolean.parseBoolean(getProperty("whisperx-use-module"));
        return useModule
                ? StringUtils.isNotBlank(StringUtils.defaultIfBlank(getProperty("whisperx-python"),
                                                                   getProperty(PythonRuntimeSupport.PROP_DEFAULT_PYTHON)))
                : StringUtils.isNotBlank(getProperty("whisperx-command"));
    }

    private boolean hasAnyTranscriptionEngine() {
        return hasOpenAiApiKey() || hasWhisperXConfiguration();
    }

    private TranscriptionEngine getPreferredTranscriptionEngine() {
        TranscriptionEngine configured = TranscriptionEngine.fromValue(getProperty("transcription-engine"));

        switch (configured) {
            case ONLINE_FIRST -> {
                if (hasOpenAiApiKey() && isOnlineReachable()) {
                    return TranscriptionEngine.OPENAI;
                }
                if (hasWhisperXConfiguration()) {
                    LOGGER.info("Online transcription unavailable, falling back to WhisperX.");
                    return TranscriptionEngine.WHISPERX;
                }
                return TranscriptionEngine.OPENAI;
            }
            case LOCAL_FIRST -> {
                if (hasWhisperXConfiguration()) {
                    return TranscriptionEngine.WHISPERX;
                }
                LOGGER.info("Local transcription unavailable, falling back to OpenAI.");
                return TranscriptionEngine.OPENAI;
            }
            case OPENAI -> { return TranscriptionEngine.OPENAI; }
            case WHISPERX -> { return TranscriptionEngine.WHISPERX; }
            default -> { return TranscriptionEngine.OPENAI; }
        }
    }

    private SeparationService buildSeparationService() {
        boolean audioSepConfigured = StringUtils.isNotBlank(StringUtils.defaultIfBlank(getProperty(AudioSeparatorSeparationService.PROP_PYTHON),
                                                                                      getProperty(PythonRuntimeSupport.PROP_DEFAULT_PYTHON)))
                && Boolean.parseBoolean(StringUtils.defaultIfBlank(getProperty(AudioSeparatorSeparationService.PROP_HEALTH_OK), "false"));
        boolean mvsepConfigured = StringUtils.isNotBlank(getProperty("mvsep-api-token"));
        SeparationPreference preference = SeparationPreference.fromValue(getProperty("separation-preference"));

        switch (preference) {
            case ONLINE_FIRST -> {
                if (mvsepConfigured && isOnlineReachable()) {
                    LOGGER.info("Using MVSEP for wizard vocal separation (online-first).");
                    return new MvsepSeparationService(yassProperties);
                }
                if (audioSepConfigured) {
                    LOGGER.info("Online separation unavailable, falling back to audio-separator.");
                    return new AudioSeparatorSeparationService(yassProperties);
                }
                return new MvsepSeparationService(yassProperties);
            }
            case MVSEP_ONLY -> {
                LOGGER.info("Using MVSEP for wizard vocal separation (MVSEP only).");
                return new MvsepSeparationService(yassProperties);
            }
            case AUDIO_SEP_ONLY -> {
                LOGGER.info("Using audio-separator for wizard vocal separation (audio-separator only).");
                return new AudioSeparatorSeparationService(yassProperties);
            }
            default -> {
                // LOCAL_FIRST: prefer audio-separator, fall back to MVSEP
                if (audioSepConfigured) {
                    LOGGER.info("Using audio-separator for wizard vocal separation (local-first).");
                    return new AudioSeparatorSeparationService(yassProperties);
                }
                LOGGER.info("Using MVSEP for wizard vocal separation.");
                return new MvsepSeparationService(yassProperties);
            }
        }
    }

    /**
     * Quick connectivity check: attempts a TCP connection to api.openai.com:443.
     * Returns true if reachable within 3 seconds, false otherwise.
     */
    private boolean isOnlineReachable() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("api.openai.com", 443), 3000);
            return true;
        } catch (java.io.IOException ex) {
            LOGGER.info("Online reachability check failed: " + ex.getMessage());
            return false;
        }
    }

    public WizardTranscriptionState getWizardTranscriptionState() {
        return wizardTranscriptionState;
    }

    private void pasteLyricsFromClipboard() {
        String clipboardText = readClipboardText();
        if (clipboardText == null) {
            JOptionPane.showMessageDialog(getDialog(),
                    I18.get("create_lyrics_paste_clipboard_unavailable"),
                    I18.get("create_lyrics_paste"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String normalizedClipboardText = normalizeClipboardLyrics(clipboardText);
        if (StringUtils.isBlank(normalizedClipboardText)) {
            JOptionPane.showMessageDialog(getDialog(),
                    I18.get("create_lyrics_paste_clipboard_empty"),
                    I18.get("create_lyrics_paste"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        OpenAiTranscriptionResult transcriptionResult = wizardTranscriptionState != null ? wizardTranscriptionState.getTranscriptionResult() : null;
        String transcriptText = "";
        if (transcriptionResult != null) {
            TranscriptTruthRewriteService rewriteService = new TranscriptTruthRewriteService();
            OpenAiTranscriptionResult alignedTranscript = rewriteService.alignToReferenceLines(transcriptionResult, normalizedClipboardText);
            transcriptText = normalizeClipboardLyrics(alignedTranscript != null ? alignedTranscript.getTranscriptText() : transcriptionResult.getTranscriptText());
        }
        if (StringUtils.isBlank(transcriptText)) {
            applyLyricsText(normalizedClipboardText);
            return;
        }
        ClipboardLyricsDiffDialog dialog = new ClipboardLyricsDiffDialog(getDialog(), transcriptText, normalizedClipboardText);
        ClipboardLyricsDiffDialog.Result diffResult = dialog.showDialog();
        if (diffResult != null) {
            applyLyricsTexts(diffResult.transcriptText(), diffResult.clipboardText());
        }
    }

    private void startLrcLibSearchFromLyrics() {
        LrcLibSearchQuery initialQuery = buildInitialLrcLibQuery();
        persistSuggestedArtistAndTitle(initialQuery);
        LrcLibSearchQuery query = new LrcLibSearchQuery(
                StringUtils.trimToEmpty(getValue("artist")),
                StringUtils.trimToEmpty(cleanTitle(getValue("title"))));
        if (StringUtils.isNotBlank(query.artist()) && StringUtils.isNotBlank(query.title())) {
            executeLrcLibSearch(query, false);
            return;
        }
        promptForLrcLibSearch(initialQuery);
    }

    private void maybePromptLrcLibSearchOnFirstLyricsDisplay() {
        if (lyricsLrcLibPromptShown) {
            return;
        }
        lyricsLrcLibPromptShown = true;
        LrcLibSearchQuery initialQuery = buildInitialLrcLibQuery();
        persistSuggestedArtistAndTitle(initialQuery);
        SwingUtilities.invokeLater(() -> promptForArtistAndTitle(initialQuery));
    }

    private void promptForArtistAndTitle(LrcLibSearchQuery initialQuery) {
        LrcLibSearchQuery query = LrcLibQueryDialog.show(
                getDialog(),
                I18.get("create_lyrics_metadata_query_title"),
                I18.get("create_lyrics_lrclib_artist"),
                I18.get("create_lyrics_lrclib_title"),
                I18.get("create_lyrics_metadata_query_required"),
                initialQuery.artist(),
                initialQuery.title());
        if (query == null) {
            return;
        }
        setValue("artist", query.artist());
        setValue("title", query.title());
    }

    private void promptForLrcLibSearch(LrcLibSearchQuery initialQuery) {
        LrcLibSearchQuery query = LrcLibQueryDialog.show(
                getDialog(),
                I18.get("create_lyrics_lrclib_query_title"),
                I18.get("create_lyrics_lrclib_artist"),
                I18.get("create_lyrics_lrclib_title"),
                I18.get("create_lyrics_lrclib_query_required"),
                initialQuery.artist(),
                initialQuery.title());
        if (query == null) {
            return;
        }

        executeLrcLibSearch(query, false);
    }

    private void persistSuggestedArtistAndTitle(LrcLibSearchQuery query) {
        if (query == null) {
            return;
        }
        if (StringUtils.isBlank(getValue("artist")) && StringUtils.isNotBlank(query.artist())) {
            setValue("artist", query.artist());
        }
        if (StringUtils.isBlank(getValue("title")) && StringUtils.isNotBlank(query.title())) {
            setValue("title", query.title());
        }
    }

    private void executeLrcLibSearch(LrcLibSearchQuery query, boolean allowInsecureTlsFallback) {
        if (query == null) {
            return;
        }

        setValue("artist", query.artist());
        setValue("title", query.title());

        JLabel progressLabel = new JLabel(I18.get("create_lyrics_lrclib_search_progress"));
        progressLabel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        JDialog progressDialog = new JDialog(getDialog(), I18.get("create_lyrics_lrclib_search"), true);
        progressDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        progressDialog.getContentPane().add(progressLabel, BorderLayout.CENTER);
        progressDialog.pack();
        progressDialog.setLocationRelativeTo(getDialog());

        SwingWorker<LrcLibSearchResponse, Void> worker = new SwingWorker<>() {
            @Override
            protected LrcLibSearchResponse doInBackground() throws Exception {
                return new LrcLibSearchService().search(query.artist(), query.title(), allowInsecureTlsFallback);
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    LrcLibSearchResponse response = get();
                    if (response == null || response.getCandidates().isEmpty()) {
                        JOptionPane.showMessageDialog(getDialog(),
                                I18.get("create_lyrics_lrclib_search_no_results"),
                                I18.get("create_lyrics_lrclib_search"),
                                JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    LrcLibResultsDialog resultsDialog = new LrcLibResultsDialog(
                            getDialog(),
                            I18.get("create_lyrics_lrclib_results_title"),
                            I18.get("create_lyrics_lrclib_apply"),
                            I18.get("create_lyrics_lrclib_compare"),
                            wizardTranscriptionState != null && wizardTranscriptionState.getTranscriptionResult() != null,
                            response.getCandidates(),
                            response.getPreferredCandidateId());
                    LrcLibResultsDialog.Result dialogResult = resultsDialog.showDialog();
                    if (dialogResult == null || dialogResult.candidate() == null) {
                        return;
                    }

                    LrcLibCandidate selectedCandidate = dialogResult.candidate();
                    if (dialogResult.compareWithTranscript()) {
                        compareLrcLibLyricsWithTranscript(selectedCandidate);
                        return;
                    }

                    OpenAiTranscriptionResult result = new LrcLibSearchService().toTranscriptionResult(selectedCandidate);
                    wizardTranscriptionState = new WizardTranscriptionState(null, null, null, null, result);
                    applyWizardTranscriptionState(wizardTranscriptionState);
                    if (lyrics != null) {
                        lyrics.setWizardStatusText(I18.get("create_lyrics_lrclib_status_ready"));
                        lyrics.refreshIntegrationAvailability();
                    }
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    LOGGER.log(Level.INFO, "LRCLib search failed", cause);
                    if (cause instanceof yass.integration.lyrics.lrclib.LrcLibCertificateException) {
                        int decision = JOptionPane.showConfirmDialog(getDialog(),
                                I18.get("create_lyrics_lrclib_insecure_prompt"),
                                I18.get("create_lyrics_lrclib_search"),
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.WARNING_MESSAGE);
                        if (decision == JOptionPane.YES_OPTION) {
                            executeLrcLibSearch(query, true);
                        }
                        return;
                    }
                    JOptionPane.showMessageDialog(getDialog(),
                            cause.getMessage(),
                            I18.get("create_lyrics_lrclib_search"),
                            JOptionPane.ERROR_MESSAGE);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }

    private LrcLibSearchQuery buildInitialLrcLibQuery() {
        return resolveInitialLrcLibQuery(getValue("artist"),
                                         getValue("title"),
                                         getValue("filename"),
                                         getValue("video"));
    }

    static LrcLibSearchQuery resolveInitialLrcLibQuery(String artistValue, String titleValue, String filename,
                                                       String video) {
        String artist = normalizeSuggestedMetadataPart(artistValue);
        String title = normalizeSuggestedMetadataPart(cleanTitleStatic(titleValue));
        if (StringUtils.isNotBlank(artist) && StringUtils.isNotBlank(title)) {
            return new LrcLibSearchQuery(artist, title);
        }

        String path = StringUtils.firstNonBlank(filename, video);
        if (StringUtils.isBlank(path)) {
            return new LrcLibSearchQuery(artist, title);
        }

        String baseName = new File(path).getName();
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) {
            baseName = baseName.substring(0, dot);
        }
        String[] parts = baseName.split("\\s+-\\s+", 2);
        if (parts.length == 2) {
            if (StringUtils.isBlank(artist)) {
                artist = normalizeSuggestedMetadataPart(parts[0]);
            }
            if (StringUtils.isBlank(title)) {
                title = normalizeSuggestedMetadataPart(cleanTitleStatic(parts[1].trim()));
            }
        } else if (StringUtils.isBlank(title)) {
            title = normalizeSuggestedMetadataPart(cleanTitleStatic(baseName.trim()));
        }
        return new LrcLibSearchQuery(artist, title);
    }

    private static String normalizeSuggestedMetadataPart(String value) {
        String normalized = StringUtils.trimToEmpty(value);
        if (StringUtils.isBlank(normalized)) {
            return normalized;
        }
        String repaired = repairMojibake(normalized);
        return StringUtils.trimToEmpty(repaired.replaceAll("\\s+", " "));
    }

    private static String repairMojibake(String value) {
        String normalized = StringUtils.defaultString(value);
        if (!looksLikeMojibake(normalized)) {
            return normalized;
        }
        String repaired = new String(normalized.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        return looksLessBroken(repaired, normalized) ? repaired : normalized;
    }

    private static boolean looksLikeMojibake(String value) {
        return value.contains("Ã") || value.contains("â€™") || value.contains("â€“")
                || value.contains("â€œ") || value.contains("â€") || value.contains("Â");
    }

    private static boolean looksLessBroken(String repaired, String original) {
        return weirdCharacterScore(repaired) < weirdCharacterScore(original);
    }

    private static int weirdCharacterScore(String value) {
        int score = 0;
        for (char c : value.toCharArray()) {
            if (c == 'Ã' || c == 'â' || c == 'Â' || c == '�') {
                score++;
            }
        }
        return score;
    }

    private void applyLyricsText(String text) {
        applyLyricsTexts(null, text);
    }

    private void compareLrcLibLyricsWithTranscript(LrcLibCandidate candidate) {
        OpenAiTranscriptionResult transcriptionResult =
                wizardTranscriptionState != null ? wizardTranscriptionState.getTranscriptionResult() : null;
        if (candidate == null || transcriptionResult == null) {
            return;
        }
        String plainLyrics = normalizeClipboardLyrics(candidate.getPlainLyrics());
        if (StringUtils.isBlank(plainLyrics)) {
            JOptionPane.showMessageDialog(getDialog(),
                    I18.get("create_lyrics_paste_clipboard_empty"),
                    I18.get("create_lyrics_lrclib_search"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        copyTextToClipboard(plainLyrics);
        String transcriptText = normalizeClipboardLyrics(transcriptionResult.getTranscriptText());
        ClipboardLyricsDiffDialog dialog = new ClipboardLyricsDiffDialog(getDialog(), transcriptText, plainLyrics);
        ClipboardLyricsDiffDialog.Result diffResult = dialog.showDialog();
        if (diffResult != null) {
            applyLyricsTexts(diffResult.transcriptText(), diffResult.clipboardText());
            if (lyrics != null) {
                lyrics.setWizardStatusText(I18.get("create_lyrics_lrclib_compare_ready"));
                lyrics.refreshIntegrationAvailability();
            }
        }
    }

    private void applyLyricsTexts(String transcriptStructureText, String finalLyricsText) {
        String normalizedFinalLyrics = normalizeClipboardLyrics(finalLyricsText);
        String normalizedTranscriptStructure = normalizeClipboardLyrics(transcriptStructureText);
        if (wizardTranscriptionState != null && wizardTranscriptionState.getTranscriptionResult() != null) {
            TranscriptTruthRewriteService rewriteService = new TranscriptTruthRewriteService();
            OpenAiTranscriptionResult baseTranscript = wizardTranscriptionState.getTranscriptionResult();
            OpenAiTranscriptionResult rewritten;
            if (StringUtils.isNotBlank(normalizedTranscriptStructure)) {
                OpenAiTranscriptionResult structuredTranscript = rewriteService.rewrite(baseTranscript, normalizedTranscriptStructure);
                rewritten = rewriteService.applyLyricsToStructuredTranscript(structuredTranscript, normalizedFinalLyrics);
            } else {
                rewritten = rewriteService.rewrite(baseTranscript, normalizedFinalLyrics);
            }
            wizardTranscriptionState = wizardTranscriptionState.withTranscriptionResult(rewritten);
            applyWizardTranscriptionState(wizardTranscriptionState);
        } else {
            setValue("lyrics", normalizedFinalLyrics);
            if (lyrics != null) {
                lyrics.setText(normalizedFinalLyrics);
            }
        }
        if (lyrics != null) {
            lyrics.setWizardStatusText(I18.get("create_lyrics_paste_status_ready"));
            lyrics.refreshIntegrationAvailability();
        }
    }

    private String readClipboardText() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clipboard == null || !clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                return null;
            }
            Object value = clipboard.getData(DataFlavor.stringFlavor);
            return value instanceof String ? (String) value : null;
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Could not read clipboard lyrics", ex);
            return null;
        }
    }

    private void copyTextToClipboard(String text) {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clipboard != null) {
                clipboard.setContents(new StringSelection(StringUtils.defaultString(text)), null);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Could not write clipboard lyrics", ex);
        }
    }

    private String normalizeClipboardLyrics(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        normalized = normalized.replaceAll("[\t\u000B\f]+", " ");
        normalized = normalized.replaceAll("(?m)[ \t]+$", "");
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String rawLine : normalized.split("\n", -1)) {
            String line = StringUtils.trimToEmpty(rawLine);
            if (YassUtils.isSongPartLine(line)) {
                continue;
            }
            lines.add(line);
        }
        return String.join("\n", lines);
    }

    public void startSeparateAndTranscribeFromLyrics() {

        String unavailableReason = getSeparateAndTranscribeUnavailableReason();
        if (unavailableReason != null) {
            JOptionPane.showMessageDialog(getDialog(), unavailableReason, I18.get("create_lyrics_separate_transcribe"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        File sourceAudio = new File(getValue("filename"));
        WizardTranscriptionState reusableState = findReusableWizardTranscriptionState(sourceAudio);
        if (reusableState != null) {
            int reuse = JOptionPane.showConfirmDialog(getDialog(),
                    I18.get("create_lyrics_separate_transcribe_cached_prompt"),
                    I18.get("create_lyrics_separate_transcribe"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (reuse == JOptionPane.YES_OPTION) {
                applyWizardTranscriptionState(reusableState);
                return;
            }
        }

        JLabel statusLabel = new JLabel(I18.get("create_lyrics_separate_transcribe_progress_prepare"));
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        JLabel latestOutputLabel = new JLabel(" ");
        latestOutputLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        latestOutputLabel.setFont(latestOutputLabel.getFont().deriveFont(Math.max(10f, latestOutputLabel.getFont().getSize2D() - 1f)));
        JTextArea statusHistory = new JTextArea(10, 68);
        statusHistory.setEditable(false);
        statusHistory.setLineWrap(true);
        statusHistory.setWrapStyleWord(true);
        statusHistory.setMargin(new Insets(6, 8, 6, 8));
        statusHistory.setText(I18.get("create_lyrics_separate_transcribe_progress_prepare"));
        JScrollPane statusScrollPane = new JScrollPane(statusHistory,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        statusScrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)));

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);

        JLabel hintLabel = new JLabel("<html>" + I18.get("create_lyrics_separate_transcribe_hint") + "</html>");
        hintLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        JPanel centerPanel = new JPanel(new BorderLayout(0, 8));
        JPanel statusPanel = new JPanel(new GridLayout(2, 1, 0, 6));
        statusPanel.add(statusLabel);
        statusPanel.add(latestOutputLabel);
        centerPanel.add(statusPanel, BorderLayout.NORTH);
        centerPanel.add(statusScrollPane, BorderLayout.CENTER);

        JButton cancelButton = new JButton(I18.get("create_lyrics_separate_transcribe_cancel"));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(cancelButton);

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(hintLabel, BorderLayout.NORTH);
        panel.add(centerPanel, BorderLayout.CENTER);
        JPanel southPanel = new JPanel(new BorderLayout(0, 4));
        southPanel.add(progressBar, BorderLayout.NORTH);
        southPanel.add(buttonPanel, BorderLayout.SOUTH);
        panel.add(southPanel, BorderLayout.SOUTH);

        JDialog progressDialog = new JDialog(getDialog(), I18.get("create_lyrics_separate_transcribe"), true);
        progressDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        progressDialog.getContentPane().add(panel);
        progressDialog.setSize(760, 400);
        progressDialog.setLocationRelativeTo(getDialog());

        SwingWorker<WizardTranscriptionState, String> worker = new SwingWorker<>() {
            @Override
            protected WizardTranscriptionState doInBackground() throws Exception {
                String runBaseName = buildWizardRunBaseName(sourceAudio);

                // Check if separation was already done in a previous run — reuse stems if found.
                WizardTranscriptionState existingState = loadPersistedWizardTranscriptionState(sourceAudio);
                File runDirectory;
                File wavFile;
                SeparationResult separationResult;
                boolean separationReused = existingState != null
                        && existingState.getSeparationResult() != null
                        && existingState.getSeparationResult().getVocalsFile() != null
                        && existingState.getSeparationResult().getVocalsFile().isFile();

                if (separationReused) {
                    LOGGER.info("Reusing existing separation from " + existingState.getRunDirectory().getAbsolutePath());
                    publish("Reusing existing vocal separation...");
                    runDirectory = existingState.getRunDirectory();
                    wavFile = existingState.getSourceConvertedFile();
                    separationResult = existingState.getSeparationResult();
                    // If a cached transcription already exists too, return it immediately.
                    if (existingState.getTranscriptionResult() != null) {
                        return existingState;
                    }
                } else {
                    runDirectory = createWizardRunDirectory();
                    publish(I18.get("create_lyrics_separate_transcribe_progress_convert"));
                    wavFile = convertWizardSourceToWav(sourceAudio, runDirectory, runBaseName);

                    SeparationService separationService = buildSeparationService();
                    SeparationRequest request;
                    if (separationService instanceof AudioSeparatorSeparationService as) {
                        request = as.createRequest(runDirectory, wavFile, runBaseName);
                        publish(I18.get("create_lyrics_separate_transcribe_progress_audiosep"));
                    } else {
                        request = ((MvsepSeparationService) separationService).createRequest(runDirectory, wavFile, runBaseName);
                        publish(I18.get("create_lyrics_separate_transcribe_progress_mvsep"));
                    }
                    separationResult = separationService.startSeparation(request, this::publish);
                    // Write metadata right after separation so the run directory is identifiable
                    // even if transcription is cancelled or fails later.
                    WizardTranscriptionState partialState = new WizardTranscriptionState(runDirectory, sourceAudio, wavFile, separationResult, null);
                    persistWizardRunMetadata(partialState, runBaseName);
                }

                File vocalsFile = separationResult.getVocalsFile();
                if (vocalsFile == null || !vocalsFile.isFile()) {
                    throw new IllegalStateException(I18.get("create_lyrics_separate_transcribe_missing_vocals"));
                }

                // Ask the user whether to proceed with transcription before starting the (potentially costly) API call.
                publish(I18.get("create_lyrics_separate_transcribe_separation_done"));
                boolean[] proceedWithTranscription = {true};
                try {
                    javax.swing.SwingUtilities.invokeAndWait(() -> {
                        int choice = JOptionPane.showConfirmDialog(
                                progressDialog,
                                I18.get("create_lyrics_separate_transcribe_confirm_transcribe"),
                                I18.get("create_lyrics_separate_transcribe_confirm_transcribe_title"),
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.QUESTION_MESSAGE);
                        proceedWithTranscription[0] = (choice == JOptionPane.YES_OPTION);
                    });
                } catch (java.lang.reflect.InvocationTargetException ex) {
                    LOGGER.log(Level.WARNING, "Error showing transcription confirmation dialog.", ex);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for transcription confirmation.", ex);
                }
                if (!proceedWithTranscription[0]) {
                    cancel(false);
                    return new WizardTranscriptionState(runDirectory, sourceAudio, wavFile, separationResult, null);
                }

                File cacheDir = new File(runDirectory, StringUtils.defaultIfBlank(getProperty("whisperx-cache-folder"), ".yass-cache"));
                TranscriptionEngine engine = getPreferredTranscriptionEngine();
                YassTable tempTable = new YassTable();
                tempTable.init(yassProperties);
                OpenAiTranscriptionResult transcriptionResult;
                if (engine == TranscriptionEngine.WHISPERX) {
                    WhisperXTranscriptionService whisperXService = new WhisperXTranscriptionService(yassProperties);
                    WhisperXTranscriptionRequest transcriptionRequest = whisperXService.createRequest(vocalsFile,
                                                                                                      "#VOCALS",
                                                                                                      cacheDir,
                                                                                                      runBaseName);
                    publish(I18.get("create_lyrics_separate_transcribe_progress_whisperx"));
                    transcriptionResult = whisperXService.transcribe(transcriptionRequest, tempTable, message -> publish(message));
                } else {
                    OpenAiTranscriptionService openAiService = new OpenAiTranscriptionService(yassProperties);
                    OpenAiTranscriptionRequest transcriptionRequest = openAiService.createRequest(vocalsFile, "#VOCALS", runBaseName);
                    publish(I18.get("edit_align_transcription_progress"));
                    transcriptionResult = openAiService.transcribe(transcriptionRequest, tempTable);
                }
                WizardTranscriptionState state = new WizardTranscriptionState(runDirectory,
                                                    sourceAudio,
                                                    wavFile,
                                                    separationResult,
                                                    transcriptionResult);
                persistWizardRunMetadata(state, runBaseName);
                return state;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                if (!chunks.isEmpty()) {
                    String latest = chunks.get(chunks.size() - 1);
                    statusLabel.setText(latest);
                    latestOutputLabel.setText(formatWhisperXProgressLine(latest));
                    for (String chunk : chunks) {
                        if (StringUtils.isBlank(chunk)) {
                            continue;
                        }
                        if (statusHistory.getDocument().getLength() > 0) {
                            statusHistory.append(System.lineSeparator());
                        }
                        statusHistory.append(chunk);
                    }
                    statusHistory.setCaretPosition(statusHistory.getDocument().getLength());
                }
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    wizardTranscriptionState = get();
                    applyWizardTranscriptionState(wizardTranscriptionState);
                    JOptionPane.showMessageDialog(getDialog(),
                                                  I18.get("create_lyrics_separate_transcribe_done"),
                                                  I18.get("create_lyrics_separate_transcribe"),
                                                  JOptionPane.INFORMATION_MESSAGE);
                } catch (CancellationException ex) {
                    if (lyrics != null) {
                        lyrics.refreshIntegrationAvailability();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    LOGGER.severe("Separate & Transcribe failed: " + cause.getMessage());
                    if (lyrics != null) {
                        lyrics.setWizardStatusText(I18.get("create_lyrics_separate_transcribe_status_failed"));
                        lyrics.refreshIntegrationAvailability();
                    }
                    JOptionPane.showMessageDialog(getDialog(), cause.getMessage(), I18.get("create_lyrics_separate_transcribe"), JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        cancelButton.addActionListener(e -> {
            cancelButton.setEnabled(false);
            worker.cancel(true);
        });
        worker.execute();
        progressDialog.setVisible(true);
    }

    private WizardTranscriptionState findReusableWizardTranscriptionState(File sourceAudio) {
        if (wizardTranscriptionState != null
                && sourceAudio != null
                && sourceAudio.isFile()
                && wizardTranscriptionState.getSourceAudioFile() != null
                && sourceAudio.equals(wizardTranscriptionState.getSourceAudioFile())
                && wizardTranscriptionState.getTranscriptionResult() != null) {
            return wizardTranscriptionState;
        }
        WizardTranscriptionState persistedState = loadPersistedWizardTranscriptionState(sourceAudio);
        if (persistedState != null) {
            wizardTranscriptionState = persistedState;
            // Only offer reuse if transcription is complete — separation-only states are
            // handled transparently by the worker (it skips re-separation automatically).
            if (persistedState.getTranscriptionResult() == null) {
                return null;
            }
        }
        return persistedState;
    }

    private String formatWhisperXProgressLine(String value) {
        if (StringUtils.isBlank(value)) {
            return " ";
        }
        String escaped = value.replace("&", "&amp;")
                              .replace("<", "&lt;")
                              .replace(">", "&gt;");
        if (escaped.length() > 220) {
            escaped = escaped.substring(0, 217) + "...";
        }
        return "<html><b>Latest output:</b> " + escaped + "</html>";
    }

    private void applyWizardTranscriptionState(WizardTranscriptionState state) {
        if (state == null || state.getTranscriptionResult() == null) {
            if (lyrics != null) {
                lyrics.setTranscriptionResult(null);
            }
            return;
        }
        OpenAiTranscriptionResult result = state.getTranscriptionResult();
        Map<Integer, String> subtitles = new yass.alignment.TranscriptNoteRebuildService().deriveDisplayPhrases(result);
        String displayLyrics = subtitles.isEmpty()
                ? result.getTranscriptText()
                : String.join(System.lineSeparator(), subtitles.values());
        setValue("lyrics", displayLyrics);
        if (lyrics != null) {
            // Build a subtitles map (ms → line text) from segments so that getTable()
            // can derive a correct #GAP and beat positions instead of defaulting to 0.
            lyrics.setSubtitles(subtitles);
            lyrics.setTranscriptionResult(result);
            lyrics.setText(displayLyrics);
            lyrics.setWizardStatusText(I18.get("create_lyrics_separate_transcribe_status_ready"));
            lyrics.refreshIntegrationAvailability();
        }
    }
    private WizardTranscriptionState loadPersistedWizardTranscriptionState(File sourceAudio) {
        if (sourceAudio == null || !sourceAudio.isFile()) {
            return null;
        }
        File wizardBaseDir = getWizardBaseDirectory();
        String expectedBaseName = buildWizardRunBaseName(sourceAudio);
        String currentYouTubeId = YouTube.extractYouTubeId(getValue("youtube"));

        // Fast path: check the named directory directly (YouTube ID or base name)
        String expectedDirName = buildWizardRunDirectoryName();
        File namedDir = new File(wizardBaseDir, expectedDirName);
        if (namedDir.isDirectory()) {
            try {
                WizardTranscriptionState state = tryLoadWizardRunState(namedDir, sourceAudio, expectedBaseName, currentYouTubeId);
                if (state != null) {
                    LOGGER.info("Reusing persisted wizard separation/transcription from " + namedDir.getAbsolutePath());
                    return state;
                }
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "Named wizard run directory not usable: " + namedDir.getAbsolutePath(), ex);
            }
        }

        // Fallback: scan all subdirectories (covers legacy run-* dirs and other cases)
        File[] runDirectories = wizardBaseDir.listFiles(f -> f.isDirectory() && !f.equals(namedDir));
        if (runDirectories == null || runDirectories.length == 0) {
            return null;
        }
        Arrays.sort(runDirectories, Comparator.comparingLong(File::lastModified).reversed());
        for (File runDirectory : runDirectories) {
            try {
                WizardTranscriptionState state = tryLoadWizardRunState(runDirectory, sourceAudio, expectedBaseName, currentYouTubeId);
                if (state != null) {
                    LOGGER.info("Reusing persisted wizard separation/transcription from " + runDirectory.getAbsolutePath());
                    return state;
                }
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "Ignoring unusable wizard run directory " + runDirectory.getAbsolutePath(), ex);
            }
        }
        return null;
    }

    private WizardTranscriptionState tryLoadWizardRunState(File runDirectory, File sourceAudio, String expectedBaseName, String currentYouTubeId) throws IOException {
        Properties metadata = loadWizardRunMetadata(runDirectory);
        String metadataSource = StringUtils.trimToEmpty(metadata.getProperty("sourceAudioPath"));
        String metadataBaseName = StringUtils.trimToEmpty(metadata.getProperty("songBaseName"));
        String metadataYouTubeId = StringUtils.trimToEmpty(metadata.getProperty("youTubeId"));
        boolean metadataMatches = StringUtils.isNotBlank(metadataSource) && sourceAudio.getAbsolutePath().equalsIgnoreCase(metadataSource);
        boolean baseNameMatches = StringUtils.isNotBlank(metadataBaseName) && metadataBaseName.equalsIgnoreCase(expectedBaseName);
        boolean youTubeIdMatches = StringUtils.isNotBlank(currentYouTubeId) && currentYouTubeId.equalsIgnoreCase(metadataYouTubeId);

        File vocalsFile = findRunStem(runDirectory, "(Vocals)", "(Lead)");
        if (vocalsFile == null || !vocalsFile.isFile()) {
            return null;
        }
        // The YouTube ID stored in metadata is the most reliable identifier for wizard runs.
        boolean stemNamedAfterBaseName = vocalsFile.getName().toLowerCase().startsWith(expectedBaseName.toLowerCase());
        if (!metadataMatches && !youTubeIdMatches && !baseNameMatches && !stemNamedAfterBaseName) {
            return null;
        }

        SeparationResult separationResult = new SeparationResult(
                findRunStem(runDirectory, "(Vocals)"),
                findRunStem(runDirectory, "(Lead)"),
                findRunStem(runDirectory, "(Instrumental)", "(Karaoke)", "(No Vocals)"),
                findRunStem(runDirectory, "(Instrumental + Backing)")
        );
        File[] sourceFiles = runDirectory.listFiles((dir, name) -> !name.contains("(") && !name.equals("wizard-run.properties"));
        File sourceFile = (sourceFiles != null && sourceFiles.length > 0) ? sourceFiles[0] : null;

        // Try to load a cached transcription if one exists — but a separation-only state
        // (null transcription) is still valid and allows skipping re-separation.
        File cacheDir = new File(runDirectory, StringUtils.defaultIfBlank(getProperty("whisperx-cache-folder"), ".yass-cache"));
        File cacheFile = new File(cacheDir, "vocals-transcript.json");
        if (!cacheFile.isFile()) {
            File[] transcriptCandidates = cacheDir.listFiles((dir, name) -> name.toLowerCase().endsWith("-transcript.json") || name.toLowerCase().endsWith(".json"));
            if (transcriptCandidates != null && transcriptCandidates.length > 0) {
                Arrays.sort(transcriptCandidates, Comparator.comparingLong(File::lastModified).reversed());
                cacheFile = transcriptCandidates[0];
            }
        }
        if (!cacheFile.isFile()) {
            return new WizardTranscriptionState(runDirectory, sourceAudio, sourceFile, separationResult, null);
        }

        WhisperXTranscriptionService whisperXService = new WhisperXTranscriptionService(yassProperties);
        WhisperXTranscriptionRequest request = whisperXService.createRequest(vocalsFile, "#VOCALS", cacheDir,
                StringUtils.defaultIfBlank(metadataBaseName, expectedBaseName));
        YassTable tempTable = new YassTable();
        tempTable.init(yassProperties);
        return new WizardTranscriptionState(runDirectory,
                sourceAudio,
                sourceFile,
                separationResult,
                whisperXService.loadCachedTranscription(request, tempTable));
    }

    private Properties loadWizardRunMetadata(File runDirectory) {
        Properties properties = new Properties();
        File metadataFile = new File(runDirectory, "wizard-run.properties");
        if (!metadataFile.isFile()) {
            return properties;
        }
        try (InputStream input = Files.newInputStream(metadataFile.toPath())) {
            properties.load(input);
        } catch (IOException ex) {
            LOGGER.log(Level.FINE, "Could not read wizard run metadata from " + metadataFile.getAbsolutePath(), ex);
        }
        return properties;
    }

    private void persistWizardRunMetadata(WizardTranscriptionState state, String songBaseName) {
        File runDirectory = state.getRunDirectory();
        if (runDirectory == null || !runDirectory.isDirectory()) {
            return;
        }
        Properties metadata = new Properties();
        metadata.setProperty("sourceAudioPath", StringUtils.defaultString(state.getSourceAudioFile() != null ? state.getSourceAudioFile().getAbsolutePath() : ""));
        metadata.setProperty("songBaseName", StringUtils.defaultString(songBaseName));
        String youTubeId = YouTube.extractYouTubeId(getValue("youtube"));
        if (StringUtils.isNotBlank(youTubeId)) {
            metadata.setProperty("youTubeId", youTubeId);
        }
        File metadataFile = new File(runDirectory, "wizard-run.properties");
        try (OutputStream output = Files.newOutputStream(metadataFile.toPath())) {
            metadata.store(output, "Wizard separation/transcription metadata");
        } catch (IOException ex) {
            LOGGER.log(Level.FINE, "Could not write wizard run metadata to " + metadataFile.getAbsolutePath(), ex);
        }
    }

    private File findRunStem(File runDirectory, String... markers) {
        File[] candidates = runDirectory.listFiles(File::isFile);
        if (candidates == null) {
            return null;
        }
        for (String marker : markers) {
            if (StringUtils.isBlank(marker)) {
                continue;
            }
            for (File candidate : candidates) {
                if (candidate.getName().contains(marker)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private File getWizardBaseDirectory() {
        File tempBaseDir = new File(StringUtils.defaultIfBlank(getProperty("temp-dir"), System.getProperty("java.io.tmpdir")));
        return new File(tempBaseDir, "wizard-separation");
    }

    private File createWizardRunDirectory() throws IOException {
        File wizardBaseDir = getWizardBaseDirectory();
        Files.createDirectories(wizardBaseDir.toPath());
        String dirName = buildWizardRunDirectoryName();
        File runDirectory = new File(wizardBaseDir, dirName);
        Files.createDirectories(runDirectory.toPath());
        return runDirectory;
    }

    private String buildWizardRunDirectoryName() {
        String youTubeId = YouTube.extractYouTubeId(getValue("youtube"));
        if (StringUtils.isNotBlank(youTubeId)) {
            return youTubeId;
        }
        String artist = StringUtils.trimToEmpty(getValue("artist"));
        String title = StringUtils.trimToEmpty(getValue("title"));
        if (StringUtils.isNotBlank(artist) && StringUtils.isNotBlank(title)) {
            return (artist + " - " + title).replaceAll("[\\\\/:*?\"<>|]", "_");
        }
        String filename = StringUtils.trimToEmpty(getValue("filename"));
        if (StringUtils.isNotBlank(filename)) {
            String base = new File(filename).getName();
            int dot = base.lastIndexOf('.');
            return (dot > 0 ? base.substring(0, dot) : base).replaceAll("[\\\\/:*?\"<>|]", "_");
        }
        return "run-" + System.currentTimeMillis();
    }

    private File convertWizardSourceToWav(File sourceAudio, File runDirectory, String baseName) throws IOException {
        FFMPEGLocator.initFfmpeg(yassProperties.getProperty("ffmpegPath"));
        String safeName = baseName.replaceAll("[\\\\/:*?\"<>|]", "_");
        File wavTarget = new File(runDirectory, safeName + ".wav");
        YassPlayer player = new YassPlayer(null, yassProperties);
        File converted = player.generateTemp(sourceAudio.getAbsolutePath(), Timebase.NORMAL, wavTarget.getAbsolutePath());
        if (converted == null || !converted.isFile()) {
            throw new IllegalStateException(I18.get("create_lyrics_separate_transcribe_convert_failed"));
        }
        return converted;
    }

    private String buildWizardRunBaseName(File sourceAudio) {
        String artist = StringUtils.trimToEmpty(getValue("artist"));
        String title = StringUtils.trimToEmpty(getValue("title"));
        if (StringUtils.isNotBlank(artist) && StringUtils.isNotBlank(title)) {
            return artist + " - " + title;
        }
        String fileName = sourceAudio.getName();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}

