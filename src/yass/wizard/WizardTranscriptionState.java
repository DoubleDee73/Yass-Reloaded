package yass.wizard;

import yass.integration.separation.SeparationResult;
import yass.integration.transcription.openai.OpenAiTranscriptionResult;

import java.io.File;

public class WizardTranscriptionState {
    private final File runDirectory;
    private final File sourceAudioFile;
    private final File sourceConvertedFile;
    private final SeparationResult separationResult;
    private final OpenAiTranscriptionResult transcriptionResult;

    public WizardTranscriptionState(File runDirectory,
                                    File sourceAudioFile,
                                    File sourceConvertedFile,
                                    SeparationResult separationResult,
                                    OpenAiTranscriptionResult transcriptionResult) {
        this.runDirectory = runDirectory;
        this.sourceAudioFile = sourceAudioFile;
        this.sourceConvertedFile = sourceConvertedFile;
        this.separationResult = separationResult;
        this.transcriptionResult = transcriptionResult;
    }

    public File getRunDirectory() {
        return runDirectory;
    }

    public File getSourceAudioFile() {
        return sourceAudioFile;
    }

    public File getSourceConvertedFile() {
        return sourceConvertedFile;
    }

    public SeparationResult getSeparationResult() {
        return separationResult;
    }

    public OpenAiTranscriptionResult getTranscriptionResult() {
        return transcriptionResult;
    }

    public WizardTranscriptionState withTranscriptionResult(OpenAiTranscriptionResult updatedTranscriptionResult) {
        return new WizardTranscriptionState(runDirectory, sourceAudioFile, sourceConvertedFile, separationResult, updatedTranscriptionResult);
    }
}
