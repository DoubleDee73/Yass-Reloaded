package yass.integration.separation.audioseparator;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import yass.YassProperties;
import yass.YassTable;
import yass.integration.separation.SeparationProgressListener;
import yass.integration.separation.SeparationRequest;
import yass.integration.separation.SeparationResult;
import yass.integration.separation.SeparationService;
import yass.options.enums.YtDlpAudioFormat;
import yass.options.YtDlpPanel;

public class AudioSeparatorSeparationService implements SeparationService {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(30);

    public static final String PROP_PYTHON       = "audiosep-python";
    static final String PROP_MODEL        = "audiosep-model";
    static final String PROP_MODEL_DIR    = "audiosep-model-dir";
    static final String PROP_OUTPUT_FORMAT = "audiosep-output-format";
    public static final String PROP_HEALTH_OK    = "audiosep-health-ok";
    private static final String DEFAULT_MODEL         = "vocals_mel_band_roformer.ckpt";
    private static final String DEFAULT_OUTPUT_FORMAT = "wav";
    private static final String DEFAULT_TARGET_FORMAT = "mp3";

    private final YassProperties properties;

    public AudioSeparatorSeparationService(YassProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.isNotBlank(properties.getProperty(PROP_PYTHON));
    }

    @Override
    public SeparationRequest createRequest(YassTable table) {
        if (table == null) {
            throw new IllegalArgumentException("No song is currently open.");
        }
        String audio = table.getAudio();
        if (StringUtils.isBlank(audio)) {
            throw new IllegalStateException("The current song has no #AUDIO file configured.");
        }
        File audioFile = new File(table.getDir(), audio);
        if (!audioFile.isFile()) {
            throw new IllegalStateException("The configured #AUDIO file could not be found.");
        }
        String model = StringUtils.defaultIfBlank(properties.getProperty(PROP_MODEL), DEFAULT_MODEL);
        String outputFormat = StringUtils.defaultIfBlank(properties.getProperty(PROP_OUTPUT_FORMAT), DEFAULT_OUTPUT_FORMAT);
        String baseName = buildSongBaseName(table, audioFile);
        return new SeparationRequest(table.getDir(), audioFile, model, outputFormat, baseName);
    }

    public SeparationRequest createRequest(File outputDirectory, File audioFile, String songBaseName) {
        if (outputDirectory == null) {
            throw new IllegalArgumentException("No output directory was provided.");
        }
        if (audioFile == null || !audioFile.isFile()) {
            throw new IllegalArgumentException("The source audio file could not be found.");
        }
        if (!isConfigured()) {
            throw new IllegalStateException("audio-separator is not configured (Python executable missing).");
        }
        String model = StringUtils.defaultIfBlank(properties.getProperty(PROP_MODEL), DEFAULT_MODEL);
        String outputFormat = StringUtils.defaultIfBlank(properties.getProperty(PROP_OUTPUT_FORMAT), DEFAULT_OUTPUT_FORMAT);
        String baseName = StringUtils.defaultIfBlank(songBaseName, stripExtension(audioFile.getName()));
        LOGGER.info("audio-separator wizard request prepared for " + baseName + " using model " + model);
        return new SeparationRequest(outputDirectory.getAbsolutePath(), audioFile, model, outputFormat, baseName);
    }

