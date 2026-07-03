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

import yass.renderer.YassPlayerNote;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Logger;

/**
 * Description of the Class
 *
 * @author Saruta
 */
public class YassCaptureAudio {

    private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private final static float MAX_8_BITS_SIGNED = Byte.MAX_VALUE;
    private final static float MAX_8_BITS_UNSIGNED = 0xff;
    private final static float MAX_16_BITS_SIGNED = Short.MAX_VALUE;
    private final static float MAX_16_BITS_UNSIGNED = 0xffff;
    // 16-bit mono @ 48 kHz: 4096 bytes = 2048 samples (~43 ms), enough to
    // autocorrelate the lowest detected pitch (~65 Hz, period ~738 samples).
    private final static int BUFFER_SIZE = 4096;
    private byte[] buffer = new byte[BUFFER_SIZE];
    private static Hashtable<String, Integer> channelsHash = new Hashtable<>();
    // Noise gate: input below this fraction of full scale (0..1) is treated as
    // silence. Lower = more sensitive (sing quieter). Default 0.15.
    double minlevel = Double.parseDouble("0.15");
    // "1", "2", "4", "8"
    int micboost = Integer.parseInt("64");

    // Input gain applied to decoded samples before level/pitch analysis. The
    // built-in mic is often quiet with no OS gain, so amplifying here lets the
    // user sing at a normal volume. 1.0 = no change.
    private double micGain = 1.0;

    /** Sets the noise-gate threshold (0..1). Lower means more sensitive. */
    public void setMinLevel(double level) {
        minlevel = Math.max(0.0, Math.min(1.0, level));
    }

    /** Sets the input gain multiplier (>= 1). Higher means more sensitive. */
    public void setMicGain(double gain) {
        micGain = Math.max(1.0, gain);
    }
    Hashtable<String, TargetDataLine> linesHash = new Hashtable<>();
    private Color leftColor = new Color(51, 153, 255);
    private Color rightColor = new Color(255, 51, 51);
    private double maxpitchprob = 1;
    private Vector<YassPlayerNote> notesLeft = null;
    private Vector<YassPlayerNote> notesRight = null;
    private YassAudioMonitor monitor = null;
    private boolean stopCapture = false;
    private final static float SAMPLE_RATE = 48000f;
    private AudioFormat audioFormat = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
    // 16-bit samples decoded down to the 8-bit numeric range (-128..127) so the
    // legacy autocorrelation heuristic (micboost, /10000.0) stays calibrated.
    private int[] samples = new int[BUFFER_SIZE / 2];
    private int sampleCount = 0;
    private TargetDataLine line;
    private int currentPitch = YassPlayerNote.NOISE;
    private double currentLevel = 0;
    private long currentMillis = 0;
    private int LEFT = 0;
    private int RIGHT = 1;
    private double pitchprob[] = new double[12];
    private final Component owner;

    /**
     * Constructor for the YassCaptureAudio object
     */
    public YassCaptureAudio() {
        this(null);
    }

    public YassCaptureAudio(Component owner) {
        this.owner = owner;
    }

    /**
     * Description of the Method
     *
     * @param args Description of the Parameter
     */
    public static void main(String args[]) {
        YassCaptureAudio cap = new YassCaptureAudio();
        cap.createGUI();
        cap.startCapture(null);
    }

    /**
     * Gets the deviceNames attribute of the YassCaptureAudio object
     *
     * @return The deviceNames value
     */
    public static String[] getDeviceNames() {
        logAvailableCaptureFormats();
        Vector<String> m = new Vector<>();
        Line.Info targetLineInfo = new Line.Info(TargetDataLine.class);
        Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();
        for (Mixer.Info aMixerInfo : mixerInfo) {
            Mixer mixer = AudioSystem.getMixer(aMixerInfo);
            if (mixer.isLineSupported(targetLineInfo)) {
                String name = aMixerInfo.getName();
                Line.Info[] lineInfo = mixer.getTargetLineInfo();
                for (Line.Info aLineInfo : lineInfo) {
                    if (!(aLineInfo instanceof DataLine.Info)) continue;

                    AudioFormat[] formats = ((DataLine.Info) aLineInfo)
                            .getFormats();
                    for (AudioFormat format : formats) {
                        int channels = format.getChannels();
                        int sampleSizeInBits = format.getSampleSizeInBits();
                        boolean pcmSigned = format.getEncoding().equals(
                                AudioFormat.Encoding.PCM_SIGNED);
                        // Capture as 16-bit signed mono; accept any device that
                        // advertises a 16-bit signed (mono or unknown-channel)
                        // line. Channel count may be reported as -1 (unspecified).
                        if (sampleSizeInBits == 16 && pcmSigned
                                && (channels == 1 || channels == AudioSystem.NOT_SPECIFIED)) {
                            m.addElement(name);
                            channelsHash.put(name, 1);
                            break;
                        }
                    }
                }
            }
        }
        return m.toArray(new String[]{});
    }

