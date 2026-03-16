package yass.options;

import yass.I18;
import yass.integration.transcription.openai.OpenAiTimestampGranularity;
import yass.integration.transcription.openai.OpenAiTranscriptionModel;

import javax.swing.*;
import java.awt.*;

public class OpenAiPanel extends OptionsPanel {

    private static final long serialVersionUID = 1L;

    private void addFullWidthComment(String text) {
        JPanel row = new JPanel(new BorderLayout());
        JLabel label = new JLabel("<html><font color=gray>" + text);
        label.setVerticalAlignment(SwingConstants.TOP);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        row.add(label, BorderLayout.CENTER);
        getRight().add(row);
    }

    @Override
    public void addRows() {
        setLabelWidth(180);
        addFullWidthComment(I18.get("options_external_tools_openai_comment"));
        addText(I18.get("options_external_tools_openai_api_key"), "openai-api-key");
        addChoice(I18.get("options_external_tools_openai_model"), OpenAiTranscriptionModel.values(), "openai-model");
        addText(I18.get("options_external_tools_openai_language"), "openai-language");
        addTextArea(I18.get("options_external_tools_openai_prompt"), "openai-prompt", 3);
        addChoice(I18.get("options_external_tools_openai_timestamp_granularity"),
                  OpenAiTimestampGranularity.values(),
                  "openai-timestamp-granularity");
        addText(I18.get("options_external_tools_openai_alignment_aggressiveness"), "openai-alignment-aggressiveness");
    }
}
