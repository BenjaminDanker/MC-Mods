package com.silver.skyislands.enderdragons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ManagedDragonStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(ManagedDragonStore.class);

    private final Path statePath;
    private final Set<UUID> dragonUuids = new HashSet<>();

    public ManagedDragonStore() {
        this.statePath = FabricLoader.getInstance().getConfigDir().resolve("sky-islands-dragons-state.json");
        load();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][dragons][managedStore] init path={} count={}", statePath, dragonUuids.size());
        }
    }

    public synchronized int size() {
        return this.dragonUuids.size();
    }

    public synchronized boolean contains(UUID uuid) {
        return this.dragonUuids.contains(uuid);
    }

    public synchronized void add(UUID uuid) {
        if (this.dragonUuids.add(uuid)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][dragons][managedStore] add uuid={} countNow={}", uuid, dragonUuids.size());
            }
            save();
        }
    }

    public synchronized void remove(UUID uuid) {
        if (this.dragonUuids.remove(uuid)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][dragons][managedStore] remove uuid={} countNow={}", uuid, dragonUuids.size());
            }
            save();
        }
    }

    private void load() {
        if (!Files.exists(this.statePath)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][dragons][managedStore] load: file missing; writing empty path={}", statePath);
            }
            save();
            return;
        }

        try {
            String json = Files.readString(this.statePath, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray arr = root.has("dragons") ? root.getAsJsonArray("dragons") : new JsonArray();
            for (int i = 0; i < arr.size(); i++) {
                try {
                    this.dragonUuids.add(UUID.fromString(arr.get(i).getAsString()));
                } catch (Exception ignored) {
                }
            }
        } catch (IOException ignored) {
            LOGGER.warn("[Sky-Islands][dragons][managedStore] load failed path={}", statePath);
        }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(this.statePath.getParent());
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (UUID uuid : this.dragonUuids) {
                arr.add(uuid.toString());
            }
            root.add("dragons", arr);
            Files.writeString(this.statePath, GSON.toJson(root), StandardCharsets.UTF_8);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][dragons][managedStore] save ok path={} count={}", statePath, dragonUuids.size());
            }
        } catch (IOException ignored) {
            LOGGER.warn("[Sky-Islands][dragons][managedStore] save failed path={}", statePath);
        }
    }
}
