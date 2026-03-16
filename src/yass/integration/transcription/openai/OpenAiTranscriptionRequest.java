package yass.integration.transcription.openai;

import java.io.File;

public class OpenAiTranscriptionRequest {
    private final File sourceAudioFile;
    private final File uploadAudioFile;
    private final String sourceTag;
    private final String model;
    private final String language;
    private final String prompt;
    private final String timestampGranularity;
    private final String songBaseName;

    public OpenAiTranscriptionRequest(File sourceAudioFile,
                                      File uploadAudioFile,
                                      String sourceTag,
                                      String model,
                                      String language,
                                      String prompt,
                                      String timestampGranularity,
                                      String songBaseName) {
        this.sourceAudioFile = sourceAudioFile;
        this.uploadAudioFile = uploadAudioFile;
        this.sourceTag = sourceTag;
        this.model = model;
        this.language = language;
        this.prompt = prompt;
        this.timestampGranularity = timestampGranularity;
        this.songBaseName = songBaseName;
    }

    public File getSourceAudioFile() {
        return sourceAudioFile;
    }

    public File getUploadAudioFile() {
        return uploadAudioFile;
    }

    public String getSourceTag() {
        return sourceTag;
    }

    public String getModel() {
        return model;
    }

    public String getLanguage() {
        return language;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getTimestampGranularity() {
        return timestampGranularity;
    }

    public String getSongBaseName() {
        return songBaseName;
    }
}