package com.silver.chronicle.velocity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

record DatabaseSettings(String jdbcUrl, String username, String password) {
    static DatabaseSettings load() throws IOException {
        String configured = System.getenv("WAKEUP_DB_ENV_FILE");
        Path file = configured == null || configured.isBlank()
                ? Path.of(System.getProperty("user.home"), ".config", "wakeuplobby", "database.env")
                : Path.of(configured);
        Map<String, String> values = new HashMap<>();
        if (Files.isRegularFile(file)) {
            for (String raw : Files.readAllLines(file)) {
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int equals = line.indexOf('=');
                if (equals < 1) continue;
                String key = line.substring(0, equals).strip();
                String value = line.substring(equals + 1).strip();
                if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) value = value.substring(1, value.length() - 1);
                values.put(key, value);
            }
        }
        values.putAll(System.getenv());
        String jdbcUrl = values.get("WAKEUP_DB_JDBC_URL");
        String username = values.get("WAKEUP_DB_USER");
        String password = values.getOrDefault("WAKEUP_DB_PASSWORD", "");
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mariadb://") || !databaseName(jdbcUrl).equals("minecraft"))
            throw new IllegalArgumentException("WAKEUP_DB_JDBC_URL must target the existing minecraft MariaDB database");
        if (username == null || username.isBlank()) throw new IllegalArgumentException("WAKEUP_DB_USER is required");
        return new DatabaseSettings(jdbcUrl, username, password);
    }

    private static String databaseName(String url) {
        int query = url.indexOf('?');
        String base = query < 0 ? url : url.substring(0, query);
        int slash = base.lastIndexOf('/');
        return slash < 0 ? "" : base.substring(slash + 1);
    }
}
