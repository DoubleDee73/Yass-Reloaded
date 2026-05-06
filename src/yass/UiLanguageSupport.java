package yass;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Locale;

public final class UiLanguageSupport {
    private static final List<LanguageOption> SUPPORTED_DIALOG_LANGUAGES = List.of(
            new LanguageOption("en"),
            new LanguageOption("de"),
            new LanguageOption("es"),
            new LanguageOption("fr"),
            new LanguageOption("hu"),
            new LanguageOption("pl")
    );

    private UiLanguageSupport() {
    }

    public static String resolveStartupLanguage(String configuredLanguage, Locale systemLocale) {
        String selected = StringUtils.trimToEmpty(configuredLanguage);
        if (selected.isBlank() || "default".equalsIgnoreCase(selected)) {
            selected = systemLocale != null ? StringUtils.trimToEmpty(systemLocale.getLanguage()) : "";
        }
        return isSupported(selected) ? selected : "en";
    }

    public static List<LanguageOption> getSupportedDialogLanguages() {
        return SUPPORTED_DIALOG_LANGUAGES;
    }

    public static LanguageOption findLanguageOption(String code) {
        return SUPPORTED_DIALOG_LANGUAGES.stream()
                .filter(option -> option.code().equals(code))
                .findFirst()
                .orElse(SUPPORTED_DIALOG_LANGUAGES.get(0));
    }

    private static boolean isSupported(String code) {
        return SUPPORTED_DIALOG_LANGUAGES.stream().anyMatch(option -> option.code().equals(code));
    }

    public record LanguageOption(String code) {
        @Override
        public String toString() {
            Locale locale = Locale.forLanguageTag(code);
            return locale.getDisplayLanguage(locale);
        }
    }
}
