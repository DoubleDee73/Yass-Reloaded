package yass.usdb;

import com.github.javakeyring.BackendNotSupportedException;
import com.github.javakeyring.Keyring;
import com.github.javakeyring.PasswordAccessException;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

public class UsdbCredentialStore {
    private static final String SERVICE_NAME = "Yass Reloaded / USDB";

    public boolean isAvailable() {
        try {
            getKeyring();
            return true;
        } catch (UsdbCredentialStoreException ex) {
            return false;
        }
    }

    public Optional<String> getStorageTypeName() {
        try {
            return Optional.of(getKeyring().getKeyringStorageType().name());
        } catch (UsdbCredentialStoreException ex) {
            return Optional.empty();
        }
    }

    public Optional<char[]> loadPassword(String username) {
        if (StringUtils.isBlank(username)) {
            return Optional.empty();
        }
        try {
            String password = getKeyring().getPassword(SERVICE_NAME, username.trim());
            if (password == null) {
                return Optional.empty();
            }
            return Optional.of(password.toCharArray());
        } catch (PasswordAccessException ex) {
            if (isCredentialMissing(ex)) {
                return Optional.empty();
            }
            throw new UsdbCredentialStoreException("USDB password could not be loaded from the system keyring.", ex);
        }
    }

    public void savePassword(String username, char[] password) {
        if (StringUtils.isBlank(username) || password == null || password.length == 0) {
            throw new IllegalArgumentException("Username and password are required.");
        }
        try {
            getKeyring().setPassword(SERVICE_NAME, username.trim(), new String(password));
        } catch (PasswordAccessException ex) {
            throw new UsdbCredentialStoreException("USDB password could not be saved to the system keyring.", ex);
        }
    }

    public void deletePassword(String username) {
        if (StringUtils.isBlank(username)) {
            return;
        }
        try {
            getKeyring().deletePassword(SERVICE_NAME, username.trim());
        } catch (PasswordAccessException ex) {
            if (isCredentialMissing(ex)) {
                return;
            }
            throw new UsdbCredentialStoreException("USDB password could not be removed from the system keyring.", ex);
        }
    }

    private Keyring getKeyring() {
        try {
            return Keyring.create();
        } catch (BackendNotSupportedException ex) {
            throw new UsdbCredentialStoreException("No supported system keyring backend is available.", ex);
        }
    }

    public static class UsdbCredentialStoreException extends RuntimeException {
        public UsdbCredentialStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private boolean isCredentialMissing(PasswordAccessException ex) {
        String message = StringUtils.defaultString(ex.getMessage());
        return message.contains("1168");
    }
}