    /**
     * Diagnostic: logs every mixer that supports a capture (TargetDataLine) line
     * and the audio formats it advertises, without applying any format filter.
     * Used to discover what the host microphones actually report so the capture
     * format and the {@link #getDeviceNames()} filter can be matched to real
     * hardware.
     */
    public static void logAvailableCaptureFormats() {
        Line.Info targetLineInfo = new Line.Info(TargetDataLine.class);
        Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();
        LOGGER.info("Mic diagnostic: scanning " + mixerInfo.length + " mixers for capture lines");
        for (Mixer.Info aMixerInfo : mixerInfo) {
            Mixer mixer = AudioSystem.getMixer(aMixerInfo);
            if (!mixer.isLineSupported(targetLineInfo)) {
                continue;
            }
            LOGGER.info("Mic diagnostic: capture mixer '" + aMixerInfo.getName()
                    + "' (" + aMixerInfo.getDescription() + ")");
            for (Line.Info aLineInfo : mixer.getTargetLineInfo()) {
                if (!(aLineInfo instanceof DataLine.Info)) {
                    LOGGER.info("    line: " + aLineInfo + " (no format details)");
                    continue;
                }
                AudioFormat[] formats = ((DataLine.Info) aLineInfo).getFormats();
                if (formats.length == 0) {
                    LOGGER.info("    line supports no enumerated formats (any format may work)");
                }
                for (AudioFormat format : formats) {
                    LOGGER.info("    format: encoding=" + format.getEncoding()
                            + " rate=" + format.getSampleRate()
                            + " bits=" + format.getSampleSizeInBits()
                            + " channels=" + format.getChannels()
                            + " frameSize=" + format.getFrameSize()
                            + " bigEndian=" + format.isBigEndian());
                }
            }
        }
        LOGGER.info("Mic diagnostic: end of scan");
    }

    /**
     * Gets the currentPitch attribute of the YassCaptureAudio object
     *
     * @param channel Description of the Parameter
     * @return The currentPitch value
     */
    public YassPlayerNote getCurrentNote(int channel) {
        return getCurrentNote(line, channel);
    }

    /**
     * Gets the currentNote attribute of the YassCaptureAudio object
     *
     * @param line    Description of the Parameter
     * @param channel Description of the Parameter
     * @return The currentNote value
     */
    public YassPlayerNote getCurrentNote(TargetDataLine line, int channel) {
        int available = line.available();
        if (available < buffer.length) {
            return new YassPlayerNote(YassPlayerNote.NOISE, 0, 0);
        }

        int read = line.read(buffer, 0, buffer.length);
        decodeSamples(read);

        float level = calculateLevel();
        boolean noise = level < minlevel;
        int pitch = noise ? YassPlayerNote.NOISE : calculatePitch();

        currentPitch = pitch;
        currentLevel = level;
        currentMillis = System.currentTimeMillis();
        return new YassPlayerNote(currentPitch, currentLevel, currentMillis);
    }

