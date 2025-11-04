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

import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import yass.ffmpeg.FFMPEGLocator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Description of the Class
 *
 * @author Saruta
 */
public class YassUtils {
    private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    public final static int NORTH = 0;
    public final static int SOUTH = 1;
    public final static int EAST = 2;
    public final static int WEST = 3;
    private static String msg = null;
    private YassHyphenator hyphenator;
    
    private int defaultLength = 2;
    private boolean spacingAfter = true;
    
    /**
     * Gets the songDir attribute of the YassUtils class
     *
     * @param parent Description of the Parameter
     * @param prop   Description of the Parameter
     * @return The songDir value
     */
    public static String getSongDir(Component parent, YassProperties prop) {
        return getSongDir(parent, prop, false);
    }

    /**
     * Gets the songDir attribute of the YassUtils class
     *
     * @param parent Description of the Parameter
     * @param prop   Description of the Parameter
     * @param force  Description of the Parameter
     * @return The songDir value
     */
    public static String getSongDir(Component parent, YassProperties prop, boolean force) {
        String songdir = prop.getProperty("song-directory");
        if (songdir == null || !new File(songdir).exists() || force) {
            JFileChooser chooser = new JFileChooser();
            File d = null;
            if (songdir != null) {
                d = new File(songdir);
            }
            if (d == null || !d.exists()) {
                d = new java.io.File(".");
            }
            chooser.setCurrentDirectory(d);
            chooser.setDialogTitle(I18.get("utils_songdir"));
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
                return null;
            }
            songdir = chooser.getSelectedFile().getAbsolutePath();
            prop.setProperty("song-directory", songdir);
            prop.store();
        }
        return songdir;
    }

    /**
     * Description of the Method
     *
     * @param songdir Description of the Parameter
     * @param title   Description of the Parameter
     * @param artist  Description of the Parameter
     * @return Description of the Return Value
     */
    public static String findSong(String songdir, String title, String artist) {
        // @deprecated      Use YassSongList.findSong instead
        artist = artist.toLowerCase().trim();
        title = title.toLowerCase().trim();

        File dFiles[] = new File(songdir).listFiles();
        for (File dFile : dFiles) {
            if (dFile.isDirectory()) {
                String fd = dFile.getName().toLowerCase();
                if (fd.startsWith(artist)) {
                    if (fd.indexOf(title, artist.length()) > 0) {
                        return dFile.getAbsolutePath();
                    }
                }
                String dir = findSong(dFile.getAbsolutePath(), title, artist);
                if (dir != null) {
                    return dir;
                }
            }
        }
        return null;
    }

    /**
     * Gets the data attribute of the YassUtils class
     *
     * @param s Description of the Parameter
     * @return The data value
     */
    public static String[] getData(String s) {
        if (s == null) {
            return null;
        }
        File f = new File(s);
        if (!f.exists()) {
            return null;
        }
        String artist = null;
        String title= null;
        String genre = "unknown";
        if (f.getName().contains(" - ")) {
            String[] temp = f.getName().split("-");
            artist = temp[0].trim();
            title = temp[1].trim();
            if (title.endsWith(".mp3") || title.endsWith(".m4a") || title.endsWith(".wav") || title.endsWith(
                    ".ogg") || title.endsWith(".aac")) {
                title = title.substring(0, title.length() - 4);
            } else if (title.endsWith(".webm") || title.endsWith(".flac") || title.endsWith(".opus")) {
                title = title.substring(0, title.length() - 5);
            }
        }
        try {
            FFMPEGLocator ffmpegLocator = FFMPEGLocator.getInstance();
            if (ffmpegLocator == null || !ffmpegLocator.hasFFmpeg()) {
                return null;
            }
            FFmpegProbeResult probeResult = ffmpegLocator.getFfprobe().probe(f.getAbsolutePath());
            Map<String, String> properties = probeResult.getFormat().tags;
            if (properties != null) {
                if (StringUtils.isNotEmpty(properties.get("artist"))) {
                    artist = properties.get("artist");
                }
                if (StringUtils.isNotEmpty(properties.get("title"))) {
                    title = properties.get("title");
                }
                if (properties.get("mp3.id3tag.genre") != null) {
                    genre = properties.get("mp3.id3tag.genre");
                }
            }
        } catch (Exception ignored) {
        }
        if (artist == null) {
            artist = f.getName();
        }
        if (title == null) {
            title = f.getName();
        }
        String data[] = new String[3];
        data[0] = title;
        data[1] = artist;
        data[2] = genre;
        return data;
    }

    /**
     * Description of the Method
     *
     * @param parent Description of the Parameter
     * @param vals   Description of the Parameter
     * @return Description of the Return Value
     */
    public static String createSong(JComponent parent, Hashtable<?, ?> vals, YassProperties prop) {
        String artist = (String) vals.get("artist");
        String title = (String) vals.get("title");
        String language = (String) vals.get("language");
        String genre = (String) vals.get("genre");
        String bpm = (String) vals.get("bpm");
        String mp3filename = (String) vals.get("filename");
        String folder = (String) vals.get("folder");
        String songdir = (String) vals.get("songdir");
        String tabletxt = (String) vals.get("melodytable");
        String encoding = (String) vals.get("encoding");
        if (encoding != null && encoding.trim().length() < 1) {
            encoding = null;
        }
        // saruta, Jan 2019: utf8-->UTF-8
        if (encoding != null && encoding.equals("utf8")) {
            encoding = "UTF-8";
        }

        if (artist == null || artist.trim().length() < 1) {
            artist = "UnknownArtist";
        }
        if (title == null || title.trim().length() < 1) {
            title = "UnknownTitle";
        }

        String at = YassSong.toFilename(artist + " - " + title);

        if (songdir == null || songdir.trim().length() < 1 || !new File(songdir).exists()) {
            JFileChooser chooser = new JFileChooser();
            File d = new java.io.File(".");
            chooser.setCurrentDirectory(d);
            chooser.setDialogTitle(I18.get("tool_prefs_songs_spec"));
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
                return null;
            }
            songdir = chooser.getSelectedFile().getAbsolutePath();
        }

        File dir;
        if (folder != null) {
            dir = new File(songdir + File.separator + folder);
            if (!dir.exists()) {
                dir.mkdir();
            }
        } else {
            dir = new File(songdir);
        }

        //new song dir; move file & create empty txt
        File newdir = new File(dir, at);
        File newtxt = new File(newdir, at + ".txt");
        if (newdir.exists() && newtxt.exists()) {
            int ok = JOptionPane.showConfirmDialog(parent,
                                                   "<html>" + MessageFormat.format(I18.get("create_error_msg_1"),
                                                                                   newdir.getAbsolutePath()) + "</html>",
                                                   I18.get("create_error_title"), JOptionPane.OK_CANCEL_OPTION);
            if (ok == JOptionPane.OK_OPTION) {
                deleteDir(newdir);
            } else {
                return null;
            }
        }
        if (!newdir.exists()) {
            newdir.mkdir();
        }

        YassTable table = new YassTable();
        table.init(prop);
        table.removeAllRows();
        table.setText(tabletxt);
        table.setArtist(artist);
        table.setTitle(title);
        table.setGenre(genre);
        table.setLanguage(language);
        table.setBPM(bpm);
        table.setEncoding(encoding);
        table.storeFile(newtxt.getAbsolutePath());
        return newtxt.getAbsolutePath();
    }

    /**
     * Description of the Method
     *
     * @param in  Description of the Parameter
     * @param out Description of the Parameter
     * @return Description of the Return Value
     */
    public static boolean copyFile(File in, File out) {
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            fis = new FileInputStream(in);
            fos = new FileOutputStream(out);
            byte[] buf = new byte[1024];
            int i;
            while ((i = fis.read(buf)) != -1) {
                fos.write(buf, 0, i);
            }
            return true;
        } catch (Exception e) {
            LOGGER.info("Cannot copy file: " + in.getName());
            return false;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (Exception ignored) {
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Gets the wildcard attribute of the YassAutoCorrect object
     *
     * @param s  Description of the Parameter
     * @param id Description of the Parameter
     * @return The wildcard value
     */
    public static String getWildcard(String s, String id) {
        s = s.toLowerCase();
        id = id.toLowerCase();

        int k = id.indexOf("*");
        if (k < 0) {
            return null;
        }

        String id2 = id.substring(k + 1);
        String id1 = id.substring(0, k);

        int n1 = s.lastIndexOf(id1);
        if (n1 < 0) {
            return null;
        }
        int n2 = s.indexOf(id2, n1 + 1);
        if (n2 < 0) {
            return null;
        }
        return s.substring(n1 + id1.length(), n2);
    }

    /**
     * Gets the fileWithExtension attribute of the YassUtils object
     *
     * @param dir Description of the Parameter
     * @param id  Description of the Parameter
     * @param ext Description of the Parameter
     * @return The fileWithExtension value
     */
    public static File getFileWithExtension(String dir, String id, String ext[]) {
        // ext = ext.toLowerCase();

        String id2 = null;
        if (id != null) {
            int k = id.indexOf("*");
            if (k > 0) {
                id2 = id.substring(k + 1);
                id = id.substring(0, k);
            }
        }

        File files[] = new File(dir).listFiles();
        if (files == null) {
            return null;
        }

        for (File file : files) {
            String filename = file.getName().toLowerCase();

            for (String anExt : ext) {
                if (filename.endsWith(anExt)) {
                    if (id == null) {
                        return file;
                    }

                    int idn = filename.lastIndexOf(id);
                    if (id2 != null) {
                        if (idn > 0 && filename.indexOf(id2, idn + 1) > 0) {
                            return file;
                        }
                    }
                    if (idn > 0) {
                        return file;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Description of the Method
     *
     * @param t            Description of the Parameter
     * @param coverID      Description of the Parameter
     * @param backgroundID Description of the Parameter
     * @param videoID      Description of the Parameter
     * @param videodirID   Description of the Parameter
     * @return Description of the Return Value
     */
    public static boolean renameFiles(YassTable t, String coverID, String backgroundID, String videoID, String videodirID) {
        String artist = YassSong.toFilename(t.getArtist());
        String title = YassSong.toFilename(t.getTitle());

        String dir = t.getDir();
        String text = t.getFilename();
        String audio = t.getMP3();
        String cover = t.getCover();
        String background = t.getBackgroundTag();
        String video = t.getVideo();
        String videogap = Double.toString(t.getVideoGap()).replace('.', ',');
        if (videogap.endsWith(",0")) {
            videogap = videogap.substring(0, videogap.length() - 2);
        }

        String at = artist + " - " + title;

        File dirfile = new File(dir);
        if (!dirfile.exists() || !dirfile.isDirectory()) {
            LOGGER.info("Error: Cannot find folder " + dir);
            return false;
        }
        String newdir = dirfile.getParent() + File.separator + at;
        if (video != null) {
            newdir = newdir + " " + videodirID;
        }

        if (!newdir.equals(dir)) {
            File newdirfile = new File(newdir);
            if (dirfile.renameTo(newdirfile)) {
                t.setDir(newdir);
                dir = newdir;
            } else {
                LOGGER.info("Error: Cannot rename folder " + dir);
            }
        }

        String extension;
        int i;
        String filename = dir + File.separator + text;
        File file = new File(filename);
        if (file.exists()) {
            i = text.lastIndexOf(".");
            extension = text.substring(i);
            extension = extension.toLowerCase();
            text = at + extension;

            filename = dir + File.separator + text;
            File newfile = new File(filename);
            if (!newfile.equals(file)) {
                if (file.renameTo(newfile)) {
                    t.setFilename(text);
                } else {
                    LOGGER.info("Error: Cannot rename karaoke " + filename);
                }
            }
        }

        filename = dir + File.separator + cover;
        file = new File(filename);
        if (file.exists()) {
            i = cover.lastIndexOf(".");
            extension = cover.substring(i).toLowerCase();

            cover = at + " " + coverID + extension;
            filename = dir + File.separator + cover;
            File newfile = new File(filename);
            if (!newfile.equals(file)) {
                if (file.renameTo(newfile)) {
                    t.setCover(cover);
                } else {
                    LOGGER.info("Error: Cannot rename cover " + filename);
                }
            }
        }

        filename = dir + File.separator + background;
        file = new File(filename);
        if (file.exists()) {
            i = background.lastIndexOf(".");
            extension = background.substring(i).toLowerCase();

            background = at + " " + backgroundID + extension;
            filename = dir + File.separator + background;
            File newfile = new File(filename);
            if (!newfile.equals(file)) {
                if (file.renameTo(newfile)) {
                    t.setBackground(background);
                } else {
                    LOGGER.info("Error: Cannot rename background " + filename);
                }
            }
        }

        filename = dir + File.separator + audio;
        file = new File(filename);
        if (file.exists()) {
            i = audio.lastIndexOf(".");
            extension = audio.substring(i).toLowerCase();

            audio = at + extension;
            filename = dir + File.separator + audio;
            File newfile = new File(filename);
            if (!newfile.equals(file)) {
                if (file.renameTo(newfile)) {
                    t.setMP3(audio);
                } else {
                    LOGGER.info("Error: Cannot rename audio " + filename);
                }
            }
        }

        int n = videoID.indexOf("*");
        String videoID1 = videoID.substring(0, n);
        String videoID2 = videoID.substring(n + 1);

        filename = dir + File.separator + video;
        file = new File(filename);
        if (file.exists()) {
            i = video.lastIndexOf(".");
            extension = video.substring(i).toLowerCase();

            video = at + " " + videoID1 + videogap + videoID2 + extension;
            filename = dir + File.separator + video;
            File newfile = new File(filename);
            if (!newfile.equals(file)) {
                if (file.renameTo(newfile)) {
                    t.setVideo(video);
                } else {
                    LOGGER.info("Error: Cannot rename video " + filename);
                }
            }
        }
        return true;
    }

    /**
     * Gets the message attribute of the YassUtils class
     *
     * @return The message value
     */
    public static String getMessage() {
        return msg;
    }

    /**
     * Description of the Method
     *
     * @param dir Description of the Parameter
     * @return Description of the Return Value
     */
    public static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (String child : children) {
                boolean success = false;
                int trials = 4;
                while (!success && trials-- > 0) {
                    success = deleteDir(new File(dir, child));
                    if (!success) {
                        //LOGGER.info("cannot delete " + children[i]);
                        try {
                            Thread.currentThread();
                            Thread.sleep(100);
                        } catch (Exception ignored) {
                        }
                    }
                }
                if (!success) {
                    msg = MessageFormat.format(I18.get("utils_msg_remove_error"), child);
                    return false;
                }
            }
        }
        return YassUtils.deleteFile(dir);
    }

    public static boolean deleteFile(File file) {
        boolean success;
        try {
            success = Desktop.getDesktop().moveToTrash(file);
        } catch (Exception e) {
            success = file.delete();
        }
        return success;
    }

    /**
     * Description of the Method
     *
     * @param s Description of the Parameter
     * @return Description of the Return Value
     * @throws ClassNotFoundException Description of the Exception
     */
    public static Class<?> forName(String s)
            throws ClassNotFoundException {
        Class<?> classDefinition;
        try {
            classDefinition = Class.forName(s, true, ClassLoader.getSystemClassLoader());
        } catch (Throwable cls) {
            //LOGGER.info("Security manager hides SystemClassLoader.");
            //LOGGER.info("Falling back to ContextClassLoader.");
            try {
                classDefinition = Class.forName(s, true, Thread.currentThread().getContextClassLoader());
            } catch (Throwable cls2) {
                //    LOGGER.info("Security manager hides current thread's ContextClassLoader.");
                try {
                    classDefinition = Class.forName(s);
                } catch (Throwable cls3) {
                    throw new ClassNotFoundException("Unknown class: " + s);
                }
                //    LOGGER.info("Falling back to Class.forName("+s+")");
            }
        }
        return classDefinition;
    }

    /**
     * Description of the Method
     *
     * @param source Description of the Parameter
     * @return Description of the Return Value
     * @throws IOException Description of the Exception
     */
    public static BufferedImage readImage(Object source) throws IOException {
        ImageInputStream stream = ImageIO.createImageInputStream(source);
        if (stream == null) {
            if (source instanceof java.net.URL) {
                return ImageIO.read((java.net.URL) source);
            } else {
                return null;
            }
        }
        Iterator<?> it = ImageIO.getImageReaders(stream);
        if (!it.hasNext()) {
            // bug with firefox 2
            BufferedImage buf = null;
            if (source instanceof File) {
                buf = ImageIO.read((File) source);
            }
            return buf;
        }
        ImageReader reader = (ImageReader) it.next();
        reader.setInput(stream);
        ImageReadParam param = reader.getDefaultReadParam();

        ImageTypeSpecifier typeToUse = null;
        boolean looking = true;
        for (Iterator<?> i = reader.getImageTypes(0); i.hasNext() && looking; ) {
            ImageTypeSpecifier type = (ImageTypeSpecifier) i.next();
            if (type.getColorModel().getColorSpace().getNumComponents() == 1) {
                typeToUse = type;
                looking = false;
            } else if (type.getColorModel().getColorSpace().isCS_sRGB()) {
                typeToUse = type;
                looking = false;
            }
        }
        if (typeToUse != null) {
            param.setDestinationType(typeToUse);
        }

        BufferedImage b = null;
        try {
            b = reader.read(0, param);
        } catch (Exception e) {
            LOGGER.log(Level.INFO, e.getMessage(), e);
        }

        reader.dispose();
        stream.close();
        return b;
    }

    /**
     * Gets the scaledInstance attribute of the YassUtils object
     *
     * @param img          Description of the Parameter
     * @param targetWidth  Description of the Parameter
     * @param targetHeight Description of the Parameter
     * @return The scaledInstance value
     */
    public static BufferedImage getScaledInstance(BufferedImage img, int targetWidth, int targetHeight) {
        boolean higherQuality = true;
        Object hint = RenderingHints.VALUE_INTERPOLATION_BICUBIC;

        int type = (img.getTransparency() == Transparency.OPAQUE) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage ret = img;
        int w;
        int h;
        if (higherQuality) {
            // Use multi-step technique: start with original size, then
            // scale down in multiple passes with drawImage()
            // until the target size is reached
            w = img.getWidth();
            h = img.getHeight();
        } else {
            // Use one-step technique: scale directly from original
            // size to target size with a single drawImage() call
            w = targetWidth;
            h = targetHeight;
        }

        do {
            if (higherQuality && w > targetWidth) {
                w /= 2;
                if (w < targetWidth) {
                    w = targetWidth;
                }
            }

            if (higherQuality && h > targetHeight) {
                h /= 2;
                if (h < targetHeight) {
                    h = targetHeight;
                }
            }

            BufferedImage tmp = new BufferedImage(w, h, type);
            Graphics2D g2 = tmp.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
            g2.drawImage(ret, 0, 0, w, h, null);
            g2.dispose();

            ret = tmp;
        } while (w != targetWidth || h != targetHeight);

        return ret;
    }

    /**
     * Description of the Method
     *
     * @param str     Description of the Parameter
     * @param pattern Description of the Parameter
     * @param replace Description of the Parameter
     * @return Description of the Return Value
     */
    public static String replace(String str, String pattern, String replace) {
        int s = 0;
        int e;
        StringBuilder result = new StringBuilder();

        while ((e = str.indexOf(pattern, s)) >= 0) {
            result.append(str.substring(s, e));
            result.append(replace);
            s = e + pattern.length();
        }
        result.append(str.substring(s));
        return result.toString();
    }

    /**
     * Description of the Method
     *
     * @param str Description of the Parameter
     * @param t   Description of the Parameter
     * @return Description of the Return Value
     */
    public static String replace(String str, Hashtable<?, ?> t) {
        int s = 0;
        int e;
        StringBuilder result = new StringBuilder();

        while ((e = str.indexOf('$', s)) >= 0) {
            int e2 = e + 1;
            char c = str.charAt(e2);
            while (c != ' ' && c != ';') {
                c = str.charAt(++e2);
            }
            String key = str.substring(e + 1, e2);
            result.append(str.substring(s, e));
            String replace = (String) t.get(key.toLowerCase());
            result.append(replace);
            s = e2 + 1;
        }
        result.append(str.substring(s));
        return result.toString();
    }

    /**
     * Description of the Method
     *
     * @param ms Description of the Parameter
     * @return Description of the Return Value
     */
    public static String commaTime(long ms) {
        String sec = "0";
        String msec = ms + "";
        int j = msec.length() - 3;
        if (j > 0) {
            sec = msec.substring(0, j);
            msec = msec.substring(j);
        }
        while (msec.length() < 3) {
            msec = "0" + msec;
        }
        return sec + "," + msec;
    }

    /**
     * Description of the Method
     *
     * @param g          Description of the Parameter
     * @param x          Description of the Parameter
     * @param y          Description of the Parameter
     * @param size       Description of the Parameter
     * @param direction  Description of the Parameter
     * @param isEnabled  Description of the Parameter
     * @param darkShadow Description of the Parameter
     * @param shadow     Description of the Parameter
     * @param highlight  Description of the Parameter
     */
    public static void paintTriangle(Graphics g, int x, int y, int size, int direction, boolean isEnabled, Color darkShadow, Color shadow, Color highlight) {
        Color oldColor = g.getColor();
        int mid;
        int i;
        int j;

        size = Math.max(size, 2);
        mid = (size / 2) - 1;

        g.translate(x, y);
        if (isEnabled) {
            g.setColor(shadow);
        } else {
            g.setColor(darkShadow);
        }

        switch (direction) {
            case NORTH:
                for (i = 0; i < size; i++) {
                    g.drawLine(mid - i, i, mid + i, i);
                }
                break;
            case SOUTH:
                j = 0;
                for (i = size - 1; i >= 0; i--) {
                    g.drawLine(mid - i, j, mid + i, j);
                    j++;
                }
                break;
            case WEST:
                for (i = 0; i < size; i++) {
                    g.drawLine(i, mid - i, i, mid + i);
                }
                break;
            case EAST:
                j = 0;
                for (i = size - 1; i >= 0; i--) {
                    g.drawLine(j, mid - i, j, mid + i);
                    j++;
                }
                break;
        }
        g.translate(-x, -y);
        g.setColor(oldColor);
    }

    /**
     * Checks if file is a karaoke file.
     *
     * @param file path to file
     * @return
     */
    public static boolean isKaraokeFile(String file) {
        File f = new File(file);
        return f.exists() && f.isFile() && YassUtils.isKaraokeFile(f);
    }

    public static boolean isKaraokeFile(File f) {
        if (!f.getName().endsWith(".txt")) {
            return false;
        }

        try {
            unicode.UnicodeReader r = new unicode.UnicodeReader(new FileInputStream(f), null);
            BufferedReader inputStream = new BufferedReader(r);
            // BufferedReader inputStream = new BufferedReader(new FileReader(f));
            String l;
            while ((l = inputStream.readLine()) != null) {
                int n = l.length();
                if (n < 1) {
                    continue;
                }
                if (n > 6) {
                    l = l.substring(0, 6).toUpperCase();
                    if (l.startsWith("#TITLE")) {
                        inputStream.close();
                        return true;
                    }
                }
                if (l.startsWith("#")) {
                    continue;
                }
                inputStream.close();
                return false;
            }
            inputStream.close();
        } catch (Exception e) {
            LOGGER.log(Level.INFO, e.getMessage(), e);
        }
        return false;
    }

    public static boolean isValidKaraokeString(String s) {
        boolean hasTitle = false;
        boolean hasArtist = false;
        try {
            StringReader r = new StringReader(s);
            BufferedReader inputStream = new BufferedReader(r);

            String l;
            while ((l = inputStream.readLine()) != null) {
                if (l.startsWith("#")) {
                    if (l.startsWith("#TITLE")) {
                        hasTitle = true;
                    }
                    if (l.startsWith("#ARTIST")) {
                        hasArtist = true;
                    }
                } else {
                    if (!hasTitle || !hasArtist) {
                        inputStream.close();
                        return false;
                    }
                    if (l.startsWith("E") && l.trim().equals("E")) {
                        inputStream.close();
                        return true;
                    }
                }
            }
            inputStream.close();
        } catch (Exception e) {
            LOGGER.log(Level.INFO, e.getMessage(), e);
        }
        return false;
    }

    /**
     * Gets all karaoke files of the given folder.
     *
     * @param folder       path to folder
     * @param songFileType only files that end with this string
     * @return null if folder does not exist or is no folder
     */
    public static Vector<String> getKaraokeFiles(String folder, String songFileType) {
        Vector<String> res = null;
        File file = new File(folder);
        if (file.exists() && file.isDirectory()) {
            res = new Vector<>();
            File[] files = file.listFiles((dir, name) -> name.endsWith(songFileType));
            for (File f : files) {
                if (YassUtils.isKaraokeFile(f))
                    res.addElement(f.getAbsolutePath());
            }
        }
        return res;
    }

    /**
     * Gets number of bits for a given number n.
     * Examples:
     * n=3 --> 2 bits
     * n=4 --> 3 bits
     *
     * @param n
     * @return
     */
    public static int getBitCount(int n) {
        int bits = 0;
        while (n > 0) {
            n >>= 1;
            ++bits;
        }
        return bits;
    }

    /**
     * Gets bit mask for a given number n.
     * Examples:
     * n=1 --> [0]
     * n=2 --> [1]
     * n=3 --> [0,1]
     * n=4 --> [2]
     * n=5 --> [0,2]
     * n=6 --> [1,2]
     * n=7 --> [0,1,2]
     * n=8 --> [3]
     * n=9 --> [0,3]
     * ...
     * n=15 --> [0,1,2,3]
     *
     * @param n
     * @return
     */
    public static Vector<Integer> getBitMask(int n) {
        Vector<Integer> bits = new Vector<>();
        int bit = 0;
        while (n > 0) {
            if ((n & 1) != 0)
                bits.add(bit);
            n >>= 1;
            ++bit;
        }
        return bits;
    }

    /**
     * Gets [count] powers of two.
     * Examples:
     * n=4 --> [1,2,4,8]
     *
     * @param count
     * @return
     */
    public static Vector<Integer> getPow2(int count) {
        Vector<Integer> pow2 = new Vector<>();
        int n = 1;
        while (count > 0) {
            pow2.add(n);
            n <<= 1;
            --count;
        }
        return pow2;
    }

    public static boolean isPunctuation(String input) {
        return Pattern.matches("\\p{IsPunctuation}", input);
    }

    static class ImageLoadStatus {
        public boolean widthDone = false;
        public boolean heightDone = false;
    }

    public static void addChangeListener(JTextComponent text, ChangeListener changeListener) {
        Objects.requireNonNull(text);
        Objects.requireNonNull(changeListener);
        DocumentListener dl = new DocumentListener() {
            private int lastChange = 0, lastNotifiedChange = 0;

            @Override
            public void insertUpdate(DocumentEvent e) {
                changedUpdate(e);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                changedUpdate(e);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                lastChange++;
                SwingUtilities.invokeLater(() -> {
                    if (lastNotifiedChange != lastChange) {
                        lastNotifiedChange = lastChange;
                        changeListener.stateChanged(new ChangeEvent(text));
                    }
                });
            }
        };
        text.addPropertyChangeListener("document", (PropertyChangeEvent e) -> {
            Document d1 = (Document) e.getOldValue();
            Document d2 = (Document) e.getNewValue();
            if (d1 != null) d1.removeDocumentListener(dl);
            if (d2 != null) d2.addDocumentListener(dl);
            dl.changedUpdate(null);
        });
        Document d = text.getDocument();
        if (d != null) d.addDocumentListener(dl);
    }

    /**
     * Creates txt rows from lyrics. The lyrics must be pre-split into an array of lines
     * @param lines A whole line of lyrics
     * @return
     */
    public List<String> splitLyricsToLines(String[] lines, int startBeat) {
        List<String> textLines = new ArrayList<>();
        for (String line : lines) {
            if (StringUtils.isEmpty(line) || line.equalsIgnoreCase("chorus") ||
                    line.equalsIgnoreCase("bridge") ||
                    line.equalsIgnoreCase("verse") ||
                    line.equalsIgnoreCase("outro")) {
                continue;
            }
            List<String> rows = createRowsFromLine(line, startBeat);
            textLines.addAll(rows);
            YassRow tempRow = new YassRow(rows.get(rows.size() -1));
            startBeat = tempRow.getBeatInt() + tempRow.getLengthInt() + 2;
            textLines.add("-	" + startBeat);
            startBeat = startBeat + 1;
        }
        return textLines;
    }

    /**
     * Create a list of rows
     * @param line
     * @param startBeat
     * @return
     */
    private List<String> createRowsFromLine(String line, int startBeat) {
        String[] words = line.split(" ");
        List<String> rows = new ArrayList<>();
        for (String word : words) {
            List<String> syllables = createRowsFromWord(word, startBeat);
            rows.addAll(syllables);
            YassRow tempRow = new YassRow(syllables.get(syllables.size() - 1));
            startBeat = tempRow.getBeatInt() + tempRow.getLengthInt() + 1;
        }
        return rows;
    }

    /**
     * Creates a list of syllables from a word
     * @param word
     * @param startBeat
     * @return
     */
    private List<String> createRowsFromWord(String word, int startBeat) {
        List<String> rows = new ArrayList<>();
        String[] syllables = hyphenator.hyphenateWord(word).split("­");
        int i = 0;
        for (String syllable : syllables) {
            StringJoiner row = new StringJoiner("	");
            row.add(":");
            row.add(Integer.toString(startBeat));
            row.add(Integer.toString(getDefaultLength()));
            row.add("6");
            if (!isSpacingAfter() && i == 0) {
                syllable = " " + syllable;
            }
            if (isSpacingAfter() && (++i == syllables.length)) {
                syllable += " ";
            }
            row.add(syllable);
            rows.add(row.toString());
            startBeat = startBeat + getDefaultLength() + 1;
        }
        return rows;
    }

    public void setHyphenator(YassHyphenator hyphenator) {
        this.hyphenator = hyphenator;
    }

    public int getDefaultLength() {
        return defaultLength;
    }

    public void setDefaultLength(int defaultLength) {
        this.defaultLength = defaultLength;
    }

    public boolean isSpacingAfter() {
        return spacingAfter;
    }

    public void setSpacingAfter(boolean spacingAfter) {
        this.spacingAfter = spacingAfter;
    }

    /**
     * Determines a Locale for a given display language in English
     * @param displayLanguage e. g. "English", "German" and not "Englisch", "Deutsch"
     * @return the first locale found. Beware, could be en_US or en_UK, or something completely different
     */
    public static Locale determineLocale(String displayLanguage) {
        List<Locale> locales = Arrays.asList(Locale.ENGLISH, Locale.GERMAN, Locale.FRENCH, Locale.of("es"));
        Locale locale = findLocaleByDisplayLanguage(displayLanguage, locales);
        if (locale == null) {
            locale = findLocaleByDisplayLanguage(displayLanguage, Arrays.asList(Locale.getAvailableLocales()));
        }
        return locale;
    }

    @Nullable
    private static Locale findLocaleByDisplayLanguage(String displayLanguage, 
                                                      List<Locale> locales) {
        Locale locale = null;
        for (Locale tempLocale : locales) {
            locale = Locale.availableLocales()
                           .filter(loc -> loc.getDisplayLanguage(tempLocale).equals(displayLanguage))
                           .findFirst()
                           .orElse(null);
            if (locale != null) {
                break;
            }
        }
        return locale;
    }

    public static String determineDisplayLanguage(String language) {
        return Locale.of(language).getDisplayLanguage(Locale.ENGLISH);
    }
    
    public static String findFirstKeyInMap(Map<String, String> map, List<String> keys) {
        String val;
        for (String key : keys) {
            val = map.get(key);
            if (val != null) {
                return val;
            }
            val = map.get(key.toUpperCase());
            if (val != null) {
                return val;
            }
            val = map.get(key.toLowerCase());
            if (val != null) {
                return val;
            }
        }
        return null;
    }

    public static boolean isUrlReachable(String urlString) {
        try {
            urlString = urlString.replace("images.fanart.tv", "assets.fanart.tv");
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD"); // oder "GET", wenn HEAD nicht unterstützt wird
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            int responseCode = connection.getResponseCode();
            return (200 <= responseCode && responseCode < 400);
        } catch (Exception e) {
            return false;
        }
    }
}
