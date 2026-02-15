package com.silver.atlantis.leviathan;

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

public final class VirtualLeviathanStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String FILE_NAME = "atlantis-leviathans-virtual.json";

    private final Path statePath;
    private final List<VirtualLeviathanState> leviathans = new ArrayList<>();
    private boolean dirty;

    public VirtualLeviathanStore() {
        this.statePath = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        load();
    }

    public synchronized List<VirtualLeviathanState> snapshot() {
        return new ArrayList<>(leviathans);
    }

    public synchronized int size() {
        return leviathans.size();
    }

    public synchronized VirtualLeviathanState get(UUID id) {
        for (VirtualLeviathanState state : leviathans) {
            if (state.id().equals(id)) {
                return state;
            }
        }
        return null;
    }

    public synchronized void upsert(VirtualLeviathanState state) {
        int firstIndex = -1;
        for (int i = 0; i < leviathans.size(); i++) {
            if (leviathans.get(i).id().equals(state.id())) {
                if (firstIndex == -1) {
                    firstIndex = i;
                    leviathans.set(i, state);
                } else {
                    leviathans.remove(i);
                    i--;
                }
            }
        }

        if (firstIndex == -1) {
            leviathans.add(state);
        }

        dirty = true;
    }

    public synchronized void remove(UUID id) {
        if (leviathans.removeIf(state -> state.id().equals(id))) {
            dirty = true;
        }
    }

    public synchronized void flush() {
        if (!dirty) {
            return;
        }
        save();
        dirty = false;
    }

    private void load() {
        if (!Files.exists(statePath)) {
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

            byId.put(id, new VirtualLeviathanState(id, new Vec3d(x, y, z), headingX, headingZ, tick));
        }

        leviathans.clear();
        leviathans.addAll(byId.values());
        dirty = false;
    }

    private synchronized void save() {
        try {
            Files.createDirectories(statePath.getParent());

            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (VirtualLeviathanState leviathan : leviathans) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", leviathan.id().toString());
                obj.addProperty("x", leviathan.pos().x);
                obj.addProperty("y", leviathan.pos().y);
                obj.addProperty("z", leviathan.pos().z);
                obj.addProperty("hx", leviathan.headingX());
                obj.addProperty("hz", leviathan.headingZ());
                obj.addProperty("tick", leviathan.lastTick());
                arr.add(obj);
            }
            root.add("leviathans", arr);

            Files.writeString(statePath, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write virtual leviathan store path=" + statePath + " error=" + e.getMessage(), e);
        }
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

    public record VirtualLeviathanState(UUID id, Vec3d pos, double headingX, double headingZ, long lastTick) {
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

            double len = Math.sqrt(headingX * headingX + headingZ * headingZ);
            if (len < 1.0e-6d) {
                throw new IllegalArgumentException("VirtualLeviathanState heading must be non-zero for id=" + id);
            }

            headingX /= len;
            headingZ /= len;
        }

        public VirtualLeviathanState withPos(Vec3d newPos, long tick) {
            return new VirtualLeviathanState(id, newPos, headingX, headingZ, tick);
        }

        public VirtualLeviathanState withHeading(double newHeadingX, double newHeadingZ, long tick) {
            return new VirtualLeviathanState(id, pos, newHeadingX, newHeadingZ, tick);
        }
    }
}
