package com.silver.disabledimensions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class DisableDimensionsConfig {
    private static final DisableDimensionsConfig DEFAULT = new DisableDimensionsConfig(true, true, true);

    private final boolean disableNether;
    private final boolean disableEnd;
    private final boolean allowOpBypass;

    DisableDimensionsConfig(boolean disableNether, boolean disableEnd, boolean allowOpBypass) {
        this.disableNether = disableNether;
        this.disableEnd = disableEnd;
        this.allowOpBypass = allowOpBypass;
    }

    static DisableDimensionsConfig defaults() {
        return DEFAULT;
    }

    static DisableDimensionsConfig loadOrCreate(Path configFile) {
        try {
            Files.createDirectories(configFile.getParent());

            if (!Files.exists(configFile)) {
                DisableDimensionsConfig defaults = defaults();
                write(configFile, defaults);
                return defaults;
            }

            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(configFile)) {
                properties.load(input);
            }

            return new DisableDimensionsConfig(
                readBoolean(properties, "disableNether", true),
                readBoolean(properties, "disableEnd", true),
                readBoolean(properties, "allowOpBypass", true)
            );
        } catch (Exception e) {
            DisableDimensionsMod.LOGGER.warn("Failed loading Disable Dimensions config: {}", e.getMessage());
            return defaults();
        }
    }

    static void write(Path configFile, DisableDimensionsConfig config) throws IOException {
        String content = """
            # Disable Dimensions config
            # Set to false to enable access to a dimension.
            disableNether=%s
            disableEnd=%s
            allowOpBypass=%s
            """.formatted(config.disableNether, config.disableEnd, config.allowOpBypass);

        try (OutputStream output = Files.newOutputStream(configFile)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static boolean readBoolean(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    boolean disableNether() {
        return disableNether;
    }

    boolean disableEnd() {
        return disableEnd;
    }

    boolean allowOpBypass() {
        return allowOpBypass;
    }

    DisableDimensionsConfig withDisableNether(boolean value) {
        return new DisableDimensionsConfig(value, disableEnd, allowOpBypass);
    }

    DisableDimensionsConfig withDisableEnd(boolean value) {
        return new DisableDimensionsConfig(disableNether, value, allowOpBypass);
    }
}
