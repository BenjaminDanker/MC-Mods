package com.silver.atlantis.leviathan;

import com.silver.atlantis.AtlantisMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class VirtualLeviathanStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String FILE_NAME = "atlantis-leviathans-virtual.json";

    private final Path statePath;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<UUID, VirtualLeviathanState> leviathansById = new LinkedHashMap<>();
    private List<VirtualLeviathanState> snapshotCache = List.of();
    private boolean snapshotDirty = true;
    private boolean dirty;

    public VirtualLeviathanStore() {
        this.statePath = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        AtlantisMod.LOGGER.info("[Atlantis][leviathan] virtual store init path={}", statePath);
        load();
    }

    public List<VirtualLeviathanState> snapshot() {
        lock.readLock().lock();
        try {
            if (!snapshotDirty) {
                return snapshotCache;
            }
        } finally {
            lock.readLock().unlock();
        }

        lock.writeLock().lock();
        try {
            if (snapshotDirty) {
                snapshotCache = List.copyOf(leviathansById.values());
                snapshotDirty = false;
            }
            return snapshotCache;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return leviathansById.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public VirtualLeviathanState get(UUID id) {
        lock.readLock().lock();
        try {
            return leviathansById.get(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void upsert(VirtualLeviathanState state) {
        lock.writeLock().lock();
        try {
            VirtualLeviathanState previous = leviathansById.put(state.id(), state);

            if (AtlantisMod.LOGGER.isDebugEnabled()) {
                if (previous == null) {
                    AtlantisMod.LOGGER.debug("[Atlantis][leviathan] virtual upsert add id={} pos=({}, {}, {})",
                        shortId(state.id()),
                        round1(state.pos().x),
                        round1(state.pos().y),
                        round1(state.pos().z));
                } else {
                    AtlantisMod.LOGGER.debug("[Atlantis][leviathan] virtual upsert update id={} pos=({}, {}, {}) duplicatesRemoved={}",
                        shortId(state.id()),
                        round1(state.pos().x),
                        round1(state.pos().y),
                        round1(state.pos().z),
                        0);
                }
            }

            snapshotDirty = true;
            dirty = true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void remove(UUID id) {
        lock.writeLock().lock();
        try {
            if (leviathansById.remove(id) != null) {
                snapshotDirty = true;
                dirty = true;
                if (AtlantisMod.LOGGER.isDebugEnabled()) {
                    AtlantisMod.LOGGER.debug("[Atlantis][leviathan] virtual remove id={}", shortId(id));
                }
            } else if (AtlantisMod.LOGGER.isDebugEnabled()) {
                AtlantisMod.LOGGER.debug("[Atlantis][leviathan] virtual remove no-op id={}", shortId(id));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void flush() {
        lock.writeLock().lock();
        try {
            if (!dirty) {
                if (AtlantisMod.LOGGER.isDebugEnabled()) {
                    AtlantisMod.LOGGER.debug("[Atlantis][leviathan] virtual flush skipped (clean) path={}", statePath);
                }
                return;
            }
            save();
            dirty = false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void load() {
        if (!Files.exists(statePath)) {
            AtlantisMod.LOGGER.info("[Atlantis][leviathan] virtual store file not found path={} (starting empty)", statePath);
            return;
        }

        JsonObject root;
        try {
            String json = Files.readString(statePath, StandardCharsets.UTF_8);
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read virtual leviathan store path=" + statePath + " error=" + e.getMessage(), e);
        } catch (IllegalStateException | JsonParseException e) {
            throw new IllegalStateException("Invalid virtual leviathan JSON path=" + statePath + " error=" + e.getMessage(), e);
        }

        JsonElement arrElement = root.get("leviathans");
        if (arrElement == null || !arrElement.isJsonArray()) {
            throw new IllegalStateException("Virtual leviathan file missing required array key 'leviathans': path=" + statePath);
        }

        JsonArray arr = arrElement.getAsJsonArray();
        Map<UUID, VirtualLeviathanState> byId = new LinkedHashMap<>();
        for (int i = 0; i < arr.size(); i++) {
            JsonElement element = arr.get(i);
            if (!element.isJsonObject()) {
                throw new IllegalStateException("Virtual leviathan entry at index=" + i + " must be object");
            }

            JsonObject obj = element.getAsJsonObject();
            UUID id = parseUuid(obj, "id", i);
            double x = parseDouble(obj, "x", i);
            double y = parseDouble(obj, "y", i);
            double z = parseDouble(obj, "z", i);
            double headingX = parseDouble(obj, "hx", i);
            double headingZ = parseDouble(obj, "hz", i);
            long tick = parseLong(obj, "tick", i);
            String entityTypeId = parseOptionalString(obj, "entityTypeId", "minecraft:salmon");
            double spawnY = parseOptionalDouble(obj, "spawnY", y);
            double scaleMultiplier = parseOptionalDouble(obj, "scaleMultiplier", 1.0d);
            double damageMultiplier = parseOptionalDouble(obj, "damageMultiplier", 1.0d);
            double healthMultiplier = parseOptionalDouble(obj, "healthMultiplier", 1.0d);

            byId.put(id, new VirtualLeviathanState(id, new Vec3d(x, y, z), headingX, headingZ, tick, entityTypeId, spawnY, scaleMultiplier, damageMultiplier, healthMultiplier));
        }

        leviathansById.clear();
        leviathansById.putAll(byId);
        snapshotDirty = true;
        dirty = false;
        AtlantisMod.LOGGER.info("[Atlantis][leviathan] virtual store loaded {} records path={}", leviathansById.size(), statePath);
    }

    private void save() {
        try {
            Files.createDirectories(statePath.getParent());

            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (VirtualLeviathanState leviathan : leviathansById.values()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", leviathan.id().toString());
                obj.addProperty("x", leviathan.pos().x);
                obj.addProperty("y", leviathan.pos().y);
                obj.addProperty("z", leviathan.pos().z);
                obj.addProperty("hx", leviathan.headingX());
                obj.addProperty("hz", leviathan.headingZ());
                obj.addProperty("tick", leviathan.lastTick());
                obj.addProperty("entityTypeId", leviathan.entityTypeId());
                obj.addProperty("spawnY", leviathan.spawnYAtCreation());
                obj.addProperty("scaleMultiplier", leviathan.scaleMultiplier());
                obj.addProperty("damageMultiplier", leviathan.damageMultiplier());
                obj.addProperty("healthMultiplier", leviathan.healthMultiplier());
                arr.add(obj);
            }
            root.add("leviathans", arr);

            Files.writeString(statePath, GSON.toJson(root), StandardCharsets.UTF_8);
            if (AtlantisMod.LOGGER.isDebugEnabled()) {
                AtlantisMod.LOGGER.debug("[Atlantis][leviathan] virtual store save complete entries={} path={}", leviathansById.size(), statePath);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write virtual leviathan store path=" + statePath + " error=" + e.getMessage(), e);
        }
    }

    private static String shortId(UUID id) {
        String value = id.toString();
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private static String round1(double value) {
        return Double.toString(Math.round(value * 10.0d) / 10.0d);
    }

    private static UUID parseUuid(JsonObject obj, String key, int index) {
        JsonElement element = obj.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalStateException("Virtual leviathan entry[" + index + "] key=" + key + " must be string UUID");
        }
        try {
            return UUID.fromString(element.getAsString());
        } catch (Exception e) {
            throw new IllegalStateException("Virtual leviathan entry[" + index + "] key=" + key + " invalid UUID=" + element.getAsString());
        }
    }

    private static double parseDouble(JsonObject obj, String key, int index) {
        JsonElement element = obj.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalStateException("Virtual leviathan entry[" + index + "] key=" + key + " must be number");
        }
        double value;
        try {
            value = element.getAsDouble();
        } catch (Exception e) {
            throw new IllegalStateException("Virtual leviathan entry[" + index + "] key=" + key + " invalid number");
        }
        if (!Double.isFinite(value)) {
            throw new IllegalStateException("Virtual leviathan entry[" + index + "] key=" + key + " must be finite");
        }
        return value;
    }

    private static long parseLong(JsonObject obj, String key, int index) {
        JsonElement element = obj.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalStateException("Virtual leviathan entry[" + index + "] key=" + key + " must be long");
        }
        try {
            return element.getAsLong();
        } catch (Exception e) {
            throw new IllegalStateException("Virtual leviathan entry[" + index + "] key=" + key + " invalid long");
        }
    }

    private static String parseOptionalString(JsonObject obj, String key, String defaultValue) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return defaultValue;
        }
        String value = element.getAsString().trim();
        return value.isEmpty() ? defaultValue : value;
    }

    private static double parseOptionalDouble(JsonObject obj, String key, double defaultValue) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return defaultValue;
        }
        try {
            double value = element.getAsDouble();
            return Double.isFinite(value) ? value : defaultValue;
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    public record VirtualLeviathanState(UUID id,
                                        Vec3d pos,
                                        double headingX,
                                        double headingZ,
                                        long lastTick,
                                        String entityTypeId,
                                        double spawnYAtCreation,
                                        double scaleMultiplier,
                                        double damageMultiplier,
                                        double healthMultiplier) {
        public VirtualLeviathanState {
            if (id == null) {
                throw new IllegalArgumentException("VirtualLeviathanState.id must not be null");
            }
            if (pos == null) {
                throw new IllegalArgumentException("VirtualLeviathanState.pos must not be null");
            }
            if (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)) {
                throw new IllegalArgumentException("VirtualLeviathanState.pos must be finite");
            }
            if (!Double.isFinite(headingX) || !Double.isFinite(headingZ)) {
                throw new IllegalArgumentException("VirtualLeviathanState heading must be finite");
            }
            if (entityTypeId == null || entityTypeId.isBlank()) {
                throw new IllegalArgumentException("VirtualLeviathanState.entityTypeId must not be blank");
            }
            if (!Double.isFinite(spawnYAtCreation)) {
                throw new IllegalArgumentException("VirtualLeviathanState.spawnYAtCreation must be finite");
            }
            if (!Double.isFinite(scaleMultiplier) || scaleMultiplier <= 0.0d) {
                throw new IllegalArgumentException("VirtualLeviathanState.scaleMultiplier must be finite and > 0");
            }
            if (!Double.isFinite(damageMultiplier) || damageMultiplier <= 0.0d) {
                throw new IllegalArgumentException("VirtualLeviathanState.damageMultiplier must be finite and > 0");
            }
            if (!Double.isFinite(healthMultiplier) || healthMultiplier <= 0.0d) {
                throw new IllegalArgumentException("VirtualLeviathanState.healthMultiplier must be finite and > 0");
            }

            double len = Math.sqrt(headingX * headingX + headingZ * headingZ);
            if (len < 1.0e-6d) {
                throw new IllegalArgumentException("VirtualLeviathanState heading must be non-zero for id=" + id);
            }

            headingX /= len;
            headingZ /= len;
        }

        public VirtualLeviathanState withPos(Vec3d newPos, long tick) {
            return new VirtualLeviathanState(id, newPos, headingX, headingZ, tick, entityTypeId, spawnYAtCreation, scaleMultiplier, damageMultiplier, healthMultiplier);
        }

        public VirtualLeviathanState withHeading(double newHeadingX, double newHeadingZ, long tick) {
            return new VirtualLeviathanState(id, pos, newHeadingX, newHeadingZ, tick, entityTypeId, spawnYAtCreation, scaleMultiplier, damageMultiplier, healthMultiplier);
        }
    }
}
