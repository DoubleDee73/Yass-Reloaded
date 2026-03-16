package yass.integration.separation.audioseparator;

public class AudioSeparatorHealthCheckResult {
    private final boolean pythonFound;
    private final String pythonCommand;
    private final String pythonVersion;
    private final boolean audioSeparatorAvailable;
    private final String audioSeparatorVersion;
    private final boolean ffmpegAvailable;
    private final String ffmpegVersion;
    private final String details;

    public AudioSeparatorHealthCheckResult(boolean pythonFound,
                                           String pythonCommand,
                                           String pythonVersion,
                                           boolean audioSeparatorAvailable,
                                           String audioSeparatorVersion,
                                           boolean ffmpegAvailable,
                                           String ffmpegVersion,
                                           String details) {
        this.pythonFound = pythonFound;
        this.pythonCommand = pythonCommand;
        this.pythonVersion = pythonVersion;
        this.audioSeparatorAvailable = audioSeparatorAvailable;
        this.audioSeparatorVersion = audioSeparatorVersion;
        this.ffmpegAvailable = ffmpegAvailable;
        this.ffmpegVersion = ffmpegVersion;
        this.details = details;
    }

    public boolean isPythonFound() { return pythonFound; }
    public String getPythonCommand() { return pythonCommand; }
    public String getPythonVersion() { return pythonVersion; }
    public boolean isAudioSeparatorAvailable() { return audioSeparatorAvailable; }
    public String getAudioSeparatorVersion() { return audioSeparatorVersion; }
    public boolean isFfmpegAvailable() { return ffmpegAvailable; }
    public String getFfmpegVersion() { return ffmpegVersion; }
    public String getDetails() { return details; }
    public boolean isHealthy() { return pythonFound && audioSeparatorAvailable; }
}
