package com.silver.skyislands.enderdragons;

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

public final class VirtualDragonStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(VirtualDragonStore.class);

    private final Path statePath;
    private final List<VirtualDragonState> dragons = new ArrayList<>();
    private boolean dirty;

    public VirtualDragonStore() {
        this.statePath = FabricLoader.getInstance().getConfigDir().resolve("sky-islands-dragons-virtual.json");
        load();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][dragons][virtualStore] init path={} loaded={}", statePath, dragons.size());
        }
    }

    public synchronized List<VirtualDragonState> snapshot() {
        return new ArrayList<>(dragons);
    }

    public synchronized VirtualDragonState get(UUID id) {
        for (VirtualDragonState s : dragons) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }

    public synchronized int size() {
        return dragons.size();
    }

    public synchronized void upsert(VirtualDragonState state) {
        final boolean trace = LOGGER.isTraceEnabled();
        final boolean debug = LOGGER.isDebugEnabled();
        int firstIndex = -1;
        int removedDupes = 0;
        for (int i = 0; i < dragons.size(); i++) {
            if (dragons.get(i).id.equals(state.id)) {
                if (firstIndex == -1) {
                    firstIndex = i;
                    dragons.set(i, state);
                } else {
                    // Remove any accidental duplicate entries for the same id.
                    dragons.remove(i);
                    i--;
                    removedDupes++;
                }
            }
        }

        if (firstIndex == -1) {
            dragons.add(state);
            if (debug) {
                LOGGER.debug("[Sky-Islands][dragons][virtualStore] insert id={} sizeNow={}", state.id, dragons.size());
            }
        } else if (trace) {
            LOGGER.trace("[Sky-Islands][dragons][virtualStore] update id={} pos=({}, {}, {}) heading=({}, {})",
                    state.id,
                    Math.round(state.pos.x * 10.0) / 10.0,
                    Math.round(state.pos.y * 10.0) / 10.0,
                    Math.round(state.pos.z * 10.0) / 10.0,
                    Math.round(state.headingX * 100.0) / 100.0,
                    Math.round(state.headingZ * 100.0) / 100.0);
        }

        if (removedDupes > 0 && debug) {
            LOGGER.debug("[Sky-Islands][dragons][virtualStore] deDupe id={} removedDupes={} sizeNow={}", state.id, removedDupes, dragons.size());
        }

        dirty = true;
    }

    public synchronized void remove(UUID id) {
        if (dragons.removeIf(s -> s.id.equals(id))) {
            dirty = true;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][dragons][virtualStore] removed id={} sizeNow={}", id, dragons.size());
            }
        }
    }

    /**
     * Persist the current state to disk if it has changed.
     * Intended to be called on server shutdown (SERVER_STOPPING).
     */
    public synchronized void flush() {
        if (!dirty) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][dragons][virtualStore] flush skipped (not dirty)");
            }
            return;
        }

        if (save()) {
            dirty = false;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][dragons][virtualStore] flush ok (saved)");
            }
        } else {
            LOGGER.warn("[Sky-Islands][dragons][virtualStore] flush failed (save returned false)");
        }
    }

    private void load() {
        if (!Files.exists(this.statePath)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][dragons][virtualStore] load: file missing path={}", statePath);
            }
            return;
        }

        try {
            String json = Files.readString(this.statePath, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray arr = root.has("dragons") ? root.getAsJsonArray("dragons") : new JsonArray();

            // De-duplicate by id (last entry wins) to avoid double-processing on tick.
            Map<UUID, VirtualDragonState> byId = new LinkedHashMap<>();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject obj = el.getAsJsonObject();

                UUID id;
                try {
                    id = UUID.fromString(obj.get("id").getAsString());
                } catch (Exception ignored) {
                    continue;
                }

                double x = getDouble(obj, "x", 0);
                double y = getDouble(obj, "y", 160);
                double z = getDouble(obj, "z", 0);
                double hx = getDouble(obj, "hx", 1);
                double hz = getDouble(obj, "hz", 0);
                long tick = getLong(obj, "tick", 0);

                byId.put(id, new VirtualDragonState(id, new Vec3d(x, y, z), hx, hz, tick));
            }

            dragons.clear();
            dragons.addAll(byId.values());

            dirty = false;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][dragons][virtualStore] load ok entries={} (raw={})", dragons.size(), arr.size());
            }
        } catch (IOException ignored) {
            LOGGER.warn("[Sky-Islands][dragons][virtualStore] load failed path={}", statePath);
        }
    }

    private synchronized boolean save() {
        try {
            Files.createDirectories(this.statePath.getParent());

            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (VirtualDragonState dragon : dragons) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", dragon.id.toString());
                obj.addProperty("x", dragon.pos.x);
                obj.addProperty("y", dragon.pos.y);
                obj.addProperty("z", dragon.pos.z);
                obj.addProperty("hx", dragon.headingX);
                obj.addProperty("hz", dragon.headingZ);
                obj.addProperty("tick", dragon.lastTick);
                arr.add(obj);
            }
            root.add("dragons", arr);

            Files.writeString(this.statePath, GSON.toJson(root), StandardCharsets.UTF_8);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][dragons][virtualStore] save ok path={} count={}", statePath, dragons.size());
            }
            return true;
        } catch (IOException ignored) {
            LOGGER.warn("[Sky-Islands][dragons][virtualStore] save failed path={}", statePath);
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

    public record VirtualDragonState(UUID id, Vec3d pos, double headingX, double headingZ, long lastTick) {
        public VirtualDragonState {
            double len = Math.sqrt(headingX * headingX + headingZ * headingZ);
            if (len < 1.0e-6) {
                headingX = 1;
                headingZ = 0;
                len = 1;
            }
            headingX /= len;
            headingZ /= len;
        }

        public VirtualDragonState withPos(Vec3d newPos, long tick) {
            return new VirtualDragonState(this.id, newPos, this.headingX, this.headingZ, tick);
        }

        public VirtualDragonState withHeading(double newHx, double newHz, long tick) {
            return new VirtualDragonState(this.id, this.pos, newHx, newHz, tick);
        }
    }
}
