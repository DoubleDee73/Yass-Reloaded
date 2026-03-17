package yass.integration.transcription.whisperx;

import java.io.File;

public class WhisperXTranscriptionRequest {
    private final File sourceAudioFile;
    private final File uploadAudioFile;
    private final String sourceTag;
    private final String songBaseName;
    private final File cacheDir;
    private final File cacheFile;
    private final String language;

    public WhisperXTranscriptionRequest(File sourceAudioFile,
                                        File uploadAudioFile,
                                        String sourceTag,
                                        String songBaseName,
                                        File cacheDir,
                                        File cacheFile,
                                        String language) {
        this.sourceAudioFile = sourceAudioFile;
        this.uploadAudioFile = uploadAudioFile;
        this.sourceTag = sourceTag;
        this.songBaseName = songBaseName;
        this.cacheDir = cacheDir;
        this.cacheFile = cacheFile;
        this.language = language;
    }

    public File getSourceAudioFile() { return sourceAudioFile; }
    public File getUploadAudioFile() { return uploadAudioFile; }
    public String getSourceTag() { return sourceTag; }
    public String getSongBaseName() { return songBaseName; }
    public File getCacheDir() { return cacheDir; }
    public File getCacheFile() { return cacheFile; }
    public String getLanguage() { return language; }
}