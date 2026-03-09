package com.silver.skyislands.giantmobs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VirtualGiantStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(VirtualGiantStore.class);

    private final Path statePath;
    private final List<VirtualGiantState> giants = new ArrayList<>();
    private boolean dirty;

    public VirtualGiantStore() {
        this.statePath = FabricLoader.getInstance().getConfigDir().resolve("sky-islands-giants-virtual.json");
        load();
    }

    public synchronized List<VirtualGiantState> snapshot() {
        return new ArrayList<>(giants);
    }

    public synchronized VirtualGiantState get(UUID id) {
        for (VirtualGiantState state : giants) {
            if (state.id.equals(id)) {
                return state;
            }
        }
        return null;
    }

    public synchronized int size() {
        return giants.size();
    }

    public synchronized void upsert(VirtualGiantState state) {
        for (int i = 0; i < giants.size(); i++) {
            if (giants.get(i).id.equals(state.id)) {
                giants.set(i, state);
                dirty = true;
                return;
            }
        }
        giants.add(state);
        dirty = true;
    }

    public synchronized void remove(UUID id) {
        if (giants.removeIf(state -> state.id.equals(id))) {
            dirty = true;
        }
    }

    public synchronized void flush() {
        if (!dirty) {
            return;
        }
        if (save()) {
            dirty = false;
        }
    }

    private void load() {
        if (!Files.exists(this.statePath)) {
            return;
        }

        try {
            String json = Files.readString(this.statePath, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray arr = root.has("giants") ? root.getAsJsonArray("giants") : new JsonArray();

            Map<UUID, VirtualGiantState> byId = new LinkedHashMap<>();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject obj = el.getAsJsonObject();
                try {
                    UUID id = UUID.fromString(obj.get("id").getAsString());
                    double x = getDouble(obj, "x", 0.0);
                    double y = getDouble(obj, "y", 80.0);
                    double z = getDouble(obj, "z", 0.0);
                    float yaw = (float) getDouble(obj, "yaw", 0.0);
                    long tick = getLong(obj, "tick", 0L);
                    byId.put(id, new VirtualGiantState(id, new Vec3d(x, y, z), yaw, tick));
                } catch (Exception ignored) {
                }
            }

            giants.clear();
            giants.addAll(byId.values());
        } catch (IOException ignored) {
            LOGGER.warn("[Sky-Islands][giants][virtualStore] load failed path={}", statePath);
        }
    }

    private synchronized boolean save() {
        try {
            Files.createDirectories(this.statePath.getParent());

            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (VirtualGiantState giant : giants) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", giant.id.toString());
                obj.addProperty("x", giant.pos.x);
                obj.addProperty("y", giant.pos.y);
                obj.addProperty("z", giant.pos.z);
                obj.addProperty("yaw", giant.yawDegrees);
                obj.addProperty("tick", giant.lastTick);
                arr.add(obj);
            }
            root.add("giants", arr);

            Files.writeString(this.statePath, GSON.toJson(root), StandardCharsets.UTF_8);
            return true;
        } catch (IOException ignored) {
            LOGGER.warn("[Sky-Islands][giants][virtualStore] save failed path={}", statePath);
            return false;
        }
    }

    private static double getDouble(JsonObject obj, String key, double def) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return def;
        }
        try {
            return obj.get(key).getAsDouble();
        } catch (Exception ignored) {
            return def;
        }
    }

    private static long getLong(JsonObject obj, String key, long def) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return def;
        }
        try {
            return obj.get(key).getAsLong();
        } catch (Exception ignored) {
            return def;
        }
    }

    public record VirtualGiantState(UUID id, Vec3d pos, float yawDegrees, long lastTick) {
        public VirtualGiantState withPos(Vec3d newPos, long tick) {
            return new VirtualGiantState(this.id, newPos, this.yawDegrees, tick);
        }

        public VirtualGiantState withYaw(float newYawDegrees, long tick) {
            return new VirtualGiantState(this.id, this.pos, newYawDegrees, tick);
        }
    }
}