package com.silver.atlantis.spawn.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.silver.atlantis.AtlantisMod;
import com.silver.atlantis.util.AtomicFiles;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Optional diagnostic logging, config/atlantis-logging.json; written with the defaults on first start. */
public final class AtlantisLoggingConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Marker discovery / seeding progress lines ([SpawnDiag], discovery progress). */
    public static volatile boolean spawnDiagnostics = true;
    /** Special-drop tagging samples. */
    public static volatile boolean specialDropDebug = true;

    private AtlantisLoggingConfig() {
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("atlantis-logging.json");
        try {
            if (Files.exists(path)) {
                JsonObject root = GSON.fromJson(Files.readString(path), JsonObject.class);
                if (root != null) {
                    if (root.has("spawnDiagnostics")) spawnDiagnostics = root.get("spawnDiagnostics").getAsBoolean();
                    if (root.has("specialDropDebug")) specialDropDebug = root.get("specialDropDebug").getAsBoolean();
                }
            } else {
                JsonObject root = new JsonObject();
                root.addProperty("spawnDiagnostics", spawnDiagnostics);
                root.addProperty("specialDropDebug", specialDropDebug);
                AtomicFiles.writeString(path, GSON.toJson(root) + "\n");
            }
        } catch (Exception e) {
            AtlantisMod.LOGGER.warn("[Atlantis] Could not read {}; using the defaults", path, e);
        }
        AtlantisMod.LOGGER.info("[Atlantis] logging: spawnDiagnostics={} specialDropDebug={}", spawnDiagnostics, specialDropDebug);
    }
}
