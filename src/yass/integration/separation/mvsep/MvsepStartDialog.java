/*
 * Yass Reloaded - Karaoke Editor
 * Copyright (C) 2009-2023 Saruta
 * Copyright (C) 2023 DoubleDee
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package yass.integration.separation.mvsep;

import yass.I18;
import yass.integration.separation.SeparationRequest;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;

public final class MvsepStartDialog {
    private MvsepStartDialog() {
    }

    public static SeparationRequest showDialog(Component parent,
                                               SeparationRequest request,
                                               MvsepAccountInfo accountInfo,
                                               Map<Integer, MvsepAlgorithmInfo> algorithms,
                                               Integer planQueue,
                                               Icon logoIcon) {
        MvsepModel[] availableModels = Arrays.stream(MvsepModel.values())
                                             .filter(model -> model.isAllowedFor(accountInfo, algorithms))
                                             .toArray(MvsepModel[]::new);
        if (availableModels.length == 0) {
            availableModels = new MvsepModel[]{MvsepModel.MELBAND_ROFORMER};
        }

        JComboBox<MvsepModel> modelBox = new JComboBox<>(availableModels);
        modelBox.setRenderer(new EnumLabelRenderer<>());
        MvsepModel selectedModel = MvsepModel.fromValue(request.getModel());
        modelBox.setSelectedItem(Arrays.stream(availableModels).anyMatch(model -> model == selectedModel)
                                         ? selectedModel
                                         : availableModels[0]);

        JComboBox<MvsepOutputFormat> formatBox = new JComboBox<>(MvsepOutputFormat.values());
        formatBox.setRenderer(new EnumLabelRenderer<>());
        formatBox.setSelectedItem(MvsepOutputFormat.fromValue(request.getOutputFormat()));

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        JPanel introPanel = new JPanel(new BorderLayout());
        JLabel hintLabel = new JLabel("<html>" + I18.get("edit_audio_separate_hint") + "</html>");
        introPanel.add(hintLabel, BorderLayout.CENTER);
        panel.add(introPanel, gbc);
        gbc.gridy++;

        if (accountInfo != null || planQueue != null) {
            JTextArea accountSummary = new JTextArea(buildAccountSummary(accountInfo, planQueue));
            accountSummary.setEditable(false);
            accountSummary.setOpaque(false);
            accountSummary.setLineWrap(true);
            accountSummary.setWrapStyleWord(true);
            panel.add(accountSummary, gbc);
            gbc.gridy++;
        }

        gbc.gridwidth = 1;

        gbc.gridx = 0;
        panel.add(new JLabel(I18.get("options_external_tools_mvsep_model")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(modelBox, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        panel.add(new JLabel(I18.get("options_external_tools_mvsep_output_format")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(formatBox, gbc);

        JOptionPane optionPane = new JOptionPane(panel,
                                                   JOptionPane.PLAIN_MESSAGE,
                                                   JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog = optionPane.createDialog(parent, I18.get("edit_audio_separate"));
        if (logoIcon instanceof ImageIcon imageIcon) {
            dialog.setIconImage(imageIcon.getImage());
        }
        dialog.setVisible(true);
        Object option = optionPane.getValue();
        if (!Integer.valueOf(JOptionPane.OK_OPTION).equals(option)) {
            return null;
        }

        MvsepModel model = (MvsepModel) modelBox.getSelectedItem();
        MvsepOutputFormat format = (MvsepOutputFormat) formatBox.getSelectedItem();
        return request.withOptions(model == null ? request.getModel() : model.getValue(),
                                   format == null ? request.getOutputFormat() : format.getValue());
    }

    private static String buildAccountSummary(MvsepAccountInfo accountInfo, Integer planQueue) {
        StringBuilder summary = new StringBuilder();
        if (accountInfo != null) {
            summary.append(accountInfo.getSummary());
        }
        if (planQueue != null) {
            if (summary.length() > 0) {
                summary.append('\n');
            }
            summary.append("Plan queue before upload: ").append(planQueue);
        }
        return summary.toString();
    }

    private static final class EnumLabelRenderer<T> extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list,
                                                      Object value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof MvsepModel model) {
                setText(model.getLabel());
            } else if (value instanceof MvsepOutputFormat format) {
                setText(format.getLabel());
            }
            return this;
        }
    }
}
