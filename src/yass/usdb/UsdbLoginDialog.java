package yass.usdb;

import org.apache.commons.lang3.StringUtils;
import yass.I18;
import yass.YassActions;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.Arrays;

public class UsdbLoginDialog extends JDialog {
    private final YassActions actions;
    private final JComboBox<UsdbCookieBrowser> browserBox = new JComboBox<>(UsdbCookieBrowser.values());
    private final JTextField usernameField = new JTextField(24);
    private final JPasswordField passwordField = new JPasswordField(24);
    private final JCheckBox rememberPasswordCheckBox = new JCheckBox(I18.get("lib_usdb_remember_password"));
    private final JLabel statusLabel = new JLabel(" ");
    private boolean loginSuccessful = false;

    public UsdbLoginDialog(YassActions actions) {
        super(actions.createOwnerFrame(), I18.get("lib_usdb_login"), true);
        this.actions = actions;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        add(createFormPanel(), BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);
        ImageIcon icon = actions.getIcon("usdbBrowserIcon");
        if (icon != null) {
            setIconImage(icon.getImage());
        }
        preloadStoredCredentials();
        pack();
        setLocationRelativeTo(getOwner());
    }

    public boolean showDialog() {
        refreshStatus();
        setVisible(true);
        return loginSuccessful;
    }

    private JPanel createFormPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel(I18.get("lib_usdb_browser")), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(browserBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel(I18.get("lib_usdb_username")), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(usernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(I18.get("lib_usdb_password")), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(passwordField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        panel.add(rememberPasswordCheckBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(statusLabel, gbc);
        boolean credentialStoreAvailable = actions.isUsdbCredentialStoreAvailable();
        rememberPasswordCheckBox.setEnabled(credentialStoreAvailable);
        if (credentialStoreAvailable) {
            String storeName = actions.getUsdbCredentialStoreName();
            if (StringUtils.isNotBlank(storeName)) {
                rememberPasswordCheckBox.setToolTipText(I18.get("lib_usdb_remember_password_tooltip").replace("{0}", storeName));
            }
        } else {
            rememberPasswordCheckBox.setToolTipText(I18.get("lib_usdb_remember_password_unavailable"));
        }
        rememberPasswordCheckBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                loadStoredPasswordIfAvailable();
            }
        });
        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton checkLoginButton = new JButton(I18.get("lib_usdb_check_login"));
        checkLoginButton.addActionListener(e -> onCheckLogin());
        JButton cancelButton = new JButton(I18.get("tool_correct_cancel"));
        cancelButton.addActionListener(e -> dispose());
        panel.add(checkLoginButton);
        panel.add(cancelButton);
        getRootPane().setDefaultButton(checkLoginButton);
        return panel;
    }

    private void onCheckLogin() {
        String username = StringUtils.trimToEmpty(usernameField.getText());
        char[] password = passwordField.getPassword();
        boolean loadedFromKeyring = false;
        UsdbCookieBrowser selectedBrowser = (UsdbCookieBrowser) browserBox.getSelectedItem();
        if (selectedBrowser != null && selectedBrowser != UsdbCookieBrowser.NONE) {
            String cookieUser = actions.loginToUsdbWithBrowserCookies(selectedBrowser);
            if (StringUtils.isNotBlank(cookieUser)) {
                actions.setUsdbCookieBrowser(selectedBrowser);
                refreshStatus();
                loginSuccessful = true;
                dispose();
                return;
            }
            statusLabel.setText(I18.get("lib_usdb_not_logged_in"));
        }
        if (password.length == 0) {
            char[] storedPassword = actions.loadUsdbStoredPassword(username);
            if (storedPassword != null && storedPassword.length > 0) {
                password = storedPassword;
                loadedFromKeyring = true;
            }
        }
        if (StringUtils.isBlank(username) || password.length == 0) {
            statusLabel.setText(I18.get("lib_usdb_login_missing"));
            return;
        }
        statusLabel.setText(I18.get("lib_usdb_login_running"));
        try {
            loginSuccessful = actions.loginToUsdb(username, password);
            refreshStatus();
            if (loginSuccessful) {
                actions.setUsdbCookieBrowser(selectedBrowser);
                actions.updateUsdbStoredCredentials(username, password, rememberPasswordCheckBox.isSelected());
                dispose();
            }
        } finally {
            Arrays.fill(password, '\0');
            if (loadedFromKeyring) {
                passwordField.setText("");
            }
        }
    }

    private void refreshStatus() {
        UsdbSessionService.UsdbSessionInfo sessionInfo = actions.getUsdbSessionInfo();
        if (sessionInfo.isLoggedIn()) {
            String key = sessionInfo.directEditAllowed()
                    ? "lib_usdb_logged_in_as_editor"
                    : "lib_usdb_logged_in_as_user";
            statusLabel.setText(I18.get(key).replace("{0}", sessionInfo.loggedInUser()));
        } else {
            statusLabel.setText(I18.get("lib_usdb_not_logged_in"));
        }
    }

    private void preloadStoredCredentials() {
        browserBox.setSelectedItem(actions.getUsdbCookieBrowser());
        usernameField.setText(actions.getUsdbStoredUsername());
        rememberPasswordCheckBox.setSelected(actions.shouldRememberUsdbPassword() && actions.isUsdbCredentialStoreAvailable());
        loadStoredPasswordIfAvailable();
    }

    private void loadStoredPasswordIfAvailable() {
        if (!rememberPasswordCheckBox.isSelected()) {
            passwordField.setText("");
            return;
        }
        char[] storedPassword = actions.loadUsdbStoredPassword(usernameField.getText());
        if (storedPassword == null || storedPassword.length == 0) {
            passwordField.setText("");
            return;
        }
        passwordField.setText(new String(storedPassword));
        Arrays.fill(storedPassword, '\0');
    }
}
