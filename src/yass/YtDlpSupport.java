package yass;

import com.jfposton.ytdlp.YtDlp;
import com.jfposton.ytdlp.YtDlpException;
import com.jfposton.ytdlp.YtDlpRequest;
import org.apache.commons.lang3.StringUtils;
import yass.options.YtDlpPanel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public final class YtDlpSupport {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    private YtDlpSupport() {
    }

    public static void ensureExecutableAvailable(YassProperties properties) throws IOException {
        String configuredPath = StringUtils.trimToNull(properties.getProperty("ytdlpPath"));
        if (StringUtils.isNotBlank(configuredPath)) {
            Path executable = Path.of(configuredPath);
            if (!Files.isRegularFile(executable)) {
                LOGGER.warning("Configured yt-dlp executable does not exist: " + configuredPath);
                properties.remove("ytdlpPath");
                properties.remove("ytdlp-version");
                properties.store();
                throw new IOException("Configured yt-dlp executable does not exist: " + configuredPath);
            }
            YtDlp.setExecutablePath(executable.toAbsolutePath().toString());
            rememberVersion(properties);
            return;
        }

        try {
            rememberVersion(properties);
        } catch (IOException primaryFailure) {
            if (isWindowsMissingCommand(primaryFailure)) {
                YtDlp.setExecutablePath("yt-dlp.exe");
                rememberVersion(properties);
                return;
            }
            throw primaryFailure;
        }
    }

    public static void applyCommonOptions(YtDlpRequest request, YassProperties properties) {
        String ffmpegPath = properties.getProperty("ffmpegPath");
        if (StringUtils.isNotBlank(ffmpegPath)) {
            request.setOption("ffmpeg-location", ffmpegPath);
        }
    }

    public static String buildVideoOnlyFormatString(YassProperties properties, boolean includeCodec) {
        String videoCodec = properties.getProperty(YtDlpPanel.YTDLP_VIDEO_CODEC);
        String videoResolution = properties.getProperty(YtDlpPanel.YTDLP_VIDEO_RESOLUTION);
        StringBuilder format = new StringBuilder("bestvideo");
        if (includeCodec && StringUtils.isNotBlank(videoCodec)) {
            format.append(videoCodec);
        }
        if (StringUtils.isNotBlank(videoResolution)) {
            format.append(videoResolution);
        }
        return format.toString();
    }

    public static String buildCombinedVideoFormatString(YassProperties properties, boolean includeCodec) {
        StringBuilder format = new StringBuilder(buildVideoOnlyFormatString(properties, includeCodec));
        format.append(",bestaudio");
        String audioBitrate = properties.getProperty(YtDlpPanel.YTDLP_AUDIO_BITRATE);
        if (StringUtils.isNotBlank(audioBitrate)) {
            format.append("[abr<=").append(audioBitrate.replace("k", "000")).append("]");
        }
        return format.toString();
    }

    public static void applyAudioExtractionOptions(YtDlpRequest request, YassProperties properties) {
        request.setOption("extract-audio");
        String audioFormat = properties.getProperty(YtDlpPanel.YTDLP_AUDIO_FORMAT);
        if (StringUtils.isNotBlank(audioFormat)) {
            request.setOption("audio-format", audioFormat);
        }
    }

    private static void rememberVersion(YassProperties properties) throws IOException {
        try {
            String version = StringUtils.trimToNull(YtDlp.getVersion());
            if (StringUtils.isBlank(version)) {
                throw new IOException("yt-dlp did not report a version.");
            }
            properties.setProperty("ytdlp-version", version);
            properties.store();
        } catch (YtDlpException ex) {
            throw new IOException(StringUtils.defaultIfBlank(ex.getMessage(), ex.toString()), ex);
        }
    }

    private static boolean isWindowsMissingCommand(IOException ex) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return false;
        }
        String message = StringUtils.defaultString(ex.getMessage());
        return message.contains("Cannot run program \"yt-dlp\"")
                || message.contains("CreateProcess error=2")
                || message.contains(File.separator.equals("\\") ? "Das System kann die angegebene Datei nicht finden" : "");
    }
}
