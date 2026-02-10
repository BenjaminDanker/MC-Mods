package com.silver.atlantis.heightcap;

import com.silver.atlantis.construct.SchematicSliceScanner;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

final class HeightCapConfig {

    private static final String FILE_NAME = "heightcap.properties";
    private static final String KEY_ENABLED = "enabled";

    private HeightCapConfig() {
    }

    static Path filePath() {
        return SchematicSliceScanner.defaultSchematicDir().resolve(FILE_NAME);
    }

    static boolean loadEnabledOrDefault(boolean defaultValue) {
        Path file = filePath();
        if (!Files.exists(file)) {
            return defaultValue;
        }

        Properties properties = new Properties();
        try (var in = new BufferedInputStream(Files.newInputStream(file))) {
            properties.load(in);
        } catch (Exception ignored) {
            return defaultValue;
        }

        String raw = properties.getProperty(KEY_ENABLED);
        if (raw == null) {
            return defaultValue;
        }

        return Boolean.parseBoolean(raw.trim());
    }

    static void saveEnabled(boolean enabled) throws Exception {
        Path file = filePath();
        Files.createDirectories(file.getParent());

        Properties properties = new Properties();
        properties.setProperty(KEY_ENABLED, Boolean.toString(enabled));

        Path tmp = file.resolveSibling(FILE_NAME + ".tmp");
        try (var out = new BufferedOutputStream(Files.newOutputStream(tmp))) {
            properties.store(out, "Atlantis Height Cap");
        }

        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
