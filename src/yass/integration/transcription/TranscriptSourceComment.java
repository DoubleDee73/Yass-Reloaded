package yass.integration.transcription;

import org.apache.commons.lang3.StringUtils;
import yass.integration.transcription.openai.OpenAiTranscriptionResult;

import java.util.ArrayList;
import java.util.List;

public final class TranscriptSourceComment {
    private static final String TEXT_KEY = "transcriptText=";
    private static final String TIMING_KEY = "transcriptTiming=";
    private static final String SOURCE_KEY = "transcriptSource=";

    private TranscriptSourceComment() {
    }

    public static String merge(String existingComment, OpenAiTranscriptionResult result) {
        if (result == null) {
            return existingComment;
        }

        List<String> parts = new ArrayList<>();
        for (String part : StringUtils.defaultString(existingComment).split(",")) {
            String trimmed = StringUtils.trimToEmpty(part);
            if (StringUtils.isBlank(trimmed) || isTranscriptSourcePart(trimmed)) {
                continue;
            }
            parts.add(trimmed);
        }

        return String.join(",", parts);
    }

    private static boolean isTranscriptSourcePart(String part) {
        return part.startsWith(TEXT_KEY) || part.startsWith(TIMING_KEY) || part.startsWith(SOURCE_KEY);
    }
}
