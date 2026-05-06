package yass.usdb;

import yass.I18;

import javax.swing.*;
import java.awt.*;

public class UsdbImportConflictDialog extends JDialog {
    private UsdbImportConflictChoice result = UsdbImportConflictChoice.CANCEL;

    private UsdbImportConflictDialog(Window owner, String existingText, String importedText, String songName) {
        super(owner, I18.get("lib_usdb_conflict_title"), ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        JLabel header = new JLabel(I18.get("lib_usdb_conflict_message").replace("{0}", songName));
        header.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        add(header, BorderLayout.NORTH);
        add(createTextPanel(existingText, importedText), BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);
        ImageIcon icon = new ImageIcon(getClass().getResource("/yass/resources/img/Usdb16.ico"));
        setIconImage(icon.getImage());
        setSize(980, 620);
        setLocationRelativeTo(owner);
    }

    public static UsdbImportConflictChoice showDialog(Window owner, String existingText, String importedText, String songName) {
        UsdbImportConflictDialog dialog = new UsdbImportConflictDialog(owner, existingText, importedText, songName);
        dialog.setVisible(true);
        return dialog.result;
    }

    private JComponent createTextPanel(String existingText, String importedText) {
        JTextArea existingArea = createTextArea(existingText);
        JTextArea importedArea = createTextArea(importedText);

        JPanel leftPanel = new JPanel(new BorderLayout(0, 6));
        leftPanel.add(new JLabel(I18.get("lib_usdb_existing_txt")), BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(existingArea), BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout(0, 6));
        rightPanel.add(new JLabel(I18.get("lib_usdb_imported_txt")), BorderLayout.NORTH);
        rightPanel.add(new JScrollPane(importedArea), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setResizeWeight(0.5);
        splitPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        return splitPane;
    }

    private JTextArea createTextArea(String text) {
        JTextArea area = new JTextArea(text, 25, 40);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setCaretPosition(0);
        return area;
    }

    private JComponent createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton replaceButton = new JButton(I18.get("lib_usdb_replace"));
        replaceButton.addActionListener(e -> closeWith(UsdbImportConflictChoice.REPLACE));
        JButton createNewButton = new JButton(I18.get("lib_usdb_create_new_version"));
        createNewButton.addActionListener(e -> closeWith(UsdbImportConflictChoice.CREATE_NEW_VERSION));
        JButton cancelButton = new JButton(I18.get("tool_correct_cancel"));
        cancelButton.addActionListener(e -> closeWith(UsdbImportConflictChoice.CANCEL));
        panel.add(replaceButton);
        panel.add(createNewButton);
        panel.add(cancelButton);
        return panel;
    }

    private void closeWith(UsdbImportConflictChoice choice) {
        result = choice;
        dispose();
    }
}
