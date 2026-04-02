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
    private final String gpuName;
    private final Integer gpuVramMiB;
    private final String torchVersion;
    private final String torchCudaBuild;
    private final String details;
    private final String recommendedDevice;
    private final String recommendedComputeType;
    private final String recommendedModel;
    private final String recommendationReason;

    public WhisperXHealthCheckResult(boolean pythonFound,
                                     String pythonCommand,
                                     String pythonVersion,
                                     boolean whisperXAvailable,
                                     String whisperXVersion,
                                     boolean ffmpegAvailable,
                                     String ffmpegVersion,
                                     String gpuAvailability,
                                     String gpuName,
                                     Integer gpuVramMiB,
                                     String torchVersion,
                                     String torchCudaBuild,
                                     String details,
                                     String recommendedDevice,
                                     String recommendedComputeType,
                                     String recommendedModel,
                                     String recommendationReason) {
        this.pythonFound = pythonFound;
        this.pythonCommand = pythonCommand;
        this.pythonVersion = pythonVersion;
        this.whisperXAvailable = whisperXAvailable;
        this.whisperXVersion = whisperXVersion;
        this.ffmpegAvailable = ffmpegAvailable;
        this.ffmpegVersion = ffmpegVersion;
        this.gpuAvailability = gpuAvailability;
        this.gpuName = gpuName;
        this.gpuVramMiB = gpuVramMiB;
        this.torchVersion = torchVersion;
        this.torchCudaBuild = torchCudaBuild;
        this.details = details;
        this.recommendedDevice = recommendedDevice;
        this.recommendedComputeType = recommendedComputeType;
        this.recommendedModel = recommendedModel;
        this.recommendationReason = recommendationReason;
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

    public String getGpuName() {
        return gpuName;
    }

    public Integer getGpuVramMiB() {
        return gpuVramMiB;
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

    public String getRecommendedDevice() {
        return recommendedDevice;
    }

    public String getRecommendedComputeType() {
        return recommendedComputeType;
    }

    public String getRecommendedModel() {
        return recommendedModel;
    }

    public String getRecommendationReason() {
        return recommendationReason;
    }

    public boolean isUsable() {
        return pythonFound && whisperXAvailable && ffmpegAvailable;
    }
}
