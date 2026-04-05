package yass.integration.separation.mvsep;

public record MvsepFieldOption(String key, String value) {
    @Override
    public String toString() {
        return value;
    }
}
