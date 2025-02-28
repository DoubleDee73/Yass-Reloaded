package yass;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class VersionUtils {
    private static String version;
    private static String releaseDate;

    static {
        try (InputStream input = VersionUtils.class.getClassLoader().getResourceAsStream("version.properties")) {
            Properties properties = new Properties();
            properties.load(input);
            version = properties.getProperty("version");
            if (version != null && version.contains(".")) {
                String[] verDat = version.split("\\.");
                releaseDate = String.format("%02d", Integer.parseInt(verDat[1])) + "/" + verDat[0];
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            version = "unknown";
        }
    }

    public static String getVersion() {
        return version;
    }

    public static String getReleaseDate() {
        return releaseDate;
    }
}
