package yass.integration.separation.audioseparator;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class AudioSeparatorHealthCheckService {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(20);

    private final String configuredPythonExecutable;
    private final String configuredFfmpegPath;

    public AudioSeparatorHealthCheckService(String configuredPythonExecutable, String configuredFfmpegPath) {
        this.configuredPythonExecutable = StringUtils.trimToEmpty(configuredPythonExecutable);
        this.configuredFfmpegPath = StringUtils.trimToNull(configuredFfmpegPath);
    }

    public String detectPythonExecutable() {
        for (String candidate : List.of(
                StringUtils.trimToEmpty(configuredPythonExecutable),
                "python", "python3", "python.exe", "python3.exe")) {
            if (candidate.isBlank()) continue;
            CommandResult result = run(List.of(candidate, "--version"));
            if (result.success && result.output.toLowerCase().contains("python")) {
                return candidate;
            }
        }
        return "";
    }

    public AudioSeparatorHealthCheckResult runHealthCheck() {
        String pythonCmd = configuredPythonExecutable.isBlank() ? detectPythonExecutable() : configuredPythonExecutable;
        if (pythonCmd.isBlank()) {
            return new AudioSeparatorHealthCheckResult(
                    false, null, null, false, null,
                    checkFfmpeg(), getFfmpegVersion(),
                    "Python was not found. Configure a Python executable or install Python first.");
        }

        CommandResult pyVersion = run(List.of(pythonCmd, "--version"));
        boolean pythonFound = pyVersion.success;
        String pythonVersion = pyVersion.success ? pyVersion.output.trim() : null;

        // Check audio-separator via python -m audio_separator --version
        CommandResult asVersion = run(List.of(pythonCmd, "-m", "audio_separator", "--version"));
        boolean asAvailable = asVersion.success && !asVersion.output.isBlank();
        String asVersionStr = asAvailable ? asVersion.output.trim() : null;

        boolean ffmpegOk = checkFfmpeg();
        String ffmpegVersion = getFfmpegVersion();

        StringBuilder details = new StringBuilder();
        details.append("Python: ").append(pythonCmd).append("\n");
        if (pythonVersion != null) details.append("Version: ").append(pythonVersion).append("\n");
        details.append("audio-separator: ").append(asAvailable ? "found" : "not found").append("\n");
        if (asVersionStr != null) details.append("audio-separator version: ").append(asVersionStr).append("\n");
        if (!asAvailable && !asVersion.output.isBlank()) {
            details.append("Output: ").append(asVersion.output.trim()).append("\n");
        }
        details.append("FFmpeg: ").append(ffmpegOk ? "found" : "not found").append("\n");
        if (ffmpegVersion != null) details.append("FFmpeg version: ").append(ffmpegVersion).append("\n");
        if (!asAvailable) {
            details.append("\nTo install audio-separator, run:\n  pip install audio-separator[cpu]\n")
                   .append("  or: pip install audio-separator[gpu]  (for NVIDIA GPU support)");
        }

        return new AudioSeparatorHealthCheckResult(
                pythonFound, pythonCmd, pythonVersion,
                asAvailable, asVersionStr,
                ffmpegOk, ffmpegVersion,
                details.toString());
    }

    private boolean checkFfmpeg() {
        return run(List.of(resolveFfmpegExecutable(), "-version")).success;
    }

    private String getFfmpegVersion() {
        CommandResult r = run(List.of(resolveFfmpegExecutable(), "-version"));
        if (!r.success || r.output.isBlank()) return null;
        return r.output.lines().findFirst().orElse(null);
    }

    private String resolveFfmpegExecutable() {
        if (configuredFfmpegPath != null) {
            java.io.File ffmpegDir = new java.io.File(configuredFfmpegPath);
            java.io.File ffmpegExe = ffmpegDir.isDirectory()
                    ? new java.io.File(ffmpegDir, "ffmpeg")
                    : ffmpegDir;
            if (ffmpegExe.isFile()) {
                return ffmpegExe.getAbsolutePath();
            }
        }
        return "ffmpeg";
    }

    private CommandResult run(List<String> command) {
        List<String> filtered = command.stream().filter(s -> !s.isBlank()).toList();
        if (filtered.isEmpty()) return new CommandResult(false, "");
        try {
            ProcessBuilder pb = new ProcessBuilder(filtered);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!output.isEmpty()) output.append("\n");
                    output.append(line);
                }
            }
            boolean finished = process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CommandResult(false, output.toString());
            }
            return new CommandResult(process.exitValue() == 0, output.toString());
        } catch (IOException | InterruptedException ex) {
            LOGGER.fine("Command failed: " + filtered + " — " + ex.getMessage());
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            return new CommandResult(false, "");
        }
    }

    private record CommandResult(boolean success, String output) {}
}
