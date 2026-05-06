package yass.usdb;

public enum UsdbCookieBrowser {
    NONE("None"),
    FIREFOX("Firefox"),
    CHROME("Chrome"),
    EDGE("Edge");

    private final String label;

    UsdbCookieBrowser(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }

    public static UsdbCookieBrowser fromProperty(String value) {
        if (value == null) {
            return NONE;
        }
        for (UsdbCookieBrowser browser : values()) {
            if (browser.name().equalsIgnoreCase(value)) {
                return browser;
            }
        }
        return NONE;
    }
}
