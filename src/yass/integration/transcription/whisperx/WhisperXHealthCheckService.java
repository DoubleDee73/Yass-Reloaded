package yass.integration.transcription.whisperx;

import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class WhisperXHealthCheckService {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration WHISPERX_TIMEOUT = Duration.ofSeconds(45);

    private final String configuredPythonExecutable;
    private final boolean useModuleInvocation;
    private final String configuredWhisperXCommand;

    public WhisperXHealthCheckService(String configuredPythonExecutable,
                                      boolean useModuleInvocation,
                                      String configuredWhisperXCommand) {
        this.configuredPythonExecutable = StringUtils.trimToEmpty(configuredPythonExecutable);
        this.useModuleInvocation = useModuleInvocation;
        this.configuredWhisperXCommand = StringUtils.defaultIfBlank(configuredWhisperXCommand, "whisperx").trim();
    }

    public String detectPythonExecutable() {
        CommandResult python = resolvePython();
        if (!python.success || python.command.isEmpty()) {
            return "";
        }
        return python.command.get(0);
    }

    public WhisperXHealthCheckResult runHealthCheck() {
        CommandResult python = resolvePython();
        if (!python.success) {
            return new WhisperXHealthCheckResult(false,
                                                 null,
                                                 null,
                                                 false,
                                                 null,
                                                 isCommandAvailable("ffmpeg"),
                                                 firstLine(runCommand(List.of("ffmpeg", "-version"))),
                                                 "unknown",
                                                 "Python was not found. Configure a Python executable or install Python first.");
        }

        String pythonExecutable = python.command.isEmpty() ? null : python.command.get(0);
        CommandResult whisperxAvailability = useModuleInvocation
                ? runCommand(List.of(pythonExecutable,
                                     "-c",
                                     "import whisperx; print('ok')"), WHISPERX_TIMEOUT)
                : runCommand(List.of(configuredWhisperXCommand, "--help"), WHISPERX_TIMEOUT);
        CommandResult whisperxVersion = useModuleInvocation
                ? runCommand(List.of(pythonExecutable,
                                     "-c",
                                     "import importlib.metadata as m; print(m.version('whisperx'))"))
                : new CommandResult(false, List.of(), "", "", -1, null);
        CommandResult ffmpeg = runCommand(List.of("ffmpeg", "-version"));
        CommandResult gpu = runCommand(List.of(pythonExecutable,
                                               "-c",
                                               "import importlib.util; spec = importlib.util.find_spec('torch'); "
                                               + "print('unknown' if spec is None else ('available' if __import__('torch').cuda.is_available() else 'not available'))"));

        StringBuilder details = new StringBuilder();
        if (!whisperxAvailability.success) {
            details.append("WhisperX could not be executed. ");
        }
        if (!ffmpeg.success) {
            details.append("FFmpeg was not found. ");
        }
        if (details.length() == 0) {
            details.append("WhisperX looks ready to use.");
        }

        return new WhisperXHealthCheckResult(true,
                                             pythonExecutable,
                                             firstLine(python),
                                             whisperxAvailability.success,
                                             firstLine(whisperxVersion),
                                             ffmpeg.success,
                                             firstLine(ffmpeg),
                                             gpu.success ? firstLine(gpu) : "unknown",
                                             details.toString().trim());
    }

    private CommandResult resolvePython() {
        for (String candidate : collectPythonCandidates()) {
            CommandResult result = runCommand(List.of(candidate, "--version"));
            if (result.success) {
                return result;
            }
        }
        return new CommandResult(false, List.of(), "", "", -1, null);
    }

    private List<String> collectPythonCandidates() {
        Set<String> candidates = new LinkedHashSet<>();
        if (StringUtils.isNotBlank(configuredPythonExecutable)) {
            candidates.add(configuredPythonExecutable);
        }

        addEnvironmentCandidate(candidates, System.getenv("PYTHON"));
        addEnvironmentCandidate(candidates, System.getenv("PYTHON3"));
        addEnvironmentCandidate(candidates, System.getenv("PY_PYTHON"));

        String path = System.getenv("PATH");
        if (StringUtils.isNotBlank(path)) {
            String[] entries = path.split(File.pathSeparator);
            for (String entry : entries) {
                if (StringUtils.isBlank(entry)) {
                    continue;
                }
                String lower = entry.toLowerCase(Locale.ROOT);
                if (lower.contains("python")) {
                    File pythonExe = new File(entry, isWindows() ? "python.exe" : "python");
                    if (pythonExe.isFile()) {
                        candidates.add(pythonExe.getAbsolutePath());
                    }
                }
            }
        }

        if (isWindows()) {
            CommandResult wherePython = runCommand(List.of("where", "python"));
            addOutputCandidates(candidates, wherePython.stdout);
            CommandResult wherePy = runCommand(List.of("where", "py"));
            addOutputCandidates(candidates, wherePy.stdout);
        }

        candidates.add("py");
        candidates.add("python");
        return new ArrayList<>(candidates);
    }

    private void addEnvironmentCandidate(Set<String> candidates, String value) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        File file = new File(value);
        if (file.isFile()) {
            candidates.add(file.getAbsolutePath());
        } else {
            candidates.add(value);
        }
    }

    private void addOutputCandidates(Set<String> candidates, String output) {
        if (StringUtils.isBlank(output)) {
            return;
        }
        for (String line : output.split("\\R")) {
            String candidate = StringUtils.trimToEmpty(line);
            if (!candidate.isEmpty()) {
                candidates.add(candidate);
            }
        }
    }

    private boolean isCommandAvailable(String command) {
        return runCommand(List.of(command, "-version")).success;
    }

    private String firstLine(CommandResult result) {
        String text = StringUtils.defaultIfBlank(result.stdout, result.stderr);
        if (StringUtils.isBlank(text)) {
            return null;
        }
        int newline = text.indexOf('\n');
        return newline >= 0 ? text.substring(0, newline).trim() : text.trim();
    }

    private CommandResult runCommand(List<String> command) {
        return runCommand(command, COMMAND_TIMEOUT);
    }

    private CommandResult runCommand(List<String> command, Duration timeout) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(false);
        try {
            Process process = builder.start();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CommandResult(false, command, "", "Timed out", -1, null);
            }
            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());
            int exitCode = process.exitValue();
            return new CommandResult(exitCode == 0, command, stdout, stderr, exitCode, null);
        } catch (Exception ex) {
            LOGGER.fine("WhisperX health check command failed: " + command + " -> " + ex.getMessage());
            return new CommandResult(false, command, "", ex.getMessage(), -1, ex);
        }
    }

    private String readStream(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private boolean isWindows() {
        return File.separatorChar == '\\';
    }

    private static final class CommandResult {
        private final boolean success;
        private final List<String> command;
        private final String stdout;
        private final String stderr;
        private final int exitCode;
        private final Exception exception;

        private CommandResult(boolean success,
                              List<String> command,
                              String stdout,
                              String stderr,
                              int exitCode,
                              Exception exception) {
            this.success = success;
            this.command = command;
            this.stdout = stdout;
            this.stderr = stderr;
            this.exitCode = exitCode;
            this.exception = exception;
        }
    }
}
