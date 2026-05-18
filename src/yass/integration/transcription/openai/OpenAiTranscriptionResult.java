package yass.integration.transcription.openai;

import yass.alignment.LyricToken;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class OpenAiTranscriptionResult {
    private final File sourceAudioFile;
    private final File uploadAudioFile;
    private final String sourceTag;
    private final String transcriptText;
    private final List<OpenAiTranscriptWord> words;
    private final List<OpenAiTranscriptSegment> segments;
    private final List<LyricToken> lyricTokens;
    private final boolean fromCache;
    private final File cacheFile;
    private final String textSourceTag;
    private final String timingSourceTag;

    public OpenAiTranscriptionResult(File sourceAudioFile,
                                     File uploadAudioFile,
                                     String sourceTag,
                                     String transcriptText,
                                     List<OpenAiTranscriptWord> words,
                                     List<OpenAiTranscriptSegment> segments,
                                     List<LyricToken> lyricTokens,
                                     boolean fromCache,
                                     File cacheFile) {
        this(sourceAudioFile,
                uploadAudioFile,
                sourceTag,
                transcriptText,
                words,
                segments,
                lyricTokens,
                fromCache,
                cacheFile,
                sourceTag,
                sourceTag);
    }

    public OpenAiTranscriptionResult(File sourceAudioFile,
                                     File uploadAudioFile,
                                     String sourceTag,
                                     String transcriptText,
                                     List<OpenAiTranscriptWord> words,
                                     List<OpenAiTranscriptSegment> segments,
                                     List<LyricToken> lyricTokens,
                                     boolean fromCache,
                                     File cacheFile,
                                     String textSourceTag,
                                     String timingSourceTag) {
        this.sourceAudioFile = sourceAudioFile;
        this.uploadAudioFile = uploadAudioFile;
        this.sourceTag = sourceTag;
        this.transcriptText = transcriptText;
        this.words = words == null ? Collections.emptyList() : List.copyOf(words);
        this.segments = segments == null ? Collections.emptyList() : List.copyOf(segments);
        this.lyricTokens = lyricTokens == null ? Collections.emptyList() : List.copyOf(lyricTokens);
        this.fromCache = fromCache;
        this.cacheFile = cacheFile;
        this.textSourceTag = textSourceTag == null ? sourceTag : textSourceTag;
        this.timingSourceTag = timingSourceTag == null ? sourceTag : timingSourceTag;
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

    public String getTranscriptText() {
        return transcriptText;
    }

    public List<OpenAiTranscriptWord> getWords() {
        return words;
    }

    public List<OpenAiTranscriptSegment> getSegments() {
        return segments;
    }

    public List<LyricToken> getLyricTokens() {
        return lyricTokens;
    }

    public boolean isFromCache() {
        return fromCache;
    }

    public File getCacheFile() {
        return cacheFile;
    }

    public String getTextSourceTag() {
        return textSourceTag;
    }

    public String getTimingSourceTag() {
        return timingSourceTag;
    }

    public OpenAiTranscriptionResult withTextSourceTag(String textSourceTag) {
        return new OpenAiTranscriptionResult(sourceAudioFile,
                uploadAudioFile,
                sourceTag,
                transcriptText,
                words,
                segments,
                lyricTokens,
                fromCache,
                cacheFile,
                textSourceTag,
                timingSourceTag);
    }
}
