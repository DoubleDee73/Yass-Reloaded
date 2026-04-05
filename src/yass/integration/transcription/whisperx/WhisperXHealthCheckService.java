package yass.integration.transcription.whisperx;

import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class WhisperXHealthCheckService {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration WHISPERX_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration UPDATE_TIMEOUT = Duration.ofMinutes(10);

    private static final int VRAM_LARGE_V3_MIB = 12288;
    private static final int VRAM_MEDIUM_MIB = 8192;
    private static final int VRAM_SMALL_CUDA_MIB = 6144;

    private final String configuredPythonExecutable;
    private final boolean useModuleInvocation;
    private final String configuredWhisperXCommand;
    private final String configuredFfmpegPath;
    private volatile Process activeUpdateProcess;
    private volatile boolean updateCancelRequested;

    public WhisperXHealthCheckService(String configuredPythonExecutable,
                                      boolean useModuleInvocation,
                                      String configuredWhisperXCommand) {
        this(configuredPythonExecutable, useModuleInvocation, configuredWhisperXCommand, null);
    }

    public WhisperXHealthCheckService(String configuredPythonExecutable,
                                      boolean useModuleInvocation,
                                      String configuredWhisperXCommand,
                                      String configuredFfmpegPath) {
        this.configuredPythonExecutable = StringUtils.trimToEmpty(configuredPythonExecutable);
        this.useModuleInvocation = useModuleInvocation;
        this.configuredWhisperXCommand = StringUtils.defaultIfBlank(configuredWhisperXCommand, "whisperx").trim();
        this.configuredFfmpegPath = StringUtils.trimToNull(configuredFfmpegPath);
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
            WhisperXRuntimeRecommendation recommendation = recommendRuntime(false, null, false);
            return new WhisperXHealthCheckResult(false,
                                                 null,
                                                 null,
                                                 false,
                                                 null,
                                                 isCommandAvailable(resolveFfmpegExecutable()),
                                                 firstLine(runCommand(List.of(resolveFfmpegExecutable(), "-version"))),
                                                 "unknown",
                                                 null,
                                                 null,
                                                 null,
                                                 null,
                                                 "Python was not found. Configure a Python executable or install Python first.",
                                                 recommendation.getDevice(),
                                                 recommendation.getComputeType(),
                                                 recommendation.getModel(),
                                                 "Python not found -> conservative fallback (small/cpu/int8)."
            );
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
        CommandResult ffmpeg = runCommand(List.of(resolveFfmpegExecutable(), "-version"));
        CommandResult torchVersion = runCommand(List.of(pythonExecutable,
                                                        "-c",
                                                        "import importlib.util; t = importlib.util.find_spec('torch'); print('N/A' if t is None else __import__('torch').__version__)"));
        CommandResult torchCuda = runCommand(List.of(pythonExecutable,
                                                     "-c",
                                                     "import importlib.util; t = importlib.util.find_spec('torch'); print('N/A' if t is None else (str(__import__('torch').version.cuda) if __import__('torch').version.cuda else 'none (CPU-only build)'))"));

        CommandResult telemetryResult = runCommand(List.of(pythonExecutable, "-c", buildGpuTelemetryScript()));
        GpuTelemetry telemetry = parseGpuTelemetry(telemetryResult.stdout);
        String gpuAvailability = telemetry.cudaAvailable
                ? "available"
                : (telemetry.mpsAvailable ? "not available (MPS available)" : "not available");

        WhisperXRuntimeRecommendation recommendation = recommendRuntime(telemetry.cudaAvailable,
                                                                         telemetry.gpuVramMiB,
                                                                         telemetry.mpsAvailable);

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
                                             gpuAvailability,
                                             telemetry.gpuName,
                                             telemetry.gpuVramMiB,
                                             torchVersion.success ? firstLine(torchVersion) : null,
                                             torchCuda.success ? firstLine(torchCuda) : null,
                                             details.toString().trim(),
                                             recommendation.getDevice(),
                                             recommendation.getComputeType(),
                                             recommendation.getModel(),
                                             recommendation.getReason());
    }


    public PackageUpdateResult updateWhisperXPackage() {
        return updateWhisperXPackage(null);
    }

    public PackageUpdateResult updateWhisperXPackage(Consumer<String> outputListener) {
        updateCancelRequested = false;
        CommandResult python = resolvePython();
        if (!python.success || python.command.isEmpty()) {
            return new PackageUpdateResult(false,
                    "Python was not found. Configure a Python executable first.");
        }
        String pythonExecutable = python.command.get(0);
        CommandResult update = runCommand(List.of(pythonExecutable,
                                                  "-m",
                                                  "pip",
                                                  "install",
                                                  "-U",
                                                  "whisperx"), UPDATE_TIMEOUT, outputListener);
        if (updateCancelRequested) {
            return new PackageUpdateResult(false, "WhisperX update cancelled.", true);
        }
        if (update.success) {
            return new PackageUpdateResult(true, "WhisperX update completed successfully.");
        }
        String details = StringUtils.defaultIfBlank(update.stderr, update.stdout);
        String message = "WhisperX update failed.";
        if (StringUtils.isNotBlank(details)) {
            message += "\n" + details;
        }
        return new PackageUpdateResult(false, message);
    }


    public boolean cancelWhisperXUpdate() {
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

    static WhisperXRuntimeRecommendation recommendRuntime(boolean cudaAvailable,
                                                          Integer gpuVramMiB,
                                                          boolean mpsAvailable) {
        if (cudaAvailable && gpuVramMiB != null) {
            if (gpuVramMiB >= VRAM_LARGE_V3_MIB) {
                return new WhisperXRuntimeRecommendation("cuda", "float16", "large-v3",
                        "CUDA VRAM >= 12GB -> large-v3/cuda/float16");
            }
            if (gpuVramMiB >= VRAM_MEDIUM_MIB) {
                return new WhisperXRuntimeRecommendation("cuda", "float16", "medium",
                        "CUDA VRAM 8-12GB -> medium/cuda/float16");
            }
            if (gpuVramMiB >= VRAM_SMALL_CUDA_MIB) {
                return new WhisperXRuntimeRecommendation("cuda", "float16", "small",
                        "CUDA VRAM 6-8GB -> small/cuda/float16");
            }
            return new WhisperXRuntimeRecommendation("cpu", "int8", "small",
                    "CUDA VRAM < 6GB -> small/cpu/int8 for stability");
        }

        if (mpsAvailable) {
            return new WhisperXRuntimeRecommendation("mps", "float32", "medium",
                    "No CUDA, MPS available -> medium/mps/float32");
        }

        return new WhisperXRuntimeRecommendation("cpu", "int8", "small",
                cudaAvailable
                        ? "CUDA telemetry missing -> conservative fallback (small/cpu/int8)"
                        : "No CUDA GPU detected -> small/cpu/int8");
    }

    private String buildGpuTelemetryScript() {
        return """
                import importlib.util
                spec = importlib.util.find_spec('torch')
                if spec is None:
                    print('cuda=unknown;mps=unknown;name=;vram=')
                else:
                    import torch
                    cuda = 1 if torch.cuda.is_available() else 0
                    mps_backend = getattr(torch.backends, 'mps', None)
                    mps = 1 if (mps_backend is not None and mps_backend.is_available()) else 0
                    name = ''
                    vram = ''
                    if cuda:
                        props = torch.cuda.get_device_properties(0)
                        name = str(props.name).replace(';', ',')
                        vram = str(int(props.total_memory / (1024 * 1024)))
                    print(f'cuda={cuda};mps={mps};name={name};vram={vram}')
                """;
    }

    private GpuTelemetry parseGpuTelemetry(String output) {
        if (StringUtils.isBlank(output)) {
            return GpuTelemetry.unknown();
        }

        String telemetryLine = null;
        for (String line : output.split("\\R")) {
            if (StringUtils.contains(line, "cuda=")) {
                telemetryLine = line.trim();
            }
        }
        if (telemetryLine == null) {
            return GpuTelemetry.unknown();
        }

        Map<String, String> values = new HashMap<>();
        for (String part : telemetryLine.split(";")) {
            int idx = part.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = part.substring(0, idx).trim();
            String value = part.substring(idx + 1).trim();
            values.put(key, value);
        }

        boolean cudaAvailable = "1".equals(values.get("cuda")) || "true".equalsIgnoreCase(values.get("cuda"));
        boolean mpsAvailable = "1".equals(values.get("mps")) || "true".equalsIgnoreCase(values.get("mps"));
        String gpuName = StringUtils.trimToNull(values.get("name"));
        Integer gpuVramMiB = parseInteger(values.get("vram"));
        return new GpuTelemetry(cudaAvailable, mpsAvailable, gpuName, gpuVramMiB);
    }

    private Integer parseInteger(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
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

    private String resolveFfmpegExecutable() {
        if (configuredFfmpegPath != null) {
            File exe = new File(configuredFfmpegPath, isWindows() ? "ffmpeg.exe" : "ffmpeg");
            if (exe.isFile()) {
                return exe.getAbsolutePath();
            }
        }
        return "ffmpeg";
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
        return runCommand(command, timeout, null);
    }

    private CommandResult runCommand(List<String> command, Duration timeout, Consumer<String> outputListener) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            boolean updateCommand = isPipUpdateCommand(command);
            if (updateCommand) {
                activeUpdateProcess = process;
            }
            String stdout = readStream(process.getInputStream(), outputListener);
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CommandResult(false, command, stdout, "Timed out", -1, null);
            }
            String stderr = "";
            int exitCode = process.exitValue();
            return new CommandResult(exitCode == 0, command, stdout, stderr, exitCode, null);
        } catch (Exception ex) {
            LOGGER.fine("WhisperX health check command failed: " + command + " -> " + ex.getMessage());
            return new CommandResult(false, command, "", ex.getMessage(), -1, ex);
        } finally {
            if (isPipUpdateCommand(command)) {
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

    private String readStream(InputStream inputStream, Consumer<String> outputListener) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (outputListener != null) {
                    outputListener.accept(line);
                }
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

    public static final class WhisperXRuntimeRecommendation {
        private final String device;
        private final String computeType;
        private final String model;
        private final String reason;

        public WhisperXRuntimeRecommendation(String device, String computeType, String model, String reason) {
            this.device = device;
            this.computeType = computeType;
            this.model = model;
            this.reason = reason;
        }

        public String getDevice() {
            return device;
        }

        public String getComputeType() {
            return computeType;
        }

        public String getModel() {
            return model;
        }

        public String getReason() {
            return reason;
        }
    }

    private static final class GpuTelemetry {
        private final boolean cudaAvailable;
        private final boolean mpsAvailable;
        private final String gpuName;
        private final Integer gpuVramMiB;

        private GpuTelemetry(boolean cudaAvailable, boolean mpsAvailable, String gpuName, Integer gpuVramMiB) {
            this.cudaAvailable = cudaAvailable;
            this.mpsAvailable = mpsAvailable;
            this.gpuName = gpuName;
            this.gpuVramMiB = gpuVramMiB;
        }

        private static GpuTelemetry unknown() {
            return new GpuTelemetry(false, false, null, null);
        }
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
    public static final class PackageUpdateResult {
        private final boolean success;
        private final String message;
        private final boolean cancelled;

        public PackageUpdateResult(boolean success, String message) {
            this(success, message, false);
        }

        public PackageUpdateResult(boolean success, String message, boolean cancelled) {
            this.success = success;
            this.message = message;
            this.cancelled = cancelled;
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
    }
}