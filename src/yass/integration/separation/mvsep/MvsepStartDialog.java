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

import org.apache.commons.lang3.StringUtils;
import yass.I18;
import yass.integration.separation.SeparationRequest;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
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
        MvsepModel initialModel = Arrays.stream(availableModels).anyMatch(m -> m == selectedModel)
                ? selectedModel : availableModels[0];
        modelBox.setSelectedItem(initialModel);

        JComboBox<MvsepOutputFormat> formatBox = new JComboBox<>(MvsepOutputFormat.values());
        formatBox.setRenderer(new EnumLabelRenderer<>());
        formatBox.setSelectedItem(MvsepOutputFormat.fromValue(request.getOutputFormat()));

        // Model type combobox — populated dynamically
        JComboBox<MvsepFieldOption> modelTypeBox = new JComboBox<>();
        modelTypeBox.setVisible(false);
        JLabel modelTypeLabel = new JLabel(I18.get("options_external_tools_mvsep_model_type"));
        modelTypeLabel.setVisible(false);
        JButton infoButton = new JButton("?");
        infoButton.setToolTipText(I18.get("options_external_tools_mvsep_model_info"));
        infoButton.setMargin(new Insets(1, 4, 1, 4));

        // Helper: update model type options when model changes
        Runnable updateModelType = () -> {
            MvsepModel model = (MvsepModel) modelBox.getSelectedItem();
            modelTypeBox.removeAllItems();
            if (model == null || algorithms == null) {
                modelTypeLabel.setVisible(false);
                modelTypeBox.setVisible(false);
                infoButton.setEnabled(false);
                return;
            }
            MvsepAlgorithmInfo info = algorithms.get(model.getSepType());
            MvsepAlgorithmField field = info != null ? info.modelTypeField() : null;
            infoButton.setEnabled(info != null && StringUtils.isNotBlank(info.descriptionHtml()));
            infoButton.putClientProperty("algorithmInfo", info);

            if (field == null || field.options().isEmpty()) {
                modelTypeLabel.setVisible(false);
                modelTypeBox.setVisible(false);
                return;
            }
            List<MvsepFieldOption> options = field.options();
            for (MvsepFieldOption opt : options) {
                modelTypeBox.addItem(opt);
            }
            // Restore saved model type
            String savedKey = request.getModelType();
            MvsepFieldOption toSelect = StringUtils.isNotBlank(savedKey) ? field.findOption(savedKey) : null;
            if (toSelect == null) {
                toSelect = field.defaultOption();
            }
            if (toSelect != null) {
                modelTypeBox.setSelectedItem(toSelect);
            }
            modelTypeLabel.setVisible(true);
            modelTypeBox.setVisible(true);
        };

        updateModelType.run();
        modelBox.addActionListener(e -> updateModelType.run());

        infoButton.addActionListener(e -> {
            MvsepAlgorithmInfo info = (MvsepAlgorithmInfo) infoButton.getClientProperty("algorithmInfo");
            if (info != null && StringUtils.isNotBlank(info.descriptionHtml())) {
                JEditorPane editorPane = new JEditorPane();
                editorPane.setEditable(false);
                editorPane.setOpaque(false);
                javax.swing.text.html.HTMLEditorKit kit = new javax.swing.text.html.HTMLEditorKit();
                String uiFont = editorPane.getFont().getFamily();
                int uiSize = editorPane.getFont().getSize();
                kit.getStyleSheet().addRule(
                        "body { font-family: '" + uiFont + "', sans-serif; font-size: " + uiSize + "pt; margin: 4px; }"
                        + "p { margin-top: 4px; margin-bottom: 4px; }"
                );
                editorPane.setEditorKit(kit);
                editorPane.setText(info.descriptionHtml());
                JScrollPane scroll = new JScrollPane(editorPane);
                scroll.setPreferredSize(new Dimension(500, 300));
                JOptionPane.showMessageDialog(parent,
                                              scroll,
                                              ((MvsepModel) modelBox.getSelectedItem()).getLabel(),
                                              JOptionPane.INFORMATION_MESSAGE);
            }
        });

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 3;
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

        gbc.gridx = 2;
        gbc.weightx = 0;
        panel.add(infoButton, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        panel.add(modelTypeLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        panel.add(modelTypeBox, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.gridwidth = 1;
        panel.add(new JLabel(I18.get("options_external_tools_mvsep_output_format")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
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
        MvsepFieldOption modelTypeOption = (MvsepFieldOption) modelTypeBox.getSelectedItem();
        String modelTypeKey = modelTypeOption != null ? modelTypeOption.key() : null;
        return request.withOptions(model == null ? request.getModel() : model.getValue(),
                                   format == null ? request.getOutputFormat() : format.getValue(),
                                   modelTypeKey);
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
            if (value instanceof MvsepModel m) {
                setText(m.getLabel());
            } else if (value instanceof MvsepOutputFormat f) {
                setText(f.getLabel());
            }
            return this;
        }
    }
}
