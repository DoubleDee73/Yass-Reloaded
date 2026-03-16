package yass.integration.separation.audioseparator;

import org.apache.commons.lang3.StringUtils;
import yass.YassProperties;
import yass.YassTable;
import yass.integration.separation.SeparationProgressListener;
import yass.integration.separation.SeparationRequest;
import yass.integration.separation.SeparationResult;
import yass.integration.separation.SeparationService;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class AudioSeparatorSeparationService implements SeparationService {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(30);

    private final YassProperties properties;

    public AudioSeparatorSeparationService(YassProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isConfigured() {
        String python = properties.getProperty("audiosep-python");
        return StringUtils.isNotBlank(python);
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
        String model = StringUtils.defaultIfBlank(properties.getProperty("audiosep-model"), "Kim_Vocal_2.onnx");
        String outputFormat = StringUtils.defaultIfBlank(properties.getProperty("audiosep-output-format"), "wav");
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
        String model = StringUtils.defaultIfBlank(properties.getProperty("audiosep-model"), "Kim_Vocal_2.onnx");
        String outputFormat = StringUtils.defaultIfBlank(properties.getProperty("audiosep-output-format"), "wav");
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

        return findOutputStems(outputDir, request.getSongBaseName(), request.getOutputFormat(), model);
    }

    private List<String> buildCommand(File audioFile, File outputDir, String model, String outputFormat) {
        String python = StringUtils.defaultIfBlank(properties.getProperty("audiosep-python"), "python");
        List<String> cmd = new ArrayList<>();
        cmd.add(python);
        cmd.add("-m");
        cmd.add("audio_separator");
        cmd.add(audioFile.getAbsolutePath());
        cmd.add("--model_filename");
        cmd.add(model);
        cmd.add("--output_dir");
        cmd.add(outputDir.getAbsolutePath());
        cmd.add("--output_format");
        cmd.add(outputFormat.toUpperCase(Locale.ROOT));

        String modelDir = properties.getProperty("audiosep-model-dir");
        if (StringUtils.isNotBlank(modelDir)) {
            cmd.add("--model_file_dir");
            cmd.add(modelDir);
        }

        String ffmpegPath = properties.getProperty("ffmpegPath");
        if (StringUtils.isNotBlank(ffmpegPath)) {
            File ffmpegExe = new File(ffmpegPath);
            String dir = ffmpegExe.isDirectory() ? ffmpegExe.getAbsolutePath() : ffmpegExe.getParent();
            if (StringUtils.isNotBlank(dir)) {
                cmd.add("--ffmpeg_base_path");
                cmd.add(dir);
            }
        }
        return cmd;
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
            } else if (name.contains("instrum") || name.contains("karaoke") || name.contains("accompan") || name.contains("music")) {
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
                    + ". Files found: " + List.of(files).stream().map(File::getName).toList());
        }

        return new SeparationResult(vocalsFile, null, instrumentalFile, null);
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
