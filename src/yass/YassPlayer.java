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

import lombok.Getter;
import lombok.Setter;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.job.FFmpegJob;
import net.bramp.ffmpeg.probe.FFmpegFormat;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.probe.FFmpegStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import yass.analysis.PitchDetector.PitchData;
import yass.ffmpeg.FFMPEGLocator;
import yass.musicalkey.MusicalKeyEnum;
import yass.renderer.YassNote;
import yass.renderer.YassPlaybackRenderer;
import yass.renderer.YassPlayerNote;
import yass.renderer.YassSession;
import yass.video.YassVideoDialog;

import javax.sound.sampled.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Description of the Class
 *
 * @author Saruta
 */
@Getter
@Setter
public class YassPlayer {
    private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    public boolean DEBUG = false;
    byte[] memcache = null;
    boolean midiEnabled = false, midisEnabled = false;
    boolean playAudio = true;
    boolean playClicks = true;
    boolean hasPlaybackRenderer = true;
    boolean live = false;
    private YassPlaybackRenderer playbackRenderer;
    private YassMIDI midi;
    private YassVideoDialog video = null;
    private YassProperties properties;
    private byte midis[][];
    private long duration = 0, position = -1, seekInOffset = 0,
            seekOutOffset = 0, seekInOffsetMs = 0, seekOutOffsetMs = 0;
    private String filename = null;
    private YassCaptureAudio capture = null;
    private boolean useCapture = false;
    private PlayThread player = null;
    private String cachedMP3 = "";
    private float fps = 0;
    private boolean useWav = true;
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
    public static final String USER_PATH = System.getProperty("user.home") + File.separator + ".yass" + File.separator;
    private Map<Integer, byte[]> LONG_NOTE_MAP;
    private Map<Integer, byte[]> SHORT_NOTE_MAP;
    private byte[] drumClick;
    private Integer lastNote = null;
    private Timebase playrate = Timebase.NORMAL;
    private File tempFile;
    private AudioFormat pianoFormat;
    private HashMap<Long, Thread> playThreadMap = new HashMap<>();
    private List<SourceDataLine> lineList = new ArrayList<>();

    private MusicalKeyEnum key;
    private double targetDbfs;
    private double replayGain;

    private SourceDataLine sharedLine = null;
    private Thread sharedLineThread = null;
    private AudioFormat sharedLineFormat = null;
    private volatile boolean sharedLineRunning = false;
    private volatile boolean sharedLineInterrupted = false;
    private final BlockingQueue<Pair<byte[], Runnable>> sharedLineQueue = new LinkedBlockingQueue<>();

    private List<PitchData> pitchDataList;
    
    public void initNoteMap() {
        lastNote = null;
        LONG_NOTE_MAP = new HashMap<>();
        SHORT_NOTE_MAP = new HashMap<>();
        drumClick = YassSynth.getWavSampleAsByteArray();
    }

    public byte[] createNotePlayer(String path) {
        File resource = fetchOrConvert(path);
        if (resource == null) {
            return null;
        }
        try {
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(resource);
            AudioFormat audioFormat = audioInputStream.getFormat();
            if (pianoFormat == null) {
                pianoFormat = audioFormat;
            }
            byte[] data = audioInputStream.readAllBytes();
            audioInputStream.close();
            return data;
        } catch (IOException | UnsupportedAudioFileException e) {
            throw new RuntimeException(e);
        }
    }

    public void addNoteToMap(byte[] note, int i, boolean longNote) {
        if (note == null) {
            if (DEBUG) LOGGER.info("Failed to create piano note " + i);
            return;
        }
        if (longNote) {
            LONG_NOTE_MAP.put(i, note);
        } else {
            SHORT_NOTE_MAP.put(i, note);
        }
    }

