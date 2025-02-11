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

import javafx.embed.swing.JFXPanel;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.job.FFmpegJob;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.id3.ID3v23Frame;
import org.tritonus.share.sampled.file.TAudioFileFormat;
import yass.ffmpeg.FFMPEGLocator;
import yass.musicalkey.MusicalKeyEnum;
import yass.renderer.YassNote;
import yass.renderer.YassPlaybackRenderer;
import yass.renderer.YassPlayerNote;
import yass.renderer.YassSession;

import javax.sound.sampled.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Description of the Class
 *
 * @author Saruta
 */
public class YassPlayer {
    private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    public static final boolean DEBUG = false;
    byte[] memcache = null;
    byte[] clickmemcache = null;
    boolean midiEnabled = false, midisEnabled = false;
    boolean playAudio = true;
    boolean playClicks = true;
    boolean hasPlaybackRenderer = true;
    boolean live = false;
    MessageFormat latency = new MessageFormat(I18.get("sheet_msg_latency"));
    private YassPlaybackRenderer playbackRenderer;
    private YassMIDI midi;
    private YassVideo video = null;
    private byte midis[][];
    private long duration = 0, position = -1, seekInOffset = 0,
            seekOutOffset = 0, seekInOffsetMs = 0, seekOutOffsetMs = 0;
    private String filename = null;
    private YassCaptureAudio capture = null;
    private boolean useCapture = false;
    private MediaPlayer mediaPlayer = null;
    private PlayThread player = null;
    private String cachedMP3 = "";
    private float fps = 0;
    private boolean useWav = true;
    private boolean ogg = false;
    private boolean createWaveform = false;
    private boolean demo = false;
    private int MAX_PLAYERS = 2;
    private Vector<String> devices = new Vector<>(MAX_PLAYERS);
    private int[] playerdevice = new int[MAX_PLAYERS];
    private int[] playerchannel = new int[MAX_PLAYERS];
    private YassPlayerNote[] playernote = new YassPlayerNote[MAX_PLAYERS * 2];
    private BufferedImage bgImage = null;
    private Vector<YassPlayerListener> listeners = null;
    private byte[] audioBytes;
    private AudioFormat audioBytesFormat = null;
    private int audioBytesChannels = 2;
    private float audioBytesSampleRate = 44100;
    private int audioBytesSampleSize = 2;
    public static final String TEMP_PATH = System.getProperty("user.home") + File.separator + ".yass" + File.separator;
    private Map<Integer, Media> LONG_NOTE_MAP;
    private Map<Integer, Media> SHORT_NOTE_MAP;
    private Integer lastNote = null;
    private Timebase playrate = Timebase.NORMAL;
    private File tempFile;

    private MusicalKeyEnum key;

    private void initNoteMap() {
        lastNote = null;
        LONG_NOTE_MAP = new HashMap<>();
        SHORT_NOTE_MAP = new HashMap<>();
        Media note;
        for (int i = 21; i < 109; i++) {
            note = createNotePlayer("yass/resources/samples/longnotes/" + i + ".mp3");
            if (note != null) {
                LONG_NOTE_MAP.put(i, note);
            }
            note = createNotePlayer("yass/resources/samples/shortnotes/" + i + ".mp3");
            if (note != null) {
                SHORT_NOTE_MAP.put(i, note);
            }
        }
    }

    private Media createNotePlayer(String path) {
        URL resource = YassPlayer.class.getClassLoader().getResource(path);
        Media media;
        if (resource != null) {
            media = new Media(resource.toExternalForm());
        } else {
            media = null;
        }
        return media;
    }

    public static final String TEMP_WAV = TEMP_PATH + "temp.wav";

    public YassPlayer(YassPlaybackRenderer s) {
        JFXPanel jfxPanel = new JFXPanel();
        jfxPanel.setVisible(false);
        playbackRenderer = s;
        midi = new YassMIDI();
        initNoteMap();
        Thread synth = new Thread(() -> {
            midis = new byte[128][];
            for (int i = 0; i < 128; i++) {
                // byte[] data = YassSynth.createRect(2);
                midis[i] = YassSynth.create(i, 15, YassSynth.SINE);
            }

            YassSynth.loadWav();
        });

//        initCapture();
        synth.start();
    }

    /**
     * Sets the capture attribute of the YassPlayer object
     *
     * @param device  The new capture value
     * @param channel The new capture value
     * @param t       The new capture value
     */
    public void setCapture(int t, String device, int channel) {
        LOGGER.info("player " + t + " " + device + " (" + channel + ")");
        if (device == null) {
            playerdevice[t] = -1;
            playerchannel[t] = channel;
            return;
        }
        if (!devices.contains(device)) {
            devices.addElement(device);
        }
        int index = devices.indexOf(device);
        playerdevice[t] = index;
        playerchannel[t] = channel;
    }

    /**
     * Sets the video attribute of the YassPlayer object
     *
     * @param v The new video value
     */
    public void setVideo(YassVideo v) {
        video = v;
    }

    /**
     * Sets the demo attribute of the YassPlayer object
     *
     * @param onoff The new demo value
     */
    public void setDemo(boolean onoff) {
        demo = onoff;
    }

    /**
     * Sets the backgroundImage attribute of the YassPlayer object
     *
     * @param img The new backgroundImage value
     */
    public void setBackgroundImage(BufferedImage img) {
        bgImage = img;
    }