    /**
     * Decodes the raw 16-bit signed mono capture buffer into {@link #samples},
     * scaling down to the 8-bit numeric range so the legacy autocorrelation
     * thresholds remain valid.
     *
     * @param bytesRead number of valid bytes in {@link #buffer}
     */
    private void decodeSamples(int bytesRead) {
        boolean bigEndian = audioFormat.isBigEndian();
        int n = bytesRead / 2;
        for (int i = 0; i < n; i++) {
            int lo = buffer[2 * i] & 0xff;
            int hi = buffer[2 * i + 1] & 0xff;
            short s = (short) (bigEndian ? ((lo << 8) | hi) : ((hi << 8) | lo));
            int v = (int) Math.round((s >> 8) * micGain); // 16-bit -> 8-bit + gain
            samples[i] = Math.max(-128, Math.min(127, v)); // clip to 8-bit range
        }
        sampleCount = n;
    }

    /**
     * Description of the Method
     *
     * @param channel Description of the Parameter
     * @return Description of the Return Value
     */
    public int calculatePitch(int channel) {
        return calculatePitch();
    }

    public int calculatePitch() {
        for (int p = 0; p < 12; p++) {
            pitchprob[p] = 0;
        }

        int maxp = -1;
        maxpitchprob = 0;
        double f;
        int basepitch;
        double cor;
        for (int p = 0; p < 36; p++) {
            basepitch = p % 12;
            // 1.05946309436 = 12th root of 2 = pitch difference between two
            // half-tones. C3 (~130.81 Hz) up three octaves, folded to pitch class.
            f = 130.81 * Math.pow(1.05946309436, p) / 2;
            cor = autocorrelate(f);
            pitchprob[basepitch] = Math.max(cor, pitchprob[basepitch]);
            if (cor > maxpitchprob) {
                maxpitchprob = cor;
                maxp = basepitch;
            }
        }
        return maxp;
    }

    public double autocorrelate(double f, int channel) {
        return autocorrelate(f);
    }

    /**
     * Mono autocorrelation over the decoded {@link #samples}. Lag is the number
     * of samples in one period of {@code f} at the capture sample rate.
     */
    public double autocorrelate(double f) {
        int lag = (int) Math.round(SAMPLE_RATE / f);
        if (lag < 1 || lag >= sampleCount) {
            return 0;
        }
        double n = 0;
        int i = 0;
        for (int src = 0; src + lag < sampleCount; src++, i++) {
            n += Math.abs(samples[src] - samples[src + lag]) / 10000.0;
        }
        if (i == 0) {
            return 0;
        }
        return 1 - micboost * (n / (double) i);
    }

    /**
     * Description of the Method
     *
     * @param channel Description of the Parameter
     * @return Description of the Return Value
     */
    public float calculateLevel(int channel) {
        return calculateLevel();
    }

    /**
     * Peak level (0..1) over the decoded mono {@link #samples}, which are scaled
     * to the 8-bit numeric range.
     */
    public float calculateLevel() {
        int max = 0;
        for (int i = 0; i < sampleCount; i++) {
            max = Math.max(max, Math.abs(samples[i]));
        }
        return (float) max / MAX_8_BITS_SIGNED;
    }

