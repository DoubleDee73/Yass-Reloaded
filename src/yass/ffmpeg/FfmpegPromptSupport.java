package yass.ffmpeg;

import org.apache.commons.lang3.StringUtils;

public final class FfmpegPromptSupport {
    private FfmpegPromptSupport() {
    }

    public static boolean shouldShowPostPromptReminder(String selectedPath) {
        return StringUtils.isBlank(selectedPath);
    }
}
