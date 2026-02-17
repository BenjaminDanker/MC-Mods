package com.silver.soulbounditems.config;

import com.silver.soulbounditems.SoulboundItemsMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class SoulboundItemsConfig {
    private static final String FILE_NAME = "soulbound-items.properties";
    private static final String KEY_OVERFLOW_HEARTS = "overflow_damage_hearts";
    private static final float DEFAULT_OVERFLOW_HEARTS = 4.0f;

    private static volatile float overflowDamageHearts = DEFAULT_OVERFLOW_HEARTS;

    private SoulboundItemsConfig() {
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        Properties properties = new Properties();

        if (Files.notExists(file)) {
            properties.setProperty(KEY_OVERFLOW_HEARTS, String.valueOf(DEFAULT_OVERFLOW_HEARTS));
            try {
                Files.createDirectories(file.getParent());
                try (OutputStream out = Files.newOutputStream(file)) {
                    properties.store(out, "Soulbound Items config");
                }
            } catch (IOException e) {
                SoulboundItemsMod.LOGGER.error("Failed to create config file {}", file, e);
            }
            overflowDamageHearts = DEFAULT_OVERFLOW_HEARTS;
            return;
        }

        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
            String raw = properties.getProperty(KEY_OVERFLOW_HEARTS, String.valueOf(DEFAULT_OVERFLOW_HEARTS));
            overflowDamageHearts = Math.max(0.0f, Float.parseFloat(raw));
        } catch (Exception e) {
            SoulboundItemsMod.LOGGER.error("Failed to load config {}; using defaults", file, e);
            overflowDamageHearts = DEFAULT_OVERFLOW_HEARTS;
        }
    }

    public static float getOverflowDamageHearts() {
        return overflowDamageHearts;
    }
}