    private File fetchOrConvert(String path) {
        File file = new File(USER_PATH + path + ".wav");
        if (file.exists()) {
            return file;
        }
        LOGGER.info("Could not find sample " + path + ". Generating it now...");
        try {
            URL resource = YassPlayer.class.getClassLoader()
                                           .getResource("yass/resources/" + path + ".mp3");
            InputStream initialStream = resource.openStream();
            File targetFile = new File(USER_PATH + "sample.mp3");
            OutputStream outStream = new FileOutputStream(targetFile);

            byte[] buffer = new byte[8 * 1024];
            int bytesRead;
            while ((bytesRead = initialStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, bytesRead);
            }
            IOUtils.closeQuietly(initialStream);
            IOUtils.closeQuietly(outStream);
            file = generateTemp(targetFile.getAbsolutePath(), Timebase.NORMAL, file.getAbsolutePath());
            targetFile.delete();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return file;
    }

    public static final String TEMP_WAV = USER_PATH + "temp.wav";

    public YassPlayer(YassPlaybackRenderer s, YassProperties prop) {
        playbackRenderer = s;
        this.properties = prop;
        if (!prop.getBooleanProperty("use-sample")) {
            midi = new YassMIDI();
        }
        DEBUG = prop.getBooleanProperty("debug-audio");
        Thread synth = new Thread(() -> {
            midis = new byte[128][];
            for (int i = 0; i < 128; i++) {
                // byte[] data = YassSynth.createRect(2);
                midis[i] = YassSynth.create(i, 15, YassSynth.SINE);
            }

            YassSynth.loadWav();
        });
        synth.start();
//        initCapture();
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
    public void setVideo(YassVideoDialog v) {
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
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (file == null || !file.exists()) {
            file = new File(filename);
        }
        if (!file.exists()) {
            playbackRenderer.setErrorMessage(I18.get("sheet_msg_audio_missing"));
            return;
        }
        setPitchDataList(Collections.emptyList());
        fps = -1;
        try (AudioInputStream in = AudioSystem.getAudioInputStream(file)) {
            AudioFormat baseFormat = in.getFormat();
            fps = baseFormat.getFrameRate();
            audioBytesFormat = baseFormat;
            audioBytesChannels = baseFormat.getChannels();
            audioBytesSampleRate = baseFormat.getSampleRate();
            audioBytes = writeByteArray(file);
            duration = (long) (in.getFrameLength() / baseFormat.getFrameRate() * 1000000);
            openSharedLine(baseFormat);
        } catch (Exception e) {
            audioBytes = null;
            createWaveform = false;
            String s = e.getMessage();
            if (s == null || !s.equals("Resetting to invalid mark")) {
                LOGGER.log(Level.INFO, e.getMessage(), e);
            }
        }
        pitchDataList = new ArrayList<>();
    }

    /**
     * Initializes YassPlayer with no audio file and determines a theoretical length of the song from the last beat
     * and the BPM.
     *
     * @param table
     */
    public void emptyMp3(YassTable table) {
        long tempDuration = Math.max((long) table.getStart() * 1000, (long) table.getEnd() * 1000);
        if (table.getRowCount() > 2 && table.getBPM() > 0) {
            YassRow lastRow = table.getRowAt(table.getRowCount() - 2);
            double beatToSec = 60d / table.getBPM();
            double lastBeatMs = table.getGap() + (1000 * beatToSec * (lastRow.getBeatInt() + lastRow.getLengthInt()) / 4);
            tempDuration = Math.max(tempDuration, (long) lastBeatMs);
        }
        duration = tempDuration * 1000;
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
    public void playSelection(long in, long out, Click[] clicks) {
        playSelection(in, out, clicks, Timebase.NORMAL);
    }

    /**
     * Description of the Method
     *
     * @param clicks Description of the Parameter
     */
    public void playAll(Click[] clicks) {
        playSelection(0, -1, clicks);
    }

    /**
     * Description of the Method
     *
     * @param clicks   Description of the Parameter
     * @param timebase Description of the Parameter
     */
    public void playAll(Click[] clicks, Timebase timebase) {
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
    public void playSelection(long in, long out, Click[] clicks, Timebase timebase) {
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
        if (isPlaying()) {
            sharedLineInterrupted = true;
            if (hasPlaybackRenderer) {
                playbackRenderer.setPlaybackInterrupted(true);
            }
        }
        playThreadMap.clear();
    }

    public void flushDatalines() {
        for (SourceDataLine line : lineList) {
            line.flush();
            line.close();
        }
        lineList.clear();
        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            for (Line.Info lineInfo : mixer.getSourceLineInfo()) {
                try {
                    Line line = mixer.getLine(lineInfo);
                    if (line instanceof SourceDataLine) {
                        ((SourceDataLine) line).flush();
                    }
                    if (line.isOpen()) {
                        line.close();
                    }
                } catch (LineUnavailableException e) {
                    // Probably already in use or unavailable
                }
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
        return player != null && player.isAlive();
    }

    /**
     * Description of the Method
     *
     * @param midiPitch Description of the Parameter
     */
    public void playMIDI(int midiPitch) {
        if (midi == null) {
            return;
        }
        midiPitch += 60;
        if (midiPitch > 127) {
            midiPitch = 127;
        }
        midi.stopPlay();
        midi.startPlay(midiPitch);
    }

    public void stopMIDI() {
        if (midi == null) {
            return;
        }
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
     * Write a file as byte array, and normalize audio bytes if normalization is activated
     *
     * @param file Temporary wave file
     */
    public byte[] writeByteArray(File file) {
        try (InputStream inputStream = new FileInputStream(file)) {
            byte[] pcmData = inputStream.readAllBytes();
            if ((int) targetDbfs == 0 && (int) replayGain == 0) {
                return pcmData;
            }
            int numSamples = pcmData.length / 2;

            double normalizationFactor;
            if ((int) replayGain != 0) {
                normalizationFactor = Math.pow(10.0, replayGain / 20.0);
            } else {
                double currentDbfs = 20 * Math.log10(calculateRMS(pcmData, numSamples) / Short.MAX_VALUE);
                double gainDb = targetDbfs - currentDbfs;
                normalizationFactor = Math.pow(10.0, gainDb / 20.0);
            }

            return normalizePCM(pcmData, normalizationFactor);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the waveFormAtMillis attribute of the YassPlayer object
     *
     * @param ms Description of the Parameter
     * @return The waveFormAtMillis value
     */
    public int getWaveFormAtMillis(double ms) {
        int bytePosition = findByteAtMilli((int) ms);
        int data;
        try {
            byte lowLeft = audioBytes[bytePosition];
            byte hiLeft = audioBytes[bytePosition + 1];
            byte lowRight = audioBytes[bytePosition + 2];
            byte hiRight = audioBytes[bytePosition + 3];

            int dataLeft = (hiLeft << 8) | (lowLeft & 255);
            int dataRight = (hiRight << 8) | (lowRight & 255);
            data = (dataLeft + dataRight) / 2;
        } catch (Exception e) {
            data = 0;
        }
        return (int) (128 * data / 32768.0);
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

    public void clearAudioBytes() {
        audioBytes = null;
    }

    public byte[] getAudioBytes() {
        return audioBytes;
    }

    private int findByteAtMilli(int milli) {
        int frameSize = audioBytesFormat.getFrameSize();
        double bytesPerMillisecond = audioBytesFormat.getFrameRate() * frameSize / 1000;
        int bytePos = Math.min(audioBytes.length - 1, (int) (milli * bytesPerMillisecond));
        if (bytePos % frameSize != 0) {
            bytePos = bytePos - (bytePos % frameSize);
        }
        return Math.max(0, bytePos);
    }

    private byte[] getAudioBytesInRange(int startMillis, int endMillis) {
        int startByte = findByteAtMilli(startMillis);
        int endByte = Math.max(startByte, findByteAtMilli(endMillis));
        byte[] audioData = new byte[endByte - startByte];
        System.arraycopy(audioBytes, startByte, audioData, 0, endByte - startByte);
        return audioData;
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
        public boolean notInterrupted = true, finished = false, started = false;
        long in, out;
        Click[] clicks;
        Timebase timebase;

        /**
         * Constructor for the PlayThread object
         *
         * @param in       Description of the Parameter
         * @param out      Description of the Parameter
         * @param clicks   Description of the Parameter
         * @param timebase Description of the Parameter
         */
        public PlayThread(long in, long out, Click[] clicks, Timebase timebase) {
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
        private void playMP3(long inpoint, long outpoint, Click[] clicks,
                             Timebase timebase) {
            finished = false;
            started = true;
            File mp3File;
            if (timebase == Timebase.NORMAL) {
                mp3File = new File(TEMP_WAV);
                setPlayrate(yass.Timebase.NORMAL);
            } else {
                mp3File = new File(USER_PATH + timebase.id + "temp.wav");
                if (!mp3File.exists()) {
                    try {
                        mp3File = generateTemp(USER_PATH + "temp.wav", timebase);
                        setPlayrate(timebase);
                    } catch (IOException e) {
                        // Couldn't find slowed down ffmpeg conversion, we are using the ugly JavaFX-slow down
                        setPlayrate(yass.Timebase.NORMAL);
                    }
                } else {
                    setPlayrate(timebase);
                }
            }
            if (!mp3File.exists()) {
                finished = true;
                return;
            } else {
                if (audioBytes == null) {
                    audioBytes = writeByteArray(mp3File);
                }
            }

            if (DEBUG) {
                LOGGER.info("in: " + inpoint);
                LOGGER.info("out: " + outpoint);
                if (ArrayUtils.isNotEmpty(clicks)) {
                    for (int i = 0; i < clicks.length; i++) {
                        long duration = clicks[i].end() - clicks[i].start();
                        System.out.println(
                                "click[" + i + "]=" + clicks[i].height() + " (at:" + clicks[i].start() + " len:"
                                        + duration + ")");
                    }
                }
            }
            double multiplier = getPlayrate().getMultiplier();
            long duration = (long) (getDuration() * multiplier);
            if (outpoint < 0 || outpoint > duration) {
                outpoint = duration;
            } else {
                outpoint = (long) (outpoint * multiplier);
            }
            int inMillis = (int) (inpoint * multiplier) / 1000;
            int outMillis = (int) outpoint / 1000;
            long off;

            if (DEBUG) {
                LOGGER.info("playAudio:" + playAudio);
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
                    video.updateTime((int) inMillis);
                }
                if (bgImage != null && playbackRenderer.showBackground()) {
                    playbackRenderer.setBackgroundImage(bgImage);
                }
            }
            if (audioBytes == null) {
                playbackRenderer.setErrorMessage(I18.get("sheet_msg_still_loading"));
                return;
            }
            final CountDownLatch playbackStartLatch = new CountDownLatch(1);
            Runnable onPlaybackStarted = () -> {
                if (hasPlaybackRenderer) {
                    playbackRenderer.setPause(false);
                    playbackRenderer.startPlayback();
                    if (video != null && playbackRenderer.showVideo()) {
                        video.play();
                    }
                }
                firePlayerStarted();
                playbackStartLatch.countDown();
            }; 
            byte[] pianoAndClicks = createAudioStreamFromClicks(clicks, timebase.timerate, playClicks, midiEnabled, inpoint);
            byte[] audioData = getAudioBytesInRange(inMillis + (int) seekInOffsetMs,
                                                    outMillis + (int) seekOutOffsetMs);
            byte[] mixedAudio = mixAudioStereo(audioData, pianoAndClicks);
            playAudioData(mixedAudio, audioBytesFormat, audioData.length, onPlaybackStarted);
            try {
                playbackStartLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sharedLineInterrupted = true;
            }

            long nanoStart = System.nanoTime() / 1000L; // Start timing AFTER audio has started
            position = (long) (inpoint * multiplier);

            long lastms = System.nanoTime();

            if (sharedLineInterrupted) {
                LOGGER.info("Playback interrupted.");
            }
            while (!sharedLineInterrupted) {
                long tempdiff = (System.nanoTime() / 1000L) - nanoStart;
                position = (long) (tempdiff + (inpoint * multiplier));
                if (position >= outpoint) {
                    position = outpoint;
                    sharedLineInterrupted = true;
                    if (DEBUG) LOGGER.info("Playback stopped.");
                    break;
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
                    if (video != null) {
                        if (playbackRenderer.showVideo()) {
                            // playbackRenderer.setVideoFrame(video.getFrame()); // No longer needed with JFXPanel
                        }
                        video.updateTime(currentMillis);
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
                    sharedLineInterrupted = true;
                }

            }

            if (playAudio && sharedLine != null && sharedLine.isOpen()) {
                sharedLine.drain();
                sharedLine.stop();
            }

            if (midiEnabled && midi != null) {
                midi.stopPlay();
            }
            sharedLineInterrupted = true;

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
                    video.stop();
                }
                playbackRenderer.finishPlayback();
            }
            live = false;

            finished = true;
            firePlayerStopped();
        }
    }

    public void setPianoVolume(int vol) {
        int volume;
        if (vol >= YassMIDI.VOLUME_MIN && vol <= YassMIDI.VOLUME_MAX) {
            volume = vol;
        } else {
            volume = YassMIDI.VOLUME_MED;
        }
        if (midi != null) {
            midi.setVolume(volume);
        }
    }

    public void reinitSynth(boolean useSamples) {
        if (useSamples) {
            midi = null;
        } else {
            midi = new YassMIDI();
        }
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

    public File generateTemp(String source) throws IOException {
        return generateTemp(source, yass.Timebase.NORMAL);
    }

    public File generateTemp(String source, Timebase timeBase) throws IOException {
        String filename;
        if (timeBase == yass.Timebase.NORMAL) {
            filename = TEMP_WAV;
        } else {
            filename = USER_PATH + timeBase.getId() + "temp.wav";
        }
        return generateTemp(source, timeBase, filename);
    }

    public File generateTemp(String source, Timebase timeBase, String filename) throws IOException {
        if (FFMPEGLocator.getInstance().getFfmpeg() == null) {
            return null;
        }
        FFmpeg ffmpeg = FFMPEGLocator.getInstance().getFfmpeg();
        FFprobe fFprobe = FFMPEGLocator.getInstance().getFfprobe();
        FFmpegProbeResult fFmpegProbeResult = fFprobe.probe(source);
        final Double durationNs;
        FFmpegBuilder fFmpegBuilder = new FFmpegBuilder();
        fFmpegBuilder.addInput(fFmpegProbeResult);
        if (fFmpegProbeResult != null && fFmpegProbeResult.getStreams() != null && !fFmpegProbeResult.getStreams()
                                                                                                     .isEmpty()) {
            audioBytesSampleRate = fFmpegProbeResult.getStreams().get(0).sample_rate;
            audioBytesChannels = fFmpegProbeResult.getStreams().get(0).channels;
            durationNs = fFmpegProbeResult.getFormat().duration * 1_000_000_000;
        } else {
            durationNs = null;
        }
        this.key = findKey();
        replayGain = determineReplayGain(source);
        File tempFile = new File(filename);
        if (timeBase != yass.Timebase.NORMAL) {
            fFmpegBuilder.setAudioFilter(timeBase.getFilter());
        }
        int channels = 2;
        if (filename.contains("samples") && (filename.contains("longnotes") || filename.contains("shortnotes"))) {
            channels = 1;
        }
        fFmpegBuilder.overrideOutputFiles(true)
                     .addOutput(tempFile.getAbsolutePath())
                     .setAudioChannels(channels)
                     .setAudioSampleRate(44100)
                     .done();
        FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, fFprobe);
        // Eine thread-sichere Variable, um den Fortschritt zwischen den Threads zu teilen.
        final java.util.concurrent.atomic.AtomicReference<net.bramp.ffmpeg.progress.Progress> progressHolder = new java.util.concurrent.atomic.AtomicReference<>();
        FFmpegJob job = executor.createJob(fFmpegBuilder, progress -> {
            if (durationNs == null) {
                return;
            }
            // Aktualisiere den Fortschritt. Dies wird im Hintergrund-Thread aufgerufen.
            progressHolder.set(progress);
        });

        Thread conversionThread = new Thread(() -> {
            LOGGER.info("YassPlayer: Starting conversion of " + source + " to " + filename);
            job.run();
            LOGGER.info("YassPlayer: finished converting " + source + " to " + filename);
        }, "FFmpeg-Conversion-Thread");

        conversionThread.start();

        SplashFrame splashFrame = new SplashFrame();
        splashFrame.setVisible(true); // Mach den Frame sofort sichtbar

        // Aktualisiere den Fortschrittsbalken in einer Schleife, bis der Thread fertig ist
        while (conversionThread.isAlive()) {
            net.bramp.ffmpeg.progress.Progress progress = progressHolder.get();
            if (progress != null && durationNs != null) {
                double percentage = (progress.out_time_ns / durationNs) * 100;
                splashFrame.updateProgress("Generating Temp file", (int) percentage);
            }
            try {
                // Gib dem UI-Thread eine kurze Pause, um sich neu zu zeichnen
                // und andere Events zu verarbeiten.
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break; // Schleife bei Interruption verlassen
            }
        }

        splashFrame.dispose();
        return tempFile;
    }

    private double determineReplayGain(String sourcePath) {
        FFprobe ffprobe = FFMPEGLocator.getInstance().getFfprobe();
        if (ffprobe == null) return 0.0;

        try {
            FFmpegProbeResult probeResult = ffprobe.probe(sourcePath);
            List<FFmpegStream> streams = probeResult.getStreams();

            if (streams != null && !streams.isEmpty()) {
                FFmpegStream firstStream = streams.getFirst();
                String trackGain = firstStream.tags != null ? firstStream.tags.get("R128_TRACK_GAIN") : null;
                if (trackGain != null) {
                    try {
                        double r128Gain = Double.parseDouble(trackGain);
                        double gain = r128Gain / 256.0;
                        LOGGER.info("Found R128_TRACK_GAIN tag: " + gain + " dB. Applying this gain.");
                        return gain;
                    } catch (NumberFormatException e) {
                        LOGGER.warning("Could not parse R128_TRACK_GAIN value: " + trackGain);
                    }
                }
            }
            FFmpegFormat format = probeResult.getFormat();
            String gainTag = format.tags != null ? format.tags.get("replaygain_track_gain") : null;
            if (gainTag == null && format.tags != null) {
                gainTag = format.tags.get("replaygain_album_gain");
            }

            if (gainTag != null && gainTag.toLowerCase().contains("db")) {
                try {
                    String numericPart = gainTag.toLowerCase().replace("db", "").trim();
                    double gain = Double.parseDouble(numericPart);
                    LOGGER.info("Found ReplayGain tag: " + gain + " dB. Applying this gain.");
                    return gain;
                } catch (NumberFormatException e) {
                    LOGGER.warning("Could not parse ReplayGain value: " + gainTag);
                }
            }
        
            // If targetDbfs is set and no ReplayGain tag was found, analyze the file with ffmpeg.
            if (Math.round(targetDbfs) != 0) {
                FFmpeg ffmpeg = FFMPEGLocator.getInstance().getFfmpeg();
                if (ffmpeg != null) {
                    try {
                        // Use ProcessBuilder for direct control over stderr
                        List<String> command = new ArrayList<>();
                        command.add(ffmpeg.getPath());
                        command.add("-i");
                        command.add(sourcePath);
                        command.add("-af");
                        command.add("replaygain");
                        command.add("-f");
                        command.add("null");
                        command.add("-");

                        ProcessBuilder processBuilder = new ProcessBuilder(command);
                        processBuilder.redirectErrorStream(true); // Redirect stderr to stdout
                        Process process = processBuilder.start();

                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.contains("track_gain")) {
                                    return parseReplayGainFromLine(line);
                                }
                            }
                        }
                        process.waitFor();
                    } catch (IOException | InterruptedException e) {
                        LOGGER.log(Level.SEVERE, "Error during ffmpeg replaygain analysis", e);
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error during ffprobe execution for normalization", e);
        }

        return 0.0; // Default: no gain
    }

    /**
     * Parses a line from the ffmpeg replaygain filter output to extract the track gain.
     * Example line: [Parsed_replaygain_0 @ 0x...] track_gain = -3.2dB
     * @param line The output line from ffmpeg.
     * @return The parsed gain in dB, or 0.0 if parsing fails.
     */
    private double parseReplayGainFromLine(String line) {
        try {
            String[] parts = line.split("track_gain = ");
            if (parts.length > 1) {
                String gainStr = parts[1].replace("dB", "").trim();
                double gain = Double.parseDouble(gainStr);
                LOGGER.info("FFmpeg analysis: ReplayGain track_gain is " + gain + " dB.");
                return gain;
            }
        } catch (NumberFormatException e) {
            LOGGER.warning("Could not parse track_gain from ffmpeg output: " + line);
        }
        return 0.0; // Default to no gain if parsing fails
    }

    public void playNote(int note) {
        playNote(note, 500, Integer.MIN_VALUE);
    }

    public void playNote(int note, double length, int nextNote) {
        if (lastNote != null && note == lastNote) {
            if (DEBUG) LOGGER.info("playNote: StopNote");
            stopNote();
        }
        byte[] audioData;
        int lengthFactor;
        int currentNote = note + 60;
        final int finalLength;
        if (length > 1000) {
            audioData = LONG_NOTE_MAP.get(currentNote);
            lengthFactor = 1000;
            finalLength = (int) length;
        } else {
            audioData = SHORT_NOTE_MAP.get(currentNote);
            lengthFactor = 1;
            finalLength = (int) Math.max(length, 500);
        }
        if (audioData != null) {
            if (DEBUG) LOGGER.info("playNote: Playing " + note);
            lastNote = currentNote * lengthFactor;
            playAudioData(audioData, pianoFormat, finalLength, null);
        } else {
            if (DEBUG) LOGGER.info("playNote: Couldn't find note " + note);
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
        return key == null ? MusicalKeyEnum.UNDEFINED : key;
    }

    public void saveKey(MusicalKeyEnum musicalKeyEnum) {
        if (musicalKeyEnum == null || musicalKeyEnum == MusicalKeyEnum.UNDEFINED || musicalKeyEnum == getKey()) {
            this.key = MusicalKeyEnum.UNDEFINED;
            return;
        }
        this.key = musicalKeyEnum;
        AudioFile audioFile;
        try {
            audioFile = AudioFileIO.read(new File(filename));
            Tag tag = audioFile.getTagOrCreateAndSetDefault();
            tag.setField(FieldKey.KEY, musicalKeyEnum.getRelevantKey());
            audioFile.commit();
        } catch (CannotReadException | IOException | TagException | ReadOnlyFileException | InvalidAudioFrameException |
                 CannotWriteException e) {
            throw new RuntimeException(e);
        }
    }

    public MusicalKeyEnum findKey() {
        AudioFile audioFile;
        if (filename == null) {
            return MusicalKeyEnum.UNDEFINED;
        }
        try {
            File file = new File(filename);
            if (!file.exists()) {
                return MusicalKeyEnum.UNDEFINED;
            }
            audioFile = AudioFileIO.read(file);
            Tag tag = audioFile.getTag();
            if (tag == null) {
                return MusicalKeyEnum.UNDEFINED;
            }
            return MusicalKeyEnum.findKey(tag.getFirst(FieldKey.KEY));
        } catch (CannotReadException | IOException | TagException | ReadOnlyFileException |
                 InvalidAudioFrameException e) {
            LOGGER.info("Could not determine key for " + filename);
        }
        return MusicalKeyEnum.UNDEFINED;
    }

    /**
     * Mischt zwei Stereo-PCM-Byte-Arrays (16 Bit, little endian, signed) sampleweise mit Clipping.
     * Gibt ein neues Array der Länge des längeren Arrays zurück.
     */
    private static byte[] mixAudioStereo(byte[] a, byte[] b) {
        int len = Math.max(a.length, b.length);
        byte[] result = new byte[len];
        for (int i = 0; i < len; i += 2) {
            short sampleA = 0;
            short sampleB = 0;
            if (i + 1 < a.length) {
                sampleA = (short) ((a[i + 1] << 8) | (a[i] & 0xFF));
            }
            if (i + 1 < b.length) {
                sampleB = (short) ((b[i + 1] << 8) | (b[i] & 0xFF));
            }
            int mixed = sampleA + sampleB;
            if (mixed > Short.MAX_VALUE) mixed = Short.MAX_VALUE;
            if (mixed < Short.MIN_VALUE) mixed = Short.MIN_VALUE;
            result[i] = (byte) (mixed & 0xFF);
            result[i + 1] = (byte) ((mixed >> 8) & 0xFF);
        }
        return result;
    }

    /**
     * Öffnet eine geteilte SourceDataLine mit dem aktuellen AudioFormat, falls noch nicht vorhanden.
     * Startet einen Thread, der die Queue abarbeitet.
     */
    public synchronized void openSharedLine(AudioFormat format) {
        if (sharedLine != null && sharedLine.isOpen() && format.matches(sharedLineFormat)) {
            return;
        }
        closeSharedLine();
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            sharedLine = (SourceDataLine) AudioSystem.getLine(info);
            sharedLine.open(format);
            sharedLine.start();
            sharedLineFormat = format;
            sharedLineRunning = true;
            sharedLineThread = new Thread(() -> {
                try {
                    while (sharedLineRunning) {
                        Pair<byte[], Runnable> job = sharedLineQueue.take();
                        byte[] data = job.getLeft();
                        Runnable callback = job.getRight();
                        boolean callbackCalled = false;

                        int offset = 0;
                        int blockSize = 1024;
                        while (offset < data.length && !sharedLineInterrupted) {
                            int toWrite = Math.min(blockSize, data.length - offset);
                            int written = sharedLine.write(data, offset, toWrite);
                            if (written <= 0) {
                                break;
                            }
                            if (!callbackCalled && callback != null) {
                                try {
                                    callback.run();
                                } finally {
                                    callbackCalled = true;
                                }
                            }
                            offset += written;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "SharedSourceLineThread");
            sharedLineThread.start();
        } catch (LineUnavailableException e) {
            LOGGER.throwing("YassPlayer", "openSharedLine", e);
            sharedLine = null;
            sharedLineThread = null;
            sharedLineFormat = null;
            sharedLineRunning = false;
        }
    }

    /**
     * Schließt die geteilte SourceDataLine und den zugehörigen Thread.
     */
    public synchronized void closeSharedLine() {
        sharedLineRunning = false;
        if (sharedLineThread != null) {
            sharedLineThread.interrupt();
            sharedLineThread = null;
        }
        if (sharedLine != null) {
            try {
                sharedLine.flush();
                sharedLine.close();
            } catch (Exception ignored) {
            }
            sharedLine = null;
        }
        sharedLineFormat = null;
        sharedLineQueue.clear();
    }

    /**
     * Prüft, ob die geteilte SourceDataLine offen ist.
     */
    public synchronized boolean isSharedLineOpen() {
        return sharedLine != null && sharedLine.isOpen() && sharedLineRunning;
    }

    /**
     * Sets the audioEnabled attribute of the YassPlayer object
     */
    public void playAudioData(byte[] audioData, AudioFormat audioFormat, int length, 
                              Runnable onStarted) {
        if (isSharedLineOpen() && audioFormat.matches(sharedLineFormat)) {
            if (!sharedLine.isRunning()) {
                sharedLine.start();
            }
            sharedLineInterrupted = false;
            int offset = 0;
            int chunkSize = 4096; // z.B.
            boolean firstChunk = true;
            while (offset < length) {
                int chunkLength = Math.min(chunkSize, length - offset);
                byte[] chunk = Arrays.copyOfRange(audioData, offset, offset + chunkLength);
                Runnable callback = firstChunk ? onStarted : null;
                try {
                    sharedLineQueue.put(new ImmutablePair<>(chunk, callback));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                firstChunk = false;
                offset += chunkLength;
            }
        } else  {
            LOGGER.info("YassPlayer.playAudioData: SourceDataLine not open");
        }
    }

    public double calculateRMS(byte[] pcmData, int numSamples) {
        long sumSquares = 0;

        for (int i = 0; i < pcmData.length; i += 2) {
            // 16-Bit-Sample extrahieren (Little-Endian)
            short sample = ByteBuffer.wrap(pcmData, i, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
            sumSquares += sample * sample;
        }

        // Durchschnitt der Quadrate und Wurzel
        return Math.sqrt((double) sumSquares / numSamples);
    }

    public byte[] normalizePCM(byte[] pcmData, double factor) {
        byte[] normalizedData = new byte[pcmData.length];

        for (int i = 0; i < pcmData.length; i += 2) {
            // 16-Bit-Sample extrahieren
            short sample = ByteBuffer.wrap(pcmData, i, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
            // Skalieren
            int normalizedSample = (int) (sample * factor);

            // Clipping auf 16-Bit-Bereich
            normalizedSample = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, normalizedSample));

            // Normalisierte Werte zurückschreiben
            ByteBuffer.wrap(normalizedData, i, 2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) normalizedSample);
        }

        return normalizedData;
    }

    public double getTargetDbfs() {
        return targetDbfs;
    }

    public void setTargetDbfs(double targetDbfs) {
        this.targetDbfs = targetDbfs;
    }

    public void setTargetDbfs(String dbfsProp) {
        if (StringUtils.isEmpty(dbfsProp) || !NumberUtils.isParsable(dbfsProp)) {
            setTargetDbfs(0);
        } else {
            setTargetDbfs(NumberUtils.toDouble(dbfsProp));
        }
    }

    public boolean hasAudio() {
        return StringUtils.isNotEmpty(filename);
    }

    /**
     * Erzeugt einen Stereo-Audio-Stream als Byte-Array aus den übergebenen Clicks.
     * Mono-Samples werden auf beide Kanäle dupliziert.
     *
     * @param clicks      Array von Click-Objekten (start, height, end)
     * @param timebase    Gibt die Abspielgeschwindigkeit an (z. B. 0.5 = halb so schnell)
     * @param playClicks  Wenn true, werden die Noten (Wave/MIDI) ins Array geschrieben
     * @param midiEnabled Wenn true, werden Piano-Töne wie in playNote ins Array geschrieben
     * @return Byte-Array mit dem Stereo-Audio-Stream
     */
    public byte[] createAudioStreamFromClicks(Click[] clicks, double timebase, boolean playClicks, boolean midiEnabled, long playbackStartUs) {
        if (clicks == null || clicks.length == 0) return new byte[0];

        // Der Offset ist der Start der Wiedergabe, nicht der Start des ersten Klicks.
        // Alle Zeitstempel der Klicks werden relativ zu diesem Punkt berechnet.
        long offset = playbackStartUs;

        long lastEnd = clicks[0].end();
        for (Click c : clicks) {
            if (c.end() > lastEnd) lastEnd = c.end();
        }
        int sampleRate = 44100;
        int bytesPerSample = 2; // 16bit PCM
        int channels = 2; // Stereo
        // Die Dauer des Streams reicht vom Wiedergabestart bis zum Ende des letzten Klicks.
        double durationSeconds = ((Math.max(lastEnd, offset) - offset) / 1_000_000.0) / timebase;
        int totalSamples = (int) (durationSeconds * sampleRate) + sampleRate; // +1s Puffer
        int totalBytes = totalSamples * bytesPerSample * channels;
        byte[] audio = new byte[totalBytes];

        for (Click click : clicks) {
            // Berechne die Startposition des Klicks relativ zum Wiedergabestart (offset).
            // Wenn click.start() > offset, ist startSample > 0, was die Stille am Anfang erzeugt.
            int startSample = (int) (((click.start() - offset) / 1_000_000.0) / timebase * sampleRate);
            if (startSample < 0) {
                // Dieser Klick liegt vor dem Wiedergabestart und wird ignoriert.
                continue;
            }
            int lengthSamples = (int) (((click.end() - click.start()) / 1_000_000.0) / timebase * sampleRate);
            if (lengthSamples <= 0) continue;
            byte[] pcm = null;
            boolean isPiano = false;
            if (playClicks && !midiEnabled) {
                if (useWav()) {
                    pcm = drumClick; // Mono
                } else {
                    int midiPitch = click.height() + 60;
                    if (midiPitch >= 0 && midiPitch < midis.length && midis[midiPitch] != null) {
                        pcm = midis[midiPitch];
                    }
                }
            } else if (midiEnabled) {
                int note = click.height() + 60;
                pcm = LONG_NOTE_MAP.get(note);
                isPiano = true;
            }
            if (pcm != null) {
                int maxSamples = Math.min(pcm.length / 2, lengthSamples);
                int audioMaxSamples = Math.min(lengthSamples, (audio.length / 4) - startSample);
                int samplesToCopy = Math.min(maxSamples, audioMaxSamples);
                for (int i = 0; i < samplesToCopy; i++) {
                    int pcmIndex = i * 2;
                    int audioIndex = (startSample + i) * 4;
                    byte lo = pcm[pcmIndex];
                    byte hi = pcm[pcmIndex + 1];
                    if (isPiano) {
                        int fadeSamples = samplesToCopy / 8;
                        boolean doFade = samplesToCopy > fadeSamples;
                        short sample = (short) (((hi & 0xFF) << 8) | (lo & 0xFF));
                        if (doFade && i >= samplesToCopy - fadeSamples) {
                            double fadeFac = (double) (samplesToCopy - i) / fadeSamples;
                            sample = (short) (sample * fadeFac);
                            lo = (byte) (sample & 0xFF);
                            hi = (byte) ((sample >> 8) & 0xFF);
                        }
                        int mixedL = audio[audioIndex] + lo;
                        int mixedH = audio[audioIndex + 1] + hi;
                        audio[audioIndex] = (byte) Math.max(Math.min(mixedL, 127), -128);
                        audio[audioIndex + 1] = (byte) Math.max(Math.min(mixedH, 127), -128);
                        int mixedRL = audio[audioIndex + 2] + lo;
                        int mixedRH = audio[audioIndex + 3] + hi;
                        audio[audioIndex + 2] = (byte) Math.max(Math.min(mixedRL, 127), -128);
                        audio[audioIndex + 3] = (byte) Math.max(Math.min(mixedRH, 127), -128);
                    } else {
                        audio[audioIndex] = lo;
                        audio[audioIndex + 1] = hi;
                        audio[audioIndex + 2] = lo;
                        audio[audioIndex + 3] = hi;
                    }
                }
            }
        }
        return audio;
    }
}
