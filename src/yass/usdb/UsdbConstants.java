package yass.usdb;

final class UsdbConstants {
    static final String BASE_URL = "https://usdb.animux.de/";
    static final String USER_AGENT = "Yass Reloaded USDB Phase1";
    static final int CONNECT_TIMEOUT_MS = 15_000;
    static final int READ_TIMEOUT_MS = 30_000;
    static final int DEFAULT_LIMIT = 100;

    private UsdbConstants() {
    }
}