    /**
     * Description of the Method
     *
     * @param onoff Description of the Parameter
     */
    public void createWaveform(boolean onoff) {
        createWaveform = onoff;
        if (createWaveform != onoff) {
            openMP3(filename);
        }
    }

    /**
     * Description of the Method
     *
     * @return Description of the Return Value
     */
    public boolean createWaveform() {
        return createWaveform;
    }

    /**
     * Gets the playbackRenderer attribute of the YassPlayer object
     *
     * @return The playbackRenderer value
     */
    public YassPlaybackRenderer getPlaybackRenderer() {
        return playbackRenderer;
    }

    /**
     * Sets the playbackRenderer attribute of the YassPlayer object
     *
     * @param s The new playbackRenderer value
     */
    public void setPlaybackRenderer(YassPlaybackRenderer s) {
        playbackRenderer = s;
    }

    /**
     * Description of the Method
     */
    public void initCapture() {
        capture = new YassCaptureAudio();
    }

    public boolean isCapture() {
        return useCapture;
    }

    /**
     * Description of the Method
     */
    public void printMixers() {
        Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();
        for (int i = 0; i < mixerInfo.length; i++) {

            LOGGER.info("Mixer[" + i + "]: \"" + mixerInfo[i].getName() + "\"");
        }
    }

    /**
     * Description of the Method
     *
     * @param indent   Description of the Parameter
     * @param lineInfo Description of the Parameter
     */
    public void printLineInfo(String indent, Line.Info[] lineInfo) {
        int numDumped = 0;

        if (lineInfo != null) {
            for (Line.Info aLineInfo : lineInfo) {
                if (aLineInfo instanceof DataLine.Info) {
                    AudioFormat[] formats = ((DataLine.Info) aLineInfo)
                            .getFormats();
                    for (int j = 0; j < formats.length; j++) {
                        LOGGER.info(indent + formats[j]);
                    }
                    numDumped++;
                } else if (aLineInfo instanceof Port.Info) {
                    LOGGER.info(indent + aLineInfo);
                    numDumped++;
                }
            }
        }
        if (numDumped == 0) {
            LOGGER.info(indent + "none");
        }
    }

    /**
     * Description of the Method
     *
     * @return Description of the Return Value
     */
    public boolean useWav() {
        return useWav;
    }

    /**
     * Description of the Method
     *
     * @param onoff Description of the Parameter
     */
    public void useWav(boolean onoff) {
        useWav = onoff;
    }

    /**
     * Sets the seekOffset attribute of the YassPlayer object
     *
     * @param in  The new seekOffset value
     * @param out The new seekOffset value
     */
    public void setSeekOffset(long in, long out) {
        seekInOffset = in;
        seekOutOffset = out;
    }

    /**
     * Gets the seekOffset attribute of the YassPlayer object
     *
     * @return The seekOffset value
     */
    public long getSeekInOffset() {
        return seekInOffset;
    }

    /**
     * Sets the seekInOffset attribute of the YassPlayer object
     *
     * @param in The new seekInOffset value
     */
    public void setSeekInOffset(long in) {
        seekInOffset = in;
    }

    /**
     * Gets the seekOutOffset attribute of the YassPlayer object
     *
     * @return The seekOutOffset value
     */
    public long getSeekOutOffset() {
        return seekOutOffset;
    }

    /**
     * Sets the seekOutOffset attribute of the YassPlayer object
     *
     * @param out The new seekOutOffset value
     */
    public void setSeekOutOffset(long out) {
        seekOutOffset = out;
    }

    /**
     * Gets the seekOffset attribute of the YassPlayer object
     *
     * @return The seekOffset value
     */
    public long getSeekInOffsetMs() {
        return seekInOffsetMs;
    }

    /**
     * Sets the seekInOffsetMs attribute of the YassPlayer object
     *
     * @param in The new seekInOffsetMs value
     */
    public void setSeekInOffsetMs(long in) {
        seekInOffsetMs = in;
    }

    /**
     * Gets the seekOutOffsetMs attribute of the YassPlayer object
     *
     * @return The seekOutOffset value
     */
    public long getSeekOutOffsetMs() {
        return seekOutOffsetMs;
    }

    /**
     * Sets the seekOutOffsetMs attribute of the YassPlayer object
     *
     * @param out The new seekOutOffsetMs value
     */
    public void setSeekOutOffsetMs(long out) {
        seekOutOffsetMs = out;
    }

