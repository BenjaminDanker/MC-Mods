package com.silver.authorization;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import java.util.Properties;

/**
 * Values every program on this host shares (the portal secret, the auth catalog address), read from
 * ~/.config/mc-network/shared.properties. mc-net writes that file from the network registry, so a value changed in
 * the registry reaches every server on the host at once. Re-read when the file changes; absent values fall back to
 * whatever the caller had configured itself.
 */
public final class NetworkShared {
    private static final Path FILE = Path.of(System.getProperty("user.home"), ".config", "mc-network", "shared.properties");
    private static Properties values = new Properties();
    private static FileTime loadedAt;

    private NetworkShared() {
    }

    public static synchronized Optional<String> get(String key) {
        reloadIfChanged();
        String value = values.getProperty(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    /** The shared value, or {@code fallback} when the shared file has none. */
    public static String get(String key, String fallback) {
        return get(key).orElse(fallback);
    }

    /** The database login every program on this host uses (~/.config/mc-network/database.env), if present. */
    public static Optional<DatabaseLogin> databaseLogin() {
        Path file = FILE.resolveSibling("database.env");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        Properties env = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            env.load(in);
        } catch (IOException unreadable) {
            return Optional.empty();
        }
        String url = env.getProperty("WAKEUP_DB_JDBC_URL", "").trim();
        var match = java.util.regex.Pattern.compile("jdbc:(?:mariadb|mysql)://([^/?]+)/([^?]+).*").matcher(url);
        String user = env.getProperty("WAKEUP_DB_USER", "").trim();
        if (!match.matches() || user.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new DatabaseLogin(match.group(1), match.group(2), user,
                env.getProperty("WAKEUP_DB_PASSWORD", "").trim()));
    }

    /** {@code hostAndPort} is "host:port" (or just "host"). */
    public record DatabaseLogin(String hostAndPort, String database, String user, String password) {
    }

    private static void reloadIfChanged() {
        try {
            if (!Files.isRegularFile(FILE)) {
                values = new Properties();
                loadedAt = null;
                return;
            }
            FileTime modified = Files.getLastModifiedTime(FILE);
            if (modified.equals(loadedAt)) {
                return;
            }
            Properties fresh = new Properties();
            try (InputStream in = Files.newInputStream(FILE)) {
                fresh.load(in);
            }
            values = fresh;
            loadedAt = modified;
        } catch (IOException unreadable) {
            // Keep the last values read; callers fall back to their own configuration for anything missing.
        }
    }
}
