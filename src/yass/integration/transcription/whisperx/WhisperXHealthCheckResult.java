package yass.integration.transcription.whisperx;

public class WhisperXHealthCheckResult {
    private final boolean pythonFound;
    private final String pythonCommand;
    private final String pythonVersion;
    private final boolean whisperXAvailable;
    private final String whisperXVersion;
    private final boolean ffmpegAvailable;
    private final String ffmpegVersion;
    private final String gpuAvailability;
    private final String torchVersion;
    private final String torchCudaBuild;
    private final String details;

    public WhisperXHealthCheckResult(boolean pythonFound,
                                     String pythonCommand,
                                     String pythonVersion,
                                     boolean whisperXAvailable,
                                     String whisperXVersion,
                                     boolean ffmpegAvailable,
                                     String ffmpegVersion,
                                     String gpuAvailability,
                                     String torchVersion,
                                     String torchCudaBuild,
                                     String details) {
        this.pythonFound = pythonFound;
        this.pythonCommand = pythonCommand;
        this.pythonVersion = pythonVersion;
        this.whisperXAvailable = whisperXAvailable;
        this.whisperXVersion = whisperXVersion;
        this.ffmpegAvailable = ffmpegAvailable;
        this.ffmpegVersion = ffmpegVersion;
        this.gpuAvailability = gpuAvailability;
        this.torchVersion = torchVersion;
        this.torchCudaBuild = torchCudaBuild;
        this.details = details;
    }

    public boolean isPythonFound() {
        return pythonFound;
    }

    public String getPythonCommand() {
        return pythonCommand;
    }

    public String getPythonVersion() {
        return pythonVersion;
    }

    public boolean isWhisperXAvailable() {
        return whisperXAvailable;
    }

    public String getWhisperXVersion() {
        return whisperXVersion;
    }

    public boolean isFfmpegAvailable() {
        return ffmpegAvailable;
    }

    public String getFfmpegVersion() {
        return ffmpegVersion;
    }

    public String getGpuAvailability() {
        return gpuAvailability;
    }

    public String getTorchVersion() {
        return torchVersion;
    }

    public String getTorchCudaBuild() {
        return torchCudaBuild;
    }

    public String getDetails() {
        return details;
    }

    public boolean isUsable() {
        return pythonFound && whisperXAvailable && ffmpegAvailable;
    }
}