    /**
     * Description of the Method
     */
    public void createGUI() {
        if (monitor == null) {
            monitor = new YassAudioMonitor();
        }

        JFrame f = new JFrame();
        f.getContentPane().setLayout(new BorderLayout());
        f.getContentPane().add("Center", monitor);

        I18.setLanguage(null);
        f.setTitle(I18.get("lib_mic"));
        f.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        f.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                stopCapture();
                e.getWindow().setVisible(false);
                e.getWindow().dispose();
            }
        });

        f.setSize(600, 400);
        f.setLocationRelativeTo(YassUtils.resolveDialogOwner(owner));
        f.setIconImage(new ImageIcon(YassCaptureAudio.this.getClass()
                .getResource("/yass/resources/img/yass-icon-16.png")).getImage());
        f.setVisible(true);
    }

    /**
     * Gets the channels attribute of the YassCaptureAudio class
     *
     * @param name Description of the Parameter
     * @return The channels value
     */
    public int getChannels(String name) {
        return channelsHash.get(name).intValue();
    }

    /**
     * Gets the channelActive attribute of the YassCaptureAudio object
     *
     * @param name Description of the Parameter
     */
    public void startQuery(String name) {
        if (linesHash.get(name) != null) {
            return;
        }

        int micIndex = -1;
        Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();
        for (int i = 0; i < mixerInfo.length; i++) {
            if (mixerInfo[i].getName().equals(name)) {
                micIndex = i;
                break;
            }
        }
        if (micIndex < 0) {
            return;
        }
        try {
            Mixer mixer = AudioSystem.getMixer(mixerInfo[micIndex]);
            TargetDataLine line = (TargetDataLine) mixer.getLine(new DataLine.Info(TargetDataLine.class, audioFormat));
            line.open(audioFormat);
            line.start();
            linesHash.put(name, line);
        } catch (Exception e) {
        }
    }

    /**
     * Description of the Method
     *
     * @param name Description of the Parameter
     */
    public void stopQuery(String name) {
        TargetDataLine line = linesHash.get(name);
        if (line == null) {
            return;
        }
        try {
            line.close();
        } catch (Exception e) {
        }
        linesHash.remove(name);
    }

    /**
     * Description of the Method
     *
     * @param name Description of the Parameter
     * @return Description of the Return Value
     */
    public YassPlayerNote[] query(String name) {
        YassPlayerNote left = new YassPlayerNote(YassPlayerNote.NOISE, 0, 0);
        YassPlayerNote right = new YassPlayerNote(YassPlayerNote.NOISE, 0, 0);

        TargetDataLine line = linesHash.get(name);
        try {
            if (line.available() < buffer.length) {
                return null;
            }
            // Mono capture: one read serves both slots so we don't double-drain
            // the line and starve the second call.
            left = getCurrentNote(line, LEFT);
            right = new YassPlayerNote(left);
        } catch (Exception e) {
        }
        return new YassPlayerNote[]{left, right};
    }

    /**
     * Description of the Method
     *
     * @return Description of the Return Value
     */
    public boolean openLine(String name) {
        try {
            int micIndex = -1;

            Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();
            for (int i = 0; i < mixerInfo.length; i++) {
                if (name == null
                        && mixerInfo[i].getName().indexOf("USBMIC") >= 0) {
                    micIndex = i;
                    break;
                }
                if (mixerInfo[i].getName().equals(name)) {
                    micIndex = i;
                    break;
                }
            }
            if (micIndex < 0) {
                System.err.println("Capture device not found: " + name);
                return false;
            }
            // LOGGER.info(audioFormat);

            Mixer mixer = AudioSystem.getMixer(mixerInfo[micIndex]);
            line = (TargetDataLine) mixer.getLine(new DataLine.Info(TargetDataLine.class, audioFormat));
            line.open(audioFormat);
            line.start();
        } catch (Exception e) {
            System.err.println("Capture failed.");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Description of the Method
     *
     * @return Description of the Return Value
     */
    public boolean startCapture(String device) {
        boolean ok = openLine(device);
        if (!ok) {
            return false;
        }

        stopCapture = false;
        Thread captureThread = new CaptureThread();
        captureThread.start();
        return true;
    }

    /**
     * Description of the Method
     */
    public void stopCapture() {
        stopCapture = true;
        if (line != null)
            line.close();
    }

    class YassAudioMonitor extends JPanel {
        private static final long serialVersionUID = -793354580252286174L;

        /**
         * Constructor for the YassAudioMonitor object
         */
        public YassAudioMonitor() {
            super(true);
        }

        public void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;

            if (notesLeft == null || notesLeft.size() < 1) {
                return;
            }

            YassPlayerNote currentLeft = notesLeft
                    .lastElement();
            YassPlayerNote currentRight = notesRight
                    .lastElement();

            double levelLeft = currentLeft.getLevel();
            double levelRight = currentRight.getLevel();

            boolean noiseLeft = levelLeft < minlevel;
            boolean noiseRight = levelRight < minlevel;

            int w = getSize().width;
            int h = getSize().height;

            while (notesLeft.size() * 4 > w - 40) {
                notesLeft.remove(0);
            }
            while (notesRight.size() * 4 > w - 40) {
                notesRight.remove(0);
            }

            g.setColor(Color.white);
            g2.setStroke(new BasicStroke(1));
            g.fillRect(0, 0, w, h);

            g.setColor(Color.black);
            int hh = (int) (minlevel * h);
            g.fillRect(2, h - hh - 1, 18, hh);
            g.setColor(leftColor);
            hh = (int) (levelLeft * h);
            g.fillRect(2, h - hh, 8, hh);
            g.setColor(rightColor);
            hh = (int) (levelRight * h);
            g.fillRect(12, h - hh, 8, hh);

            g.setColor(Color.black);
            for (int i = 0; i < 12; i++) {
                int y = (int) ((i + 1) / 12.0 * (h - 10));
                g.fillRect(20, h - y - 1, w - 20, 2);
            }

            if (!noiseLeft || !noiseRight) {
                g.setColor(Color.lightGray);
                g2.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                if (pitchprob != null) {
                    g.setColor(Color.gray);
                    int lastx = -1;
                    int lasty = -1;
                    for (int i = 0; i < 12; i++) {
                        int y = (int) ((i + 1) / 12.0 * (h - 10));
                        int x = (int) (pitchprob[i] / maxpitchprob * (w - 30));
                        y = h - y;
                        if (i == 0) {
                            lastx = x;
                            lasty = y;
                        }
                        g.drawLine(lastx, lasty, x, y);
                        lastx = x;
                        lasty = y;
                    }
                }
            }

            int x = 20;
            int last = YassPlayerNote.NOISE;

            g.setColor(leftColor);
            int lastpy = -10;
            for (Enumeration<YassPlayerNote> en = notesLeft.elements(); en
                    .hasMoreElements(); ) {
                YassPlayerNote n = en.nextElement();
                if (n.getHeight() == YassPlayerNote.NOISE) {
                    last = YassPlayerNote.NOISE;
                    x += 4;
                    lastpy = -10;
                    continue;
                }

                int py = n.getHeight() + 1;
                int y = (int) (py / 12.0 * (h - 10));
                if (last == YassPlayerNote.NOISE || Math.abs(py - lastpy) > 4) {
                    last = y;
                }
                lastpy = py;

                int strokeWidth = Math.min(15,
                        (int) (15 * (n.getLevel() - minlevel)));
                g2.setStroke(new BasicStroke(strokeWidth,
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(x - 4, h - last, x, h - y);
                x += 4;
                last = y;
            }
            x = 20;
            last = YassPlayerNote.NOISE;
            lastpy = -10;
            g.setColor(rightColor);
            for (Enumeration<YassPlayerNote> en = notesRight.elements(); en
                    .hasMoreElements(); ) {
                YassPlayerNote n = en.nextElement();
                if (n.getHeight() == YassPlayerNote.NOISE) {
                    last = YassPlayerNote.NOISE;
                    x += 4;
                    lastpy = -10;
                    continue;
                }

                int py = n.getHeight() + 1;
                int y = (int) (py / 12.0 * (h - 10));
                if (last == YassPlayerNote.NOISE || Math.abs(py - lastpy) > 4) {
                    last = y;
                }
                lastpy = py;

                int strokeWidth = Math.min(15,
                        (int) (15 * (n.getLevel() - minlevel)));
                g2.setStroke(new BasicStroke(strokeWidth,
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(x - 4, h - last, x, h - y);
                x += 4;
                last = y;
            }
        }
    }

    class CaptureThread extends Thread {
        public void run() {
            if (monitor != null) {
                if (notesLeft == null) {
                    notesLeft = new Vector<>(4096);
                    notesRight = new Vector<>(4096);
                }
                if (notesLeft.size() < 1) {
                    for (int i = 0; i < 100; i++) {
                        notesLeft.addElement(new YassPlayerNote(
                                YassPlayerNote.NOISE, 0, 0));
                        notesRight.addElement(new YassPlayerNote(
                                YassPlayerNote.NOISE, 0, 0));
                    }
                }
            }

            try {
                while (!stopCapture) {
                    if (line.available() < buffer.length) {
                        try {
                            Thread.currentThread();
                            Thread.sleep(10);
                        } catch (Exception e) {
                        }
                        continue;
                    }
                    YassPlayerNote pnoteLeft = getCurrentNote(LEFT);
                    YassPlayerNote pnoteRight = new YassPlayerNote(pnoteLeft);

                    if (monitor != null) {
                        notesLeft.addElement(pnoteLeft);
                        notesRight.addElement(pnoteRight);
                        monitor.repaint();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