    /**
     * Description of the Method
     *
     * @param filename Description of the Parameter
     */
    public void openMP3(String filename) {
        this.filename = filename;
        if (filename == null)
            return;
        File file;
        try {
            file = generateTemp(filename);
            tempFile = file;
        } catch (IOException | UnsupportedAudioFileException | LineUnavailableException e) {
            throw new RuntimeException(e);
        }
        if (file == null || !file.exists()) {
            file = new File(filename);
        }
        if (!file.exists()) {
            playbackRenderer.setErrorMessage(I18.get("sheet_msg_audio_missing"));
            return;
        }

        fps = -1;
        AudioInputStream in = null;
        try {
            if (!filename.endsWith("m4a")) {
                in = AudioSystem.getAudioInputStream(file);
                if (in != null) {
                    AudioFormat baseFormat = in.getFormat();
                    fps = baseFormat.getFrameRate();
                }
            }
        } catch (Exception e) {
            String s = e.getMessage();
            if (s == null || !s.equals("Resetting to invalid mark")) {
                LOGGER.log(Level.INFO, e.getMessage(), e);
            }
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                }
            }
        }
        if (fps < 0 && !filename.endsWith("m4a")) {
            AudioFileFormat baseFileFormat;
            try {
                baseFileFormat = AudioSystem.getAudioFileFormat(file);
                if (baseFileFormat instanceof TAudioFileFormat) {
                    Map<?, ?> properties = baseFileFormat.properties();
                    Float fpsf = (Float) properties.get("mp3.framerate.fps");
                    if (fpsf != null) {
                        fps = fpsf.floatValue();
                    }
                }
            } catch (UnsupportedAudioFileException | IOException e) {
                throw new RuntimeException(e);
            }
        }
        Media media = new Media(file.toURI().toString());
        playbackRenderer.setErrorMessage(null);
        mediaPlayer = new MediaPlayer(media);
        mediaPlayer.statusProperty().addListener((observable, old, cur) -> {
            if (cur == MediaPlayer.Status.READY) {
                duration = (long) media.getDuration().toMillis() * 1000;
            }
            Map<String, Object> metadata = media.getMetadata();
            LOGGER.info(metadata.toString());
        });
        long now = System.currentTimeMillis();
        int counter = 0;
        try {
            while (mediaPlayer.getStatus() != MediaPlayer.Status.READY) {
                Thread.sleep(50);
                counter++;
                if (counter > 200) {
                    mediaPlayer.dispose();
                    throw new RuntimeException("Mediaplayer could not be readied in less than 10 seconds");
                }
            }
            LOGGER.info("Slept " + (System.currentTimeMillis() - now) + " ms");
        } catch (InterruptedException e) {
            return;
        }
    }

    /**
     * Description of the Method
     */
    public void cacheMP3() {
        if (!filename.equals(cachedMP3)) {
            FileInputStream fi = null;
            BufferedInputStream bfi = null;
            ByteArrayOutputStream bout = null;
            try {
                fi = new FileInputStream(new File(filename));
                bfi = new BufferedInputStream(fi, 4096);

                bout = new ByteArrayOutputStream();
                // Fast buffer implementation
                // BufferInputStream and OutputStream is much slower
                int readP;
                byte[] bufferP = new byte[1024];
                while ((readP = bfi.read(bufferP)) > -1) {
                    bout.write(bufferP, 0, readP);
                }
                memcache = bout.toByteArray();
                cachedMP3 = filename;
                LOGGER.info("MP3 cached.");
            } catch (Exception e) {
                LOGGER.log(Level.INFO, e.getMessage(), e);
            } finally {
                if (bout != null) {
                    try {
                        bout.close();
                    } catch (Exception e) {
                        LOGGER.log(Level.INFO, e.getMessage(), e);
                    }
                }
                if (bfi != null) {
                    try {
                        bfi.close();
                    } catch (Exception e) {
                        LOGGER.log(Level.INFO, e.getMessage(), e);
                    }
                }
                if (fi != null) {
                    try {
                        fi.close();
                    } catch (Exception e) {
                        LOGGER.log(Level.INFO, e.getMessage(), e);
                    }
                }
            }
        }
    }

    /**
     * Gets the duration attribute of the YassPlayer object
     *
     * @return The duration value
     */
    public long getDuration() {
        return duration;
    }

    /**
     * Description of the Method
     *
     * @param in     Description of the Parameter
     * @param out    Description of the Parameter
     * @param clicks Description of the Parameter
     */
    public void playSelection(long in, long out, long clicks[][]) {
        playSelection(in, out, clicks, Timebase.NORMAL);
    }

    /**
     * Description of the Method
     *
     * @param clicks Description of the Parameter
     */
    public void playAll(long clicks[][]) {
        playSelection(0, -1, clicks);
    }

    /**
     * Description of the Method
     *
     * @param clicks   Description of the Parameter
     * @param timebase Description of the Parameter
     */
    public void playAll(long clicks[][], Timebase timebase) {
        playSelection(0, -1, clicks, timebase);
    }

    /**
     * Description of the Method
     *
     * @param in       Description of the Parameter
     * @param out      Description of the Parameter
     * @param clicks   Description of the Parameter
     * @param timebase Description of the Parameter
     */
    public void playSelection(long in, long out, long clicks[][], Timebase timebase) {
        if (filename == null) {
            return;
        }

        interruptMP3();

        player = new PlayThread(in, out, clicks, timebase);
        player.start();
    }

    /**
     * Description of the Method
     */
    public void interruptMP3() {
        if (player != null && player.started) {
            player.notInterrupted = false;
            if (hasPlaybackRenderer) {
                playbackRenderer.setPlaybackInterrupted(true);
            }
        }
        while (player != null && player.started && !player.finished) {
            try {
                player.notInterrupted = false;
                if (hasPlaybackRenderer) {
                    playbackRenderer.setPlaybackInterrupted(true);
                }
            } catch (Exception e) {
            }

            if (!player.finished) {
                try {
                    Thread.currentThread();
                    Thread.sleep(10);
                } catch (Exception e) {
                }
                // LOGGER.info("waiting for finished " + player.started +
                // " " + player.finished);
            }
        }
    }

    /**
     * Sets the mIDIEnabled attribute of the YassPlayer object
     *
     * @param onoff The new mIDIEnabled value
     */
    public void setMIDIEnabled(boolean onoff) {
        midiEnabled = onoff;
        midisEnabled = !onoff;

        // ENABLE CLICK SAMPLE INSTEAD GENERATED SINES
        // midisEnabled = false;
        // clickEnabled = !onoff;
    }

    /**
     * Sets the audioEnabled attribute of the YassPlayer object
     *
     * @param onoff The new audioEnabled value
     */
    public void setAudioEnabled(boolean onoff) {
        playAudio = onoff;
    }

    /**
     * Sets the hasPlaybackRenderer attribute of the YassPlayer object
     *
     * @param onoff The new hasPlaybackRenderer value
     */
    public void setHasPlaybackRenderer(boolean onoff) {
        hasPlaybackRenderer = onoff;
    }

    /**
     * Description of the Method
     *
     * @return Description of the Return Value
     */
    public boolean hasPlaybackRenderer() {
        return hasPlaybackRenderer;
    }

    /**
     * Gets the live attribute of the YassPlayer object
     *
     * @return The live value
     */
    public boolean isLive() {
        return live;
    }

    /**
     * Sets the live attribute of the YassPlayer object
     *
     * @param onoff The new live value
     */
    public void setLive(boolean onoff) {
        live = onoff;
    }

    /**
     * Gets the playing attribute of the YassPlayer object
     *
     * @return The playing value
     */
    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING;
    }

    /**
     * Description of the Method
     *
     * @param midiPitch Description of the Parameter
     */
    public void playMIDI(int midiPitch) {
        midiPitch += 60;
        if (midiPitch > 127) {
            midiPitch = 127;
        }
        midi.stopPlay();
        midi.startPlay(midiPitch);
    }

    public void stopMIDI() {
        midi.stopPlay();
    }

    /**
     * Gets the position attribute of the YassPlayer object
     *
     * @return The position value
     */
    public long getPosition() {
        return position;
    }

    /**
     * Adds a feature to the PlayerListener attribute of the YassPlayer object
     *
     * @param p The feature to be added to the PlayerListener attribute
     */
    public void addPlayerListener(YassPlayerListener p) {
        if (listeners == null) {
            listeners = new Vector<>();
        }
        listeners.addElement(p);
    }

    /**
     * Description of the Method
     *
     * @param p Description of the Parameter
     */
    public void removePlayerListener(YassPlayerListener p) {
        if (listeners == null) {
            return;
        }
        listeners.removeElement(p);
    }

    /**
     * Description of the Method
     */
    public void firePlayerStarted() {
        if (listeners == null) {
            return;
        }
        for (Enumeration<YassPlayerListener> en = listeners.elements(); en
                .hasMoreElements(); ) {
            en.nextElement().playerStarted();
        }
    }

    /**
     * Description of the Method
     */
    public void firePlayerStopped() {
        if (listeners == null) {
            return;
        }
        for (Enumeration<YassPlayerListener> en = listeners.elements(); en
                .hasMoreElements(); ) {
            en.nextElement().playerStopped();
        }
    }

    /**
     * Description of the Method
     *
     * @param audioInputStream Description of the Parameter
     */
    public void createWaveForm(AudioInputStream audioInputStream) {
        audioBytesFormat = audioInputStream.getFormat();
        audioBytesChannels = audioBytesFormat.getChannels();
        audioBytesSampleRate = audioBytesFormat.getSampleRate();

        AudioFormat decodedFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED, audioBytesSampleRate,
                audioBytesSampleSize * 8, audioBytesChannels,
                audioBytesChannels * audioBytesSampleSize, // framesize
                audioBytesFormat.getSampleRate(), false);
        // LOGGER.info(decodedFormat);

        ByteArrayOutputStream bout = new ByteArrayOutputStream();

        AudioInputStream decodedStream = AudioSystem.getAudioInputStream(
                decodedFormat, audioInputStream);

        try {
            int readP;
            byte[] bufferP = new byte[1024];
            while ((readP = decodedStream.read(bufferP)) != -1) {
                bout.write(bufferP, 0, readP);
            }
            audioBytes = bout.toByteArray();
            // LOGGER.info("len " + audioBytes.length);
        } catch (Exception e) {
            LOGGER.log(Level.INFO, e.getMessage(), e);
        } finally {
            try {
                decodedStream.close();
            } catch (Exception e) {
            }
            try {
                bout.close();
            } catch (Exception e) {
            }
        }

        if (audioBytes != null && audioBytes.length < 1) {
            audioBytes = null;
            createWaveform = false;
        }
    }

    /**
     * Gets the waveFormAtMillis attribute of the YassPlayer object
     *
     * @param ms Description of the Parameter
     * @return The waveFormAtMillis value
     */
    public int getWaveFormAtMillis(double ms) {
        // sampleSize == 16, bigEndian == false

        int i = (int) (ms * audioBytesSampleRate / 1000.0);

        if (2 * i + 1 >= audioBytes.length) {
            i = (audioBytes.length - 1) / 2;
        }
        if (i < 0) {
            i = 0;
        }

        byte low = audioBytes[2 * i];
        byte big = audioBytes[2 * i + 1];

        int data = (big << 8) | (low & 255);

        return (int) (128 * data / 32768.0);
    }

    /**
     * Gets the waveFormAtMillis attribute of the YassPlayer object
     *
     * @param ms1 Description of the Parameter
     * @param ms2 Description of the Parameter
     * @return The waveFormAtMillis value
     */
    public int getWaveFormAtMillis(double ms1, double ms2) {
        // sampleSize == 16, bigEndian == false

        int i1 = (int) (ms1 * audioBytesSampleRate / 1000.0 * 2);
        int i2 = (int) (ms2 * audioBytesSampleRate / 1000.0 * 2);

        if (i1 >= audioBytes.length) {
            i1 = audioBytes.length - 2;
        }
        if (i2 >= audioBytes.length) {
            i2 = audioBytes.length - 2;
        }
        if (i1 < 0) {
            i1 = 0;
        }
        if (i2 < 0) {
            i2 = 0;
        }

        double sum = 0;
        int n = (i2 - i1) / 2;
        if (n < 1) {
            n = 1;
        }

        for (int i = i1; i <= i2; i += 2) {
            int LSB = (int) audioBytes[i];
            int MSB = (int) audioBytes[i + 1];
            int data = (MSB << 8) | (255 & LSB);
            sum += Math.abs(data / n);
        }
        return (int) (128 * sum / 32768.0);
    }

    public boolean isClicksEnabled() {
        return playClicks;
    }

    /**
     * Sets the audioEnabled attribute of the YassPlayer object
     *
     * @param onoff The new audioEnabled value
     */
    public void setClicksEnabled(boolean onoff) {
        playClicks = onoff;
    }

    public YassCaptureAudio getCapture() {
        return capture;
    }

    /**
     * Sets the capture attribute of the YassPlayer object
     *
     * @param onoff The new capture value
     */
    public void setCapture(boolean onoff) {
        useCapture = onoff;
    }

    public void disposeMediaPlayer() {
        try {
            if (mediaPlayer != null && mediaPlayer.getStatus() != MediaPlayer.Status.DISPOSED) {
                mediaPlayer.stop();
                mediaPlayer.dispose();
            }
        } catch (Exception ex) {
            LOGGER.log(Level.INFO, ex.getMessage(), ex);
        }
    }

    /**
     * Description of the Class
     *
     * @author Saruta
     */
    class Play2Thread extends Thread {
        MediaPlayer mp;
        long skip1, skip2;

        /**
         * Constructor for the Play2Thread object
         *
         * @param mp    Description of the Parameter
         * @param skip1 Description of the Parameter
         * @param skip2 Description of the Parameter
         */
        public Play2Thread(MediaPlayer mp, long skip1, long skip2) {
            this.mp = mp;
            this.skip1 = skip1;
            this.skip2 = skip2;
        }

        /**
         * Main processing method for the Play2Thread object
         */
        public void run() {
            try {
                if (mp != null) {
                    mp.setStartTime(Duration.millis(skip1));
                    mp.setStopTime(Duration.millis(skip2));
                    mp.setAutoPlay(true);
                }
            } catch (Exception e) {
                LOGGER.info("Playback Error");
                LOGGER.log(Level.INFO, e.getMessage(), e);
            }
        }
    }

    /**
     * Description of the Class
     *
     * @author Saruta
     */
    class PlayThread extends Thread {
        /**
         * Description of the Field
         */
        public boolean notInterrupted = true, finished = false,
                started = false;
        long in, out, clicks[][];
        Timebase timebase;

        /**
         * Constructor for the PlayThread object
         *
         * @param in       Description of the Parameter
         * @param out      Description of the Parameter
         * @param clicks   Description of the Parameter
         * @param timebase Description of the Parameter
         */
        public PlayThread(long in, long out, long[][] clicks, Timebase timebase) {
            this.in = in;
            this.out = out;
            this.clicks = clicks;
            this.timebase = timebase;
        }

        /**
         * Main processing method for the PlayThread object
         */
        public void run() {
            playMP3(in, out, clicks, timebase);
        }

        /**
         * Description of the Method
         *
         * @param inpoint  Description of the Parameter
         * @param outpoint Description of the Parameter
         * @param clicks   Description of the Parameter
         * @param timebase Description of the Parameter
         */
        private void playMP3(long inpoint, long outpoint, long[][] clicks,
                             Timebase timebase) {
            finished = false;
            started = true;
            File mp3File;
            double playrate = timebase.timerate;
            if (timebase == Timebase.NORMAL) {
                mp3File = new File(TEMP_WAV);
                setPlayrate(Timebase.NORMAL);
            } else {
                mp3File = new File(TEMP_PATH + timebase.id + "temp.wav");
                if (!mp3File.exists()) {
                    try {
                        mp3File = generateTemp(TEMP_PATH + "temp.wav", timebase);
                        playrate = 1;
                        setPlayrate(timebase);
                    } catch (IOException | UnsupportedAudioFileException | LineUnavailableException e) {
                        // Couldn't find slowed down ffmpeg conversion, we are using the ugly JavaFX-slow down
                        setPlayrate(Timebase.NORMAL);
                    }
                } else {
                    setPlayrate(timebase);
                    playrate = 1;
                }
            }
            if (!mp3File.exists()) {
                finished = true;
                return;
            }

            if (DEBUG) {
                LOGGER.info("in: " + inpoint);
                LOGGER.info("out: " + outpoint);
                for (int i = 0; i < clicks.length; i++) {
                    long duration = clicks[i][2] - clicks[i][0];
                    System.out.println("click[" + i + "]=" + clicks[i][1] + " (at:" + clicks[i][0] + " len:"
                                               + duration + ")");
                }
            }
            double multiplier = getPlayrate().getMultiplier();
            long duration = (long) (getDuration() * multiplier);
            if (outpoint < 0 || outpoint > duration) {
                outpoint = duration;
            } else {
                outpoint = (long) (outpoint * multiplier);
            }
            long inMillis = (long) (inpoint * multiplier) / 1000;
            long outMillis = outpoint / 1000;
            long off;

            long maxClickOffset = 0;
            int midiPitch = 0;
            int clicksPos = 0;
            int n = clicks != null ? clicks.length : 0;
            long nextClick = clicks == null ? -1 : (long) (clicks[clicksPos][0] * multiplier);
            long nextClickPitch = (clicks == null || clicks.length < 2) ? Integer.MIN_VALUE : clicks[clicksPos][1];
            long nextClickEnd = clicks == null ? -1 : (long) (clicks[clicksPos][2] * multiplier);

            if (DEBUG) {
                LOGGER.info("playAudio:" + playAudio + "  ogg:" + ogg);
            }
            if (playAudio && !ogg) {
                try {
                    Media media = new Media(mp3File.toURI().toString());
                    mediaPlayer = new MediaPlayer(media);
                    mediaPlayer.setVolume(1);
                    if (DEBUG)
                        LOGGER.info("JavaFX MediaPlayer created.");
                } catch (IllegalArgumentException e) {
                    System.err.println("YassPlayer: " + e.getMessage());
                } catch (Exception e) {
                    notInterrupted = false;
                    if (DEBUG)
                        LOGGER.info("Cannot create JavaFX MediaPlayer.");
                }
            }

            if (hasPlaybackRenderer) {
                playbackRenderer.setErrorMessage(null);

                if (useCapture) {
                    YassSession session = playbackRenderer.getSession();
                    if (session != null) {
                        int trackCount = session.getTrackCount();
                        for (int t = 0; t < trackCount; t++) {
                            session.getTrack(t).getPlayerNotes().removeAllElements();
                        }
                        for (Enumeration<String> devEnum = devices.elements(); devEnum.hasMoreElements(); ) {
                            String device = devEnum.nextElement();
                            capture.startQuery(device);
                        }
                    }
                }

                if (!playbackRenderer.preparePlayback(inMillis, outMillis)) {
                    finished = true;
                    return;
                }
                playbackRenderer.setPlaybackInterrupted(false);

                if (video != null && playbackRenderer.showVideo()) {
                    video.setTime((int) inMillis);
                }
                if (bgImage != null && playbackRenderer.showBackground()) {
                    playbackRenderer.setBackgroundImage(bgImage);
                }
            }

            // Thread.currentThread().setPriority(Thread.NORM_PRIORITY + 1);

            if (midisEnabled && playClicks) {
                if (useWav) {
                    YassSynth.openWavLine();
                } else {
                    YassSynth.openLine();
                }
            }

            firePlayerStarted();
            if (hasPlaybackRenderer) {
                playbackRenderer.setPause(false);
                playbackRenderer.startPlayback();
                if (video != null && playbackRenderer.showVideo()) {
                    video.playVideo();
                }
            }
            if (playAudio && !ogg) {
                try {
                    mediaPlayer.setRate(playrate);
                    new Play2Thread(mediaPlayer, inMillis + seekInOffsetMs,
                                    outMillis + seekOutOffsetMs).start();
                } catch (Exception e) {
                    LOGGER.log(Level.INFO, e.getMessage(), e);
                }
                int nn = 200;
                try {
                    while (!isPlaying() && nn-- > 0) {
                        Thread.sleep(0, 100);
                    }
                } catch (InterruptedException e) {
                }
                if (nn <= 0) {
                    if (DEBUG)
                        LOGGER.info("Cannot start playback.");
                    notInterrupted = false;
                }
                if (DEBUG) LOGGER.info("Waited " + ((200 - nn) * 100) + " nanos");
            }

            long nanoStart = System.nanoTime() / 1000L;
            position = (long) (inpoint * multiplier);

            long lastms = System.nanoTime();

            if (!notInterrupted) {
                LOGGER.info("Playback interrupted.");
            }
            while (notInterrupted) {
                long tempdiff = (System.nanoTime() / 1000L) - nanoStart;
                position = (long) (tempdiff + (inpoint * multiplier));
                if (position >= outpoint) {
                    position = outpoint;
                    notInterrupted = false;
                    if (DEBUG) LOGGER.info("Playback stopped.");
                    break;
                }
                if (clicks != null && clicksPos < n) {
                    if (position >= nextClickEnd) {
                        if (midiEnabled) {
                            midi.stopPlay();
                        }
                    }
                    if (position >= nextClick) {
                        off = Math.abs(position - nextClick);
//                        System.out.println("Playing note " + clicks[clicksPos][1] + " at " + position);
                        if (DEBUG)
                            LOGGER.info(off + " us offset  at line "
                                                + clicksPos);
                        maxClickOffset = Math.max(maxClickOffset, off);
                        midiPitch = (int) clicks[clicksPos][1];
                        midiPitch += 60;
                        if (midiPitch > 127) {
                            midiPitch = 127;
                        }

                        if (playClicks && midisEnabled && n > 1) {
                            int midiPitch2 = midiPitch + 12;
                            if (midiPitch2 > 127) {
                                midiPitch2 = 127;
                            }
                            if (useWav) {
                                YassSynth.playWav();
                            } else {
                                YassSynth.play(midis[midiPitch2]);
                            }
                        }

                        if (midiEnabled) {
                            if (useWav) {
                                playNote((int) clicks[clicksPos][1], (double) (nextClickEnd - nextClick) / 1000,
                                         (int) nextClickPitch);
                            } else {
                                midi.playNote(midiPitch, (nextClickEnd - nextClick) / 1000);
                            }
                        }

                        if (++clicksPos < n) {
                            nextClick = (long) (clicks[clicksPos][0] * multiplier);
                            nextClickEnd = (long) (clicks[clicksPos][2] * multiplier);
                        }
                    }
                }

                if (hasPlaybackRenderer) {
                    long currentMillis = (position / (long) (1000 * multiplier));
                    YassSession session = playbackRenderer.getSession();
                    if (session != null) {
                        session.updateSession(currentMillis);

                        if (demo) {
                            YassPlayerNote note = new YassPlayerNote(YassPlayerNote.NOISE, 1, currentMillis);

                            int trackCount = session.getTrackCount();
                            for (int t = 0; t < trackCount; t++) {
                                int currentNoteIndex = session.getTrack(t).getCurrentNote();
                                YassNote currentTrackNote = session.getTrack(t).getNote(currentNoteIndex);
                                if (currentTrackNote.getStartMillis() <= currentMillis
                                        && currentMillis <= currentTrackNote.getEndMillis()) {
                                    if (currentMillis < currentTrackNote.getStartMillis() + 10) {
                                        note.setStartMillis(currentTrackNote.getStartMillis());
                                    }
                                    if (currentMillis > currentTrackNote.getEndMillis() - 10) {
                                        note.setEndMillis(currentTrackNote.getEndMillis());
                                    }

                                    int h = currentTrackNote.getHeight();
                                    note.setHeight(h);
                                }
                                session.getTrack(t).addPlayerNote(new YassPlayerNote(note));

                            }
                        } else if (useCapture) {
                            int d = 0;
                            for (Enumeration<String> devEnum = devices.elements(); devEnum.hasMoreElements(); ) {
                                String device = devEnum.nextElement();
                                YassPlayerNote[] note = capture.query(device);
                                playernote[d++] = note != null ? note[0] : null;
                                playernote[d++] = note != null ? note[1] : null;
                            }

                            int trackCount = session.getTrackCount();
                            for (int t = 0; t < trackCount; t++) {
                                if (playerdevice[t] < 0) {
                                    continue;
                                }
                                YassPlayerNote note = playernote[playerdevice[t] + playerchannel[t]];
                                if (note == null) {
                                    continue;
                                }
                                note.setStartMillis(currentMillis);

                                int currentNoteIndex = session.getTrack(t).getCurrentNote();
                                YassNote currentTrackNote = session.getTrack(t).getNote(currentNoteIndex);
                                if (currentTrackNote.getStartMillis() <= currentMillis
                                        && currentMillis <= currentTrackNote.getEndMillis()) {
                                    if (currentMillis < currentTrackNote.getStartMillis() + 10) {
                                        note.setStartMillis(currentTrackNote.getStartMillis());
                                    }
                                    if (currentMillis > currentTrackNote.getEndMillis() - 10) {
                                        note.setEndMillis(currentTrackNote.getEndMillis());
                                    }
                                } else {
                                    note.setHeight(YassPlayerNote.NOISE);
                                }
                                session.getTrack(t).addPlayerNote(new YassPlayerNote(note));
                            }
                        }
                    }
                    if (video != null && playbackRenderer.showVideo()) {
                        playbackRenderer.setVideoFrame(video.getFrame());
                    }
                    playbackRenderer.updatePlayback(currentMillis);
                }

                try {
                    long curms = System.nanoTime();
                    long diff = curms - lastms;
                    lastms = curms;
                    int diffms = (int) (diff / 1000L);
                    if (diffms < 1000) {
                        Thread.currentThread();
                        Thread.sleep(0, 1000 - diffms);
                        // LOGGER.info("   wait " + (1000 - diffms));
                    }
                } catch (InterruptedException e) {
                    if (DEBUG)
                        LOGGER.info("Playback renderer: interrupt.");
                    notInterrupted = false;
                }

            }

            if (midiEnabled) {
                midi.stopPlay();
            }
            notInterrupted = false;

            if (playAudio && !ogg) {
                if (mediaPlayer != null && isPlaying()) {
                    try {
                        Thread.currentThread();
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                    }
                    try {
                        mediaPlayer.stop();
                        mediaPlayer.dispose();
                        // LOGGER.info("player stop()");
                    } catch (Throwable t) {
                        // t.printStackTrace();
                    }
                }
            }

            if (playClicks && midisEnabled) {
                if (useWav) {
                    YassSynth.closeWavLine();
                } else {
                    YassSynth.closeLine();
                }
            }

            if (hasPlaybackRenderer) {
                if (useCapture) {
                    for (Enumeration<String> devEnum = devices.elements(); devEnum
                            .hasMoreElements(); ) {
                        String device = devEnum.nextElement();
                        capture.stopQuery(device);
                    }
                }
                playbackRenderer.setPlaybackInterrupted(false);
                if (video != null && playbackRenderer.showVideo()) {
                    video.stopVideo();
                }
                playbackRenderer.finishPlayback();

                if (clicks != null && maxClickOffset / 1000.0 > 10) {
                    // greater 10 ms
                    playbackRenderer.setErrorMessage(latency.format(new Object[]{
                            Math.round(maxClickOffset / 1000.0) + ""
                    }));
                }
            }
            live = false;

            // Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
            finished = true;
            firePlayerStopped();
        }
    }

    public void setPianoVolume(int vol) {
        midi.setVolume(vol);
    }

    public void reinitSynth() {
        midi = new YassMIDI();
    }

    class MidiThread extends Thread {
        YassMIDI midi;

        int note;
        long length;

        /**
         * Constructor for the Play2Thread object
         *
         * @param midi   Description of the Parameter
         * @param note   Description of the Parameter
         * @param length Description of the Parameter
         */
        public MidiThread(YassMIDI midi, int note, long length) {
            this.midi = midi;
            this.note = note;
            this.length = length;
        }

        /**
         * Main processing method for the Play2Thread object
         */
        public void run() {
            try {
                if (midi != null) {
                    midi.playNote(note, YassMIDI.VOLUME_MED);
                    Thread.sleep(length);
                    midi.playNote(note, 0);
                }
            } catch (Exception e) {
                LOGGER.info("Playback Error");
                LOGGER.log(Level.INFO, e.getMessage(), e);
            }
        }
    }

    public File generateTemp(String source) throws IOException, UnsupportedAudioFileException, LineUnavailableException {
        return generateTemp(source, Timebase.NORMAL);
    }

    public File generateTemp(String source, Timebase timeBase) throws IOException, UnsupportedAudioFileException,
            LineUnavailableException {
        if (FFMPEGLocator.getInstance().getFfmpeg() == null) {
            return null;
        }
        FFmpeg ffmpeg = FFMPEGLocator.getInstance().getFfmpeg();
        FFprobe fFprobe = FFMPEGLocator.getInstance().getFfprobe();
        FFmpegProbeResult fFmpegProbeResult = fFprobe.probe(source);
        FFmpegBuilder fFmpegBuilder = new FFmpegBuilder();
        fFmpegBuilder.addInput(fFmpegProbeResult);
        if (fFmpegProbeResult != null && fFmpegProbeResult.getStreams() != null && !fFmpegProbeResult.getStreams()
                                                                                                     .isEmpty()) {
            audioBytesSampleRate = fFmpegProbeResult.getStreams().get(0).sample_rate;
            audioBytesChannels = fFmpegProbeResult.getStreams().get(0).channels;
        }
        this.key = findKey();
        File tempFile;
        if (timeBase == Timebase.NORMAL) {
            tempFile = new File(TEMP_WAV);
        } else {
            tempFile = new File(TEMP_PATH + timeBase.getId() + "temp.wav");
            fFmpegBuilder.setAudioFilter(timeBase.getFilter());
        }
        fFmpegBuilder.overrideOutputFiles(true)
                     .addOutput(tempFile.getAbsolutePath())
                     .done();
        FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, fFprobe);
        // Run a one-pass encode
        FFmpegJob job = executor.createJob(fFmpegBuilder);
        job.run();
        return tempFile;
    }

    public void playNote(int note) {
        playNote(note, 500, Integer.MIN_VALUE);
    }

    public void playNote(int note, double length, int nextNote) {
        if (lastNote != null && note == lastNote) {
            stopNote();
        }
        Media noteMedia;
        int lengthFactor;
        int currentNote = note + 60;
//        if (length > 1000 && note != nextNote) {
        if (length > 1000) {
            noteMedia = LONG_NOTE_MAP.get(currentNote);
            lengthFactor = 1000;
        } else {
            noteMedia = SHORT_NOTE_MAP.get(currentNote);
            lengthFactor = 1;
        }
        if (noteMedia != null) {
            lastNote = currentNote * lengthFactor;
            MediaPlayer notePlayer = new MediaPlayer(noteMedia);
            notePlayer.setVolume((double) midi.getVolume() / 127);
            notePlayer.setStopTime(Duration.millis(length + 100));
            notePlayer.setOnEndOfMedia(notePlayer::dispose);
            notePlayer.setAutoPlay(true);
        }
    }

    public void stopNote() {
        if (lastNote == null) {
            return;
        }
        lastNote = null;
    }

    public Timebase getPlayrate() {
        return playrate;
    }

    public void setPlayrate(Timebase playrate) {
        this.playrate = playrate;
    }

    public File getTempFile() {
        return tempFile;
    }

    public int getAudioBytesChannels() {
        return audioBytesChannels;
    }

    public float getAudioBytesSampleRate() {
        return audioBytesSampleRate;
    }

    public MusicalKeyEnum getKey() {
        return key;
    }

    public void saveKey(MusicalKeyEnum musicalKeyEnum) {
        if (musicalKeyEnum == null || musicalKeyEnum == MusicalKeyEnum.UNDEFINED || musicalKeyEnum == getKey()) {
            return;
        }
        this.key = musicalKeyEnum;
        AudioFile audioFile;
        try {
            audioFile = AudioFileIO.read(new File(filename));
            Tag tag = audioFile.getTag();
            tag.setField(FieldKey.KEY, musicalKeyEnum.getRelevantKey());
            audioFile.commit();
        } catch (CannotReadException | IOException | TagException | ReadOnlyFileException | InvalidAudioFrameException |
                 CannotWriteException e) {
            throw new RuntimeException(e);
        }
    }
    
    public MusicalKeyEnum findKey() {
        AudioFile audioFile;
        try {
            audioFile = AudioFileIO.read(new File(filename));
            Tag tag = audioFile.getTag();
            TagField tagField = tag.getFirstField(FieldKey.KEY);
            if (tagField != null && !tagField.isEmpty()) {
                return MusicalKeyEnum.findKey(((ID3v23Frame) tagField).getContent());
            }
        } catch (CannotReadException | IOException | TagException | ReadOnlyFileException | InvalidAudioFrameException e) {
            throw new RuntimeException(e);
        }
        return MusicalKeyEnum.UNDEFINED;
    }
}
