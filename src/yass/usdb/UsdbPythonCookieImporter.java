package yass.usdb;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.HttpCookie;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class UsdbPythonCookieImporter {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration INSTALL_TIMEOUT = Duration.ofMinutes(3);
    private final List<String> preferredExecutables;

    public UsdbPythonCookieImporter(String... preferredExecutables) {
        this.preferredExecutables = new ArrayList<>();
        if (preferredExecutables != null) {
            for (String executable : preferredExecutables) {
                if (StringUtils.isNotBlank(executable)) {
                    this.preferredExecutables.add(executable.trim());
                }
            }
        }
    }

    public PythonCommand detectPython() {
        for (PythonCommand candidate : candidateCommands()) {
            CommandResult result = run(candidate.command(), COMMAND_TIMEOUT, "--version");
            if (result.success() && result.output().toLowerCase().contains("python")) {
                LOGGER.info("Detected Python for USDB browser-cookie fallback: " + candidate.display());
                return candidate;
            }
        }
        LOGGER.info("No Python executable detected for USDB browser-cookie fallback.");
        return null;
    }

    public boolean hasBrowserCookie3(PythonCommand python) {
        if (python == null) {
            return false;
        }
        CommandResult result = run(commandWithArgs(python.command(),
                                                   "-c",
                                                   "import browser_cookie3; print('ok')"),
                                   COMMAND_TIMEOUT);
        return result.success() && result.output().contains("ok");
    }

    public boolean installBrowserCookie3(PythonCommand python) {
        if (python == null) {
            return false;
        }
        LOGGER.info("Installing browser-cookie3 via " + python.display());
        CommandResult result = run(commandWithArgs(python.command(),
                                                   "-m",
                                                   "pip",
                                                   "install",
                                                   "--disable-pip-version-check",
                                                   "browser-cookie3"),
                                   INSTALL_TIMEOUT);
        if (!result.success()) {
            LOGGER.warning("browser-cookie3 installation failed: " + result.output());
        }
        return result.success();
    }

    public List<HttpCookie> loadCookies(UsdbCookieBrowser browser, PythonCommand python) throws IOException {
        if (browser == null || python == null) {
            return List.of();
        }
        String browserArgument = switch (browser) {
            case CHROME -> "chrome";
            case EDGE -> "edge";
            default -> "";
        };
        if (browserArgument.isBlank()) {
            return List.of();
        }
        CommandResult result = run(commandWithArgs(python.command(),
                                                   "-c",
                                                   buildCookieScript(),
                                                   browserArgument),
                                   COMMAND_TIMEOUT);
        if (!result.success()) {
            throw new IOException("Python browser-cookie import failed: " + result.output());
        }
        return parseCookies(result.output());
    }

    private List<PythonCommand> candidateCommands() {
        Set<PythonCommand> candidates = new LinkedHashSet<>();
        for (String preferredExecutable : preferredExecutables) {
            candidates.add(new PythonCommand(List.of(preferredExecutable), preferredExecutable));
        }
        candidates.add(new PythonCommand(List.of("py", "-3"), "py -3"));
        candidates.add(new PythonCommand(List.of("python"), "python"));
        candidates.add(new PythonCommand(List.of("python3"), "python3"));
        candidates.add(new PythonCommand(List.of("py"), "py"));
        candidates.add(new PythonCommand(List.of("python.exe"), "python.exe"));
        return new ArrayList<>(candidates);
    }

    private List<HttpCookie> parseCookies(String json) {
        List<HttpCookie> cookies = new ArrayList<>();
        JsonArray array = JsonParser.parseString(StringUtils.defaultString(json).trim()).getAsJsonArray();
        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            String name = getAsString(object, "name");
            String value = getAsString(object, "value");
            if (StringUtils.isBlank(name) || value == null) {
                continue;
            }
            HttpCookie cookie = new HttpCookie(name, value);
            cookie.setDomain(StringUtils.removeStart(getAsString(object, "domain"), "."));
            cookie.setPath(StringUtils.defaultIfBlank(getAsString(object, "path"), "/"));
            cookie.setSecure(object.has("secure") && object.get("secure").getAsBoolean());
            long expiryEpoch = object.has("expires") && !object.get("expires").isJsonNull()
                    ? object.get("expires").getAsLong()
                    : 0L;
            if (expiryEpoch > 0L) {
                long remainingSeconds = expiryEpoch - Instant.now().getEpochSecond();
                if (remainingSeconds > 0L) {
                    cookie.setMaxAge(remainingSeconds);
                }
            }
            cookie.setVersion(0);
            cookies.add(cookie);
        }
        LOGGER.info("Imported " + cookies.size() + " cookie(s) from Python browser-cookie fallback.");
        return cookies;
    }

    private String getAsString(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString();
    }

    private List<String> commandWithArgs(List<String> baseCommand, String... args) {
        List<String> command = new ArrayList<>(baseCommand);
        command.addAll(List.of(args));
        return command;
    }

    private CommandResult run(List<String> command, Duration timeout, String... trailingArgs) {
        return run(commandWithArgs(command, trailingArgs), timeout);
    }

    private CommandResult run(List<String> command, Duration timeout) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CommandResult(false, "Command timed out: " + String.join(" ", command));
            }
            String output = new String(process.getInputStream().readAllBytes());
            return new CommandResult(process.exitValue() == 0, StringUtils.defaultString(output).trim());
        } catch (Exception ex) {
            LOGGER.log(Level.INFO, "Python command failed: " + String.join(" ", command), ex);
            if (process != null) {
                process.destroyForcibly();
            }
            return new CommandResult(false, StringUtils.defaultIfBlank(ex.getMessage(), ex.toString()));
        }
    }

    private String buildCookieScript() {
        return """
                import json
                import sys
                import browser_cookie3

                browser = sys.argv[1]
                loader = getattr(browser_cookie3, browser)
                jar = loader(domain_name='usdb.animux.de')
                cookies = []
                for cookie in jar:
                    if 'usdb.animux.de' not in (cookie.domain or ''):
                        continue
                    cookies.append({
                        'name': cookie.name,
                        'value': cookie.value,
                        'domain': cookie.domain,
                        'path': cookie.path,
                        'secure': bool(cookie.secure),
                        'expires': int(cookie.expires) if cookie.expires else None,
                    })
                print(json.dumps(cookies))
                """;
    }

    public record PythonCommand(List<String> command, String display) {
    }

    private record CommandResult(boolean success, String output) {
    }
}