    @Override
    public SeparationResult startSeparation(SeparationRequest request,
                                            SeparationProgressListener progressListener) throws Exception {
        SeparationProgressListener listener = progressListener == null ? status -> {} : progressListener;
        String model = request.getModel();
        File audioFile = request.getAudioFile();
        File outputDir = new File(request.getSongDirectory());
        Files.createDirectories(outputDir.toPath());

        LOGGER.info("Starting audio-separator for " + audioFile.getName() + " with model " + model);
        listener.onStatusChanged("Separating vocals locally with audio-separator...");

        List<String> command = buildCommand(audioFile, outputDir, model, request.getOutputFormat());
        LOGGER.info("audio-separator command: " + command);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.directory(outputDir);
        prependFfmpegToPath(pb);
        Process process = pb.start();

        StringBuilder fullOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LOGGER.info("[audio-separator] " + line);
                fullOutput.append(line).append("\n");
                if (line.contains("%") || line.toLowerCase().contains("processing") || line.toLowerCase().contains("separating")) {
                    listener.onStatusChanged(line);
                }
            }
        }

        boolean finished = process.waitFor(PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("audio-separator timed out after " + PROCESS_TIMEOUT.toMinutes() + " minutes.");
        }
        if (process.exitValue() != 0) {
            LOGGER.severe("audio-separator failed:\n" + fullOutput);
            throw new IOException("audio-separator failed with exit code " + process.exitValue() + ".\n" + fullOutput);
        }

        SeparationResult rawResult = findOutputStems(outputDir, request.getSongBaseName(), request.getOutputFormat(), model);
        return convertStemsToTargetFormat(rawResult, listener);
    }

    private List<String> buildCommand(File audioFile, File outputDir, String model, String outputFormat) {
        String python = StringUtils.defaultIfBlank(properties.getProperty(PROP_PYTHON), "python");
        String script = AudioSeparatorHealthCheckService.resolveAudioSeparatorScript(python);
        List<String> cmd = new ArrayList<>();
        cmd.add(script);
        cmd.add(audioFile.getAbsolutePath());
        cmd.add("--model_filename");
        cmd.add(model);
        cmd.add("--output_dir");
        cmd.add(outputDir.getAbsolutePath());
        cmd.add("--output_format");
        cmd.add(outputFormat.toUpperCase(Locale.ROOT));

        String modelDir = properties.getProperty(PROP_MODEL_DIR);
        if (StringUtils.isNotBlank(modelDir)) {
            cmd.add("--model_file_dir");
            cmd.add(modelDir);
        }
        return cmd;
    }

    private void prependFfmpegToPath(ProcessBuilder pb) {
        String ffmpegExe = resolveFfmpegExecutable();
        File ffmpegFile = new File(ffmpegExe);
        String ffmpegDir = ffmpegFile.getParent();
        if (StringUtils.isBlank(ffmpegDir)) {
            return;
        }
        pb.environment().merge("PATH", ffmpegDir, (existing, added) -> added + File.pathSeparator + existing);
    }

    private SeparationResult findOutputStems(File outputDir, String baseName, String outputFormat, String model) throws IOException {
        // audio-separator names output files as: <inputBaseName>_(Vocals).<ext> and <inputBaseName>_(Instrumental).<ext>
        // The exact stem names depend on the model. We scan the output directory for expected patterns.
        String ext = outputFormat.toLowerCase(Locale.ROOT);
        File[] files = outputDir.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith("." + ext));
        if (files == null || files.length == 0) {
            throw new IOException("audio-separator finished but no output files were found in " + outputDir.getAbsolutePath());
        }

        File vocalsFile = null;
        File instrumentalFile = null;

        for (File f : files) {
            String name = f.getName().toLowerCase(Locale.ROOT);
            if (name.contains("vocal") || name.contains("lead") || name.contains("singing")) {
                if (vocalsFile == null) vocalsFile = f;
            } else if (name.contains("instrum") || name.contains("karaoke") || name.contains("accompan") || name.contains("music") || name.contains("no vocal")) {
                if (instrumentalFile == null) instrumentalFile = f;
            }
        }

        // Fallback: if only two files, assign by elimination
        if (vocalsFile == null && instrumentalFile == null && files.length == 2) {
            vocalsFile = files[0];
            instrumentalFile = files[1];
        } else if (vocalsFile == null && files.length >= 1) {
            for (File f : files) {
                if (!f.equals(instrumentalFile)) {
                    vocalsFile = f;
                    break;
                }
            }
        }

        LOGGER.info("audio-separator vocals: " + (vocalsFile != null ? vocalsFile.getName() : "not found"));
        LOGGER.info("audio-separator instrumental: " + (instrumentalFile != null ? instrumentalFile.getName() : "not found"));

        if (vocalsFile == null) {
            throw new IOException("audio-separator finished but no vocals stem could be identified in " + outputDir.getAbsolutePath()
                    + ". Files found: " + Arrays.stream(files).map(File::getName).toList());
        }

        return new SeparationResult(vocalsFile, null, instrumentalFile, null);
    }

    private SeparationResult convertStemsToTargetFormat(SeparationResult result,
                                                         SeparationProgressListener listener) throws IOException {
        String targetExt = resolveTargetExtension();
        File vocals = convertStem(result.getVocalsFile(), targetExt, listener).orElse(result.getVocalsFile());
        File instrumental = convertStem(result.getInstrumentalFile(), targetExt, listener).orElse(result.getInstrumentalFile());
        File instrumentalBacking = convertStem(result.getInstrumentalBackingFile(), targetExt, listener).orElse(result.getInstrumentalBackingFile());
        // getLeadFile() is the same object as vocalsFile in our usage; pass null to avoid duplicate
        return new SeparationResult(vocals, null, instrumental, instrumentalBacking);
    }

    /** Returns the target extension from ytdlp-audio-format, defaulting to mp3. */
    private String resolveTargetExtension() {
        String formatValue = properties.getProperty(YtDlpPanel.YTDLP_AUDIO_FORMAT);
        if (StringUtils.isNotBlank(formatValue)) {
            for (YtDlpAudioFormat fmt : YtDlpAudioFormat.values()) {
                if (fmt.getValue().equalsIgnoreCase(formatValue)) {
                    return fmt.getExtension();
                }
            }
        }
        return DEFAULT_TARGET_FORMAT;
    }

    /**
     * Converts a stem file to the target extension using FFmpeg.
     * Returns an empty Optional if the stem is null or already in the target format.
     * Deletes the original WAV after successful conversion.
     */
    private Optional<File> convertStem(File stem, String targetExt, SeparationProgressListener listener) throws IOException {
        if (stem == null) {
            return Optional.empty();
        }
        if (stem.getName().toLowerCase(Locale.ROOT).endsWith("." + targetExt)) {
            return Optional.of(stem);
        }

        String baseName = stripExtension(stem.getName());
        File output = new File(stem.getParentFile(), baseName + "." + targetExt);
        listener.onStatusChanged("Converting " + stem.getName() + " to " + targetExt + "...");
        LOGGER.info("Converting stem: " + stem.getAbsolutePath() + " -> " + output.getAbsolutePath());

        List<String> cmd = new ArrayList<>(Arrays.asList(resolveFfmpegExecutable(), "-y", "-i", stem.getAbsolutePath()));
        addFormatArgs(cmd, targetExt);
        cmd.add(output.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        prependFfmpegToPath(pb);
        Process proc = pb.start();

        StringBuilder out = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                out.append(line).append("\n");
            }
        }
        boolean done = false;
        try {
            done = proc.waitFor(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("FFmpeg conversion interrupted for " + stem.getName(), e);
        }
        if (!done) {
            proc.destroyForcibly();
            throw new IOException("FFmpeg conversion timed out for " + stem.getName());
        }
        if (proc.exitValue() != 0) {
            throw new IOException("FFmpeg conversion failed for " + stem.getName() + ":\n" + out);
        }

        Files.deleteIfExists(stem.toPath());
        return Optional.of(output);
    }

    /** Adds format-specific FFmpeg encoding arguments. */
    private void addFormatArgs(List<String> cmd, String ext) {
        switch (ext.toLowerCase(Locale.ROOT)) {
            case "mp3"  -> { cmd.add("-q:a"); cmd.add("0"); }
            case "ogg"  -> { cmd.add("-c:a"); cmd.add("libvorbis"); cmd.add("-q:a"); cmd.add("6"); }
            case "opus" -> { cmd.add("-c:a"); cmd.add("libopus"); cmd.add("-b:a"); cmd.add("192k"); }
            case "m4a"  -> { cmd.add("-c:a"); cmd.add("aac"); cmd.add("-q:a"); cmd.add("2"); }
            default     -> { cmd.add("-q:a"); cmd.add("0"); }
        }
    }

    /** Resolves the ffmpeg executable path from properties, falling back to bare "ffmpeg" on PATH. */
    private String resolveFfmpegExecutable() {
        String ffmpegPath = properties.getProperty("ffmpegPath");
        if (StringUtils.isBlank(ffmpegPath)) {
            return "ffmpeg";
        }
        File f = new File(ffmpegPath);
        if (f.isDirectory()) {
            File exe = new File(f, "ffmpeg.exe");
            if (exe.isFile()) return exe.getAbsolutePath();
            exe = new File(f, "ffmpeg");
            if (exe.isFile()) return exe.getAbsolutePath();
        } else if (f.isFile()) {
            return f.getAbsolutePath();
        }
        return "ffmpeg";
    }

    private String buildSongBaseName(YassTable table, File audioFile) {
        String artist = sanitize(table.getArtist());
        String title = sanitize(table.getTitle());
        if (StringUtils.isNotBlank(artist) && StringUtils.isNotBlank(title)) {
            return artist + " - " + title;
        }
        return StringUtils.defaultIfBlank(sanitize(stripExtension(audioFile.getName())), "Separation");
    }

    private String sanitize(String value) {
        if (StringUtils.isBlank(value)) return "";
        return value.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", " ").trim();
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
