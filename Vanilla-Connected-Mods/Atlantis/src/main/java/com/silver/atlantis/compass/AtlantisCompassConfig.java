package com.silver.atlantis.compass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AtlantisCompassConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String FILE_NAME = "atlantis-compass.json";

    public final int crystalIntervalSeconds;

    private AtlantisCompassConfig(int crystalIntervalSeconds) {
        this.crystalIntervalSeconds = crystalIntervalSeconds;
    }

    public long crystalIntervalTicks() {
        return (long) crystalIntervalSeconds * 20L;
    }

    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static AtlantisCompassConfig loadOrCreateStrict() {
        Path path = configPath();
        if (!Files.exists(path)) {
            write(path, defaults());
        }
        return loadStrict(path);
    }

    public static AtlantisCompassConfig loadStrict(Path path) {
        JsonObject root;
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (IOException e) {
            throw new ValidationException(List.of("io: unable to read config path=" + path + " error=" + e.getMessage()));
        } catch (IllegalStateException | JsonParseException e) {
            throw new ValidationException(List.of("json: invalid JSON object in " + path + " error=" + e.getMessage()));
        }

        List<String> errors = new ArrayList<>();
        enforceKnownKeys(root, errors);

        Integer crystalIntervalSeconds = requireInt(root, "crystalIntervalSeconds", errors);

        if (crystalIntervalSeconds != null && crystalIntervalSeconds < 1) {
            errors.add("crystalIntervalSeconds must be >= 1 (was " + crystalIntervalSeconds + ")");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        return new AtlantisCompassConfig(crystalIntervalSeconds);
    }

    public static AtlantisCompassConfig defaults() {
        return new AtlantisCompassConfig(300);
    }

    public static void write(Path path, AtlantisCompassConfig config) {
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("crystalIntervalSeconds", config.crystalIntervalSeconds);
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ValidationException(List.of("io: unable to write config path=" + path + " error=" + e.getMessage()));
        }
    }

    private static void enforceKnownKeys(JsonObject root, List<String> errors) {
        Set<String> known = new LinkedHashSet<>(List.of("crystalIntervalSeconds"));
        for (String key : root.keySet()) {
            if (!known.contains(key)) {
                errors.add("unknown key: " + key);
            }
        }
    }

    private static Integer requireInt(JsonObject root, String key, List<String> errors) {
        JsonElement e = root.get(key);
        if (e == null || e.isJsonNull()) {
            errors.add(key + " is required and must be integer");
            return null;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            errors.add(key + " must be integer");
            return null;
        }

        String literal = e.getAsJsonPrimitive().getAsString();
        try {
            BigDecimal decimal = new BigDecimal(literal);
            if (decimal.stripTrailingZeros().scale() > 0) {
                errors.add(key + " must be integer (was " + literal + ")");
                return null;
            }
            return decimal.intValueExact();
        } catch (Exception ex) {
            errors.add(key + " must be valid int (was " + literal + ")");
            return null;
        }
    }

    public static final class ValidationException extends RuntimeException {
        private final List<String> errors;

        public ValidationException(List<String> errors) {
            super(String.join("; ", errors));
            this.errors = List.copyOf(errors);
        }

        public List<String> errors() {
            return errors;
        }
    }
}
