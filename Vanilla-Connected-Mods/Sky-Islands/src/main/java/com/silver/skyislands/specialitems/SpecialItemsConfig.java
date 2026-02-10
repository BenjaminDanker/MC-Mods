package com.silver.skyislands.specialitems;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SpecialItemsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(SpecialItemsConfig.class);

    public final int feathersPerArrow;

    private SpecialItemsConfig(int feathersPerArrow) {
        this.feathersPerArrow = Math.max(1, feathersPerArrow);
    }

    public static SpecialItemsConfig loadOrCreate() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("sky-islands-specialitems.json");

        if (!Files.exists(configPath)) {
            SpecialItemsConfig defaults = defaults();
            write(configPath, defaults);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][specialitems] wrote default config: {}", configPath);
            }
            return defaults;
        }

        try {
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            int feathersPerArrow = getInt(root, "feathersPerArrow", 3);

            SpecialItemsConfig parsed = new SpecialItemsConfig(feathersPerArrow);
            write(configPath, parsed);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][specialitems] loaded config: {} feathersPerArrow={}", configPath, parsed.feathersPerArrow);
            }
            return parsed;
        } catch (IOException | IllegalStateException | JsonParseException e) {
            SpecialItemsConfig defaults = defaults();
            write(configPath, defaults);
            LOGGER.warn("[Sky-Islands][specialitems] failed to read config; using defaults. path={} err={}", configPath, e.toString());
            return defaults;
        }
    }

    private static SpecialItemsConfig defaults() {
        return new SpecialItemsConfig(3);
    }

    private static int getInt(JsonObject root, String key, int def) {
        try {
            if (root.has(key)) {
                return root.get(key).getAsInt();
            }
        } catch (RuntimeException ignored) {
        }
        return def;
    }

    private static void write(Path path, SpecialItemsConfig cfg) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("feathersPerArrow", cfg.feathersPerArrow);

            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("[Sky-Islands][specialitems] failed to write config. path={} err={}", path, e.toString());
        }
    }
}
