package yass.integration.separation.audioseparator;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class AudioSeparatorHealthCheckService {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration UPDATE_TIMEOUT = Duration.ofMinutes(10);
    static final Path MANAGED_VENV_DIR = Path.of(System.getProperty("user.home"), ".yass", "audio-separator-venv");

    private final String configuredPythonExecutable;
    private final String defaultPythonExecutable;
    private final String configuredFfmpegPath;
    private volatile Process activeUpdateProcess;
    private volatile boolean updateCancelRequested;

    public AudioSeparatorHealthCheckService(String configuredPythonExecutable, String configuredFfmpegPath) {
        this(configuredPythonExecutable, "", configuredFfmpegPath);
    }

    public AudioSeparatorHealthCheckService(String configuredPythonExecutable,
                                            String defaultPythonExecutable,
                                            String configuredFfmpegPath) {
        this.configuredPythonExecutable = StringUtils.trimToEmpty(configuredPythonExecutable);
        this.defaultPythonExecutable = StringUtils.trimToEmpty(defaultPythonExecutable);
        this.configuredFfmpegPath = StringUtils.trimToNull(configuredFfmpegPath);
    }

    public String detectPythonExecutable() {
        for (String candidate : List.of(
                StringUtils.trimToEmpty(configuredPythonExecutable),
                StringUtils.trimToEmpty(defaultPythonExecutable),
                "python", "python3", "python.exe", "python3.exe")) {
            if (candidate.isBlank()) continue;
            CommandResult result = run(List.of(candidate, "--version"));
            if (result.success && result.output.toLowerCase().contains("python")) {
                String resolvedExecutable = resolvePythonExecutable(candidate);
                return StringUtils.defaultIfBlank(resolvedExecutable, candidate);
            }
        }
        return "";
    }

    private String resolvePythonExecutable(String candidate) {
        if (StringUtils.isBlank(candidate)) {
            return "";
        }
        CommandResult result = run(List.of(candidate,
                "-c",
                "import os, sys; print(os.path.realpath(sys.executable))"));
        if (!result.success) {
            return "";
        }
        String resolved = StringUtils.trimToEmpty(result.output);
        if (resolved.contains("\n")) {
            resolved = resolved.lines()
                    .map(StringUtils::trimToEmpty)
                    .filter(StringUtils::isNotBlank)
                    .findFirst()
                    .orElse("");
        }
        return resolved;
    }

    public AudioSeparatorHealthCheckResult runHealthCheck() {
        String pythonCmd = resolvePythonExecutable();
        if (pythonCmd.isBlank()) {
            return new AudioSeparatorHealthCheckResult(
                    false, null, null, false, null,
                    checkFfmpeg(), getFfmpegVersion(),
                    "Python was not found. Configure a Python executable or install Python first.");
        }

        CommandResult pyVersion = run(List.of(pythonCmd, "--version"));
        boolean pythonFound = pyVersion.success;
        String pythonVersion = pyVersion.success ? pyVersion.output.trim() : null;

        // Check audio-separator via its console script (audio-separator --version).
        // The package does not support `python -m audio_separator` in recent versions.
        String scriptCmd = resolveAudioSeparatorScript(pythonCmd);
        CommandResult asVersion = run(List.of(scriptCmd, "--version"));
        boolean asAvailable = asVersion.success && !asVersion.output.isBlank();
        String asVersionStr = asAvailable ? asVersion.output.trim() : null;

        boolean ffmpegOk = checkFfmpeg();
        String ffmpegVersion = getFfmpegVersion();

        StringBuilder details = new StringBuilder();
        details.append("Python: ").append(pythonCmd).append("\n");
        if (pythonVersion != null) details.append("Version: ").append(pythonVersion).append("\n");
        details.append("audio-separator script: ").append(scriptCmd).append("\n");
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

    public PackageUpdateResult updateAudioSeparatorPackage() {
        return updateAudioSeparatorPackage(null);
    }

    public PackageUpdateResult updateAudioSeparatorPackage(Consumer<String> outputListener) {
        String bootstrapPython = resolvePythonExecutable();
        if (bootstrapPython.isBlank()) {
            return new PackageUpdateResult(false, "Python was not found. Configure a Python executable first.");
        }
        Path managedPython = getManagedPythonExecutable();
        try {
            Files.createDirectories(MANAGED_VENV_DIR.getParent());
        } catch (IOException ex) {
            return new PackageUpdateResult(false, "Could not prepare the managed audio-separator environment.\n" + ex.getMessage());
        }
        if (!Files.isRegularFile(managedPython)) {
            if (outputListener != null) {
                outputListener.accept("Creating managed audio-separator environment...");
            }
            CommandResult createVenv = run(List.of(bootstrapPython, "-m", "venv", MANAGED_VENV_DIR.toString()), UPDATE_TIMEOUT, outputListener);
            if (updateCancelRequested) {
                return new PackageUpdateResult(false, "audio-separator update cancelled.", true);
            }
            if (!createVenv.success) {
                String details = StringUtils.defaultIfBlank(createVenv.output, "No output.");
                return new PackageUpdateResult(false, "audio-separator environment creation failed.\n" + details);
            }
        }
        if (outputListener != null) {
            outputListener.accept("Updating local audio-separator installation...");
        }
        CommandResult update = run(List.of(managedPython.toString(),
                                           "-m",
                                           "pip",
                                           "install",
                                           "-U",
                                           "audio-separator"), UPDATE_TIMEOUT, outputListener);
        if (updateCancelRequested) {
            return new PackageUpdateResult(false, "audio-separator update cancelled.", true);
        }
        if (update.success) {
            return new PackageUpdateResult(true,
                    "audio-separator update completed successfully.",
                    false,
                    managedPython.toString());
        }
        String details = StringUtils.defaultIfBlank(update.output, "No output.");
        return new PackageUpdateResult(false, "audio-separator update failed.\n" + details);
    }


    public boolean cancelAudioSeparatorUpdate() {
        updateCancelRequested = true;
        Process process = activeUpdateProcess;
        if (process == null) {
            return false;
        }
        process.destroy();
        try {
            Thread.sleep(150);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        if (process.isAlive()) {
            process.destroyForcibly();
        }
        return true;
    }
    /**
     * Lists available models by running {@code audio-separator --list_models}.
     * Returns model filenames parsed from the tabular output (first column, skipping header/separator rows).
     * Returns an empty list if audio-separator is not available or the command fails.
     */
    public List<String> listModels(String pythonExecutable) {
        if (StringUtils.isBlank(pythonExecutable)) {
            pythonExecutable = resolvePythonExecutable();
        }
        String scriptCmd = resolveAudioSeparatorScript(pythonExecutable);
        CommandResult result = run(List.of(scriptCmd, "--list_models"));
        if (!result.success || result.output.isBlank()) {
            return List.of();
        }
        List<String> models = new ArrayList<>();
        for (String line : result.output.lines().toList()) {
            // Skip header, separator, and blank lines
            if (line.isBlank() || line.startsWith("-") || line.startsWith("=")
                    || line.toLowerCase().contains("model name") || line.toLowerCase().contains("filename")) {
                continue;
            }
            // Each data row starts with the model filename in the first column, separated by | or whitespace
            String candidate;
            if (line.contains("|")) {
                candidate = line.substring(0, line.indexOf('|')).strip();
            } else {
                candidate = line.strip().split("\\s{2,}")[0].strip();
            }
            // Model filenames end with a known extension
            if (!candidate.isBlank() && (candidate.endsWith(".ckpt") || candidate.endsWith(".onnx")
                    || candidate.endsWith(".yaml") || candidate.endsWith(".th") || candidate.endsWith(".pth"))) {
                models.add(candidate);
            }
        }
        return models;
    }

    /**
     * Resolves the audio-separator console script path from the Python executable.
     * It checks interpreter-local and user script directories first, then falls back to PATH.
     */
    public static String resolveAudioSeparatorScript(String pythonExecutable) {
        for (File directory : resolveCandidateScriptDirectories(pythonExecutable)) {
            String resolved = findAudioSeparatorScriptIn(directory);
            if (resolved != null) {
                return resolved;
            }
        }
        return "audio-separator"; // fall back to PATH
    }

    static List<File> candidateScriptDirectories(String pythonExecutable, String sysconfigScriptsPath, String userBasePath) {
        Set<File> candidates = new LinkedHashSet<>();
        if (StringUtils.isNotBlank(pythonExecutable)) {
            File pyFile = new File(pythonExecutable);
            if (pyFile.isFile() && pyFile.getParentFile() != null) {
                candidates.add(new File(pyFile.getParentFile(), "Scripts"));
                candidates.add(new File(pyFile.getParentFile(), "bin"));
            }
        }
        if (StringUtils.isNotBlank(sysconfigScriptsPath)) {
            candidates.add(new File(sysconfigScriptsPath));
        }
        if (StringUtils.isNotBlank(userBasePath)) {
            File userBase = new File(userBasePath);
            candidates.add(new File(userBase, "Scripts"));
            candidates.add(new File(userBase, "bin"));
            if (isWindowsPath(userBasePath)) {
                String versionDirName = extractWindowsPythonVersionDirName(pythonExecutable);
                if (versionDirName != null) {
                    candidates.add(new File(new File(userBase, versionDirName), "Scripts"));
                }
            }
        }
        return new ArrayList<>(candidates);
    }

    private static List<File> resolveCandidateScriptDirectories(String pythonExecutable) {
        String sysconfigScriptsPath = null;
        String userBasePath = null;
        if (StringUtils.isNotBlank(pythonExecutable)) {
            AudioSeparatorHealthCheckService service = new AudioSeparatorHealthCheckService(pythonExecutable, null);
            sysconfigScriptsPath = service.queryPythonValue(pythonExecutable,
                    "import sysconfig; print(sysconfig.get_path('scripts') or '')");
            userBasePath = service.queryPythonValue(pythonExecutable,
                    "import site; print(site.getuserbase() or '')");
        }
        return candidateScriptDirectories(pythonExecutable, sysconfigScriptsPath, userBasePath);
    }

    private static String findAudioSeparatorScriptIn(File directory) {
        if (directory == null) {
            return null;
        }
        for (String name : List.of("audio-separator.exe", "audio-separator")) {
            File candidate = new File(directory, name);
            if (candidate.isFile()) {
                return candidate.getAbsolutePath();
            }
        }
        return null;
    }

    private String queryPythonValue(String pythonExecutable, String script) {
        CommandResult result = run(List.of(pythonExecutable, "-c", script));
        if (!result.success) {
            return null;
        }
        return result.output.lines()
                .map(StringUtils::trimToEmpty)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    private static boolean isWindowsPath(String path) {
        return path != null && path.matches("^[A-Za-z]:.*");
    }

    private static String extractWindowsPythonVersionDirName(String pythonExecutable) {
        if (StringUtils.isBlank(pythonExecutable)) {
            return null;
        }
        File pythonFile = new File(pythonExecutable);
        File parent = pythonFile.getParentFile();
        return parent != null ? parent.getName() : null;
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
            File configured = new File(configuredFfmpegPath);
            // ffmpegPath may point to the executable directly or to its parent directory
            File dir = configured.isDirectory() ? configured : configured.getParentFile();
            if (dir != null) {
                for (String name : List.of("ffmpeg.exe", "ffmpeg")) {
                    File candidate = new File(dir, name);
                    if (candidate.isFile()) {
                        return candidate.getAbsolutePath();
                    }
                }
            }
            // ffmpegPath points directly to the executable
            if (configured.isFile()) {
                return configured.getAbsolutePath();
            }
        }
        return "ffmpeg";
    }

    private CommandResult run(List<String> command) {
        return run(command, COMMAND_TIMEOUT);
    }

    private CommandResult run(List<String> command, Duration timeout) {
        return run(command, timeout, null);
    }

    private CommandResult run(List<String> command, Duration timeout, Consumer<String> outputListener) {
        List<String> filtered = command.stream().filter(s -> !s.isBlank()).toList();
        if (filtered.isEmpty()) return new CommandResult(false, "");
        try {
            ProcessBuilder pb = new ProcessBuilder(filtered);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean updateCommand = isPipUpdateCommand(filtered);
            if (updateCommand) {
                activeUpdateProcess = process;
            }
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (outputListener != null) {
                        outputListener.accept(line);
                    }
                    if (!output.isEmpty()) output.append("\n");
                    output.append(line);
                }
            }
            boolean finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CommandResult(false, output.toString());
            }
            return new CommandResult(process.exitValue() == 0, output.toString());
        } catch (IOException | InterruptedException ex) {
            LOGGER.fine("Command failed: " + filtered + " — " + ex.getMessage());
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            return new CommandResult(false, "");
        } finally {
            if (isPipUpdateCommand(filtered)) {
                activeUpdateProcess = null;
            }
        }
    }

    private boolean isPipUpdateCommand(List<String> command) {
        return command != null
                && command.size() >= 6
                && "-m".equals(command.get(1))
                && "pip".equalsIgnoreCase(command.get(2))
                && "install".equalsIgnoreCase(command.get(3))
                && "-U".equalsIgnoreCase(command.get(4));
    }

    static Path getManagedVenvDirectory() {
        return MANAGED_VENV_DIR;
    }

    static Path getManagedPythonExecutable() {
        return isWindows() ? MANAGED_VENV_DIR.resolve("Scripts").resolve("python.exe")
                           : MANAGED_VENV_DIR.resolve("bin").resolve("python");
    }

    private static boolean isWindows() {
        return File.separatorChar == '\\';
    }

    private String resolvePythonExecutable() {
        if (StringUtils.isNotBlank(configuredPythonExecutable)) {
            return configuredPythonExecutable;
        }
        if (StringUtils.isNotBlank(defaultPythonExecutable)) {
            return defaultPythonExecutable;
        }
        return detectPythonExecutable();
    }

    private record CommandResult(boolean success, String output) {}

    public static final class PackageUpdateResult {
        private final boolean success;
        private final String message;
        private final boolean cancelled;
        private final String pythonExecutable;

        public PackageUpdateResult(boolean success, String message) {
            this(success, message, false, null);
        }

        public PackageUpdateResult(boolean success, String message, boolean cancelled) {
            this(success, message, cancelled, null);
        }

        public PackageUpdateResult(boolean success, String message, boolean cancelled, String pythonExecutable) {
            this.success = success;
            this.message = message;
            this.cancelled = cancelled;
            this.pythonExecutable = pythonExecutable;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public String getPythonExecutable() {
            return pythonExecutable;
        }
    }
}
